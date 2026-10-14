package com.banking.service;

import com.banking.dto.request.BeneficiaryRequest;
import com.banking.dto.response.BeneficiaryResponse;
import com.banking.entity.Beneficiary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Optional;

public interface BeneficiaryService {

    BeneficiaryResponse add(BeneficiaryRequest request);

    BeneficiaryResponse update(Long id, BeneficiaryRequest request);

    /**
     * Confirms the payee against the bank's own records.
     *
     * <p>Takes the name from the bank rather than from what the payer typed: a
     * payer who mistypes both number and name would otherwise see a payee that
     * looks entirely correct.
     */
    BeneficiaryResponse verify(Long id);

    void delete(Long id);

    Page<BeneficiaryResponse> list(Pageable pageable);

    BeneficiaryResponse get(Long id);

    /**
     * The saved payee for a destination, if the payer has one.
     *
     * <p>Consulted on the transfer path so verification and the cooling-off
     * window are enforced where the money moves, not only when the record is
     * created. A rule checked once at add time is not a rule.
     */
    Optional<Beneficiary> findForTransfer(Long customerId, String toAccountNumber);

    /** Throws if this payee may not receive the given amount right now. */
    void assertTransferAllowed(Beneficiary beneficiary, BigDecimal amount);
}
