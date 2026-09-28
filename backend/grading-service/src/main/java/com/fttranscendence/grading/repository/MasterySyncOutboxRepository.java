package com.fttranscendence.grading.repository;

import com.fttranscendence.grading.model.MasterySyncOutbox;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MasterySyncOutboxRepository extends JpaRepository<MasterySyncOutbox, Long> {

  List<MasterySyncOutbox> findTop25ByDeliveredAtIsNullOrderByIdAsc();
}
