-- =============================================================================
--  EMS Seed Data — runs automatically on application startup
--  Spring Boot picks this up from src/main/resources/data.sql
--
--  NOTE: Uses INSERT IGNORE so re-runs don't fail on duplicate emails.
-- =============================================================================

INSERT IGNORE INTO employees (first_name, last_name, email, department, designation, salary, date_of_joining) VALUES
('Ayush',   'Sharma',    'ayush.sharma@company.com',    'Engineering', 'Senior Software Engineer',  125000.00, '2022-01-15'),
('Priya',   'Kapoor',    'priya.kapoor@company.com',    'Product',     'Product Manager',           110000.00, '2023-03-10'),
('Rahul',   'Verma',     'rahul.verma@company.com',     'Engineering', 'Junior Developer',           65000.00, '2024-06-01'),
('Sneha',   'Mehta',     'sneha.mehta@company.com',     'HR',          'HR Business Partner',        72000.00, '2021-09-20'),
('Arjun',   'Nair',      'arjun.nair@company.com',      'Engineering', 'Tech Lead',                 145000.00, '2020-04-05'),
('Divya',   'Iyer',      'divya.iyer@company.com',      'Finance',     'Financial Analyst',          85000.00, '2022-11-12'),
('Kiran',   'Patel',     'kiran.patel@company.com',     'Product',     'UX Designer',                90000.00, '2023-07-18'),
('Rohit',   'Joshi',     'rohit.joshi@company.com',     'Engineering', 'DevOps Engineer',           105000.00, '2021-02-28'),
('Ananya',  'Reddy',     'ananya.reddy@company.com',    'HR',          'Recruiter',                  60000.00, '2024-01-03'),
('Vikram',  'Singh',     'vikram.singh@company.com',    'Finance',     'Finance Manager',           130000.00, '2019-08-15'),
('Meera',   'Bose',      'meera.bose@company.com',      'Engineering', 'QA Engineer',                78000.00, '2022-05-22'),
('Suresh',  'Kumar',     'suresh.kumar@company.com',    'Product',     'Product Owner',             115000.00, '2020-10-30');
