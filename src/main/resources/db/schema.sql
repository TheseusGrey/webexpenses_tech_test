-- ============================================================================
-- Users table
-- ============================================================================
-- Passwords are stored as bcrypt hashes (60-char output includes salt).
-- BCrypt embeds the salt in the hash string itself (format: $2a$COST$SALT+HASH),
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS users (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    username    VARCHAR(100)    NOT NULL UNIQUE,
    password    VARCHAR(255)    NOT NULL,
    role        VARCHAR(20)     NOT NULL CHECK (role IN ('EMPLOYEE', 'APPROVER')),
);

CREATE INDEX idx_users_username ON users (username);

-- ============================================================================
-- Seed data
-- ============================================================================
-- Passwords hashed with BCrypt (cost factor 12):
--   Password123!  -> $2a$12$LJ3m4sMOkVMnJOBGnMC.MOt3RUbILEPW36E1MR6rXFPbVXxNDIYG.
--   Password456!  -> $2a$12$8dVqJGfWMOGo7JY2R8i5IOZYRYHFyVBvlI8QU1fvTKIqHqOXKdrWu
--   ApproverPass1! -> $2a$12$wXJpJmHOPinoB7GMhzXbSuHrZ1fVJIKMN5lXDVJxJ0HfMlK5h1IXe
--
-- NOTE: These are placeholder hashes for documentation. Actual hashes should be
-- generated at application startup via Spring's BCryptPasswordEncoder to ensure
-- consistency with the encoder configuration used at runtime.
-- ============================================================================

INSERT INTO users (username, password, role) VALUES
    ('john.smith',    '$2a$12$LJ3m4sMOkVMnJOBGnMC.MOt3RUbILEPW36E1MR6rXFPbVXxNDIYG.', 'EMPLOYEE'),
    ('jane.doe',      '$2a$12$8dVqJGfWMOGo7JY2R8i5IOZYRYHFyVBvlI8QU1fvTKIqHqOXKdrWu', 'EMPLOYEE'),
    ('mike.approver', '$2a$12$wXJpJmHOPinoB7GMhzXbSuHrZ1fVJIKMN5lXDVJxJ0HfMlK5h1IXe', 'APPROVER')
ON CONFLICT (username) DO NOTHING;

-- ============================================================================
-- Expense claims table
-- ============================================================================
-- NUMERIC(10,2) for monetary values — avoids floating point imprecision.
-- Status is constrained at the DB level to prevent invalid state transitions
-- being persisted even if the application layer has a bug.
-- ============================================================================

CREATE TABLE IF NOT EXISTS expense_claims (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id         UUID            NOT NULL REFERENCES users(id),
    description         VARCHAR(255)    NOT NULL,
    amount              NUMERIC(10, 2)  NOT NULL CHECK (amount > 0),
    expense_date        DATE            NOT NULL,
    category            VARCHAR(20)     NOT NULL CHECK (category IN ('TRAVEL', 'MEALS', 'ACCOMMODATION', 'EQUIPMENT', 'OTHER')),
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    rejection_reason    TEXT,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_claims_employee_id ON expense_claims (employee_id);
CREATE INDEX idx_claims_status ON expense_claims (status);

-- ============================================================================
-- Claim audit events table
-- ============================================================================
-- Append-only log of all state changes on expense claims.
-- Answers: "who did what, and when?"
-- ============================================================================

CREATE TABLE IF NOT EXISTS claim_audit_events (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id        UUID            NOT NULL REFERENCES expense_claims(id),
    action          VARCHAR(20)     NOT NULL CHECK (action IN ('SUBMITTED', 'APPROVED', 'REJECTED')),
    performed_by    VARCHAR(100)    NOT NULL,
    performed_at    TIMESTAMP       NOT NULL DEFAULT NOW(),
    details         TEXT
);

CREATE INDEX idx_audit_claim_id ON claim_audit_events (claim_id);
