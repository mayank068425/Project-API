package com.example.lms.controller;

import com.example.lms.dto.response.ApiResponse;
import com.example.lms.jdbc.ReportJdbcRepository;
import com.example.lms.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
@Tag(name = "Reports", description = "Aggregate queries, served by Spring JDBC")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/most-borrowed")
    @Operation(summary = "Most borrowed titles. Counts loans, not copies held.")
    public ApiResponse<List<ReportJdbcRepository.BorrowedBookRow>> mostBorrowed(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(reportService.mostBorrowed(from, to, limit));
    }

    @GetMapping("/active-members")
    public ApiResponse<List<ReportJdbcRepository.ActiveMemberRow>> activeMembers(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(reportService.activeMembers(from, to, limit));
    }

    @GetMapping("/overdue")
    @Operation(summary = "Everything out past its due date, with the fine accrued so far")
    public ApiResponse<List<ReportJdbcRepository.OverdueRow>> overdue() {
        return ApiResponse.ok(reportService.overdue());
    }

    @GetMapping("/fine-collection")
    public ApiResponse<List<ReportJdbcRepository.FineCollectionRow>> fineCollection(
            @RequestParam(defaultValue = "12") int months) {
        return ApiResponse.ok(reportService.fineCollection(months));
    }

    @GetMapping("/monthly-issues")
    public ApiResponse<List<ReportJdbcRepository.MonthlyIssueRow>> monthlyIssues(
            @RequestParam(defaultValue = "12") int months) {
        return ApiResponse.ok(reportService.monthlyIssues(months));
    }

    @GetMapping("/monthly-returns")
    @Operation(summary = "Returns per month, bucketed by return date",
            description = "Separate from the issue report on purpose. Grouping issues by "
                    + "issue date and counting returns in the same pass buckets returns "
                    + "by the month the book went out, which looks plausible and is wrong.")
    public ApiResponse<List<ReportJdbcRepository.MonthlyReturnRow>> monthlyReturns(
            @RequestParam(defaultValue = "12") int months) {
        return ApiResponse.ok(reportService.monthlyReturns(months));
    }

    @GetMapping("/books-by-category")
    public ApiResponse<List<Map<String, Object>>> booksByCategory() {
        return ApiResponse.ok(reportService.booksByCategory());
    }

    @GetMapping("/books-by-author")
    public ApiResponse<List<Map<String, Object>>> booksByAuthor(
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(reportService.booksByAuthor(limit));
    }

    /**
     * Counters that disagree with the loan table.
     *
     * <p>Should always be empty. Anything here means the catalogue is claiming a
     * different number of copies on the shelf than are actually out, and that
     * failure is silent until a member is sent to fetch a book that is not there.
     */
    @GetMapping("/reconciliation")
    @Operation(summary = "Counters that disagree with the loan table. Should be empty.")
    public ApiResponse<Map<String, List<Map<String, Object>>>> reconciliation() {
        Map<String, List<Map<String, Object>>> result = reportService.reconciliation();
        boolean clean = result.values().stream().allMatch(List::isEmpty);
        return ApiResponse.ok(result, clean
                ? "All counters agree with the loan table."
                : "Counters have drifted. Investigate before trusting availability.");
    }
}
