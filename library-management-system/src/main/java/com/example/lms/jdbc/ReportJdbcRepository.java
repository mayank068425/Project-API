package com.example.lms.jdbc;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Reporting queries, written in SQL.
 *
 * <h2>Why these are not JPA</h2>
 * Every query here is an aggregate over a join, and several rank or bucket the
 * result. JPQL expresses that badly, and the entity-graph approach makes it
 * worse: "most borrowed books" through JPA means loading books, loading their
 * loans and counting in Java — O(loans) in memory, getting slower exactly as the
 * library gets busier.
 *
 * <p>In SQL each of these is one indexed pass. That is why both Spring Data JPA
 * and Spring JDBC are in this project: they are for different shapes of problem,
 * not two ways of doing the same thing.
 *
 * <p>Both share the application DataSource and transaction manager, so a report
 * read inside an open transaction sees that transaction's uncommitted writes.
 */
@Repository
public class ReportJdbcRepository {

    private final JdbcTemplate jdbc;

    public ReportJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record BorrowedBookRow(Long bookId, String isbn, String title, String author,
                                  String category, long timesBorrowed, long currentlyOut) {
    }

    /**
     * Most borrowed titles over a window.
     *
     * <p>Counts loans, not copies. Ranking by copies held measures what the
     * library bought rather than what its members read, and a title with forty
     * copies nobody takes out would top the list.
     */
    public List<BorrowedBookRow> mostBorrowed(LocalDate from, LocalDate to, int limit) {
        String sql = "SELECT b.book_id, b.isbn, b.title, a.name AS author, c.name AS category, "
                + "       COUNT(i.issue_id) AS times_borrowed, "
                + "       SUM(CASE WHEN i.status IN ('ISSUED','OVERDUE') THEN 1 ELSE 0 END) "
                + "         AS currently_out "
                + "FROM book b "
                + "JOIN author a ON a.author_id = b.author_id "
                + "JOIN category c ON c.category_id = b.category_id "
                + "JOIN book_issue i ON i.book_id = b.book_id "
                + "WHERE i.issue_date BETWEEN ? AND ? "
                + "GROUP BY b.book_id, b.isbn, b.title, a.name, c.name "
                + "ORDER BY times_borrowed DESC, b.title ASC "
                + "LIMIT ?";
        return jdbc.query(sql, (rs, i) -> new BorrowedBookRow(
                rs.getLong("book_id"), rs.getString("isbn"), rs.getString("title"),
                rs.getString("author"), rs.getString("category"),
                rs.getLong("times_borrowed"), rs.getLong("currently_out")),
                from, to, limit);
    }

    public record ActiveMemberRow(Long memberId, String membershipNumber, String name,
                                  long loansInPeriod, long currentlyOut, BigDecimal finesPaid) {
    }

    /**
     * Most active members over a window.
     *
     * <p>The fines figure is a correlated subquery, not a join. Joining the fine
     * table alongside the loan table multiplies the loan count by the number of
     * fine rows — the fan-out that silently inflates every number in a report
     * nobody double-checks.
     */
    public List<ActiveMemberRow> activeMembers(LocalDate from, LocalDate to, int limit) {
        String sql = "SELECT m.member_id, m.membership_number, m.name, "
                + "       COUNT(i.issue_id) AS loans_in_period, "
                + "       SUM(CASE WHEN i.status IN ('ISSUED','OVERDUE') THEN 1 ELSE 0 END) "
                + "         AS currently_out, "
                + "       COALESCE((SELECT SUM(f.amount_paid) FROM fine f "
                + "                 WHERE f.member_id = m.member_id), 0) AS fines_paid "
                + "FROM member m "
                + "JOIN book_issue i ON i.member_id = m.member_id "
                + "WHERE i.issue_date BETWEEN ? AND ? "
                + "GROUP BY m.member_id, m.membership_number, m.name "
                + "ORDER BY loans_in_period DESC, m.name ASC "
                + "LIMIT ?";
        return jdbc.query(sql, (rs, i) -> new ActiveMemberRow(
                rs.getLong("member_id"), rs.getString("membership_number"),
                rs.getString("name"), rs.getLong("loans_in_period"),
                rs.getLong("currently_out"), rs.getBigDecimal("fines_paid")),
                from, to, limit);
    }

    public record OverdueRow(String reference, String isbn, String title,
                             String membershipNumber, String memberName, String memberPhone,
                             LocalDate dueDate, long daysOverdue, BigDecimal accruedFine) {
    }

