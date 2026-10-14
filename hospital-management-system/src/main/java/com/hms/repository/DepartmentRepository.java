package com.hms.repository;

import com.hms.entity.Department;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    Page<Department> findByActiveTrue(Pageable pageable);

    /**
     * Doctor counts for every department, in one grouped query.
     *
     * <p>Returns {@code [departmentId, count]} pairs. The alternative is reading
     * {@code department.getDoctors().size()} per row, which is one query each: a
     * page of twenty departments becomes twenty-one queries, and the number grows
     * with the data rather than staying fixed. It looks fine against three rows
     * in development.
     */
    @Query("select d.department.id, count(d) from Doctor d where d.active = true "
            + "group by d.department.id")
    List<Object[]> countActiveDoctorsByDepartment();

    @Query("select count(d) from Doctor d where d.department.id = :departmentId "
            + "and d.active = true")
    long countActiveDoctors(Long departmentId);
}
