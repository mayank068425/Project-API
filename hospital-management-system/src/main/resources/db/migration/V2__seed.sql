-- Reference data and a small sample.

INSERT INTO department (department_name, description, floor_number) VALUES
 ('Cardiology',  'Heart and cardiovascular system', 3),
 ('Neurology',   'Brain, spine and nervous system', 4),
 ('Orthopedics', 'Bones, joints and musculoskeletal', 2),
 ('Pediatrics',  'Infants, children and adolescents', 1),
 ('Dermatology', 'Skin, hair and nails', 5);

INSERT INTO doctor (doctor_name, specialization, experience_years, qualification,
                    mobile, email, consultation_fee, department_id)
VALUES
 ('Dr Anita Rao', 'Interventional Cardiology', 14, 'MBBS, MD, DM (Cardiology)',
  '9876543210', 'anita.rao@hospital.local', 1200.00,
  (SELECT department_id FROM department WHERE department_name='Cardiology')),
 ('Dr Vikram Nair', 'Electrophysiology', 9, 'MBBS, MD, DNB',
  '9876543211', 'vikram.nair@hospital.local', 1000.00,
  (SELECT department_id FROM department WHERE department_name='Cardiology')),
 ('Dr Sunita Menon', 'Neurology', 18, 'MBBS, MD, DM (Neurology)',
  '9876543212', 'sunita.menon@hospital.local', 1500.00,
  (SELECT department_id FROM department WHERE department_name='Neurology')),
 ('Dr Rahul Desai', 'Joint Replacement', 11, 'MBBS, MS (Ortho)',
  '9876543213', 'rahul.desai@hospital.local', 900.00,
  (SELECT department_id FROM department WHERE department_name='Orthopedics')),
 ('Dr Kavita Shah', 'Pediatrics', 7, 'MBBS, MD (Pediatrics)',
  '9876543214', 'kavita.shah@hospital.local', 700.00,
  (SELECT department_id FROM department WHERE department_name='Pediatrics'));

-- Dates of birth, not ages. Each patient's department is set from their doctor,
-- which is the rule the service enforces and the /inconsistent endpoint checks.
INSERT INTO patient (first_name, last_name, date_of_birth, gender, blood_group,
                     phone, email, address, disease, doctor_id, department_id)
VALUES
 ('Priya','Sharma', '1991-03-14','FEMALE','O_POSITIVE','9812345670',
  'priya.sharma@example.com','Kothrud, Pune','Hypertension',
  (SELECT doctor_id FROM doctor WHERE email='anita.rao@hospital.local'),
  (SELECT department_id FROM department WHERE department_name='Cardiology')),
 ('Arjun','Patel',  '1978-11-02','MALE','B_POSITIVE','9812345671',
  'arjun.patel@example.com','Baner, Pune','Arrhythmia',
  (SELECT doctor_id FROM doctor WHERE email='vikram.nair@hospital.local'),
  (SELECT department_id FROM department WHERE department_name='Cardiology')),
 ('Meera','Iyer',   '2015-06-21','FEMALE','A_NEGATIVE','9812345672',
  NULL,'Viman Nagar, Pune','Asthma',
  (SELECT doctor_id FROM doctor WHERE email='kavita.shah@hospital.local'),
  (SELECT department_id FROM department WHERE department_name='Pediatrics')),
 ('Sameer','Kulkarni','1965-01-30','MALE','AB_POSITIVE','9812345673',
  'sameer.k@example.com','Aundh, Pune','Migraine',
  (SELECT doctor_id FROM doctor WHERE email='sunita.menon@hospital.local'),
  (SELECT department_id FROM department WHERE department_name='Neurology')),
 -- Admitted to a department with no doctor assigned yet. This is the case the
 -- nullable doctor_id exists for.
 ('Kiran','Joshi',  '1988-09-09','OTHER','O_NEGATIVE','9812345674',
  NULL,'Hadapsar, Pune','Knee injury', NULL,
  (SELECT department_id FROM department WHERE department_name='Orthopedics'));
