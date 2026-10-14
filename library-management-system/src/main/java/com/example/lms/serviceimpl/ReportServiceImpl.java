package com.example.lms.serviceimpl;

import com.example.lms.config.LibraryPolicy;
import com.example.lms.jdbc.ReportJdbcRepository;
import com.example.lms.service.ReportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class ReportServiceImpl implements ReportService {

    private final ReportJdbcRepository reports;
    private final LibraryPolicy policy;

    public ReportServiceImpl(ReportJdbcRepository reports, LibraryPolicy policy) {
        this.reports = reports;
        this.policy = policy;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportJdbcRepository.BorrowedBookRow> mostBorrowed(LocalDate from, LocalDate to,
                                                                    int limit) {
        LocalDate end = to != null ? to : LocalDate.now(policy.getZone());
        LocalDate start = from != null ? from : end.minusMonths(12);
        return reports.mostBorrowed(start, end, clamp(limit, 1, 200));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportJdbcRepository.ActiveMemberRow> activeMembers(LocalDate from, LocalDate to,
                                                                     int limit) {
        LocalDate end = to != null ? to : LocalDate.now(policy.getZone());
        LocalDate start = from != null ? from : end.minusMonths(12);
        return reports.activeMembers(start, end, clamp(limit, 1, 200));
    }

    /**
     * Everything out past its due date, with the fine accrued so far.
     *
     * <p>The policy values are passed into the query so the report computes the
     * fine the same way the desk does. Two implementations of one rule always
     * drift apart, and a report that disagrees with the counter is worse than no
     * report.
     */
    @Override
    @Transactional(readOnly = true)
    public List<ReportJdbcRepository.OverdueRow> overdue() {
        return reports.overdue(LocalDate.now(policy.getZone()), policy.getFinePerDay(),
                policy.getGraceDays(), policy.getDefaultReplacementCost());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportJdbcRepository.FineCollectionRow> fineCollection(int months) {
        return reports.fineCollection(clamp(months, 1, 60));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportJdbcRepository.MonthlyIssueRow> monthlyIssues(int months) {
        return reports.monthlyIssues(clamp(months, 1, 60));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportJdbcRepository.MonthlyReturnRow> monthlyReturns(int months) {
        return reports.monthlyReturns(clamp(months, 1, 60));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> booksByCategory() {
        return reports.booksByCategory();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> booksByAuthor(int limit) {
        return reports.booksByAuthor(clamp(limit, 1, 500));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, List<Map<String, Object>>> reconciliation() {
        return Map.of(
                "books", reports.unreconciledBooks(),
                "members", reports.unreconciledMembers(),
                // Impossible while the unique constraint on book_return.issue_id
                // holds, which is exactly why it is checked: a row here means the
                // constraint was dropped by a migration and returns have been
                // double-counted since.
                "duplicateReturns", reports.loansWithMultipleReturns());
    }

    private int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }
}
