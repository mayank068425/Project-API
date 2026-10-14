package com.hms.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "doctor", indexes = {
        @Index(name = "ix_doctor_name", columnList = "doctor_name"),
        @Index(name = "ix_doctor_specialization", columnList = "specialization"),
        @Index(name = "ix_doctor_department", columnList = "department_id"),
        @Index(name = "ux_doctor_email", columnList = "email", unique = true)
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Doctor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "doctor_id")
    private Long id;

    @Column(name = "doctor_name", nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 100)
    private String specialization;

    /** Years in practice. */
    @Column(name = "experience_years", nullable = false)
    private Integer experienceYears;

    @Column(length = 200)
    private String qualification;

    @Column(length = 20)
    private String mobile;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    /**
     * Money, so {@code BigDecimal} over a {@code DECIMAL} column.
     *
     * <p>{@code double} cannot represent 0.10 exactly. One consultation fee is
     * fine; a month of billing totals is not.
     */
    @Column(name = "consultation_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal consultationFee;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * The owning side of the relationship: the foreign key lives on this table.
     *
     * <p>LAZY because loading a doctor to show a name should not also load the
     * department, and the default for {@code @ManyToOne} is EAGER — which is how
     * a listing endpoint quietly turns into one extra query per row.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_doctor_department"))
    @ToString.Exclude
    private Department department;

    /**
     * Patients under this doctor's care.
     *
     * <p>No cascade on remove, for the same reason as on Department: a doctor
     * leaving must not take their patients' records with them. Patients are
     * reassigned or unassigned; the records stay.
     */
    @OneToMany(mappedBy = "doctor", fetch = FetchType.LAZY,
            cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @Builder.Default
    @ToString.Exclude
    private List<Patient> patients = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    private Long version;
}
