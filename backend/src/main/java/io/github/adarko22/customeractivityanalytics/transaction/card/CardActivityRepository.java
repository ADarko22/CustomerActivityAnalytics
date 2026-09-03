package io.github.adarko22.customeractivityanalytics.transaction.card;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CardActivityRepository
    extends JpaRepository<CardActivity, UUID>, JpaSpecificationExecutor<CardActivity> {}
