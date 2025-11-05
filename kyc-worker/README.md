# gephub kyc-worker (Python)

A background worker that consumes KYC processing jobs from RabbitMQ, reads uploaded media from the shared volume, runs liveness/anti-spoof checks (stub for now), and calls kyc-service internal completion endpoint.

## Env vars
- RABBITMQ_URL: amqp://guest:guest@rabbitmq:5672/
- QUEUE_NAME: kyc-processing
- KYC_SERVICE_BASE_URL: http://kyc-service:8081
- WORKER_TOKEN: shared secret to call internal completion
- MEDIA_ROOT: /var/lib/gephub/kyc-media (mounted read-only)

## Run locally (Docker)
The root docker-compose sets this up; the volume `kycmedia` is mounted read-only.

