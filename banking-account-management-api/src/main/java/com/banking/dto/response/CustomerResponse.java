package com.banking.dto.response;

import com.banking.entity.KycStatus;

import java.time.LocalDate;

/**
 * A customer as it leaves the API.
 *
 * <p>Note what is absent: {@code nationalId}. It identifies a person for life,
 * cannot be reissued after a leak, and no screen in this system needs it back
 * once it has been captured. It is written and never returned.
 *
 * @param age derived on every read from the date of birth, so it cannot go stale
 */
public record CustomerResponse(Long id, String customerNumber, String firstName,
                               String lastName, String fullName, String email, String phone,
                               LocalDate dateOfBirth, int age, String address, String city,
                               String postalCode, KycStatus kycStatus, boolean active,
                               int accountCount) {
}
