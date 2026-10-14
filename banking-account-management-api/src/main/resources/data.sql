-- Seed data.
--
-- Runs after schema.sql at every start-up, so every statement is idempotent:
-- INSERT IGNORE where a unique constraint exists, NOT EXISTS guards elsewhere.
-- A restart against a populated database is a no-op rather than a pile of
-- duplicate-key errors.
--
-- All logins share the password: Password@123
-- The hash was verified against the bcrypt library, not written by hand.
--
-- Account numbers carry a Luhn check digit and were generated rather than
-- invented, so AccountNumberGenerator.isValid accepts them.

INSERT IGNORE INTO number_sequence (sequence_name, next_value) VALUES
 ('ACCOUNT', 100006),
 ('CUSTOMER', 4);

-- No ROLE_ prefix. See the comment on the roles table in schema.sql.
INSERT IGNORE INTO roles (name, description) VALUES
 ('ADMIN',         'Full access including customer deletion'),
 ('BANK_EMPLOYEE', 'Counter operations: customers, accounts, cash'),
 ('CUSTOMER',      'Own accounts, transfers and payees');

INSERT IGNORE INTO customers (customer_number, first_name, last_name, email, phone,
                              date_of_birth, address, city, postal_code, national_id,
                              kyc_status, active)
VALUES
 ('CUS000000001','Priya','Nair','priya.nair@example.com','9000000001',
  '1991-03-14','12 Kothrud Road','Pune','411038','ABCPN1234A','VERIFIED',TRUE),
 ('CUS000000002','Arjun','Rao','arjun.rao@example.com','9000000002',
  '1985-11-02','44 Baner Road','Pune','411045','ABCPR5678B','VERIFIED',TRUE),
 -- KYC still pending on purpose, so the "cannot open an account" path has
 -- something to act on.
 ('CUS000000003','Meera','Iyer','meera.iyer@example.com','9000000003',
  '1997-06-21','8 Viman Nagar','Pune','411014','ABCPI9012C','PENDING',TRUE);

INSERT IGNORE INTO users (username, email, password_hash, customer_id, enabled) VALUES
 ('admin','admin@bank.local','$2a$12$GOS/X7wjoLC8O9PNIf0F7.J9mQQ/U2fEBrWQahcAtxC2yFk5F7oRi',NULL,TRUE),
 ('teller','teller@bank.local','$2a$12$GOS/X7wjoLC8O9PNIf0F7.J9mQQ/U2fEBrWQahcAtxC2yFk5F7oRi',NULL,TRUE),
 ('priya','priya.nair@example.com','$2a$12$GOS/X7wjoLC8O9PNIf0F7.J9mQQ/U2fEBrWQahcAtxC2yFk5F7oRi',
  (SELECT customer_id FROM customers WHERE customer_number='CUS000000001'),TRUE),
 ('arjun','arjun.rao@example.com','$2a$12$GOS/X7wjoLC8O9PNIf0F7.J9mQQ/U2fEBrWQahcAtxC2yFk5F7oRi',
  (SELECT customer_id FROM customers WHERE customer_number='CUS000000002'),TRUE),
 ('meera','meera.iyer@example.com','$2a$12$GOS/X7wjoLC8O9PNIf0F7.J9mQQ/U2fEBrWQahcAtxC2yFk5F7oRi',
  (SELECT customer_id FROM customers WHERE customer_number='CUS000000003'),TRUE);

INSERT IGNORE INTO user_roles (user_id, role_id) VALUES
 ((SELECT user_id FROM users WHERE username='admin'),
  (SELECT role_id FROM roles WHERE name='ADMIN')),
 ((SELECT user_id FROM users WHERE username='teller'),
  (SELECT role_id FROM roles WHERE name='BANK_EMPLOYEE')),
 ((SELECT user_id FROM users WHERE username='priya'),
  (SELECT role_id FROM roles WHERE name='CUSTOMER')),
 ((SELECT user_id FROM users WHERE username='arjun'),
  (SELECT role_id FROM roles WHERE name='CUSTOMER')),
 ((SELECT user_id FROM users WHERE username='meera'),
  (SELECT role_id FROM roles WHERE name='CUSTOMER'));

INSERT IGNORE INTO accounts (account_number, customer_id, account_type, status,
                             balance, minimum_balance, interest_rate,
                             daily_transfer_limit, opened_on)
VALUES
 ('000001000017',(SELECT customer_id FROM customers WHERE customer_number='CUS000000001'),
  'SAVINGS','ACTIVE',25000.0000,1000.0000,3.50,100000.0000,CURDATE() - INTERVAL 200 DAY),
 ('000001000025',(SELECT customer_id FROM customers WHERE customer_number='CUS000000001'),
  'CURRENT','ACTIVE',80000.0000,5000.0000,0.00,500000.0000,CURDATE() - INTERVAL 150 DAY),
 ('000001000033',(SELECT customer_id FROM customers WHERE customer_number='CUS000000002'),
  'SAVINGS','ACTIVE',12000.0000,1000.0000,3.50,100000.0000,CURDATE() - INTERVAL 90 DAY),
 ('000001000041',(SELECT customer_id FROM customers WHERE customer_number='CUS000000002'),
  'SALARY','ACTIVE',45000.0000,0.0000,3.00,NULL,CURDATE() - INTERVAL 60 DAY),
 -- Frozen on purpose, so the refusal paths have something to act on.
 ('000001000058',(SELECT customer_id FROM customers WHERE customer_number='CUS000000002'),
  'SAVINGS','FROZEN',5000.0000,1000.0000,3.50,NULL,CURDATE() - INTERVAL 300 DAY);

