-- Seed data. Idempotent: runs after schema.sql at every start-up.
--
-- All logins share the password: Password@123
-- The hash was verified against the bcrypt library, not written by hand.

INSERT INTO number_sequence (sequence_name, next_value) VALUES ('INCIDENT', 1)
ON CONFLICT (sequence_name) DO NOTHING;

-- No ROLE_ prefix. The clearance is on the role, because retrieval is the least
-- obvious place a restricted runbook leaks.
INSERT INTO roles (name, description, max_classification) VALUES
 ('ADMIN',    'Full access including document deletion', 'RESTRICTED'),
 ('LEAD',     'Incident command, document upload, audit', 'RESTRICTED'),
 ('ENGINEER', 'Incidents, search, assistant',            'INTERNAL'),
 ('VIEWER',   'Read-only dashboards',                    'PUBLIC')
ON CONFLICT (name) DO NOTHING;

INSERT INTO users (username, email, display_name, password_hash, team, enabled) VALUES
 ('admin','admin@supportai.local','Admin',
  '$2a$12$GOS/X7wjoLC8O9PNIf0F7.J9mQQ/U2fEBrWQahcAtxC2yFk5F7oRi','platform',TRUE),
 ('lead','lead@supportai.local','Priya Nair',
  '$2a$12$GOS/X7wjoLC8O9PNIf0F7.J9mQQ/U2fEBrWQahcAtxC2yFk5F7oRi','payments',TRUE),
 ('arjun','arjun@supportai.local','Arjun Rao',
  '$2a$12$GOS/X7wjoLC8O9PNIf0F7.J9mQQ/U2fEBrWQahcAtxC2yFk5F7oRi','payments',TRUE),
 ('viewer','viewer@supportai.local','Read Only',
  '$2a$12$GOS/X7wjoLC8O9PNIf0F7.J9mQQ/U2fEBrWQahcAtxC2yFk5F7oRi','exec',TRUE)
ON CONFLICT (username) DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.user_id, r.role_id FROM users u, roles r
WHERE (u.username, r.name) IN
      (('admin','ADMIN'),('lead','LEAD'),('arjun','ENGINEER'),('viewer','VIEWER'))
ON CONFLICT DO NOTHING;

-- Two incidents with IDENTICAL SYMPTOMS and DIFFERENT ROOT CAUSES, seeded
-- deliberately. This is what makes the precedent feature's caution concrete:
-- text similarity will rank both highly for a new "pool exhausted" incident, and
-- applying the wrong one's fix is worse than doing nothing.
INSERT INTO incidents (incident_number, title, description, severity, status, service,
                       customer_impacting, detected_at, acknowledged_at, mitigated_at,
                       resolved_at, root_cause, resolution_summary, reported_by)
VALUES
 ('INC-000001','Payments API latency spike, pool exhausted',
  'p99 on POST /payments rose above 8s. HikariPool-1 reported 50/50 active with 180 pending. '
  || 'Downstream card processor p99 measured at 28s during the window.',
  'SEV1','CLOSED','payments',TRUE,
  NOW() - INTERVAL '45 days', NOW() - INTERVAL '45 days' + INTERVAL '6 minutes',
  NOW() - INTERVAL '45 days' + INTERVAL '38 minutes',
  NOW() - INTERVAL '45 days' + INTERVAL '150 minutes',
  'The HTTP client had no read timeout configured, so a slow downstream held pool '
  || 'connections open indefinitely until the pool was exhausted.',
  'Set a 5s read timeout on the card processor client and added a circuit breaker.','lead'),
 ('INC-000002','Payments API latency spike, pool exhausted',
  'p99 on POST /payments rose above 6s. HikariPool-1 reported 50/50 active with 210 pending. '
  || 'Downstream latency was normal throughout.',
  'SEV1','CLOSED','payments',TRUE,
  NOW() - INTERVAL '12 days', NOW() - INTERVAL '12 days' + INTERVAL '4 minutes',
  NOW() - INTERVAL '12 days' + INTERVAL '25 minutes',
  NOW() - INTERVAL '12 days' + INTERVAL '90 minutes',
  'A reporting query introduced in release 4.19 held a transaction open across an '
  || 'external HTTP call, leaking one connection per report run.',
  'Reverted the reporting change and moved the HTTP call outside the transaction.','lead')
ON CONFLICT (incident_number) DO NOTHING;

UPDATE number_sequence SET next_value = 3
WHERE sequence_name = 'INCIDENT' AND next_value < 3;

-- A timeline with enough evidence to clear the RCA floor, plus one ASSISTANT
-- entry so the exclusion is demonstrable on a fresh database.
INSERT INTO timeline_entries (incident_id, occurred_at, entry_type, source, content, author)
SELECT i.incident_id, v.occurred_at, v.entry_type, v.source, v.content, v.author
FROM incidents i, (VALUES
    (NOW() - INTERVAL '45 days' + INTERVAL '2 minutes','OBSERVATION','SYSTEM',
     'Alert: payments p99 above threshold (8.2s)','alertmanager'),
    (NOW() - INTERVAL '45 days' + INTERVAL '9 minutes','OBSERVATION','HUMAN',
     'HikariPool-1 saturated at 50/50 active, 180 pending','lead'),
    (NOW() - INTERVAL '45 days' + INTERVAL '14 minutes','OBSERVATION','HUMAN',
     'Card processor p99 measured at 28s over the last 10 minutes','arjun'),
    (NOW() - INTERVAL '45 days' + INTERVAL '21 minutes','OBSERVATION','HUMAN',
     'No read timeout configured on the card processor HTTP client','arjun'),
    (NOW() - INTERVAL '45 days' + INTERVAL '30 minutes','ACTION','HUMAN',
     'Applied a 5s read timeout and restarted the payments deployment','arjun'),
    -- Assistant-authored. Excluded from evidence: an analysis grounded on its own
    -- earlier output compounds until nothing traces back to what happened.
    (NOW() - INTERVAL '45 days' + INTERVAL '35 minutes','ANALYSIS','ASSISTANT',
     'RCA draft generated (MEDIUM, 1 correction)','assistant')
) AS v(occurred_at, entry_type, source, content, author)
WHERE i.incident_number = 'INC-000001'
  AND NOT EXISTS (SELECT 1 FROM timeline_entries t WHERE t.incident_id = i.incident_id);
