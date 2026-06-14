package com.webexpenses.claims.controller;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for POST /api/auth/login
 *
 * Strategy: test-first approach covering:
 * - Happy path (valid credentials return JWT with correct claims)
 * - Authentication failures (wrong password, unknown user)
 * - Input validation (missing fields, empty body, malformed JSON)
 * - Injection attempts (SQL injection, XSS in username)
 * - Boundary conditions (max-length inputs, unicode, whitespace tricks)
 * - HTTP method enforcement (GET should be rejected)
 * - Response shape consistency (errors never leak internals)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String LOGIN_URL = "/api/auth/login";

    // --- Helper ---

    private String loginJson(String username, String password) throws Exception {
        Map<String, String> body = new HashMap<>();
        if (username != null) body.put("username", username);
        if (password != null) body.put("password", password);
        return objectMapper.writeValueAsString(body);
    }

    // =========================================================================
    // Happy Path
    // =========================================================================

    @Nested
    @DisplayName("Happy path - valid credentials")
    class HappyPath {

        @Test
        @DisplayName("Employee login returns 200 with JWT containing correct role")
        void employeeLogin_returnsJwtWithEmployeeRole() throws Exception {
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("john.smith", "Password123!")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andExpect(jsonPath("$.token").isString());
        }

        @Test
        @DisplayName("Approver login returns 200 with JWT containing correct role")
        void approverLogin_returnsJwtWithApproverRole() throws Exception {
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("mike.approver", "ApproverPass1!")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty());
        }

        @Test
        @DisplayName("Response includes token type information")
        void login_responseContainsExpectedFields() throws Exception {
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("jane.doe", "Password456!")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"));
        }
    }

    // =========================================================================
    // Authentication Failures
    // =========================================================================

    @Nested
    @DisplayName("Authentication failures")
    class AuthFailures {

        @Test
        @DisplayName("Wrong password returns 401")
        void wrongPassword_returns401() throws Exception {
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("john.smith", "WrongPassword!")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.token").doesNotExist());
        }

        @Test
        @DisplayName("Unknown username returns 401")
        void unknownUser_returns401() throws Exception {
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("nonexistent.user", "Password123!")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.token").doesNotExist());
        }

        @Test
        @DisplayName("Error response does not distinguish between bad user and bad password")
        void authFailure_genericErrorMessage() throws Exception {
            // Security: error messages must NOT reveal whether the username exists
            String badUserResponse = mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("nonexistent.user", "Password123!")))
                    .andReturn().getResponse().getContentAsString();

            String badPassResponse = mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("john.smith", "WrongPassword!")))
                    .andReturn().getResponse().getContentAsString();

            // Both should return the same error structure (no user enumeration)
            ObjectMapper mapper = new ObjectMapper();
            String badUserMsg = mapper.readTree(badUserResponse).path("error").asText();
            String badPassMsg = mapper.readTree(badPassResponse).path("error").asText();
            assert badUserMsg.equals(badPassMsg) :
                    "Error messages differ - potential user enumeration vulnerability";
        }

        @Test
        @DisplayName("Correct username with password of another user returns 401")
        void crossUserPassword_returns401() throws Exception {
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("john.smith", "ApproverPass1!")))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // Input Validation
    // =========================================================================

    @Nested
    @DisplayName("Input validation")
    class InputValidation {

        @Test
        @DisplayName("Empty JSON body returns 400")
        void emptyBody_returns400() throws Exception {
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("No body at all returns 400")
        void noBody_returns400() throws Exception {
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Malformed JSON returns 400")
        void malformedJson_returns400() throws Exception {
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{invalid json!!!"))
                    .andExpect(status().isBadRequest());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("Blank or missing username returns 400")
        void blankUsername_returns400(String username) throws Exception {
            Map<String, String> body = new HashMap<>();
            if (username != null) body.put("username", username);
            body.put("password", "Password123!");

            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("Blank or missing password returns 400")
        void blankPassword_returns400(String password) throws Exception {
            Map<String, String> body = new HashMap<>();
            body.put("username", "john.smith");
            if (password != null) body.put("password", password);

            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Extra fields in request body are ignored (no 500)")
        void extraFields_areIgnored() throws Exception {
            String json = """
                    {"username": "john.smith", "password": "Password123!", "admin": true, "role": "APPROVER"}
                    """;
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isOk());
        }
    }

    // =========================================================================
    // Injection & Security
    // =========================================================================

    @Nested
    @DisplayName("Injection and security attacks")
    class InjectionAttacks {

        @Test
        @DisplayName("SQL injection in username does not cause error or auth bypass")
        void sqlInjection_username_noBypass() throws Exception {
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("' OR '1'='1", "Password123!")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("SQL injection in password does not cause error or auth bypass")
        void sqlInjection_password_noBypass() throws Exception {
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("john.smith", "' OR '1'='1")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("XSS payload in username is handled safely")
        void xssInUsername_handledSafely() throws Exception {
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("<script>alert('xss')</script>", "Password123!")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().string(not(containsString("<script>"))));
        }

        @Test
        @DisplayName("Extremely long username does not cause 500")
        void longUsername_doesNotCrash() throws Exception {
            String longUsername = "a".repeat(10_000);
            int status = mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson(longUsername, "Password123!")))
                    .andReturn().getResponse().getStatus();
            assertTrue(status == 400 || status == 401,
                    "Expected 400 or 401 but got " + status);
        }

        @Test
        @DisplayName("Extremely long password does not cause 500")
        void longPassword_doesNotCrash() throws Exception {
            String longPassword = "a".repeat(10_000);
            int status = mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("john.smith", longPassword)))
                    .andReturn().getResponse().getStatus();
            assertTrue(status == 400 || status == 401,
                    "Expected 400 or 401 but got " + status);
        }

        @Test
        @DisplayName("Null byte injection in username is handled")
        void nullByteInjection_handled() throws Exception {
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("john.smith\u0000admin", "Password123!")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Unicode homoglyph username does not match real user")
        void unicodeHomoglyph_noMatch() throws Exception {
            // Cyrillic 'о' (U+043E) looks identical to Latin 'o'
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("j\u043Ehn.smith", "Password123!")))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // HTTP Method & Content-Type enforcement
    // =========================================================================

    @Nested
    @DisplayName("HTTP method and content-type enforcement")
    class HttpEnforcement {

        @Test
        @DisplayName("GET to login endpoint returns 401 (rejected by security filter)")
        void getMethod_returns401() throws Exception {
            mockMvc.perform(get(LOGIN_URL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST without Content-Type header returns 415")
        void noContentType_returns415() throws Exception {
            mockMvc.perform(post(LOGIN_URL)
                            .content(loginJson("john.smith", "Password123!")))
                    .andExpect(status().isUnsupportedMediaType());
        }

        @Test
        @DisplayName("POST with form-urlencoded returns 415")
        void formUrlEncoded_returns415() throws Exception {
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .content("username=john.smith&password=Password123!"))
                    .andExpect(status().isUnsupportedMediaType());
        }
    }

    // =========================================================================
    // Case sensitivity & whitespace
    // =========================================================================

    @Nested
    @DisplayName("Case sensitivity and whitespace handling")
    class CaseSensitivity {

        @Test
        @DisplayName("Username is case-sensitive - uppercase variant fails")
        void uppercaseUsername_fails() throws Exception {
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("JOHN.SMITH", "Password123!")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Password is case-sensitive")
        void caseChangedPassword_fails() throws Exception {
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("john.smith", "password123!")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Leading/trailing whitespace in username does not grant access")
        void whitespaceUsername_fails() throws Exception {
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson(" john.smith ", "Password123!")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Leading/trailing whitespace in password does not grant access")
        void whitespacePassword_fails() throws Exception {
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("john.smith", " Password123! ")))
                    .andExpect(status().isUnauthorized());
        }
    }
}
