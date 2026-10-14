package com.hms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;

@Entity
@Table(name = "patient", indexes = {
        @Index(name = "ix_patient_last_name", columnList = "last_name"),
        @Index(name = "ix_patient_phone", columnList = "phone"),
        @Index(name = "ix_patient_blood_group", columnList = "blood_group"),
        @Index(name = "ix_patient_doctor", columnList = "doctor_id"),
        @Index(name = "ix_patient_department", columnList = "department_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "patient_id")
    private Long id;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    /**
     * Date of birth. The stored fact.
     *
     * <p>Note that there is no {@code age} column. The specification listed both,
     * and storing age is a bug: it is correct on the day it is written and wrong
     * from the patient's next birthday onwards, with nothing to correct it. A
     * patient registered at 34 is still 34 in the database five years later.
     *
     * <p>In a hospital that is not cosmetic. Paediatric versus adult dosing,
     * screening intervals and consent thresholds are all age-gated, and every one
     * of them reads this number.
     *
     * @see #getAge()
     */
    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_group", nullable = false, length = 15)
    private BloodGroup bloodGroup;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(length = 300)
    private String address;

    @Column(length = 300)
    private String disease;

    /**
     * The attending doctor. Optional: a patient can be admitted to a department
     * before anyone is assigned to them.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id",
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_patient_doctor"))
    @ToString.Exclude
    private Doctor doctor;

    /**
     * The treating department.
     *
     * <p>Denormalised: a doctor already has a department, so this column can
     * disagree with {@code doctor.department}. It is kept because a patient can
     * be admitted under a department before a doctor is assigned, and dropping it
     * would leave those patients belonging nowhere.
     *
     * <p>The rule that keeps the two honest lives in the service: whenever a
     * doctor is assigned, the patient's department is set from that doctor. The
     * doctor wins, always, and the reconciliation report surfaces any row where
     * they have drifted apart anyway.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id",
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_patient_department"))
    @ToString.Exclude
    private Department department;

    /**
     * Soft delete.
     *
     * <p>A patient record is a medical record. Hard-deleting one destroys history
     * that may be needed for continuity of care or required to be retained by
     * law, and it orphans anything that referenced it.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    private Long version;

    /**
     * Age today, computed from the date of birth.
     *
     * <p>{@code @Transient}: never persisted, never stale. {@link Period} handles
     * the awkward cases correctly — a patient born on 29 February turns a year
     * older on 1 March in non-leap years, and someone whose birthday is today is
     * already the older age.
     */
    @Transient
    public int getAge() {
        return ageOn(LocalDate.now());
    }

    /** Age as at a given date, which is what makes this testable. */
    public int ageOn(LocalDate on) {
        if (dateOfBirth == null) {
            throw new IllegalStateException("Patient has no date of birth");
        }
        if (on.isBefore(dateOfBirth)) {
            throw new IllegalArgumentException("Date is before the patient's date of birth");
        }
        return Period.between(dateOfBirth, on).getYears();
    }

    @Transient
    public String getFullName() {
        return firstName + " " + lastName;
    }

    @Transient
    public boolean isMinor() {
        return getAge() < 18;
    }
}
