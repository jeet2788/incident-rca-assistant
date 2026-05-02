package com.rca.repository;

import com.rca.model.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AlertRepository extends JpaRepository<Alert, UUID> {

    List<Alert> findByService(String service);

    List<Alert> findByStatus(Alert.AlertStatus status);

    List<Alert> findByServiceAndStatus(String service, Alert.AlertStatus status);
}
