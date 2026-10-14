package com.example.lms.serviceimpl;

import com.example.lms.config.LibraryPolicy;
import com.example.lms.dto.response.DashboardResponse;
import com.example.lms.entity.BookStatus;
import com.example.lms.entity.MembershipStatus;
import com.example.lms.jdbc.ReportJdbcRepository;
import com.example.lms.repository.BookIssueRepository;
import com.example.lms.repository.BookRepository;
import com.example.lms.repository.BookReturnRepository;
import com.example.lms.repository.FineRepository;
import com.example.lms.repository.MemberRepository;
import com.example.lms.service.DashboardService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class DashboardServiceImpl implements DashboardService {

    private final BookRepository books;
    private final MemberRepository members;
    private final BookIssueRepository issues;
    private final BookReturnRepository returns;
    private final FineRepository fines;
    private final ReportJdbcRepository reports;
    private final LibraryPolicy policy;

    public DashboardServiceImpl(BookRepository books, MemberRepository members,
                                BookIssueRepository issues, BookReturnRepository returns,
                                FineRepository fines, ReportJdbcRepository reports,
                                LibraryPolicy policy) {
        this.books = books;
        this.members = members;
        this.issues = issues;
        this.returns = returns;
        this.fines = fines;
        this.reports = reports;
        this.policy = policy;
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardResponse.Admin adminDashboard() {
        LocalDate today = LocalDate.now(policy.getZone());
        long total = books.sumTotalCopies();
        long available = books.sumAvailableCopies();

        return new DashboardResponse.Admin(
                books.countByStatus(BookStatus.ACTIVE),
                total,
                available,
                total - available,
                issues.findOverdueAsOf(today).size(),
                members.count(),
                members.countByMembershipStatus(MembershipStatus.ACTIVE),
                fines.totalCollected(),
                fines.totalPending(),
                // Surfaced here rather than buried in a report: a non-zero value
                // means the catalogue is lying about what is on the shelf, and
                // somebody needs to know today.
                reports.unreconciledBooks().size());
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardResponse.Librarian librarianDashboard() {
        LocalDate today = LocalDate.now(policy.getZone());
        return new DashboardResponse.Librarian(
                issues.countIssuedOn(today),
                returns.countByReturnedOn(today),
                returns.countLateReturnsOn(today),
                issues.countOutstanding(),
                issues.findOverdueAsOf(today).size(),
                fines.totalPending(),
                today);
    }
}