    /**
     * Everything currently out past its due date.
     *
     * <p>The accrued fine is computed and capped inside the query rather than row
     * by row in Java, so the report agrees with what the desk will charge. Two
     * implementations of the same rule always drift apart eventually.
     */
    public List<OverdueRow> overdue(LocalDate asOf, BigDecimal ratePerDay, int graceDays,
                                    BigDecimal defaultReplacementCost) {
        String sql = "SELECT i.reference, b.isbn, b.title, "
                + "       m.membership_number, m.name AS member_name, m.phone, "
                + "       i.due_date, DATEDIFF(?, i.due_date) AS days_overdue, "
                + "       LEAST(GREATEST(DATEDIFF(?, i.due_date) - ?, 0) * ?, "
                + "             COALESCE(b.replacement_cost, ?)) AS accrued_fine "
                + "FROM book_issue i "
                + "JOIN book b ON b.book_id = i.book_id "
                + "JOIN member m ON m.member_id = i.member_id "
                + "WHERE i.status IN ('ISSUED','OVERDUE') AND i.due_date < ? "
                + "ORDER BY i.due_date ASC";
        return jdbc.query(sql, (rs, i) -> new OverdueRow(
                rs.getString("reference"), rs.getString("isbn"), rs.getString("title"),
                rs.getString("membership_number"), rs.getString("member_name"),
                rs.getString("phone"), rs.getDate("due_date").toLocalDate(),
                rs.getLong("days_overdue"), rs.getBigDecimal("accrued_fine")),
                asOf, asOf, graceDays, ratePerDay, defaultReplacementCost, asOf);
    }

    public record FineCollectionRow(int year, int month, long fineCount, BigDecimal charged,
                                    BigDecimal collected, BigDecimal waived,
                                    BigDecimal outstanding) {
    }

    public List<FineCollectionRow> fineCollection(int months) {
        String sql = "SELECT YEAR(created_at) AS yr, MONTH(created_at) AS mth, "
                + "       COUNT(*) AS fine_count, "
                + "       COALESCE(SUM(amount), 0) AS charged, "
                + "       COALESCE(SUM(amount_paid), 0) AS collected, "
                + "       COALESCE(SUM(CASE WHEN status = 'WAIVED' THEN amount ELSE 0 END), 0) "
                + "         AS waived, "
                + "       COALESCE(SUM(CASE WHEN status = 'PENDING' THEN amount - amount_paid "
                + "                         ELSE 0 END), 0) AS outstanding "
                + "FROM fine "
                + "WHERE created_at >= DATE_SUB(NOW(), INTERVAL ? MONTH) "
                + "GROUP BY YEAR(created_at), MONTH(created_at) "
                + "ORDER BY yr DESC, mth DESC";
        return jdbc.query(sql, (rs, i) -> new FineCollectionRow(
                rs.getInt("yr"), rs.getInt("mth"), rs.getLong("fine_count"),
                rs.getBigDecimal("charged"), rs.getBigDecimal("collected"),
                rs.getBigDecimal("waived"), rs.getBigDecimal("outstanding")), months);
    }

    public record MonthlyIssueRow(int year, int month, long issued) {
    }

    public record MonthlyReturnRow(int year, int month, long returned, long returnedLate) {
    }

    /**
     * Issues per month, bucketed by issue date.
     *
     * <p>Separate from the return report on purpose. The tempting version groups
     * {@code book_issue} by {@code issue_date} and counts returns in the same
     * pass, which buckets returns by the month the book went <em>out</em>. It
     * looks plausible and is wrong: a book issued in March and returned in May
     * counts as a May return only if the return date does the grouping.
     */
    public List<MonthlyIssueRow> monthlyIssues(int months) {
        String sql = "SELECT YEAR(issue_date) AS yr, MONTH(issue_date) AS mth, "
                + "       COUNT(*) AS issued "
                + "FROM book_issue "
                + "WHERE issue_date >= DATE_SUB(CURDATE(), INTERVAL ? MONTH) "
                + "GROUP BY YEAR(issue_date), MONTH(issue_date) "
                + "ORDER BY yr DESC, mth DESC";
        return jdbc.query(sql, (rs, i) -> new MonthlyIssueRow(
                rs.getInt("yr"), rs.getInt("mth"), rs.getLong("issued")), months);
    }

