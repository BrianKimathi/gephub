import json
import os
import signal
import sys
import time
from dataclasses import dataclass

import pika
import requests
import cv2
import numpy as np
from PIL import Image
try:
    import onnxruntime as ort
except Exception:
    ort = None


@dataclass
class Config:
    rabbitmq_url: str
    queue_name: str
    kyc_base_url: str
    worker_token: str
    media_root: str
    # Liveness model config
    liveness_model: str | None
    liveness_input_name: str
    liveness_input_w: int
    liveness_input_h: int
    liveness_mean: float
    liveness_std: float
    liveness_output_index: int
    # ArcFace model config
    arcface_model: str | None
    arcface_input_name: str


def get_config() -> Config:
    return Config(
        rabbitmq_url=os.getenv("RABBITMQ_URL", "amqp://guest:guest@rabbitmq:5672/"),
        queue_name=os.getenv("QUEUE_NAME", "kyc-processing"),
        kyc_base_url=os.getenv("KYC_SERVICE_BASE_URL", "http://kyc-service:8081"),
        worker_token=os.getenv("WORKER_TOKEN", ""),
        media_root=os.getenv("MEDIA_ROOT", "/var/lib/gephub/kyc-media"),
        liveness_model=os.getenv("LIVENESS_MODEL_PATH"),
        liveness_input_name=os.getenv("LIVENESS_INPUT_NAME", "input"),
        liveness_input_w=int(os.getenv("LIVENESS_INPUT_W", "112")),
        liveness_input_h=int(os.getenv("LIVENESS_INPUT_H", "112")),
        liveness_mean=float(os.getenv("LIVENESS_MEAN", "0.5")),
        liveness_std=float(os.getenv("LIVENESS_STD", "0.5")),
        liveness_output_index=int(os.getenv("LIVENESS_OUTPUT_INDEX", "0")),
        arcface_model=os.getenv("ARCFACE_MODEL_PATH"),
        arcface_input_name=os.getenv("ARCFACE_INPUT_NAME", "data"),
    )


