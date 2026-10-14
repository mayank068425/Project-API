package com.hms.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "department")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "department_id")
    private Long id;

    @Column(name = "department_name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "floor_number")
    private Integer floorNumber;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Doctors in this department.
     *
     * <h2>No cascade on remove, deliberately</h2>
     * {@code CascadeType.ALL} is what most examples of this relationship use, and
     * on a hospital hierarchy it is dangerous: deleting one department would
     * delete its doctors, and if the doctor-to-patient side cascaded too, every
     * patient record under them. One HTTP call, and a department's worth of
     * medical history is gone with nothing to warn you — the cascade is doing
     * exactly what it was told.
     *
     * <p>PERSIST and MERGE only. Deletion is refused while doctors remain, and
     * the service says so rather than silently obeying.
     *
     * <h2>LAZY, and why the DTO layer matters</h2>
     * Fetching every doctor whenever a department is loaded makes the department
     * list N+1. Lazy is right, but it means this collection must not be touched
     * outside a transaction — which is why entities are never serialised and
     * {@code open-in-view} is off.
     */
    @OneToMany(mappedBy = "department", fetch = FetchType.LAZY,
            cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @Builder.Default
    @ToString.Exclude
    private List<Doctor> doctors = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    private Long version;

    /**
     * Keeps both sides of the relationship consistent in memory.
     *
     * <p>Setting only {@code doctor.setDepartment(dept)} updates the database
     * correctly, because the foreign key lives on the owning side — but the
     * in-memory {@code doctors} list is then stale for the rest of the
     * transaction, and code that reads it sees the wrong answer.
     */
    public void addDoctor(Doctor doctor) {
        doctors.add(doctor);
        doctor.setDepartment(this);
    }

    public void removeDoctor(Doctor doctor) {
        doctors.remove(doctor);
        doctor.setDepartment(null);
    }
}
