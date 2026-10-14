package com.example.lms.service;

import com.example.lms.jdbc.ReportJdbcRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface ReportService {

    List<ReportJdbcRepository.BorrowedBookRow> mostBorrowed(LocalDate from, LocalDate to,
                                                             int limit);

    List<ReportJdbcRepository.ActiveMemberRow> activeMembers(LocalDate from, LocalDate to,
                                                              int limit);

    List<ReportJdbcRepository.OverdueRow> overdue();

    List<ReportJdbcRepository.FineCollectionRow> fineCollection(int months);

    List<ReportJdbcRepository.MonthlyIssueRow> monthlyIssues(int months);

    List<ReportJdbcRepository.MonthlyReturnRow> monthlyReturns(int months);

    List<Map<String, Object>> booksByCategory();

    List<Map<String, Object>> booksByAuthor(int limit);

    /** Counters that disagree with the loan table. Should always be empty. */
    Map<String, List<Map<String, Object>>> reconciliation();
}
