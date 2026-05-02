package com.rca.repository;

import com.rca.model.RcaReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RcaReportRepository extends JpaRepository<RcaReport, UUID> {

    Optional<RcaReport> findByAlertId(UUID alertId);

    List<RcaReport> findByStatus(RcaReport.RcaStatus status);
}
