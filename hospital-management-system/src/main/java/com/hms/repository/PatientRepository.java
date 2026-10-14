package com.hms.repository;

import com.hms.entity.BloodGroup;
import com.hms.entity.Gender;
import com.hms.entity.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {

    /**
     * A patient with doctor and department loaded.
     *
     * <p>{@code left join fetch} on both, because either can legitimately be
     * null: a patient may be admitted before a doctor is assigned. An inner
     * fetch join would silently drop exactly those patients from the result,
     * which is the kind of bug that only shows up as "some records are missing".
     */
    @Query("select p from Patient p "
            + "left join fetch p.doctor left join fetch p.department where p.id = :id")
    Optional<Patient> findByIdWithRelations(@Param("id") Long id);

    @Query(value = "select p from Patient p "
            + "left join fetch p.doctor left join fetch p.department where p.active = true",
            countQuery = "select count(p) from Patient p where p.active = true")
    Page<Patient> findAllActive(Pageable pageable);

    /**
     * Name search across first and last name together.
     *
     * <p>Concatenating the two means "john smith" matches, which searching the
     * columns separately does not. The trailing wildcard prevents index use on
     * this predicate; acceptable at this scale, and the note is here so the
     * reason is known rather than rediscovered under load.
     */
    @Query(value = "select p from Patient p "
            + "left join fetch p.doctor left join fetch p.department "
            + "where p.active = true and ("
            + "lower(concat(p.firstName, ' ', p.lastName)) like lower(concat('%', :name, '%')))",
            countQuery = "select count(p) from Patient p where p.active = true and ("
                    + "lower(concat(p.firstName, ' ', p.lastName)) "
                    + "like lower(concat('%', :name, '%')))")
    Page<Patient> searchByName(@Param("name") String name, Pageable pageable);

    @Query(value = "select p from Patient p "
            + "left join fetch p.doctor left join fetch p.department "
            + "where p.active = true and p.phone = :phone",
            countQuery = "select count(p) from Patient p where p.active = true "
                    + "and p.phone = :phone")
    Page<Patient> findByPhone(@Param("phone") String phone, Pageable pageable);

    @Query(value = "select p from Patient p "
            + "left join fetch p.doctor left join fetch p.department "
            + "where p.active = true and p.bloodGroup = :bloodGroup",
            countQuery = "select count(p) from Patient p where p.active = true "
                    + "and p.bloodGroup = :bloodGroup")
    Page<Patient> findByBloodGroup(@Param("bloodGroup") BloodGroup bloodGroup, Pageable pageable);

    @Query(value = "select p from Patient p "
            + "left join fetch p.doctor left join fetch p.department "
            + "where p.active = true and p.gender = :gender",
            countQuery = "select count(p) from Patient p where p.active = true "
                    + "and p.gender = :gender")
    Page<Patient> findByGender(@Param("gender") Gender gender, Pageable pageable);

    @Query(value = "select p from Patient p "
            + "left join fetch p.doctor left join fetch p.department "
            + "where p.active = true and p.department.id = :departmentId",
            countQuery = "select count(p) from Patient p where p.active = true "
                    + "and p.department.id = :departmentId")
    Page<Patient> findByDepartment(@Param("departmentId") Long departmentId, Pageable pageable);

    @Query(value = "select p from Patient p "
            + "left join fetch p.doctor left join fetch p.department "
            + "where p.active = true and p.doctor.id = :doctorId",
            countQuery = "select count(p) from Patient p where p.active = true "
                    + "and p.doctor.id = :doctorId")
    Page<Patient> findByDoctor(@Param("doctorId") Long doctorId, Pageable pageable);

    long countByActiveTrue();

    long countByActiveTrueAndDepartmentId(Long departmentId);

    List<Patient> findByActiveTrueAndDoctorId(Long doctorId);

    /**
     * Patients born on or after a date, i.e. younger than a given age.
     *
     * <p>Age is filtered by converting it to a date range rather than computing
     * an age per row. The computed version cannot use the index on date of birth
     * and has to evaluate every patient in the table.
     */
    @Query("select count(p) from Patient p where p.active = true and p.dateOfBirth > :bornAfter")
    long countBornAfter(@Param("bornAfter") LocalDate bornAfter);

    /**
     * Patients whose department disagrees with their doctor's.
     *
     * <p>The two columns are denormalised and can drift — a doctor transferring
     * department leaves every one of their patients pointing at the old one.
     * Should always be empty, because assignment sets the department from the
     * doctor; anything here means something wrote the columns independently.
     */
    @Query("select p from Patient p where p.doctor is not null and p.department is not null "
            + "and p.doctor.department.id <> p.department.id")
    List<Patient> findWithMismatchedDepartment();
}
