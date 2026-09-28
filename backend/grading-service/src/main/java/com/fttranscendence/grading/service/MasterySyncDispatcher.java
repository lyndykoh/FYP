package com.fttranscendence.grading.service;

import com.fttranscendence.grading.model.MasterySyncOutbox;
import com.fttranscendence.grading.repository.MasterySyncOutboxRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Retries only durable outbox records; failed remote calls never discard tutor approval. */
@Service
public class MasterySyncDispatcher {
  private final MasterySyncOutboxRepository outboxRepository;
  private final LearningAuthorizationClient learningClient;

  public MasterySyncDispatcher(
      MasterySyncOutboxRepository outboxRepository, LearningAuthorizationClient learningClient) {
    this.outboxRepository = outboxRepository;
    this.learningClient = learningClient;
  }

  @Scheduled(fixedDelayString = "${learning.service.sync.retry-delay-ms:60000}")
  public void dispatchPending() {
    List<MasterySyncOutbox> pendingEvents =
        outboxRepository.findTop25ByDeliveredAtIsNullOrderByIdAsc();

    pendingEvents.forEach(this::dispatchOne);
  }

  @Transactional
  public void dispatchOne(MasterySyncOutbox outboxEvent) {
    if (outboxEvent.getDeliveredAt() != null) {
      return;
    }

    try {
      learningClient.sync(outboxEvent.getEventType(), outboxEvent.getPayload());
      outboxEvent.delivered();
    } catch (RuntimeException exception) {
      outboxEvent.failed(exception.getMessage());
    }

    outboxRepository.saveAndFlush(outboxEvent);
  }
}
