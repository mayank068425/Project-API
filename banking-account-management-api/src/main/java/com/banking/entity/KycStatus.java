package com.banking.entity;

/**
 * Know-your-customer verification state.
 *
 * <p>Gates account opening rather than customer creation: a customer record can
 * exist while documents are being checked, but no account is opened against it
 * until they pass.
 */
public enum KycStatus {

    PENDING,
    VERIFIED,
    REJECTED;

    public boolean allowsAccountOpening() {
        return this == VERIFIED;
    }
}
