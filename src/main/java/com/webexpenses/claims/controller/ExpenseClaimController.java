package com.webexpenses.claims.controller;

import com.webexpenses.claims.dto.AuditEventResponse;
import com.webexpenses.claims.dto.ClaimResponse;
import com.webexpenses.claims.dto.CreateClaimRequest;
import com.webexpenses.claims.dto.RejectClaimRequest;
import com.webexpenses.claims.repository.UserRepository;
import com.webexpenses.claims.service.ExpenseClaimService;
import com.webexpenses.claims.service.TokenService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    public ExpenseClaimController(ExpenseClaimService claimService, TokenService tokenService) {
        this.claimService = claimService;
        this.tokenService = tokenService;
    }

    /**
     * Submit a new expense claim. Only EMPLOYEE role permitted.
     */
    @RolesAllowed("EMPLOYEE")
    @PostMapping(consumes = "application/json")
    public ResponseEntity<ClaimResponse> submitClaim(
            @Valid @RequestBody CreateClaimRequest request,
            JwtAuthenticationToken auth) {

        TokenService.TokenClaims claims = tokenService.extractUser(auth);
        ClaimResponse response = claimService.submitClaim(request, claims.username(), claims.userId());

        return ResponseEntity.ok(response);
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
    public ResponseEntity<List<ClaimResponse>> getClaims(
            @RequestParam(required = false) UUID user,
            @RequestParam(required = false) String status,
            JwtAuthenticationToken auth) {

        // TODO: Implement
        // 1. If both user and status are null → return 400
        // 2. Extract role from JWT
        // 3. If EMPLOYEE:
        //    - Cannot use status filter → 403
        //    - Can only query own userId → 403 if user != own ID
        // 4. If APPROVER:
        //    - Can use any combination of filters
        // 5. Parse status string to ClaimStatus enum (400 if invalid)
        // 6. Call service.getClaims(user, parsedStatus)
        // 7. Return 200 with list
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * View a single claim by ID.
     * EMPLOYEE can only view own claims; APPROVER can view any.
     */
    @RolesAllowed({"EMPLOYEE", "APPROVER"})
    @GetMapping("/{id}")
    public ResponseEntity<ClaimResponse> getClaim(
            @PathVariable UUID id,
            JwtAuthenticationToken auth) {

        // TODO: Implement
        // 1. Call service.getClaim(id) — throws 404 if not found
        // 2. Check role: EMPLOYEE must own the claim, else 403
        // 3. Return 200 with claim
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Approve a pending claim. APPROVER only.
     */
    @RolesAllowed("APPROVER")
    @PatchMapping("/{id}/approve")
    public ResponseEntity<ClaimResponse> approveClaim(
            @PathVariable UUID id,
            JwtAuthenticationToken auth) {

        // TODO: Implement
        // 1. Extract role — if not APPROVER, return 403
        // 2. Extract username from JWT
        // 3. Call service.approveClaim(id, username)
        //    - 404 if not found, 409 if not PENDING
        // 4. Return 200 with updated claim
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Reject a pending claim with a reason. APPROVER only.
     */
    @RolesAllowed("APPROVER")
    @PatchMapping(value = "/{id}/reject", consumes = "application/json")
    public ResponseEntity<ClaimResponse> rejectClaim(
            @PathVariable UUID id,
            @Valid @RequestBody RejectClaimRequest request,
            JwtAuthenticationToken auth) {

        // TODO: Implement
        // 1. Extract role — if not APPROVER, return 403
        // 2. Extract username from JWT
        // 3. Call service.rejectClaim(id, request, username)
        //    - 404 if not found, 409 if not PENDING
        // 4. Return 200 with updated claim
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * View audit trail for a claim. APPROVER only.
     */
    @RolesAllowed("APPROVER")
    @GetMapping("/{id}/audit")
    public ResponseEntity<List<AuditEventResponse>> getAuditTrail(
            @PathVariable UUID id,
            JwtAuthenticationToken auth) {

        // TODO: Implement
        // 1. Extract role — if not APPROVER, return 403
        // 2. Call service.getAuditTrail(id)
        //    - 404 if claim doesn't exist
        // 3. Return 200 with audit events
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
