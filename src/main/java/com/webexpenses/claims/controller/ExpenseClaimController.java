package com.webexpenses.claims.controller;

import com.webexpenses.claims.dto.AuditEventResponse;
import com.webexpenses.claims.dto.ClaimResponse;
import com.webexpenses.claims.dto.CreateClaimRequest;
import com.webexpenses.claims.dto.RejectClaimRequest;
import com.webexpenses.claims.entity.ClaimStatus;
import com.webexpenses.claims.entity.Role;
import com.webexpenses.claims.service.ExpenseClaimService;
import com.webexpenses.claims.service.TokenService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * REST controller for expense claim operations.
 *
 * Endpoints:
 * - POST   /api/claims              — submit new claim (EMPLOYEE only)
 * - GET    /api/claims?user=&status= — list claims with filters
 * - GET    /api/claims/{id}         — view single claim
 * - PATCH  /api/claims/{id}/approve — approve claim (APPROVER only)
 * - PATCH  /api/claims/{id}/reject  — reject claim (APPROVER only)
 * - GET    /api/claims/{id}/audit   — view audit trail (APPROVER only)
 *
 * Authorization is enforced at the controller level by inspecting JWT claims.
 * The "role" claim determines what operations are permitted.
 *
 * NOTE: GET /api/claims with no query params returns 400. In a production system,
 * this could be extended to support admin/support use cases (e.g. return all claims
 * with pagination).
 */
@RestController
@RequestMapping("/api/claims")
public class ExpenseClaimController {

    private final ExpenseClaimService claimService;
    private final TokenService tokenService;

    public ExpenseClaimController(
            ExpenseClaimService claimService,
            TokenService tokenService) {
        this.claimService = claimService;
        this.tokenService = tokenService;
    }

    @RolesAllowed("EMPLOYEE")
    @PostMapping(consumes = "application/json")
    @CircuitBreaker(name = "claimsService")
    @Retry(name = "claimsService")
    public ResponseEntity<ClaimResponse> submitClaim(
            @Valid @RequestBody CreateClaimRequest request,
            JwtAuthenticationToken auth) {

        TokenService.TokenClaims claims = tokenService.extractUser(auth);
        ClaimResponse response = claimService.submitClaim(request, claims.username(), claims.userId());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List claims with filtering.
     * - ?user={id} — EMPLOYEE can only query own ID; APPROVER can query any
     * - ?status={status} — APPROVER only
     * - Both params can be combined (APPROVER only)
     * - No params → 400
     */
    @RolesAllowed({"EMPLOYEE", "APPROVER"})
    @GetMapping
    @CircuitBreaker(name = "claimsService")
    @Retry(name = "claimsService")
    public ResponseEntity<List<ClaimResponse>> getClaims(
            @RequestParam(required = false) UUID id,
            @RequestParam(required = false) String status,
            JwtAuthenticationToken auth) {

        // Calling this a 400 for now, if you wanted an "ADMIN" role, having an option to return
        // everything on everyone could be useful here (also for support/debugging)
        if (id == null && status == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At least one filter (id or status) is required");
        }

        TokenService.TokenClaims claims = tokenService.extractUser(auth);

        if (claims.role() == Role.EMPLOYEE) {
            if (!claims.userId().equals(id)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
            }
        }

        ClaimStatus parsedStatus = null;
        if (status != null) {
            try {
                parsedStatus = ClaimStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid status: " + status);
            }
        }

        List<ClaimResponse> results = claimService.getClaims(
                Optional.ofNullable(id), Optional.ofNullable(parsedStatus));
        return ResponseEntity.ok(results);
    }

    /**
     * View a single claim by ID.
     * EMPLOYEE can only view own claims; APPROVER can view any.
     */
    @RolesAllowed({"EMPLOYEE", "APPROVER"})
    @GetMapping("/{id}")
    @CircuitBreaker(name = "claimsService")
    @Retry(name = "claimsService")
    public ResponseEntity<ClaimResponse> getClaim(
            @PathVariable UUID id,
            JwtAuthenticationToken auth) {

        ClaimResponse response = claimService.getClaim(id);
        TokenService.TokenClaims claims = tokenService.extractUser(auth);

        if (claims.role() == Role.EMPLOYEE && !claims.userId().equals(response.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Approve a pending claim. APPROVER only.
     */
    @RolesAllowed("APPROVER")
    @PatchMapping("/{id}/approve")
    @CircuitBreaker(name = "claimsService")
    @Retry(name = "claimsService")
    public ResponseEntity<ClaimResponse> approveClaim(
            @PathVariable UUID id,
            JwtAuthenticationToken auth) {

        String approver = tokenService.extractUser(auth).username();
        ClaimResponse response = claimService.approveClaim(id, approver);

        return ResponseEntity.ok(response);
    }

    /**
     * Reject a pending claim with a reason. APPROVER only.
     */
    @RolesAllowed("APPROVER")
    @PatchMapping(value = "/{id}/reject", consumes = "application/json")
    @CircuitBreaker(name = "claimsService")
    @Retry(name = "claimsService")
    public ResponseEntity<ClaimResponse> rejectClaim(
            @PathVariable UUID id,
            @Valid @RequestBody RejectClaimRequest request,
            JwtAuthenticationToken auth) {

        String approver = tokenService.extractUser(auth).username();
        ClaimResponse response = claimService.rejectClaim(id, request, approver);

        return ResponseEntity.ok(response);
    }

    /**
     * View audit trail for a claim. APPROVER only.
     */
    @RolesAllowed("APPROVER")
    @GetMapping("/{id}/audit")
    @CircuitBreaker(name = "claimsService")
    @Retry(name = "claimsService")
    public ResponseEntity<List<AuditEventResponse>> getAuditTrail(
            @PathVariable UUID id) {

        List<AuditEventResponse> response = claimService.getAuditTrail(id);

        return ResponseEntity.ok(response);
    }
}
