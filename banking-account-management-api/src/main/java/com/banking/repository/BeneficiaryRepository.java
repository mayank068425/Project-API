package com.banking.repository;

import com.banking.entity.Beneficiary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BeneficiaryRepository extends JpaRepository<Beneficiary, Long> {

    @Query("select b from Beneficiary b join fetch b.customer where b.id = :id")
    Optional<Beneficiary> findByIdWithCustomer(@Param("id") Long id);

    /**
     * A customer's saved payee for a given account number.
     *
     * <p>Scoped by customer, not just account number. Beneficiaries are personal:
     * one customer verifying a payee must not silently vouch for it on another
     * customer's behalf.
     */
    Optional<Beneficiary> findByCustomerIdAndBeneficiaryAccountNumber(
            Long customerId, String beneficiaryAccountNumber);

    List<Beneficiary> findByCustomerIdAndActiveTrue(Long customerId);

    @Query(value = "select b from Beneficiary b where b.customer.id = :customerId "
            + "and b.active = true order by b.beneficiaryName",
            countQuery = "select count(b) from Beneficiary b "
                    + "where b.customer.id = :customerId and b.active = true")
    Page<Beneficiary> findByCustomer(@Param("customerId") Long customerId, Pageable pageable);

    long countByCustomerIdAndActiveTrue(Long customerId);

    boolean existsByCustomerIdAndBeneficiaryAccountNumberAndActiveTrue(
            Long customerId, String beneficiaryAccountNumber);
}