    /** Returns per month, bucketed by the return date, from its own table. */
    public List<MonthlyReturnRow> monthlyReturns(int months) {
        String sql = "SELECT YEAR(returned_on) AS yr, MONTH(returned_on) AS mth, "
                + "       COUNT(*) AS returned, "
                + "       SUM(CASE WHEN days_late > 0 THEN 1 ELSE 0 END) AS returned_late "
                + "FROM book_return "
                + "WHERE returned_on >= DATE_SUB(CURDATE(), INTERVAL ? MONTH) "
                + "GROUP BY YEAR(returned_on), MONTH(returned_on) "
                + "ORDER BY yr DESC, mth DESC";
        return jdbc.query(sql, (rs, i) -> new MonthlyReturnRow(
                rs.getInt("yr"), rs.getInt("mth"), rs.getLong("returned"),
                rs.getLong("returned_late")), months);
    }

    public List<Map<String, Object>> booksByCategory() {
        return jdbc.queryForList(
                "SELECT c.name AS category, COUNT(b.book_id) AS titles, "
                        + "       COALESCE(SUM(b.total_copies), 0) AS total_copies, "
                        + "       COALESCE(SUM(b.available_copies), 0) AS available_copies "
                        + "FROM category c "
                        + "LEFT JOIN book b ON b.category_id = c.category_id "
                        + "       AND b.status = 'ACTIVE' "
                        + "GROUP BY c.category_id, c.name ORDER BY titles DESC");
    }

    public List<Map<String, Object>> booksByAuthor(int limit) {
        return jdbc.queryForList(
                "SELECT a.name AS author, COUNT(b.book_id) AS titles, "
                        + "       COALESCE(SUM(b.total_copies), 0) AS total_copies "
                        + "FROM author a "
                        + "LEFT JOIN book b ON b.author_id = a.author_id "
                        + "       AND b.status = 'ACTIVE' "
                        + "GROUP BY a.author_id, a.name ORDER BY titles DESC LIMIT ?", limit);
    }

    /**
     * Books whose stored counters disagree with the loan table.
     *
     * <p>{@code available_copies} is derived and stored, so it drifts. Nothing
     * should ever appear here; anything that does means a copy count was written
     * without a matching loan, and the failure is otherwise completely silent —
     * the first symptom is a member sent to a shelf that is empty.
     */
    public List<Map<String, Object>> unreconciledBooks() {
        return jdbc.queryForList(
                "SELECT b.book_id, b.isbn, b.title, b.total_copies, b.available_copies, "
                        + "       b.held_copies, COALESCE(l.active_loans, 0) AS active_loans, "
                        + "       b.total_copies - b.available_copies - b.held_copies "
                        + "         - COALESCE(l.active_loans, 0) AS discrepancy "
                        + "FROM book b "
                        + "LEFT JOIN (SELECT book_id, COUNT(*) AS active_loans "
                        + "           FROM book_issue WHERE status IN ('ISSUED','OVERDUE') "
                        + "           GROUP BY book_id) l ON l.book_id = b.book_id "
                        + "WHERE b.total_copies - b.available_copies - b.held_copies "
                        + "      <> COALESCE(l.active_loans, 0)");
    }

    public List<Map<String, Object>> unreconciledMembers() {
        return jdbc.queryForList(
                "SELECT m.member_id, m.membership_number, m.name, m.books_on_loan, "
                        + "       COALESCE(l.active_loans, 0) AS active_loans "
                        + "FROM member m "
                        + "LEFT JOIN (SELECT member_id, COUNT(*) AS active_loans "
                        + "           FROM book_issue WHERE status IN ('ISSUED','OVERDUE') "
                        + "           GROUP BY member_id) l ON l.member_id = m.member_id "
                        + "WHERE m.books_on_loan <> COALESCE(l.active_loans, 0)");
    }

    /**
     * Loans with more than one return row.
     *
     * <p>Impossible while the unique constraint on {@code book_return.issue_id}
     * holds, which is exactly why it is worth checking: if this ever returns a
     * row, the constraint was dropped by a migration and returns have been
     * double-counted since.
     */
    public List<Map<String, Object>> loansWithMultipleReturns() {
        return jdbc.queryForList(
                "SELECT issue_id, COUNT(*) AS return_count FROM book_return "
                        + "GROUP BY issue_id HAVING COUNT(*) > 1");
    }
}
