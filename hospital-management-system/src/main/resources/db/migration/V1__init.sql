-- Hospital Management System schema. MySQL 8.
-- Table and column names follow the specification.

CREATE TABLE department (
    department_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    department_name VARCHAR(100) NOT NULL,
    description     VARCHAR(500),
    floor_number    INT,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6) NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    -- The service checks for duplicates first so the caller gets a readable
    -- message, but that check loses the race against a simultaneous insert.
    -- This constraint is the actual guarantee.
    CONSTRAINT ux_department_name UNIQUE (department_name)
) ENGINE=InnoDB;

CREATE TABLE doctor (
    doctor_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    doctor_name      VARCHAR(150)  NOT NULL,
    specialization   VARCHAR(100)  NOT NULL,
    experience_years INT           NOT NULL,
    qualification    VARCHAR(200),
    mobile           VARCHAR(20),
    email            VARCHAR(150)  NOT NULL,
    -- DECIMAL, never FLOAT. Binary floating point cannot represent 0.10
    -- exactly; one fee is fine, a month of billing totals is not.
    consultation_fee DECIMAL(10,2) NOT NULL,
    department_id    BIGINT        NOT NULL,
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       TIMESTAMP(6)  NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ux_doctor_email UNIQUE (email),
    -- RESTRICT, not CASCADE. The JPA mapping omits CascadeType.REMOVE for the
    -- same reason: deleting a department must never delete its doctors, and
    -- through them their patients' records.
    CONSTRAINT fk_doctor_department FOREIGN KEY (department_id)
        REFERENCES department (department_id) ON DELETE RESTRICT,
    CONSTRAINT ck_doctor_experience CHECK (experience_years >= 0 AND experience_years <= 70),
    CONSTRAINT ck_doctor_fee CHECK (consultation_fee > 0)
) ENGINE=InnoDB;

CREATE INDEX ix_doctor_name ON doctor (doctor_name);
CREATE INDEX ix_doctor_specialization ON doctor (specialization);
CREATE INDEX ix_doctor_department ON doctor (department_id);

CREATE TABLE patient (
    patient_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    first_name    VARCHAR(80)  NOT NULL,
    last_name     VARCHAR(80)  NOT NULL,
    -- There is no age column, deliberately. The specification listed both age
    -- and date of birth; a stored age is correct on the day it is written and
    -- wrong from the patient's next birthday onwards, with nothing to correct
    -- it. Age is derived from this column on every read.
    date_of_birth DATE         NOT NULL,
    gender        VARCHAR(20)  NOT NULL,
    -- An enum rather than free text: "O+" and "0+" are indistinguishable at a
    -- glance, and this is the field where a typo does the most harm.
    blood_group   VARCHAR(15)  NOT NULL,
    phone         VARCHAR(20)  NOT NULL,
    email         VARCHAR(150),
    address       VARCHAR(300),
    disease       VARCHAR(300),
    -- Both nullable: a patient can be admitted to a department before a doctor
    -- is assigned to them.
    doctor_id     BIGINT NULL,
    department_id BIGINT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    TIMESTAMP(6) NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    -- SET NULL rather than CASCADE: if a doctor record is ever removed by hand,
    -- the patient is unassigned, not deleted.
    CONSTRAINT fk_patient_doctor FOREIGN KEY (doctor_id)
        REFERENCES doctor (doctor_id) ON DELETE SET NULL,
    CONSTRAINT fk_patient_department FOREIGN KEY (department_id)
        REFERENCES department (department_id) ON DELETE SET NULL,
    CONSTRAINT ck_patient_gender CHECK (gender IN ('MALE','FEMALE','OTHER','UNDISCLOSED')),
    CONSTRAINT ck_patient_blood_group CHECK (blood_group IN
        ('A_POSITIVE','A_NEGATIVE','B_POSITIVE','B_NEGATIVE',
         'AB_POSITIVE','AB_NEGATIVE','O_POSITIVE','O_NEGATIVE')),
    -- Only a static floor here. MySQL rejects non-deterministic functions in
    -- CHECK constraints, so `date_of_birth <= CURRENT_DATE` is not expressible
    -- and would fail at DDL time with ERROR 3814 -- the migration would never
    -- apply and the application would never start. The future-date rule lives
    -- in @Past on the DTO and in PatientService.validateDateOfBirth, which is
    -- where a rule that depends on "now" belongs anyway.
    CONSTRAINT ck_patient_dob CHECK (date_of_birth >= '1890-01-01')
) ENGINE=InnoDB;

CREATE INDEX ix_patient_last_name ON patient (last_name);
CREATE INDEX ix_patient_phone ON patient (phone);
CREATE INDEX ix_patient_blood_group ON patient (blood_group);
CREATE INDEX ix_patient_doctor ON patient (doctor_id);
CREATE INDEX ix_patient_department ON patient (department_id);
-- Age filters are converted to a date range rather than computed per row, so
-- this index is usable.
CREATE INDEX ix_patient_dob ON patient (date_of_birth);
