package com.webexpenses.claims.service;

import com.webexpenses.claims.dto.AuditEventResponse;
import com.webexpenses.claims.dto.ClaimResponse;
import com.webexpenses.claims.dto.CreateClaimRequest;
import com.webexpenses.claims.dto.RejectClaimRequest;
import com.webexpenses.claims.entity.*;
import com.webexpenses.claims.repository.ClaimAuditEventRepository;
import com.webexpenses.claims.repository.ExpenseClaimRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service layer for expense claim operations.
 */
@Service
public class ExpenseClaimService {

    private final ExpenseClaimRepository claimRepository;
    private final ClaimAuditEventRepository auditRepository;

    public ExpenseClaimService(ExpenseClaimRepository claimRepository,
                               ClaimAuditEventRepository auditRepository) {
        this.claimRepository = claimRepository;
        this.auditRepository = auditRepository;
    }

    /**
     * Submit a new expense claim. Sets status to PENDING and records
     * a SUBMITTED audit event.
     */
    public ClaimResponse submitClaim(CreateClaimRequest request, String username, UUID userId) {
        Category category;
        try {
            category = Category.valueOf(request.category().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid category: " + request.category());
        }

        LocalDate expenseDate;
        try {
            expenseDate = LocalDate.parse(request.expenseDate());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid date format: " + request.expenseDate());
        }

        ExpenseClaim claim = claimRepository.save(ExpenseClaim.builder()
                .employeeId(userId)
                .description(request.description())
                .amount(request.amount())
                .expenseDate(expenseDate)
                .category(category)
                .build());

        // Record audit event
        auditRepository.save(ClaimAuditEvent.builder()
                .claimId(claim.getId())
                .action(AuditAction.SUBMITTED)
                .performedBy(username)
                .build());

        return toResponse(claim);
    }

    /**
     * Retrieve claims filtered by user and/or status.
     *
     * @param userId  filter by employee
     * @param status  filter by status
     * @return list of matching claims
     */
    public List<ClaimResponse> getClaims(Optional<UUID> userId, Optional<ClaimStatus> status) {
        List<ExpenseClaim> claims = userId
                .map(uid -> status
                        .map(s -> claimRepository.findByEmployeeIdAndStatus(uid, s))
                        .orElseGet(() -> claimRepository.findByEmployeeId(uid)))
                .orElseGet(() -> status
                        .map(claimRepository::findByStatus)
                        .orElseThrow());
        return claims.stream().map(this::toResponse).toList();
    }

    /**
     * Retrieve a single claim by ID.
     *
     * @param claimId the claim UUID
     * @return the claim
     */
    public ClaimResponse getClaim(UUID claimId) {
        ExpenseClaim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found"));
        return toResponse(claim);
    }

    /**
     * Approve a pending claim. Records an APPROVED audit event.
     *
     * @param claimId   the claim to approve
     * @param username  the approver's username
     * @return the updated claim
     */
    public ClaimResponse approveClaim(UUID claimId, String username) {
        ExpenseClaim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found"));

        if (claim.getStatus() != ClaimStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Claim has already been " + claim.getStatus().name().toLowerCase());
        }

        claim.setStatus(ClaimStatus.APPROVED);
        claim = claimRepository.save(claim);

        auditRepository.save(ClaimAuditEvent.builder()
                .claimId(claim.getId())
                .action(AuditAction.APPROVED)
                .performedBy(username)
                .build());

        return toResponse(claim);
    }

    /**
     * Reject a pending claim with a reason. Records a REJECTED audit event.
     *
     * @param claimId   the claim to reject
     * @param request   contains the rejection reason
     * @param username  the approver's username
     * @return the updated claim
     */
    public ClaimResponse rejectClaim(UUID claimId, RejectClaimRequest request, String username) {
        ExpenseClaim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found"));

        if (claim.getStatus() != ClaimStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Claim has already been " + claim.getStatus().name().toLowerCase());
        }

        claim.setStatus(ClaimStatus.REJECTED);
        claim.setRejectionReason(request.reason());
        claim = claimRepository.save(claim);

        auditRepository.save(ClaimAuditEvent.builder()
                .claimId(claim.getId())
                .action(AuditAction.REJECTED)
                .performedBy(username)
                .details(request.reason())
                .build());

        return toResponse(claim);
    }

    /**
     * Retrieve the audit trail for a given claim.
     *
     * @param claimId the claim UUID
     * @return ordered list of audit events
     */
    public List<AuditEventResponse> getAuditTrail(UUID claimId) {
        if (!claimRepository.existsById(claimId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found");
        }

        return auditRepository.findByClaimIdOrderByPerformedAtAsc(claimId).stream()
                .map(event -> new AuditEventResponse(
                        event.getId(),
                        event.getClaimId(),
                        event.getAction().name(),
                        event.getPerformedBy(),
                        event.getPerformedAt(),
                        event.getDetails()))
                .toList();
    }

    /**
     * Maps an ExpenseClaim entity to its API response representation.
     */
    private ClaimResponse toResponse(ExpenseClaim claim) {
        return new ClaimResponse(
                claim.getId(),
                claim.getEmployeeId(),
                claim.getDescription(),
                claim.getAmount(),
                claim.getExpenseDate(),
                claim.getCategory().name(),
                claim.getStatus().name(),
                claim.getRejectionReason(),
                claim.getCreatedAt()
        );
    }
}
