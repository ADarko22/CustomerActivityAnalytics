package io.github.adarko22.customeractivityanalytics.transaction.crypto;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CryptoActivityRepository
    extends JpaRepository<CryptoActivity, UUID>, JpaSpecificationExecutor<CryptoActivity> {}
