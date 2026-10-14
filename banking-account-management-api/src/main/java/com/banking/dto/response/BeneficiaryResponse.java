package com.banking.dto.response;

import java.time.Instant;

/**
 * @param usableNow whether a transfer to this payee would be accepted right now.
 *                  Computed rather than left for the client to infer from
 *                  verified plus verifiedAt, because the cooling-off rule lives
 *                  on the server and two implementations of it would drift.
 * @param maskedAccountNumber only the last four digits. A beneficiary list is one
 *                  of the more useful things to steal from a compromised session.
 */
public record BeneficiaryResponse(Long id, String maskedAccountNumber, String beneficiaryName,
                                  String nickname, String bankName, String ifscCode,
                                  boolean verified, Instant verifiedAt, boolean usableNow,
                                  String usabilityNote) {
}
