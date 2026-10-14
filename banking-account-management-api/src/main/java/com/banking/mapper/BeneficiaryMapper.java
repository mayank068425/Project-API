package com.banking.mapper;

import com.banking.dto.response.BeneficiaryResponse;
import com.banking.entity.Beneficiary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Beneficiary entity to response.
 *
 * <p>Masks the account number to its last four digits. A saved-payee list is one
 * of the more useful things to lift from a compromised session — it is a
 * ready-made set of destinations the victim already trusts.
 */
@Component
public class BeneficiaryMapper {

    public BeneficiaryResponse toResponse(Beneficiary beneficiary, Duration coolingPeriod) {
        if (beneficiary == null) {
            return null;
        }

        Instant now = Instant.now();
        boolean cooling = beneficiary.isInCoolingPeriod(coolingPeriod, now);
        boolean usable = beneficiary.isUsable() && !cooling;

        String note;
        if (!beneficiary.isVerified()) {
            note = "Not yet verified. Transfers to this payee are refused.";
        } else if (cooling) {
            note = "Verified recently. Higher-value transfers are held until "
                    + beneficiary.getVerifiedAt().plus(coolingPeriod) + ".";
        } else {
            note = null;
        }

        return new BeneficiaryResponse(
                beneficiary.getId(),
                mask(beneficiary.getBeneficiaryAccountNumber()),
                beneficiary.getBeneficiaryName(),
                beneficiary.getNickname(),
                beneficiary.getBankName(),
                beneficiary.getIfscCode(),
                beneficiary.isVerified(),
                beneficiary.getVerifiedAt(),
                usable,
                note);
    }

    private String mask(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return "****";
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }
}
