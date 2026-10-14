package com.banking.repository;

import com.banking.entity.Customer;
import com.banking.entity.KycStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByCustomerNumber(String customerNumber);

    Optional<Customer> findByEmail(String email);

    Optional<Customer> findByNationalId(String nationalId);

    boolean existsByEmail(String email);

    boolean existsByNationalId(String nationalId);

    Page<Customer> findByActiveTrue(Pageable pageable);

    Page<Customer> findByKycStatus(KycStatus status, Pageable pageable);

    /**
     * Search across the fields a teller actually types.
     *
     * <p>Names are concatenated so "jane smith" matches, which searching the
     * columns separately does not. The leading wildcard prevents index use;
     * acceptable at this scale, and noted so it is a known cost rather than a
     * mystery when the customer table grows.
     */
    @Query(value = "select c from Customer c where c.active = true and ("
            + "lower(concat(c.firstName, ' ', c.lastName)) like lower(concat('%', :q, '%')) "
            + "or lower(c.email) like lower(concat('%', :q, '%')) "
            + "or c.phone like concat('%', :q, '%') "
            + "or lower(c.customerNumber) like lower(concat('%', :q, '%')))",
            countQuery = "select count(c) from Customer c where c.active = true and ("
                    + "lower(concat(c.firstName, ' ', c.lastName)) like lower(concat('%', :q, '%')) "
                    + "or lower(c.email) like lower(concat('%', :q, '%')) "
                    + "or c.phone like concat('%', :q, '%') "
                    + "or lower(c.customerNumber) like lower(concat('%', :q, '%')))")
    Page<Customer> search(@Param("q") String q, Pageable pageable);

    @Query("select count(a) from Account a where a.customer.id = :customerId "
            + "and a.status <> com.banking.entity.AccountStatus.CLOSED")
    long countOpenAccounts(@Param("customerId") Long customerId);
}
