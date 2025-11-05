package com.gephub.kyc_service.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "kyc_results")
public class KycResult {
    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;

    @Column(name = "liveness_score")
    private Double livenessScore;

    @Column(name = "face_match_score")
    private Double faceMatchScore;

    @Column(name = "reason_codes")
    private String[] reasonCodes;

    @Column(name = "manual_review", nullable = false)
    private boolean manualReview = false;

    @Column(name = "finalized_at")
    private OffsetDateTime finalizedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }
    public Double getLivenessScore() { return livenessScore; }
    public void setLivenessScore(Double livenessScore) { this.livenessScore = livenessScore; }
    public Double getFaceMatchScore() { return faceMatchScore; }
    public void setFaceMatchScore(Double faceMatchScore) { this.faceMatchScore = faceMatchScore; }
    public String[] getReasonCodes() { return reasonCodes; }
    public void setReasonCodes(String[] reasonCodes) { this.reasonCodes = reasonCodes; }
    public boolean isManualReview() { return manualReview; }
    public void setManualReview(boolean manualReview) { this.manualReview = manualReview; }
    public OffsetDateTime getFinalizedAt() { return finalizedAt; }
    public void setFinalizedAt(OffsetDateTime finalizedAt) { this.finalizedAt = finalizedAt; }
}


