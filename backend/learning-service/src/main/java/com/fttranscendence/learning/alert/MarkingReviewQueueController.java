package com.fttranscendence.learning.alert;

import com.fttranscendence.learning.security.AuthenticatedUser;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lists only the authenticated tutor's submissions that still require review. */
@RestController
@RequestMapping("/api/learning/tutor/marking-reviews")
public class MarkingReviewQueueController {
  private final MarkingReviewQueueService queue;

  public MarkingReviewQueueController(MarkingReviewQueueService queue) {
    this.queue = queue;
  }

  @GetMapping
  public List<MarkingReviewQueueResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
    return queue.pendingForTutor(user.userId());
  }
}
