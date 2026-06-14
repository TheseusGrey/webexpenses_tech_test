package com.webexpenses.claims.repository;

import com.webexpenses.claims.entity.ClaimStatus;
import com.webexpenses.claims.entity.ExpenseClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExpenseClaimRepository extends JpaRepository<ExpenseClaim, UUID> {

    List<ExpenseClaim> findByEmployeeId(UUID employeeId);

    List<ExpenseClaim> findByStatus(ClaimStatus status);

    List<ExpenseClaim> findByEmployeeIdAndStatus(UUID employeeId, ClaimStatus status);
}
