-- Company: Department, Employee, Project, Works_On (joins, GROUP BY, subqueries, self join)
CREATE TABLE Department (
    dept_id INTEGER PRIMARY KEY,
    dept_name TEXT NOT NULL UNIQUE,
    location TEXT
);

CREATE TABLE Employee (
    emp_id INTEGER PRIMARY KEY,
    emp_name TEXT NOT NULL,
    salary REAL NOT NULL CHECK (salary > 0),
    hire_date TEXT NOT NULL,
    dept_id INTEGER,
    manager_id INTEGER,
    FOREIGN KEY (dept_id) REFERENCES Department(dept_id),
    FOREIGN KEY (manager_id) REFERENCES Employee(emp_id)
);

CREATE TABLE Project (
    project_id INTEGER PRIMARY KEY,
    title TEXT NOT NULL,
    budget REAL,
    dept_id INTEGER REFERENCES Department(dept_id)
);

CREATE TABLE Works_On (
    emp_id INTEGER NOT NULL REFERENCES Employee(emp_id),
    project_id INTEGER NOT NULL REFERENCES Project(project_id),
    hours INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (emp_id, project_id)
);

INSERT INTO Department VALUES
    (1, 'Engineering', 'Bengaluru'),
    (2, 'Sales', 'Mumbai'),
    (3, 'HR', 'Delhi'),
    (4, 'Research', 'Pune');

INSERT INTO Employee (emp_id, emp_name, salary, hire_date, dept_id, manager_id) VALUES
    (1, 'Anita Rao', 150000, '2015-04-01', 1, NULL),
    (2, 'Vikram Shah', 98000, '2018-07-15', 1, 1),
    (3, 'Pooja Nair', 87000, '2019-01-10', 1, 1),
    (4, 'Sanjay Gupta', 120000, '2016-09-20', 2, NULL),
    (5, 'Divya Menon', 64000, '2021-03-05', 2, 4),
    (6, 'Arjun Das', 59000, '2022-11-12', 2, 4),
    (7, 'Kavita Joshi', 72000, '2017-06-30', 3, NULL),
    (8, 'Manish Iyer', 91000, '2020-02-18', 1, 2),
    (9, 'Sneha Pillai', 76000, '2023-08-01', NULL, 7);

INSERT INTO Project VALUES
    (101, 'Mobile App', 500000, 1),
    (102, 'CRM Upgrade', 300000, 2),
    (103, 'Hiring Portal', 150000, 3),
    (104, 'AI Research', 800000, 4);

INSERT INTO Works_On VALUES
    (1, 101, 120), (2, 101, 200), (3, 101, 150), (8, 101, 90),
    (4, 102, 80), (5, 102, 160), (6, 102, 140),
    (7, 103, 60), (9, 103, 100),
    (2, 104, 40), (8, 104, 75);
