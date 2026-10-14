package com.hms.repository;

import com.hms.entity.Doctor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    boolean existsByEmailIgnoreCase(String email);

    Optional<Doctor> findByEmailIgnoreCase(String email);

    /**
     * A doctor with its department already loaded.
     *
     * <p>{@code join fetch} rather than a plain find, because the response
     * includes the department name. Without it, mapping to a DTO touches a lazy
     * association after the transaction has closed, and with
     * {@code open-in-view} off that is a LazyInitializationException.
     */
    @Query("select d from Doctor d join fetch d.department where d.id = :id")
    Optional<Doctor> findByIdWithDepartment(@Param("id") Long id);

    /**
     * A page of doctors with their departments.
     *
     * <p>A count query is supplied separately: Hibernate cannot derive one from a
     * query containing a fetch join, and paginating a fetch join on a collection
     * would make it load everything into memory and page there. This one fetches
     * a {@code @ManyToOne}, which is a single row per doctor and safe to paginate.
     */
    @Query(value = "select d from Doctor d join fetch d.department",
            countQuery = "select count(d) from Doctor d")
    Page<Doctor> findAllWithDepartment(Pageable pageable);

    @Query(value = "select d from Doctor d join fetch d.department "
            + "where lower(d.name) like lower(concat('%', :name, '%'))",
            countQuery = "select count(d) from Doctor d "
                    + "where lower(d.name) like lower(concat('%', :name, '%'))")
    Page<Doctor> searchByName(@Param("name") String name, Pageable pageable);

    @Query(value = "select d from Doctor d join fetch d.department "
            + "where lower(d.specialization) like lower(concat('%', :specialization, '%'))",
            countQuery = "select count(d) from Doctor d "
                    + "where lower(d.specialization) like lower(concat('%', :specialization, '%'))")
    Page<Doctor> searchBySpecialization(@Param("specialization") String specialization,
                                        Pageable pageable);

    @Query(value = "select d from Doctor d join fetch d.department "
            + "where d.department.id = :departmentId",
            countQuery = "select count(d) from Doctor d where d.department.id = :departmentId")
    Page<Doctor> findByDepartment(@Param("departmentId") Long departmentId, Pageable pageable);

    @Query("select p.doctor.id, count(p) from Patient p where p.active = true "
            + "and p.doctor is not null group by p.doctor.id")
    List<Object[]> countActivePatientsByDoctor();

    @Query("select count(p) from Patient p where p.doctor.id = :doctorId and p.active = true")
    long countActivePatients(@Param("doctorId") Long doctorId);
}
