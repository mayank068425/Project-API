package com.banking.service;

import com.banking.dto.request.CustomerRequest;
import com.banking.dto.response.AccountResponse;
import com.banking.dto.response.CustomerResponse;
import com.banking.entity.Customer;
import com.banking.entity.KycStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CustomerService {

    CustomerResponse create(CustomerRequest request);

    /** Used by registration; creates the record without the staff-only checks. */
    Customer createForSelfRegistration(String firstName, String lastName, String email,
                                       String phone, java.time.LocalDate dateOfBirth,
                                       String address, String nationalId);

    CustomerResponse update(Long id, CustomerRequest request);

    /**
     * Soft-deletes a customer. Refused while any account is open.
     *
     * <p>A customer record anchors every account and transaction they ever had.
     * Removing it orphans the ledger and destroys records the bank is required
     * to retain.
     */
    void delete(Long id);

    CustomerResponse get(Long id);

    Page<CustomerResponse> search(String query, Pageable pageable);

    List<AccountResponse> accountsOf(Long customerId);

    CustomerResponse setKycStatus(Long id, KycStatus status, String reason);

    Customer requireCustomer(Long id);
}
