-- Library Management System schema. MySQL 8.
--
-- This file OWNS the schema. spring.jpa.hibernate.ddl-auto is `none` and
-- spring.sql.init.mode is `always`, so these statements run at every start-up
-- and Hibernate only validates against what they built.
--
-- The alternative -- letting Hibernate generate the DDL and running data.sql
-- afterwards -- needs spring.jpa.defer-datasource-initialization: true, because
-- script init otherwise runs BEFORE Hibernate's DDL and data.sql inserts into
-- tables that do not exist yet. Mixing the two owners gives a schema that
-- differs between a fresh database and an existing one, which is the version of
-- this problem that takes a day to find.
--
-- Everything is CREATE TABLE IF NOT EXISTS so a restart against an existing
-- database is a no-op rather than an error.

CREATE TABLE IF NOT EXISTS number_sequence (
    sequence_name VARCHAR(40) NOT NULL PRIMARY KEY,
    next_value    BIGINT      NOT NULL DEFAULT 1
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS role (
    role_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    -- No ROLE_ prefix. Spring Security's hasRole() adds it before comparing, so
    -- storing it here yields ROLE_ROLE_LIBRARIAN and every check fails in a way
    -- that looks like the role was never granted.
    name        VARCHAR(40)  NOT NULL,
    description VARCHAR(200),
    CONSTRAINT ux_role_name UNIQUE (name)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS member (
    member_id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    membership_number VARCHAR(20)  NOT NULL,
    name              VARCHAR(150) NOT NULL,
    email             VARCHAR(150) NOT NULL,
    phone             VARCHAR(20)  NOT NULL,
    address           VARCHAR(300),
    membership_date   DATE         NOT NULL,
    membership_expiry DATE,
    membership_status VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    -- Derived from book_issue and stored so the borrowing-limit check is not a
    -- COUNT on every issue. Cross-checked by /api/reports/reconciliation.
    books_on_loan     INT          NOT NULL DEFAULT 0,
    created_at        TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ux_member_number UNIQUE (membership_number),
    CONSTRAINT ux_member_email UNIQUE (email),
    CONSTRAINT ck_member_status
        CHECK (membership_status IN ('ACTIVE','SUSPENDED','EXPIRED','CANCELLED')),
    CONSTRAINT ck_member_loans CHECK (books_on_loan >= 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS users (
    user_id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    username               VARCHAR(80)  NOT NULL,
    email                  VARCHAR(150) NOT NULL,
    password_hash          VARCHAR(100) NOT NULL,
    member_id              BIGINT NULL,
    enabled                BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_attempts        INT          NOT NULL DEFAULT 0,
    locked_until           TIMESTAMP(6) NULL,
    last_login_at          TIMESTAMP(6) NULL,
    -- Hashed, like a password. A reset token is a temporary credential; stored
    -- in plaintext it hands over every account with a reset in flight.
    reset_token_hash       VARCHAR(100) NULL,
    reset_token_expires_at TIMESTAMP(6) NULL,
    created_at             TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version                BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ux_user_username UNIQUE (username),
    CONSTRAINT ux_user_email UNIQUE (email),
    CONSTRAINT fk_user_member FOREIGN KEY (member_id) REFERENCES member (member_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE CASCADE,
    -- CASCADE on the user side only: removing a user should drop their grants,
    -- but removing a role must not silently strip it from everyone. That is
    -- RESTRICT, so the attempt fails and someone has to think about it.
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id)
        REFERENCES role (role_id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS category (
    category_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(80)  NOT NULL,
    description VARCHAR(300),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ux_category_name UNIQUE (name)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS author (
    author_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(150)  NOT NULL,
    biography   VARCHAR(1000),
    nationality VARCHAR(80),
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX ix_author_name (name)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS book (
    book_id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    isbn             VARCHAR(20)   NOT NULL,
    title            VARCHAR(300)  NOT NULL,
    author_id        BIGINT        NOT NULL,
    category_id      BIGINT        NOT NULL,
    publisher        VARCHAR(200),
    edition          VARCHAR(40),
    language         VARCHAR(40)   NOT NULL DEFAULT 'English',
    shelf_number     VARCHAR(30),
    publication_year INT,
    total_copies     INT           NOT NULL DEFAULT 1,
    -- available + held + on-loan must always equal total. Enforced in the
    -- application under a row lock, cross-checked by the reconciliation report,
    -- and the constraints below catch the impossible cases outright.
    available_copies INT           NOT NULL DEFAULT 1,
    held_copies      INT           NOT NULL DEFAULT 0,
    replacement_cost DECIMAL(10,2) NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       TIMESTAMP(6)  NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ux_book_isbn UNIQUE (isbn),
    CONSTRAINT fk_book_author FOREIGN KEY (author_id)
        REFERENCES author (author_id) ON DELETE RESTRICT,
    CONSTRAINT fk_book_category FOREIGN KEY (category_id)
        REFERENCES category (category_id) ON DELETE RESTRICT,
    CONSTRAINT ck_book_copies_nonneg
        CHECK (total_copies >= 0 AND available_copies >= 0 AND held_copies >= 0),
    CONSTRAINT ck_book_copies_bounded
        CHECK (available_copies + held_copies <= total_copies),
    CONSTRAINT ck_book_status CHECK (status IN ('ACTIVE','WITHDRAWN','RETIRED')),
    INDEX ix_book_title (title),
    INDEX ix_book_category (category_id),
    INDEX ix_book_author (author_id),
    INDEX ix_book_available (available_copies)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS book_issue (
    issue_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    reference     VARCHAR(24)  NOT NULL,
    book_id       BIGINT       NOT NULL,
    member_id     BIGINT       NOT NULL,
    issue_date    DATE         NOT NULL,
    due_date      DATE         NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ISSUED',
    renewal_count INT          NOT NULL DEFAULT 0,
    issued_by     VARCHAR(80),
    remarks       VARCHAR(300),
    created_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ux_issue_reference UNIQUE (reference),
    CONSTRAINT fk_issue_book FOREIGN KEY (book_id)
        REFERENCES book (book_id) ON DELETE RESTRICT,
    CONSTRAINT fk_issue_member FOREIGN KEY (member_id)
        REFERENCES member (member_id) ON DELETE RESTRICT,
    CONSTRAINT ck_issue_dates CHECK (due_date >= issue_date),
    CONSTRAINT ck_issue_status CHECK (status IN ('ISSUED','RETURNED','OVERDUE','LOST')),
    INDEX ix_issue_member (member_id),
    INDEX ix_issue_book (book_id),
    INDEX ix_issue_status_due (status, due_date)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS book_return (
    return_id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    reference            VARCHAR(24)  NOT NULL,
    -- THE constraint that makes the split issue/return model safe. Exactly zero
    -- or one return per loan: two rows would mean a double-click at the desk put
    -- the copy count up twice and, with a fine involved, charged the member
    -- twice. /api/reports/reconciliation checks for rows that could only exist
    -- if this were dropped.
    issue_id             BIGINT       NOT NULL,
    -- The instant it happened, and that instant as a date in the library's own
    -- timezone. Both stored: the conversion depends on configuration that can
    -- change, and recomputing an old return under a new zone would silently
    -- restate whether a member was late years ago.
    returned_at          TIMESTAMP(6) NOT NULL,
    returned_on          DATE         NOT NULL,
    days_late            INT          NOT NULL DEFAULT 0,
    book_condition       VARCHAR(20)  NOT NULL DEFAULT 'GOOD',
    held_for_reservation BOOLEAN      NOT NULL DEFAULT FALSE,
    received_by          VARCHAR(80),
    remarks              VARCHAR(300),
    CONSTRAINT ux_return_reference UNIQUE (reference),
    CONSTRAINT ux_return_issue UNIQUE (issue_id),
    CONSTRAINT fk_return_issue FOREIGN KEY (issue_id)
        REFERENCES book_issue (issue_id) ON DELETE RESTRICT,
    CONSTRAINT ck_return_days CHECK (days_late >= 0),
    CONSTRAINT ck_return_condition
        CHECK (book_condition IN ('GOOD','MINOR_DAMAGE','DAMAGED','LOST')),
    INDEX ix_return_date (returned_on)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS fine (
    fine_id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    -- One fine per loan, same reasoning as book_return above.
    issue_id          BIGINT        NOT NULL,
    member_id         BIGINT        NOT NULL,
    -- The inputs are stored, not just the total. The question a member asks is
    -- not "how much" but "why", and a row holding only the amount cannot answer
    -- it.
    days_overdue      INT           NOT NULL,
    rate_per_day      DECIMAL(10,2) NOT NULL,
    calculated_amount DECIMAL(10,2) NOT NULL,
    amount            DECIMAL(10,2) NOT NULL,
    amount_paid       DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    capped            BOOLEAN       NOT NULL DEFAULT FALSE,
    status            VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    paid_at           TIMESTAMP(6)  NULL,
    collected_by      VARCHAR(80),
    waived_reason     VARCHAR(300),
    created_at        TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version           BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ux_fine_issue UNIQUE (issue_id),
    CONSTRAINT fk_fine_issue FOREIGN KEY (issue_id)
        REFERENCES book_issue (issue_id) ON DELETE RESTRICT,
    CONSTRAINT fk_fine_member FOREIGN KEY (member_id)
        REFERENCES member (member_id) ON DELETE RESTRICT,
    CONSTRAINT ck_fine_amounts
        CHECK (amount >= 0 AND amount_paid >= 0 AND amount_paid <= amount),
    CONSTRAINT ck_fine_status CHECK (status IN ('PENDING','PAID','WAIVED')),
    INDEX ix_fine_member_status (member_id, status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS reservation (
    reservation_id  BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id         BIGINT       NOT NULL,
    member_id       BIGINT       NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'WAITING',
    -- Queue order. Strictly first-come, by this column.
    reserved_at     TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ready_at        TIMESTAMP(6) NULL,
    -- A returned copy is held for the head of the queue rather than shelved, or
    -- a walk-in takes it and the queue never drains. The hold expires so an
    -- uncollected reservation cannot block the copy forever.
    hold_expires_at TIMESTAMP(6) NULL,
    expires_at      TIMESTAMP(6) NULL,
    fulfilled_at    TIMESTAMP(6) NULL,
    notified        BOOLEAN      NOT NULL DEFAULT FALSE,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT fk_reservation_book FOREIGN KEY (book_id)
        REFERENCES book (book_id) ON DELETE RESTRICT,
    CONSTRAINT fk_reservation_member FOREIGN KEY (member_id)
        REFERENCES member (member_id) ON DELETE RESTRICT,
    CONSTRAINT ck_reservation_status
        CHECK (status IN ('WAITING','READY','FULFILLED','CANCELLED','EXPIRED')),
    INDEX ix_reservation_book_status (book_id, status),
    INDEX ix_reservation_member (member_id),
    INDEX ix_reservation_queue (book_id, reserved_at)
) ENGINE=InnoDB;

-- Append only. No update path and no delete path: an audit trail that can be
-- edited answers no question anyone would ask it.
CREATE TABLE IF NOT EXISTS audit_log (
    audit_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actor       VARCHAR(80)  NOT NULL,
    actor_role  VARCHAR(40),
    action      VARCHAR(60)  NOT NULL,
    entity_type VARCHAR(40),
    entity_id   VARCHAR(40),
    old_value   VARCHAR(2000),
    new_value   VARCHAR(2000),
    detail      VARCHAR(500),
    ip_address  VARCHAR(45),
    INDEX ix_audit_at (occurred_at),
    INDEX ix_audit_actor (actor),
    INDEX ix_audit_entity (entity_type, entity_id),
    INDEX ix_audit_action (action)
) ENGINE=InnoDB;
