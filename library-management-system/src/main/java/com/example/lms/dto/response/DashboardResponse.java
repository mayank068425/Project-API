package com.example.lms.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class DashboardResponse {

    private DashboardResponse() {
    }

    /**
     * @param titles      distinct catalogue entries
     * @param totalCopies physical stock, which is the larger and more useful
     *                    number when someone asks how many books there are
     * @param unreconciledBooks counters disagreeing with the loan table. Should
     *                    be zero; surfaced here rather than buried in a report,
     *                    because a non-zero value means the catalogue is lying
     *                    about what is on the shelf
     */
    public record Admin(long titles, long totalCopies, long availableCopies,
                        long issuedCopies, long overdueLoans, long totalMembers,
                        long activeMembers, BigDecimal finesCollected,
                        BigDecimal finesPending, int unreconciledBooks) {
    }

    public record Librarian(long issuedToday, long returnedToday, long lateReturnsToday,
                            long outstandingLoans, long overdueLoans,
                            BigDecimal finesPending, LocalDate asOf) {
    }
}
