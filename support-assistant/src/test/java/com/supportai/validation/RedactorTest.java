package com.supportai.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What must not reach a customer.
 *
 * <p>This runs on the draft after generation. The prompt asks the model to omit
 * all of it; the prompt is advisory and this is not.
 */
class RedactorTest {

    @Test
    @DisplayName("stack frames and exception types are removed")
    void removesDiagnostics() {
        var result = Redactor.redact(
                "The error was java.sql.SQLTransientConnectionException at "
                        + "com.acme.PayService(PayService.java:142)");

        assertThat(result.text()).doesNotContain("SQLTransientConnectionException");
        assertThat(result.text()).doesNotContain("PayService.java:142");
        assertThat(result.isClean()).isFalse();
    }

    @Test
    @DisplayName("internal hosts, addresses and resources are removed")
    void removesInfrastructureDetail() {
        var result = Redactor.redact(
                "We restarted deploy/payments on 10.42.3.17 after checking payments-db-3.internal");

        assertThat(result.text()).doesNotContain("deploy/payments");
        assertThat(result.text()).doesNotContain("10.42.3.17");
        assertThat(result.redactions()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("internal ticket references are removed")
    void removesTicketReferences() {
        var result = Redactor.redact("Tracked internally as INC-004821 and JIRA-9912.");

        assertThat(result.text()).doesNotContain("INC-004821");
        assertThat(result.text()).doesNotContain("JIRA-9912");
    }

    @Test
    @DisplayName("ordinary customer-facing prose is left alone")
    void leavesCleanTextAlone() {
        String clean = "Between 09:15 and 09:53 some payment attempts failed. "
                + "The issue has been resolved and no further action is needed.";

        var result = Redactor.redact(clean);

        assertThat(result.text()).isEqualTo(clean);
        assertThat(result.isClean()).isTrue();
    }

    @Test
    @DisplayName("commitments are flagged, not deleted")
    void flagsCommitments() {
        // A restoration-time promise is a contractual statement. Deleting it
        // silently would leave the accountable person unaware it was drafted.
        var commitments = Redactor.findCommitments(
                "Service will be fully restored within two hours and we guarantee no data "
                        + "was lost.");

        assertThat(commitments).isNotEmpty();
        assertThat(commitments).anyMatch(c -> c.toLowerCase().contains("guarantee"));
    }

    @Test
    @DisplayName("a data-loss claim is flagged even when phrased reassuringly")
    void flagsDataLossClaims() {
        assertThat(Redactor.findCommitments("No data was lost during the incident."))
                .isNotEmpty();
    }

    @Test
    void handlesNullAndBlank() {
        assertThat(Redactor.redact(null).redactions()).isEmpty();
        assertThat(Redactor.redact("").redactions()).isEmpty();
        assertThat(Redactor.findCommitments(null)).isEmpty();
    }
}
