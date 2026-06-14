package com.webexpenses.claims.repository;

import com.webexpenses.claims.entity.ClaimAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClaimAuditEventRepository extends JpaRepository<ClaimAuditEvent, UUID> {

    List<ClaimAuditEvent> findByClaimIdOrderByPerformedAtAsc(UUID claimId);
}
