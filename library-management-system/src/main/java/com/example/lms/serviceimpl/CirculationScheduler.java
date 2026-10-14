package com.example.lms.serviceimpl;

import com.example.lms.security.TokenBlacklist;
import com.example.lms.service.IssueService;
import com.example.lms.service.ReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic housekeeping.
 *
 * <p>All three sweeps are convenience only. Overdue status is a reporting label
 * and the fine is derived from dates on demand; lapsed holds are also detected
 * when somebody tries to issue the title; expired tokens are rejected by the
 * signature check anyway. Nothing here is required for correctness, which is
 * deliberate — a library whose fines are wrong because a cron job died is a
 * library that finds out from an angry member.
 *
 * <p>Each catches its own exceptions, because an unhandled one in a scheduled
 * method stops that schedule permanently in some configurations.
 */
@Component
public class CirculationScheduler {

    private static final Logger log = LoggerFactory.getLogger(CirculationScheduler.class);

    private final IssueService issueService;
    private final ReservationService reservationService;
    private final TokenBlacklist blacklist;

    public CirculationScheduler(IssueService issueService,
                                ReservationService reservationService,
                                TokenBlacklist blacklist) {
        this.issueService = issueService;
        this.reservationService = reservationService;
        this.blacklist = blacklist;
    }

    @Scheduled(cron = "${lms.schedule.overdue-sweep:0 15 0 * * *}")
    public void sweepOverdue() {
        try {
            int marked = issueService.markOverdue();
            if (marked > 0) {
                log.info("marked {} loans overdue", marked);
            }
        } catch (RuntimeException ex) {
            log.error("overdue sweep failed", ex);
        }
    }

    @Scheduled(cron = "${lms.schedule.hold-sweep:0 0/30 * * * *}")
    public void sweepHolds() {
        try {
            reservationService.expireLapsed();
        } catch (RuntimeException ex) {
            log.error("hold sweep failed", ex);
        }
    }

    @Scheduled(cron = "${lms.schedule.token-sweep:0 0 * * * *}")
    public void sweepRevokedTokens() {
        try {
            int evicted = blacklist.evictExpired();
            if (evicted > 0) {
                log.debug("evicted {} expired tokens from the blacklist", evicted);
            }
        } catch (RuntimeException ex) {
            log.error("token sweep failed", ex);
        }
    }
}