-- Opening deposits as real ledger rows. Every seeded balance above is the signed
-- sum of that account's own transactions, which is exactly what the
-- reconciliation query checks: seed data that got this wrong would make the
-- system report itself broken on the first request.
INSERT INTO transactions (transaction_reference, account_id, transaction_type, direction,
                          amount, balance_after, description, transacted_at, value_date,
                          performed_by)
SELECT 'TXNSEED0000000000001',(SELECT account_id FROM accounts WHERE account_number='000001000017'),
       'DEPOSIT','CREDIT',25000.0000,25000.0000,'Opening deposit',
       NOW() - INTERVAL 200 DAY, CURDATE() - INTERVAL 200 DAY,'teller'
WHERE NOT EXISTS (SELECT 1 FROM transactions WHERE transaction_reference='TXNSEED0000000000001');
INSERT INTO transactions (transaction_reference, account_id, transaction_type, direction,
                          amount, balance_after, description, transacted_at, value_date,
                          performed_by)
SELECT 'TXNSEED0000000000002',(SELECT account_id FROM accounts WHERE account_number='000001000025'),
       'DEPOSIT','CREDIT',80000.0000,80000.0000,'Opening deposit',
       NOW() - INTERVAL 150 DAY, CURDATE() - INTERVAL 150 DAY,'teller'
WHERE NOT EXISTS (SELECT 1 FROM transactions WHERE transaction_reference='TXNSEED0000000000002');
INSERT INTO transactions (transaction_reference, account_id, transaction_type, direction,
                          amount, balance_after, description, transacted_at, value_date,
                          performed_by)
SELECT 'TXNSEED0000000000003',(SELECT account_id FROM accounts WHERE account_number='000001000033'),
       'DEPOSIT','CREDIT',12000.0000,12000.0000,'Opening deposit',
       NOW() - INTERVAL 90 DAY, CURDATE() - INTERVAL 90 DAY,'teller'
WHERE NOT EXISTS (SELECT 1 FROM transactions WHERE transaction_reference='TXNSEED0000000000003');
INSERT INTO transactions (transaction_reference, account_id, transaction_type, direction,
                          amount, balance_after, description, transacted_at, value_date,
                          performed_by)
SELECT 'TXNSEED0000000000004',(SELECT account_id FROM accounts WHERE account_number='000001000041'),
       'DEPOSIT','CREDIT',45000.0000,45000.0000,'Opening deposit',
       NOW() - INTERVAL 60 DAY, CURDATE() - INTERVAL 60 DAY,'teller'
WHERE NOT EXISTS (SELECT 1 FROM transactions WHERE transaction_reference='TXNSEED0000000000004');
INSERT INTO transactions (transaction_reference, account_id, transaction_type, direction,
                          amount, balance_after, description, transacted_at, value_date,
                          performed_by)
SELECT 'TXNSEED0000000000005',(SELECT account_id FROM accounts WHERE account_number='000001000058'),
       'DEPOSIT','CREDIT',5000.0000,5000.0000,'Opening deposit',
       NOW() - INTERVAL 300 DAY, CURDATE() - INTERVAL 300 DAY,'teller'
WHERE NOT EXISTS (SELECT 1 FROM transactions WHERE transaction_reference='TXNSEED0000000000005');

-- A payee verified long enough ago to be outside the cooling-off window, so the
-- happy path works immediately.
INSERT INTO beneficiaries (customer_id, beneficiary_account_number, beneficiary_name,
                           nickname, bank_name, verified, verified_at, verified_by, active)
SELECT (SELECT customer_id FROM customers WHERE customer_number='CUS000000001'),
       '000001000033','Arjun Rao','Arjun','Example Bank',TRUE,
       NOW() - INTERVAL 30 DAY,'priya',TRUE
WHERE NOT EXISTS (SELECT 1 FROM beneficiaries
                  WHERE customer_id=(SELECT customer_id FROM customers
                                     WHERE customer_number='CUS000000001')
                    AND beneficiary_account_number='000001000033');

-- An unverified payee, so the refusal path has something to act on.
INSERT INTO beneficiaries (customer_id, beneficiary_account_number, beneficiary_name,
                           nickname, bank_name, verified, active)
SELECT (SELECT customer_id FROM customers WHERE customer_number='CUS000000002'),
       '000001000017','Priya Nair','Priya','Example Bank',FALSE,TRUE
WHERE NOT EXISTS (SELECT 1 FROM beneficiaries
                  WHERE customer_id=(SELECT customer_id FROM customers
                                     WHERE customer_number='CUS000000002')
                    AND beneficiary_account_number='000001000017');
