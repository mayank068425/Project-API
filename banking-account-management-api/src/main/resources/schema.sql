-- Banking Account Management schema. MySQL 8.
--
-- This file OWNS the schema. spring.jpa.hibernate.ddl-auto is `none` and
-- spring.sql.init.mode is `always`, so Hibernate only validates against what
-- these statements build.
--
-- The alternative -- Hibernate generating the DDL with data.sql after it --
-- needs spring.jpa.defer-datasource-initialization: true, because script init
-- otherwise runs BEFORE Hibernate's DDL and data.sql inserts into tables that do
-- not exist. Mixing owners gives a schema that differs between a fresh database
-- and an existing one.
--
-- Every statement is idempotent so a restart against a populated database is a
-- no-op.

-- Atomic number issuing. UPDATE ... LAST_INSERT_ID(next_value + 1) makes the
-- increment and the read one statement; MAX(account_number)+1 lets two
-- concurrent openings read the same value and one then fails on the unique index.
CREATE TABLE IF NOT EXISTS number_sequence (
    sequence_name VARCHAR(40) NOT NULL PRIMARY KEY,
    next_value    BIGINT      NOT NULL DEFAULT 1
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS roles (
    role_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    -- No ROLE_ prefix. hasRole() adds it before comparing, so storing it here
    -- gives ROLE_ROLE_BANK_EMPLOYEE and every check fails as though the role had
    -- never been granted.
    name        VARCHAR(40)  NOT NULL,
    description VARCHAR(200),
    CONSTRAINT ux_role_name UNIQUE (name)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS customers (
    customer_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_number VARCHAR(20)  NOT NULL,
    first_name      VARCHAR(80)  NOT NULL,
    last_name       VARCHAR(80)  NOT NULL,
    email           VARCHAR(150) NOT NULL,
    phone           VARCHAR(20)  NOT NULL,
    -- Date of birth is stored; age is derived on every read. A stored age is
    -- wrong from the customer's next birthday, and here it gates account opening.
    date_of_birth   DATE         NOT NULL,
    address         VARCHAR(300),
    city            VARCHAR(80),
    postal_code     VARCHAR(20),
    -- Unique: two customers sharing one is a duplicate or a second identity.
    -- Written, never returned by the API.
    national_id     VARCHAR(40)  NULL,
    kyc_status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6) NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ux_customer_number UNIQUE (customer_number),
    CONSTRAINT ux_customer_email UNIQUE (email),
    CONSTRAINT ux_customer_national_id UNIQUE (national_id),
    CONSTRAINT ck_customer_kyc CHECK (kyc_status IN ('PENDING','VERIFIED','REJECTED')),
    -- Static floor only. MySQL rejects non-deterministic functions in CHECK
    -- constraints, so `date_of_birth <= CURRENT_DATE` would fail at DDL time with
    -- ERROR 3814 and the migration would never apply. The future-date rule lives
    -- in @Past and CustomerValidator, where a rule that depends on "now" belongs.
    CONSTRAINT ck_customer_dob CHECK (date_of_birth >= '1890-01-01'),
    INDEX ix_customer_last_name (last_name),
    INDEX ix_customer_phone (phone)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS users (
    user_id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    username            VARCHAR(80)  NOT NULL,
    email               VARCHAR(150) NOT NULL,
    password_hash       VARCHAR(100) NOT NULL,
    customer_id         BIGINT NULL,
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_attempts     INT          NOT NULL DEFAULT 0,
    locked_until        TIMESTAMP(6) NULL,
    last_login_at       TIMESTAMP(6) NULL,
    password_changed_at TIMESTAMP(6) NULL,
    created_at          TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ux_user_username UNIQUE (username),
    CONSTRAINT ux_user_email UNIQUE (email),
    CONSTRAINT fk_user_customer FOREIGN KEY (customer_id)
        REFERENCES customers (customer_id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE CASCADE,
    -- CASCADE on the user side only. Removing a user drops their grants; removing
    -- a role must not silently strip it from everyone who holds it.
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id)
        REFERENCES roles (role_id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS accounts (
    account_id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_number       VARCHAR(20)   NOT NULL,
    customer_id          BIGINT        NOT NULL,
    account_type         VARCHAR(20)   NOT NULL,
    status               VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    -- DECIMAL, never FLOAT. Binary floating point cannot represent 0.10 exactly,
    -- and a bank that cannot reconcile to the cent has a regulatory problem.
    -- Four decimal places stored for interest accrual; two are presented.
    balance              DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    minimum_balance      DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    interest_rate        DECIMAL(5,2)  NOT NULL DEFAULT 0.00,
    daily_transfer_limit DECIMAL(19,4) NULL,
    opened_on            DATE          NOT NULL,
    closed_on            DATE          NULL,
    last_transaction_at  TIMESTAMP(6)  NULL,
    created_at           TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           TIMESTAMP(6)  NULL,
    version              BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ux_account_number UNIQUE (account_number),
    -- RESTRICT. A customer row anchors every account and transaction they ever
    -- had; cascading the delete would take the ledger with it.
    CONSTRAINT fk_account_customer FOREIGN KEY (customer_id)
        REFERENCES customers (customer_id) ON DELETE RESTRICT,
    -- The last line of defence against an overdrawn account. The application
    -- checks under a row lock; this catches anything that bypasses it.
    CONSTRAINT ck_account_balance CHECK (balance >= 0),
    CONSTRAINT ck_account_minimum CHECK (minimum_balance >= 0),
    CONSTRAINT ck_account_type CHECK (account_type IN ('SAVINGS','CURRENT','SALARY')),
    CONSTRAINT ck_account_status CHECK (status IN ('ACTIVE','FROZEN','CLOSED','INACTIVE')),
    INDEX ix_account_customer (customer_id),
    INDEX ix_account_status (status)
) ENGINE=InnoDB;

-- Append only. No update path and no delete path: a transaction that turns out
-- to be wrong is corrected by a reversing entry, never by editing history.
CREATE TABLE IF NOT EXISTS transactions (
    transaction_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    -- NOT unique, deliberately. A transfer writes two rows sharing this
    -- reference: a debit against the source and a credit against the
    -- destination. That is what lets any account's balance be the signed sum of
    -- its own rows, and what makes a transfer net to zero across the pair.
    transaction_reference VARCHAR(32)   NOT NULL,
    account_id            BIGINT        NOT NULL,
    transaction_type      VARCHAR(20)   NOT NULL,
    direction             VARCHAR(10)   NOT NULL,
    -- Always positive; the sign lives in `direction`.
    amount                DECIMAL(19,4) NOT NULL,
    -- The running balance after this row. Denormalised so a statement does not
    -- have to sum every prior transaction, and so drift can be located rather
    -- than merely detected.
    balance_after         DECIMAL(19,4) NOT NULL,
    counterparty_account  VARCHAR(20),
    counterparty_name     VARCHAR(150),
    description           VARCHAR(300),
    transacted_at         TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    -- The banking date this counts against, separate from the instant. Monthly
    -- statements and daily limits group on this: a deposit at 23:55 belongs to
    -- that day, and grouping on a UTC instant moves it for any customer east of
    -- Greenwich.
    value_date            DATE          NOT NULL,
    performed_by          VARCHAR(80),
    CONSTRAINT fk_transaction_account FOREIGN KEY (account_id)
        REFERENCES accounts (account_id) ON DELETE RESTRICT,
    CONSTRAINT ck_transaction_amount CHECK (amount > 0),
    CONSTRAINT ck_transaction_direction CHECK (direction IN ('CREDIT','DEBIT')),
    CONSTRAINT ck_transaction_type CHECK (transaction_type IN
        ('DEPOSIT','WITHDRAWAL','TRANSFER_OUT','TRANSFER_IN',
         'INTEREST_CREDIT','CLOSURE_PAYOUT')),
    INDEX ix_txn_account_date (account_id, transacted_at),
    INDEX ix_txn_reference (transaction_reference),
    INDEX ix_txn_type (transaction_type),
    INDEX ix_txn_value_date (value_date)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS beneficiaries (
    beneficiary_id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id                BIGINT       NOT NULL,
    beneficiary_account_number VARCHAR(20)  NOT NULL,
    -- Captured at verification from the bank's own record, not from what the
    -- payer typed.
    beneficiary_name           VARCHAR(150) NOT NULL,
    nickname                   VARCHAR(80),
    bank_name                  VARCHAR(150),
    ifsc_code                  VARCHAR(20),
    verified                   BOOLEAN      NOT NULL DEFAULT FALSE,
    -- The cooling-off window is measured from here. Someone who takes over a
    -- session adds a payee and empties the account in seconds; a few hours at a
    -- low cap is the window in which the real customer sees the notification.
    verified_at                TIMESTAMP(6) NULL,
    verified_by                VARCHAR(80),
    active                     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at                 TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version                    BIGINT       NOT NULL DEFAULT 0,
    -- Scoped by customer: payees are personal, and one customer verifying a
    -- destination must not vouch for it on anybody else's behalf.
    CONSTRAINT ux_beneficiary_customer_account
        UNIQUE (customer_id, beneficiary_account_number),
    CONSTRAINT fk_beneficiary_customer FOREIGN KEY (customer_id)
        REFERENCES customers (customer_id) ON DELETE RESTRICT,
    INDEX ix_beneficiary_customer (customer_id)
) ENGINE=InnoDB;
