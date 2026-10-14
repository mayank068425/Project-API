-- Seed data.
--
-- Runs after schema.sql at every start-up (spring.sql.init.mode: always), so
-- every insert is written to be idempotent: INSERT IGNORE where a unique
-- constraint exists, and NOT EXISTS guards where one does not. A restart against
-- a populated database is a no-op rather than a pile of duplicate-key errors.
--
-- All logins share the password: Password@123
-- The hash below was verified against the bcrypt library, not written by hand.

INSERT IGNORE INTO number_sequence (sequence_name, next_value) VALUES ('MEMBER', 1);

-- No ROLE_ prefix. See the comment on the role table in schema.sql.
INSERT IGNORE INTO role (name, description) VALUES
 ('ADMIN',     'Full access, including administrative dashboards'),
 ('LIBRARIAN', 'Circulation, catalogue and member management'),
 ('MEMBER',    'Borrowing, reservations and own records');

INSERT IGNORE INTO category (name, description) VALUES
 ('Programming', 'Software development and languages'),
 ('Database',    'Relational and non-relational data stores'),
 ('AI',          'Machine learning and artificial intelligence'),
 ('Networking',  'Protocols, infrastructure and security'),
 ('Fiction',     'Novels and short stories');

INSERT INTO author (name, nationality)
SELECT * FROM (SELECT 'Robert C. Martin' AS n, 'American' AS c) t
WHERE NOT EXISTS (SELECT 1 FROM author WHERE name = 'Robert C. Martin');
INSERT INTO author (name, nationality)
SELECT * FROM (SELECT 'Joshua Bloch', 'American') t
WHERE NOT EXISTS (SELECT 1 FROM author WHERE name = 'Joshua Bloch');
INSERT INTO author (name, nationality)
SELECT * FROM (SELECT 'Martin Kleppmann', 'British') t
WHERE NOT EXISTS (SELECT 1 FROM author WHERE name = 'Martin Kleppmann');
INSERT INTO author (name, nationality)
SELECT * FROM (SELECT 'Ian Goodfellow', 'American') t
WHERE NOT EXISTS (SELECT 1 FROM author WHERE name = 'Ian Goodfellow');
INSERT INTO author (name, nationality)
SELECT * FROM (SELECT 'Ursula K. Le Guin', 'American') t
WHERE NOT EXISTS (SELECT 1 FROM author WHERE name = 'Ursula K. Le Guin');

-- ISBNs are real and their check digits verify, so BookValidator accepts them.
INSERT IGNORE INTO book (isbn, title, author_id, category_id, publisher, edition,
                         language, shelf_number, publication_year, total_copies,
                         available_copies, held_copies, replacement_cost, status)
VALUES
 ('9780132350884', 'Clean Code',
  (SELECT author_id FROM author WHERE name='Robert C. Martin'),
  (SELECT category_id FROM category WHERE name='Programming'),
  'Prentice Hall', '1st', 'English', 'A-12-03', 2008, 4, 4, 0, 45.00, 'ACTIVE'),
 ('9780134685991', 'Effective Java',
  (SELECT author_id FROM author WHERE name='Joshua Bloch'),
  (SELECT category_id FROM category WHERE name='Programming'),
  'Addison-Wesley', '3rd', 'English', 'A-12-07', 2018, 3, 3, 0, 55.00, 'ACTIVE'),
 ('9781449373320', 'Designing Data-Intensive Applications',
  (SELECT author_id FROM author WHERE name='Martin Kleppmann'),
  (SELECT category_id FROM category WHERE name='Database'),
  'O''Reilly', '1st', 'English', 'B-04-01', 2017, 2, 2, 0, 60.00, 'ACTIVE'),
 ('9780262035613', 'Deep Learning',
  (SELECT author_id FROM author WHERE name='Ian Goodfellow'),
  (SELECT category_id FROM category WHERE name='AI'),
  'MIT Press', '1st', 'English', 'C-02-05', 2016, 2, 2, 0, 80.00, 'ACTIVE'),
 ('9780441013593', 'The Left Hand of Darkness',
  (SELECT author_id FROM author WHERE name='Ursula K. Le Guin'),
  (SELECT category_id FROM category WHERE name='Fiction'),
  'Ace Books', '1st', 'English', 'F-09-11', 1969, 1, 1, 0, 15.00, 'ACTIVE');

INSERT IGNORE INTO member (membership_number, name, email, phone, membership_date,
                           membership_expiry, membership_status, books_on_loan)
VALUES
 ('LIB0000001', 'Priya Nair', 'priya.nair@example.com', '9000000001',
  CURDATE() - INTERVAL 200 DAY, CURDATE() + INTERVAL 165 DAY, 'ACTIVE', 0),
 ('LIB0000002', 'Arjun Rao', 'arjun.rao@example.com', '9000000002',
  CURDATE() - INTERVAL 90 DAY, CURDATE() + INTERVAL 275 DAY, 'ACTIVE', 0),
 -- Expired on purpose, so the "cannot borrow" path has something to demonstrate.
 ('LIB0000003', 'Meera Iyer', 'meera.iyer@example.com', '9000000003',
  CURDATE() - INTERVAL 400 DAY, CURDATE() - INTERVAL 35 DAY, 'EXPIRED', 0);

