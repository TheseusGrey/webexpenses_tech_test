package com.webexpenses.claims.controller;

import com.webexpenses.claims.repository.UserRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for /api/claims endpoints.
 *
 * Covers: claim submission, filtered listing, single retrieval,
 * approval, rejection, and audit trail.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ExpenseClaimControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private UserRepository userRepository;

    private UUID johnId;
    private UUID janeId;
    private UUID mikeId;

    @BeforeEach
    void setUp() {
        johnId = userRepository.findByUsername("john.smith").orElseThrow().getId();
        janeId = userRepository.findByUsername("jane.doe").orElseThrow().getId();
        mikeId = userRepository.findByUsername("mike.approver").orElseThrow().getId();
    }

    // ======================== HELPERS ========================

    private Map<String, Object> claimPayload(double amount, String date, String category) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("description", "Business lunch with client");
        payload.put("amount", amount);
        payload.put("expenseDate", date);
        payload.put("category", category);
        return payload;
    }

    private Map<String, Object> claimPayload() {
        return claimPayload(45.50, "2024-03-15", "MEALS");
    }

    private RequestPostProcessor asEmployee() {
        return jwt().jwt(j -> j
                .subject("john.smith")
                .claim("role", "EMPLOYEE")
                .claim("userId", johnId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
    }

    private RequestPostProcessor asJane() {
        return jwt().jwt(j -> j
                .subject("jane.doe")
                .claim("role", "EMPLOYEE")
                .claim("userId", janeId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
    }

    private RequestPostProcessor asApprover() {
        return jwt().jwt(j -> j
                .subject("mike.approver")
                .claim("role", "APPROVER")
                .claim("userId", mikeId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_APPROVER"));
    }

    /**
     * Submits a claim and returns the created claim's ID.
     */
    private String submitClaim(RequestPostProcessor auth, Map<String, Object> payload) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/claims")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();

        return mapper.readTree(result.getResponse().getContentAsString())
                .path("id").asString();
    }

    private String submitClaim(RequestPostProcessor auth) throws Exception {
        return submitClaim(auth, claimPayload());
    }

    // ========================================================================
    // SUBMIT CLAIM
    // ========================================================================

    @Nested
    @DisplayName("POST /api/claims — Submit Claim")
    class SubmitClaim {

        @Test
        @DisplayName("Employee submits valid claim")
        void submitsValidClaim_asEmployee_returns201WithClaimData() throws Exception {
            mockMvc.perform(post("/api/claims")
                            .with(asEmployee())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(claimPayload())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.description").value("Business lunch with client"))
                    .andExpect(jsonPath("$.amount").value(45.50))
                    .andExpect(jsonPath("$.expenseDate").value("2024-03-15"))
                    .andExpect(jsonPath("$.category").value("MEALS"))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.userId").value(johnId.toString()))
                    .andExpect(jsonPath("$.createdAt").exists());
        }


        @Test
        @DisplayName("Employee submits claim with missing description")
        void submitsClaim_withMissingDescription_returns400() throws Exception {
            Map<String, Object> payload = claimPayload();
            payload.remove("description");

            mockMvc.perform(post("/api/claims")
                            .with(asEmployee())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Employee submits claim with too long description")
        void submitsClaim_withDescriptionOver255Chars_returns400() throws Exception {
            Map<String, Object> payload = claimPayload();
            payload.put("description", "x".repeat(256));

            mockMvc.perform(post("/api/claims")
                            .with(asEmployee())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Employee submits claim with max chars description")
        void submitsClaim_withDescriptionExactly255Chars_returns201() throws Exception {
            Map<String, Object> payload = claimPayload();
            payload.put("description", "x".repeat(255));

            mockMvc.perform(post("/api/claims")
                            .with(asEmployee())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(payload)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("employee submits claim with missing amount")
        void submitsClaim_withMissingAmount_returns400() throws Exception {
            Map<String, Object> payload = claimPayload();
            payload.remove("amount");

            mockMvc.perform(post("/api/claims")
                            .with(asEmployee())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("employee submits claim with negative amount")
        void submitsClaim_withNegativeAmount_returns400() throws Exception {
            mockMvc.perform(post("/api/claims")
                            .with(asEmployee())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(claimPayload(-10.00, "2024-03-15", "MEALS"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("employee submits claim with 0 amount")
        void submitsClaim_withZeroAmount_returns400() throws Exception {
            mockMvc.perform(post("/api/claims")
                            .with(asEmployee())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(claimPayload(0, "2024-03-15", "MEALS"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("employee submits claim with missing date")
        void submitsClaim_withMissingDate_returns400() throws Exception {
            Map<String, Object> payload = claimPayload();
            payload.remove("expenseDate");

            mockMvc.perform(post("/api/claims")
                            .with(asEmployee())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("employee submits claim with missing category")
        void submitsClaim_withMissingCategory_returns400() throws Exception {
            Map<String, Object> payload = claimPayload();
            payload.remove("category");

            mockMvc.perform(post("/api/claims")
                            .with(asEmployee())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("employee submits claim with invalid category")
        void submitsClaim_withInvalidCategory_returns400() throws Exception {
            mockMvc.perform(post("/api/claims")
                            .with(asEmployee())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(claimPayload(45.50, "2024-03-15", "GAMBLING"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("submitsClaim_asApprover_returns403")
        void submitsClaim_asApprover_returns403() throws Exception {
            mockMvc.perform(post("/api/claims")
                            .with(asApprover())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(claimPayload())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("UnAuthenticated user submits claim")
        void submitsClaim_unauthenticated_returns401() throws Exception {
            mockMvc.perform(post("/api/claims")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(claimPayload())))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ========================================================================
    // VIEW CLAIMS (filtered list)
    // ========================================================================

    @Nested
    @DisplayName("GET /api/claims — View Claims (filtered)")
    class ViewClaims {

        @Test
        @DisplayName("getsClaims_withNoParams_returns400")
        void getsClaims_withNoParams_returns400() throws Exception {
            mockMvc.perform(get("/api/claims")
                            .with(asEmployee()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Get claims for authed employee")
        void getsClaims_asEmployeeWithOwnId_returns200WithOwnClaims() throws Exception {
            submitClaim(asEmployee());

            mockMvc.perform(get("/api/claims")
                            .param("id", johnId.toString())
                            .with(asEmployee()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].description").value("Business lunch with client"))
                    .andExpect(jsonPath("$[0].userId").value(johnId.toString()));
        }

        @Test
        @DisplayName("Employee tried to retrieve claims for a different employee")
        void getsClaims_asEmployeeWithOtherId_returns403() throws Exception {
            submitClaim(asJane());

            mockMvc.perform(get("/api/claims")
                            .param("id", janeId.toString())
                            .with(asEmployee()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Employee cannot view ALL pending claims")
        void getsClaims_asEmployeeWithStatusFilter_returns403() throws Exception {
            mockMvc.perform(get("/api/claims")
                            .param("status", "PENDING")
                            .with(asEmployee()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Approver can view ALL pending claims")
        void getsClaims_asApproverWithStatusPending_returns200WithPendingClaims() throws Exception {
            submitClaim(asEmployee());
            submitClaim(asJane());

            mockMvc.perform(get("/api/claims")
                            .param("status", "PENDING")
                            .with(asApprover()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(2)));
        }

        @Test
        @DisplayName("Approver can view claims for specific employee")
        void getsClaims_asApproverWithUserId_returns200WithUserClaims() throws Exception {
            submitClaim(asEmployee());
            submitClaim(asJane());

            mockMvc.perform(get("/api/claims")
                            .param("id", johnId.toString())
                            .with(asApprover()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].userId").value(johnId.toString()));
        }

        @Test
        @DisplayName("Should be able to filter by BOTH employee and status")
        void getsClaims_asApproverWithBothFilters_returns200WithFilteredClaims() throws Exception {
            submitClaim(asEmployee());
            submitClaim(asJane());

            mockMvc.perform(get("/api/claims")
                            .param("id", johnId.toString())
                            .param("status", "PENDING")
                            .with(asApprover()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].userId").value(johnId.toString()));
        }

        @Test
        @DisplayName("Getting claims excludes non-matching from filters")
        void getsClaims_statusFilterExcludesNonMatching_returns200() throws Exception {
            String claimId = submitClaim(asEmployee());
            submitClaim(asJane());

            // Approve john's claim — it should no longer appear in PENDING
            mockMvc.perform(patch("/api/claims/" + claimId + "/approve")
                    .with(asApprover()));

            mockMvc.perform(get("/api/claims")
                            .param("status", "PENDING")
                            .with(asApprover()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].userId").value(janeId.toString()));
        }

        @Test
        @DisplayName("Cannot get claims using invalid status type")
        void getsClaims_withInvalidStatus_returns400() throws Exception {
            mockMvc.perform(get("/api/claims")
                            .param("status", "INVALID")
                            .with(asApprover()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Getting claims should return OK when employee has none")
        void getsClaims_withNoClaims_returns200WithEmptyArray() throws Exception {
            mockMvc.perform(get("/api/claims")
                            .param("id", johnId.toString())
                            .with(asEmployee()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("Cannot get claims when unauthenticated")
        void getsClaims_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/claims").param("id", johnId.toString()))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ========================================================================
    // VIEW SINGLE CLAIM
    // ========================================================================

    @Nested
    @DisplayName("GET /api/claims/{id} — View Single Claim")
    class ViewSingleClaim {

        @Test
        @DisplayName("Retrieve claimById succeeds for owning employee")
        void getsClaim_asOwningEmployee_returns200() throws Exception {
            String claimId = submitClaim(asEmployee());

            mockMvc.perform(get("/api/claims/" + claimId)
                            .with(asEmployee()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(claimId))
                    .andExpect(jsonPath("$.description").value("Business lunch with client"));
        }

        @Test
        @DisplayName("Retrieve claimById fails for non-owning employee")
        void getsClaim_asNonOwningEmployee_returns403() throws Exception {
            String janeClaimId = submitClaim(asJane());

            mockMvc.perform(get("/api/claims/" + janeClaimId)
                            .with(asEmployee()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Retrieve claimById succeeds for approver")
        void getsClaim_asApprover_returns200() throws Exception {
            String claimId = submitClaim(asEmployee());

            mockMvc.perform(get("/api/claims/" + claimId)
                            .with(asApprover()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(claimId));
        }

        @Test
        @DisplayName("Retrieve claimById fails for unknown user")
        void getsClaim_nonExistent_returns404() throws Exception {
            mockMvc.perform(get("/api/claims/" + UUID.randomUUID())
                            .with(asEmployee()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Retrieve claimById fails for unauthed user")
        void getsClaim_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/claims/" + UUID.randomUUID()))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ========================================================================
    // APPROVE CLAIM
    // ========================================================================

    @Nested
    @DisplayName("PATCH /api/claims/{id}/approve — Approve Claim")
    class ApproveClaim {

        @Test
        @DisplayName("Approver can approve pending claims")
        void approvesClaim_asPending_returns200WithApprovedStatus() throws Exception {
            String claimId = submitClaim(asEmployee());

            mockMvc.perform(patch("/api/claims/" + claimId + "/approve")
                            .with(asApprover()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APPROVED"))
                    .andExpect(jsonPath("$.id").value(claimId));
        }

        @Test
        @DisplayName("Approver cannot approve already approved claims")
        void approvesClaim_alreadyApproved_returns409() throws Exception {
            String claimId = submitClaim(asEmployee());

            mockMvc.perform(patch("/api/claims/" + claimId + "/approve")
                    .with(asApprover()));

            mockMvc.perform(patch("/api/claims/" + claimId + "/approve")
                            .with(asApprover()))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Approver cannot approve rejected claims")
        void approvesClaim_alreadyRejected_returns409() throws Exception {
            String claimId = submitClaim(asEmployee());

            mockMvc.perform(patch("/api/claims/" + claimId + "/reject")
                    .with(asApprover())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"Insufficient documentation\"}"));

            mockMvc.perform(patch("/api/claims/" + claimId + "/approve")
                            .with(asApprover()))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Employee cannot approve claims")
        void approvesClaim_asEmployee_returns403() throws Exception {
            String claimId = submitClaim(asEmployee());

            mockMvc.perform(patch("/api/claims/" + claimId + "/approve")
                            .with(asEmployee()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Cannot approve non existant claim")
        void approvesClaim_nonExistent_returns404() throws Exception {
            mockMvc.perform(patch("/api/claims/" + UUID.randomUUID() + "/approve")
                            .with(asApprover()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Unauthenticated user cannot approve claims")
        void approvesClaim_unauthenticated_returns401() throws Exception {
            mockMvc.perform(patch("/api/claims/" + UUID.randomUUID() + "/approve"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ========================================================================
    // REJECT CLAIM
    // ========================================================================

    @Nested
    @DisplayName("PATCH /api/claims/{id}/reject — Reject Claim")
    class RejectClaim {

        @Test
        @DisplayName("Approver can reject claims with reason")
        void rejectsClaim_asApproverWithReason_returns200WithRejectedStatus() throws Exception {
            String claimId = submitClaim(asEmployee());

            mockMvc.perform(patch("/api/claims/" + claimId + "/reject")
                            .with(asApprover())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"Receipt not attached\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("REJECTED"))
                    .andExpect(jsonPath("$.rejectionReason").value("Receipt not attached"));
        }

        @Test
        @DisplayName("Approver cannot reject claims with missing reason")
        void rejectsClaim_withMissingReason_returns400() throws Exception {
            String claimId = submitClaim(asEmployee());

            mockMvc.perform(patch("/api/claims/" + claimId + "/reject")
                            .with(asApprover())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Approver cannot reject claims with blank reason")
        void rejectsClaim_withBlankReason_returns400() throws Exception {
            String claimId = submitClaim(asEmployee());

            mockMvc.perform(patch("/api/claims/" + claimId + "/reject")
                            .with(asApprover())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"   \"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Approver cannot reject approved claims")
        void rejectsClaim_alreadyApproved_returns409() throws Exception {
            String claimId = submitClaim(asEmployee());

            mockMvc.perform(patch("/api/claims/" + claimId + "/approve")
                    .with(asApprover()));

            mockMvc.perform(patch("/api/claims/" + claimId + "/reject")
                            .with(asApprover())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"Changed my mind\"}"))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Approver cannot reject already rejected claims")
        void rejectsClaim_alreadyRejected_returns409() throws Exception {
            String claimId = submitClaim(asEmployee());

            mockMvc.perform(patch("/api/claims/" + claimId + "/reject")
                    .with(asApprover())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"No receipt\"}"));

            mockMvc.perform(patch("/api/claims/" + claimId + "/reject")
                            .with(asApprover())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"Still no receipt\"}"))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Employee cannot reject claims")
        void rejectsClaim_asEmployee_returns403() throws Exception {
            String claimId = submitClaim(asEmployee());

            mockMvc.perform(patch("/api/claims/" + claimId + "/reject")
                            .with(asEmployee())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"Self-reject\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("cannot reject non existent claims")
        void rejectsClaim_nonExistent_returns404() throws Exception {
            mockMvc.perform(patch("/api/claims/" + UUID.randomUUID() + "/reject")
                            .with(asApprover())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"Ghost claim\"}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Unauthenticated user cannot reject claims")
        void rejectsClaim_unauthenticated_returns401() throws Exception {
            mockMvc.perform(patch("/api/claims/" + UUID.randomUUID() + "/reject")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"test\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ========================================================================
    // AUDIT TRAIL
    // ========================================================================

    @Nested
    @DisplayName("GET /api/claims/{id}/audit — Audit Trail")
    class AuditTrail {

        @Test
        @DisplayName("Approver can an request audit for a given claim")
        void getsAudit_afterSubmission_returns200WithSubmittedEvent() throws Exception {
            String claimId = submitClaim(asEmployee());

            mockMvc.perform(get("/api/claims/" + claimId + "/audit")
                            .with(asApprover()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].action").value("SUBMITTED"))
                    .andExpect(jsonPath("$[0].performedBy").value("john.smith"))
                    .andExpect(jsonPath("$[0].performedAt").exists());
        }

        @Test
        @DisplayName("Approver can an request audit for a given claim with multiple events")
        void getsAudit_afterApproval_returns200WithTwoEvents() throws Exception {
            String claimId = submitClaim(asEmployee());

            mockMvc.perform(patch("/api/claims/" + claimId + "/approve")
                    .with(asApprover()));

            mockMvc.perform(get("/api/claims/" + claimId + "/audit")
                            .with(asApprover()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].action").value("SUBMITTED"))
                    .andExpect(jsonPath("$[1].action").value("APPROVED"))
                    .andExpect(jsonPath("$[1].performedBy").value("mike.approver"));
        }

        @Test
        @DisplayName("Approver can an request audit for a rejected claim")
        void getsAudit_afterRejection_returns200WithReasonInDetails() throws Exception {
            String claimId = submitClaim(asEmployee());

            mockMvc.perform(patch("/api/claims/" + claimId + "/reject")
                    .with(asApprover())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"No receipt provided\"}"));

            mockMvc.perform(get("/api/claims/" + claimId + "/audit")
                            .with(asApprover()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[1].action").value("REJECTED"))
                    .andExpect(jsonPath("$[1].performedBy").value("mike.approver"))
                    .andExpect(jsonPath("$[1].details").value("No receipt provided"));
        }

        @Test
        @DisplayName("Checking audit events have required fields")
        void getsAudit_checkResponseShape_hasRequiredFields() throws Exception {
            String claimId = submitClaim(asEmployee());

            mockMvc.perform(get("/api/claims/" + claimId + "/audit")
                            .with(asApprover()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").exists())
                    .andExpect(jsonPath("$[0].claimId").value(claimId))
                    .andExpect(jsonPath("$[0].action").exists())
                    .andExpect(jsonPath("$[0].performedBy").exists())
                    .andExpect(jsonPath("$[0].performedAt").exists());
        }

        @Test
        @DisplayName("Employees cannot request audits")
        void getsAudit_asEmployee_returns403() throws Exception {
            String claimId = submitClaim(asEmployee());

            mockMvc.perform(get("/api/claims/" + claimId + "/audit")
                            .with(asEmployee()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Cannot request audit for non-existent claim")
        void getsAudit_nonExistentClaim_returns404() throws Exception {
            mockMvc.perform(get("/api/claims/" + UUID.randomUUID() + "/audit")
                            .with(asApprover()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Unauthenticated user cannot request audits")
        void getsAudit_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/claims/" + UUID.randomUUID() + "/audit"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
