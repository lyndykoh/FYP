package com.fttranscendence.grading.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * Transactional outbox for a tutor-approved marking event. Delivery is retried and Learning
 * deduplicates using {@code eventKey}; no raw OCR or AI content is ever stored in this payload.
 */
@Entity
@Table(
    name = "mastery_sync_outbox",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_mastery_sync_outbox_event", columnNames = "event_key"))
public class MasterySyncOutbox {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "event_key", nullable = false, length = 120)
  private String eventKey;

  @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
  @Column(name = "event_type", nullable = false, length = 32)
  private EventType eventType;

  @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
  private String payload;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "last_error", columnDefinition = "TEXT")
  private String lastError;

  @Column(name = "delivered_at")
  private LocalDateTime deliveredAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  protected MasterySyncOutbox() {}

  public MasterySyncOutbox(String eventKey, EventType eventType, String payload) {
    if (eventKey == null || eventKey.isBlank())
      throw new IllegalArgumentException("Outbox event key is required");
    if (eventType == null) throw new IllegalArgumentException("Outbox event type is required");
    if (payload == null || payload.isBlank())
      throw new IllegalArgumentException("Outbox payload is required");
    this.eventKey = eventKey.trim();
    this.eventType = eventType;
    this.payload = payload;
  }

  public void delivered() {
    attemptCount++;
    lastError = null;
    deliveredAt = LocalDateTime.now();
  }

  public void failed(String message) {
    attemptCount++;
    lastError =
        message == null
            ? "Learning synchronization failed"
            : message.substring(0, Math.min(message.length(), 2000));
  }

  @PrePersist
  protected void beforeInsert() {
    createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
  }

  public Long getId() {
    return id;
  }

  public String getEventKey() {
    return eventKey;
  }

  public EventType getEventType() {
    return eventType;
  }

  public String getPayload() {
    return payload;
  }

  public int getAttemptCount() {
    return attemptCount;
  }

  public String getLastError() {
    return lastError;
  }

  public LocalDateTime getDeliveredAt() {
    return deliveredAt;
  }

  public enum EventType {
    APPROVED_MARKING,
    MARKING_REVIEW_STATE
  }
}
