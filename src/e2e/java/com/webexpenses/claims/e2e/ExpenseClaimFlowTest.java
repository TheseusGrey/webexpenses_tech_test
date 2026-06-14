package com.webexpenses.claims.e2e;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E test exercising the full claim lifecycle against a running compose stack.
 *
 * Prerequisites: `podman compose up` (or docker compose up) with the app healthy on port 8080.
 *
 * Run with: ./gradlew e2eTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExpenseClaimFlowTest {

    private static final String BASE_URL = System.getProperty("e2e.baseUrl", "http://localhost:8080");
    private static final ObjectMapper mapper = new ObjectMapper();

    private final ApiClient api = new ApiClient(BASE_URL);

    // State carried across ordered tests
    private String employeeToken;
    private String approverToken;
    private String employeeUserId;
    private String claim1Id;
    private String claim2Id;

    // --- Authentication ---

    @Test
    @Order(1)
    void employeeCanLogin() throws Exception {
        var response = api.post("/api/auth/login",
                """
                {"username": "john.smith", "password": "Password123!"}
                """, null);

        assertEquals(200, response.status(), "Employee login should return 200");

        JsonNode body = mapper.readTree(response.body());
        employeeToken = body.path("token").asString();
        assertNotNull(employeeToken, "Token should be present");
        assertFalse(employeeToken.isBlank(), "Token should not be blank");
        assertEquals("Bearer", body.path("tokenType").asString());
    }

    @Test
    @Order(2)
    void approverCanLogin() throws Exception {
        var response = api.post("/api/auth/login",
                """
                {"username": "mike.approver", "password": "ApproverPass1!"}
                """, null);

        assertEquals(200, response.status(), "Approver login should return 200");

        JsonNode body = mapper.readTree(response.body());
        approverToken = body.path("token").asString();
        assertNotNull(approverToken);
        assertFalse(approverToken.isBlank());
    }

    @Test
    @Order(3)
    void invalidCredentialsRejected() {
        var response = api.post("/api/auth/login",
                """
                {"username": "john.smith", "password": "WrongPassword!"}
                """, null);

        assertEquals(401, response.status());
    }

    // --- Claim Submission ---

    @Test
    @Order(10)
    void employeeSubmitsClaim1() throws Exception {
        var response = api.post("/api/claims",
                """
                {
                    "description": "Train ticket to London office",
                    "amount": 85.50,
                    "expenseDate": "2025-06-10",
                    "category": "TRAVEL"
                }
                """, employeeToken);

        assertEquals(201, response.status(), "Claim submission should return 201");

        JsonNode body = mapper.readTree(response.body());
        claim1Id = body.path("id").asString();
        employeeUserId = body.path("userId").asString();
        assertNotNull(claim1Id);
        assertEquals("PENDING", body.path("status").asString());
        assertEquals("TRAVEL", body.path("category").asString());
        assertEquals(85.50, body.path("amount").asDouble(), 0.01);
    }

    @Test
    @Order(11)
    void employeeSubmitsClaim2() throws Exception {
        var response = api.post("/api/claims",
                """
                {
                    "description": "Team lunch with client",
                    "amount": 42.00,
                    "expenseDate": "2025-06-11",
                    "category": "MEALS"
                }
                """, employeeToken);

        assertEquals(201, response.status());

        JsonNode body = mapper.readTree(response.body());
        claim2Id = body.path("id").asString();
        assertEquals("PENDING", body.path("status").asString());
        assertEquals("MEALS", body.path("category").asString());
    }

    @Test
    @Order(12)
    void employeeCanViewOwnClaims() throws Exception {
        var response = api.get("/api/claims?id=" + employeeUserId, employeeToken);

        assertEquals(200, response.status());

        JsonNode claims = mapper.readTree(response.body());
        assertTrue(claims.isArray());
        assertEquals(2, claims.size(), "Employee should see both submitted claims");
    }

    @Test
    @Order(13)
    void employeeCanViewSpecificClaim() throws Exception {
        var response = api.get("/api/claims/" + claim1Id, employeeToken);

        assertEquals(200, response.status());

        JsonNode body = mapper.readTree(response.body());
        assertEquals(claim1Id, body.path("id").asString());
        assertEquals("Train ticket to London office", body.path("description").asString());
    }

    // --- Approver Workflow ---

    @Test
    @Order(20)
    void approverCanViewPendingClaims() throws Exception {
        var response = api.get("/api/claims?status=PENDING", approverToken);

        assertEquals(200, response.status());

        JsonNode claims = mapper.readTree(response.body());
        assertTrue(claims.isArray());
        assertTrue(claims.size() >= 2, "Approver should see at least the 2 pending claims");
    }

    @Test
    @Order(21)
    void approverApprovesClaim1() throws Exception {
        var response = api.patch("/api/claims/" + claim1Id + "/approve", null, approverToken);

        assertEquals(200, response.status());

        JsonNode body = mapper.readTree(response.body());
        assertEquals("APPROVED", body.path("status").asString());
    }

    @Test
    @Order(22)
    void approverRejectsClaim2() throws Exception {
        var response = api.patch("/api/claims/" + claim2Id + "/reject",
                """
                {"reason": "Receipt not attached, please resubmit with documentation"}
                """, approverToken);

        assertEquals(200, response.status());

        JsonNode body = mapper.readTree(response.body());
        assertEquals("REJECTED", body.path("status").asString());
        assertEquals("Receipt not attached, please resubmit with documentation",
                body.path("rejectionReason").asString());
    }

    // --- State Verification ---

    @Test
    @Order(30)
    void approvedClaimCannotBeRejected() {
        var response = api.patch("/api/claims/" + claim1Id + "/reject",
                """
                {"reason": "Changed my mind"}
                """, approverToken);

        assertEquals(409, response.status(), "Already-approved claim should return 409");
    }

    @Test
    @Order(31)
    void rejectedClaimCannotBeApproved() {
        var response = api.patch("/api/claims/" + claim2Id + "/approve", null, approverToken);

        assertEquals(409, response.status(), "Already-rejected claim should return 409");
    }

    @Test
    @Order(32)
    void employeeSeesUpdatedStatuses() throws Exception {
        var response = api.get("/api/claims?id=" + employeeUserId, employeeToken);

        assertEquals(200, response.status());

        JsonNode claims = mapper.readTree(response.body());
        for (int i = 0; i < claims.size(); i++) {
            JsonNode claim = claims.get(i);
            String id = claim.path("id").asString();
            if (id.equals(claim1Id)) {
                assertEquals("APPROVED", claim.path("status").asString());
            } else if (id.equals(claim2Id)) {
                assertEquals("REJECTED", claim.path("status").asString());
            }
        }
    }

    // --- Audit Trail ---

    @Test
    @Order(40)
    void auditTrailForApprovedClaim() throws Exception {
        var response = api.get("/api/claims/" + claim1Id + "/audit", approverToken);

        assertEquals(200, response.status());

        JsonNode events = mapper.readTree(response.body());
        assertTrue(events.isArray());
        assertEquals(2, events.size(), "Approved claim should have SUBMITTED + APPROVED events");
        assertEquals("SUBMITTED", events.get(0).path("action").asString());
        assertEquals("APPROVED", events.get(1).path("action").asString());
    }

    @Test
    @Order(41)
    void auditTrailForRejectedClaim() throws Exception {
        var response = api.get("/api/claims/" + claim2Id + "/audit", approverToken);

        assertEquals(200, response.status());

        JsonNode events = mapper.readTree(response.body());
        assertTrue(events.isArray());
        assertEquals(2, events.size(), "Rejected claim should have SUBMITTED + REJECTED events");
        assertEquals("SUBMITTED", events.get(0).path("action").asString());
        assertEquals("REJECTED", events.get(1).path("action").asString());
    }

    // --- Access Control ---

    @Test
    @Order(50)
    void unauthenticatedRequestRejected() {
        var response = api.get("/api/claims?id=" + employeeUserId, null);

        assertEquals(401, response.status());
    }

    @Test
    @Order(51)
    void employeeCannotApprove() {
        var response = api.patch("/api/claims/" + claim1Id + "/approve", null, employeeToken);

        assertEquals(403, response.status());
    }

    @Test
    @Order(52)
    void employeeCannotViewAuditTrail() {
        var response = api.get("/api/claims/" + claim1Id + "/audit", employeeToken);

        assertEquals(403, response.status());
    }
}