UPDATE number_sequence SET next_value = 4
WHERE sequence_name = 'MEMBER' AND next_value < 4;

INSERT IGNORE INTO users (username, email, password_hash, member_id, enabled) VALUES
 ('admin', 'admin@library.local',
  '$2a$12$GOS/X7wjoLC8O9PNIf0F7.J9mQQ/U2fEBrWQahcAtxC2yFk5F7oRi', NULL, TRUE),
 ('librarian', 'librarian@library.local',
  '$2a$12$GOS/X7wjoLC8O9PNIf0F7.J9mQQ/U2fEBrWQahcAtxC2yFk5F7oRi', NULL, TRUE),
 ('priya', 'priya.nair@example.com',
  '$2a$12$GOS/X7wjoLC8O9PNIf0F7.J9mQQ/U2fEBrWQahcAtxC2yFk5F7oRi',
  (SELECT member_id FROM member WHERE membership_number='LIB0000001'), TRUE),
 ('arjun', 'arjun.rao@example.com',
  '$2a$12$GOS/X7wjoLC8O9PNIf0F7.J9mQQ/U2fEBrWQahcAtxC2yFk5F7oRi',
  (SELECT member_id FROM member WHERE membership_number='LIB0000002'), TRUE),
 ('meera', 'meera.iyer@example.com',
  '$2a$12$GOS/X7wjoLC8O9PNIf0F7.J9mQQ/U2fEBrWQahcAtxC2yFk5F7oRi',
  (SELECT member_id FROM member WHERE membership_number='LIB0000003'), TRUE);

INSERT IGNORE INTO user_role (user_id, role_id) VALUES
 ((SELECT user_id FROM users WHERE username='admin'),
  (SELECT role_id FROM role WHERE name='ADMIN')),
 ((SELECT user_id FROM users WHERE username='librarian'),
  (SELECT role_id FROM role WHERE name='LIBRARIAN')),
 ((SELECT user_id FROM users WHERE username='priya'),
  (SELECT role_id FROM role WHERE name='MEMBER')),
 ((SELECT user_id FROM users WHERE username='arjun'),
  (SELECT role_id FROM role WHERE name='MEMBER')),
 ((SELECT user_id FROM users WHERE username='meera'),
  (SELECT role_id FROM role WHERE name='MEMBER'));

-- One loan already overdue, so the fine flow and the overdue report have
-- something to show on a fresh database. The counters are adjusted to match:
-- available_copies down one, books_on_loan up one. That is exactly what
-- /api/reports/reconciliation verifies, so seed data that got this wrong would
-- make the system report itself as broken from the first request.
INSERT INTO book_issue (reference, book_id, member_id, issue_date, due_date, status, issued_by)
SELECT 'ISS-SEED00000001',
       (SELECT book_id FROM book WHERE isbn='9780132350884'),
       (SELECT member_id FROM member WHERE membership_number='LIB0000001'),
       CURDATE() - INTERVAL 19 DAY, CURDATE() - INTERVAL 5 DAY, 'OVERDUE', 'librarian'
WHERE NOT EXISTS (SELECT 1 FROM book_issue WHERE reference = 'ISS-SEED00000001');

UPDATE book SET available_copies = available_copies - 1
WHERE isbn = '9780132350884'
  AND available_copies = total_copies
  AND EXISTS (SELECT 1 FROM book_issue WHERE reference = 'ISS-SEED00000001');

UPDATE member SET books_on_loan = 1
WHERE membership_number = 'LIB0000001' AND books_on_loan = 0
  AND EXISTS (SELECT 1 FROM book_issue WHERE reference = 'ISS-SEED00000001');

-- A second loan, on time.
INSERT INTO book_issue (reference, book_id, member_id, issue_date, due_date, status, issued_by)
SELECT 'ISS-SEED00000002',
       (SELECT book_id FROM book WHERE isbn='9781449373320'),
       (SELECT member_id FROM member WHERE membership_number='LIB0000002'),
       CURDATE() - INTERVAL 3 DAY, CURDATE() + INTERVAL 11 DAY, 'ISSUED', 'librarian'
WHERE NOT EXISTS (SELECT 1 FROM book_issue WHERE reference = 'ISS-SEED00000002');

UPDATE book SET available_copies = available_copies - 1
WHERE isbn = '9781449373320'
  AND available_copies = total_copies
  AND EXISTS (SELECT 1 FROM book_issue WHERE reference = 'ISS-SEED00000002');

UPDATE member SET books_on_loan = 1
WHERE membership_number = 'LIB0000002' AND books_on_loan = 0
  AND EXISTS (SELECT 1 FROM book_issue WHERE reference = 'ISS-SEED00000002');