def on_message(ch, method, properties, body):
    cfg = on_message.cfg
    try:
        msg = json.loads(body.decode("utf-8")) if body else {}
        session_id = msg.get("sessionId")
        prompts = msg.get("prompts") or ["look_left","look_right","look_up","look_down"]
        if not session_id:
            ch.basic_ack(delivery_tag=method.delivery_tag)
            return

        # Basic liveness heuristic on selfie video: motion magnitude over time
        session_dir = os.path.join(cfg.media_root, session_id)
        selfie_video = None
        if os.path.isdir(session_dir):
            for name in os.listdir(session_dir):
                low = name.lower()
                if low.startswith("selfie_") and (low.endswith(".mp4") or low.endswith(".mov") or low.endswith(".avi")):
                    selfie_video = os.path.join(session_dir, name)
                    break

        score = 0.0
        reasons = []
        face_match = None
        if selfie_video and os.path.exists(selfie_video):
            cap = cv2.VideoCapture(selfie_video)
            ok, prev = cap.read()
            frames = 0
            motion_accum = 0.0
            vx = []
            vy = []
            sampled_frames = []
            while ok:
                ok2, frame = cap.read()
                if not ok2:
                    break
                prev_gray = cv2.cvtColor(prev, cv2.COLOR_BGR2GRAY)
                gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
                flow = cv2.calcOpticalFlowFarneback(prev_gray, gray, None, 0.5, 3, 15, 3, 5, 1.2, 0)
                fx, fy = flow[...,0].mean(), flow[...,1].mean()
                vx.append(float(fx))
                vy.append(float(fy))
                diff = cv2.absdiff(prev_gray, gray)
                motion = float(np.mean(diff)) / 255.0
                motion_accum += motion
                frames += 1
                prev = frame
                if frames % 5 == 0:
                    sampled_frames.append(frame.copy())
            cap.release()
            if frames > 0:
                avg_motion = motion_accum / max(frames, 1)
                # Heuristic: normalize to [0,1], require some movement
                score = min(1.0, max(0.0, avg_motion * 4.0))
                if score < 0.2:
                    reasons.append("low_motion")
                # Directional check per prompt by splitting into segments
                segs = max(1, len(prompts))
                seg_len = max(1, len(vx) // segs)
                def check_dir(px, py, expected):
                    mx = float(np.mean(px))
                    my = float(np.mean(py))
                    if expected == "look_left":
                        return mx < -0.05
                    if expected == "look_right":
                        return mx > 0.05
                    if expected == "look_up":
                        return my < -0.05
                    if expected == "look_down":
                        return my > 0.05
                    return True
                for i, p in enumerate(prompts):
                    start = i * seg_len
                    end = (i+1) * seg_len if i < segs-1 else len(vx)
                    if start >= len(vx):
                        reasons.append(f"missing_segment_{i}")
                        score = min(score, 0.5)
                        continue
                    if not check_dir(vx[start:end], vy[start:end], p):
                        reasons.append(f"prompt_failed_{p}")
                        score = min(score, 0.6)

                # Liveness model via ONNX (optional)
                live_scores = []
                if cfg.liveness_model and ort is not None and os.path.exists(cfg.liveness_model):
                    try:
                        if not hasattr(on_message, "_live_sess"):
                            on_message._live_sess = ort.InferenceSession(cfg.liveness_model, providers=["CPUExecutionProvider"])  # type: ignore
                        sess = on_message._live_sess
                        for f in sampled_frames[:16]:
                            img = Image.fromarray(cv2.cvtColor(f, cv2.COLOR_BGR2RGB)).resize((cfg.liveness_input_w, cfg.liveness_input_h))
                            arr = (np.asarray(img).astype(np.float32) / 255.0 - cfg.liveness_mean) / cfg.liveness_std
                            arr = np.transpose(arr, (2, 0, 1))[None, ...]
                            out = sess.run(None, {cfg.liveness_input_name: arr})
                            y = out[0]
                            if isinstance(y, list):
                                y = y[0]
                            try:
                                prob = float(y.squeeze()[cfg.liveness_output_index])
                            except Exception:
                                prob = float(np.mean(y))
                            live_scores.append(prob)
                        if live_scores:
                            score = float(np.clip(0.5 * score + 0.5 * (sum(live_scores) / len(live_scores)), 0.0, 1.0))
                    except Exception:
                        reasons.append("liveness_model_error")
        else:
            reasons.append("no_selfie_video")

        # Face match (ArcFace ONNX): compare ID front image with a frame from video
        if cfg.arcface_model and ort is not None and os.path.exists(cfg.arcface_model):
            id_front_path = None
            if os.path.isdir(session_dir):
                for name in os.listdir(session_dir):
                    low = name.lower()
                    if low.startswith("id_front") and (low.endswith(".jpg") or low.endswith(".jpeg") or low.endswith(".png")):
                        id_front_path = os.path.join(session_dir, name)
                        break
            selfie_frame = None
            if selfie_video and os.path.exists(selfie_video):
                cap = cv2.VideoCapture(selfie_video)
                total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
                if total > 0:
                    cap.set(cv2.CAP_PROP_POS_FRAMES, total // 2)
                    ok, frame = cap.read()
                    if ok:
                        selfie_frame = frame
                cap.release()
            if id_front_path and selfie_frame is not None:
                try:
                    if not hasattr(on_message, "_arc_sess"):
                        on_message._arc_sess = ort.InferenceSession(cfg.arcface_model, providers=["CPUExecutionProvider"])  # type: ignore
                    sess = on_message._arc_sess
                    def embed(img_bgr: np.ndarray) -> np.ndarray:
                        img = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2RGB)
                        img = cv2.resize(img, (112, 112))
                        arr = img.astype(np.float32) / 255.0
                        arr = (arr - 0.5) / 0.5
                        arr = np.transpose(arr, (2, 0, 1))[None, ...]
                        out = sess.run(None, {cfg.arcface_input_name: arr})
                        v = out[0].squeeze().astype(np.float32)
                        v /= (np.linalg.norm(v) + 1e-6)
                        return v
                    id_img = cv2.imdecode(np.fromfile(id_front_path, dtype=np.uint8), cv2.IMREAD_COLOR)
                    if id_img is not None:
                        v1 = embed(id_img)
                        v2 = embed(selfie_frame)
                        face_match = float(np.dot(v1, v2))
                except Exception:
                    reasons.append("face_match_error")
        payload = {
            "sessionId": session_id,
            "livenessScore": score,
            "reasonCodes": reasons,
            "manualReview": False,
        }
        if face_match is not None:
            payload["faceMatchScore"] = face_match
        headers = {
            "Content-Type": "application/json",
            "X-Gephub-Worker-Token": cfg.worker_token,
        }
        url = f"{cfg.kyc_base_url}/api/v1/kyc/internal/complete"
        resp = requests.post(url, headers=headers, data=json.dumps(payload), timeout=10)
        resp.raise_for_status()
        ch.basic_ack(delivery_tag=method.delivery_tag)
    except Exception as e:
        # Nack with requeue=False to avoid hot-looping; rely on DLQ later
        ch.basic_nack(delivery_tag=method.delivery_tag, requeue=False)


def main():
    cfg = get_config()
    params = pika.URLParameters(cfg.rabbitmq_url)
    connection = pika.BlockingConnection(params)
    channel = connection.channel()
    channel.queue_declare(queue=cfg.queue_name, durable=True)
    on_message.cfg = cfg
    channel.basic_qos(prefetch_count=1)
    channel.basic_consume(queue=cfg.queue_name, on_message_callback=on_message)

    def handle_sigterm(signum, frame):
        try:
            channel.stop_consuming()
        except Exception:
            pass
        try:
            connection.close()
        except Exception:
            pass
        sys.exit(0)

    signal.signal(signal.SIGTERM, handle_sigterm)
    print("kyc-worker started; waiting for messages...")
    try:
        channel.start_consuming()
    except KeyboardInterrupt:
        handle_sigterm(None, None)


if __name__ == "__main__":
    main()


