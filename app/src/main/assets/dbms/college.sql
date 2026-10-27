-- College: Student, Course, Enrolment (the schema used in your DBMS notes)
CREATE TABLE Student (
    student_id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    age INTEGER CHECK (age >= 0),
    department TEXT NOT NULL,
    email TEXT UNIQUE
);

CREATE TABLE Course (
    course_id INTEGER PRIMARY KEY,
    course_name TEXT NOT NULL UNIQUE,
    credits INTEGER NOT NULL CHECK (credits > 0)
);

CREATE TABLE Enrolment (
    student_id INTEGER NOT NULL,
    course_id INTEGER NOT NULL,
    status TEXT NOT NULL DEFAULT 'Active',
    marks INTEGER,
    PRIMARY KEY (student_id, course_id),
    FOREIGN KEY (student_id) REFERENCES Student(student_id),
    FOREIGN KEY (course_id) REFERENCES Course(course_id)
);

INSERT INTO Student (student_id, name, age, department, email) VALUES
    (1, 'Aarav', 19, 'BCA', 'aarav@example.test'),
    (2, 'Meera', 18, 'BCA', 'meera@example.test'),
    (3, 'Kabir', 20, 'BBA', 'kabir@example.test'),
    (4, 'Riya', 19, 'BCA', 'riya@example.test'),
    (5, 'Neha', 21, 'BBA', 'neha@example.test'),
    (6, 'Rohan', 20, 'BSc', 'rohan@example.test'),
    (7, 'Isha', 18, 'BSc', NULL);

INSERT INTO Course (course_id, course_name, credits) VALUES
    (10, 'Database Systems', 4),
    (20, 'Web Development', 3),
    (30, 'Python Programming', 4),
    (40, 'Discrete Mathematics', 3),
    (50, 'Data Structures', 4);

INSERT INTO Enrolment (student_id, course_id, status, marks) VALUES
    (1, 10, 'Active', 82),
    (1, 20, 'Active', 74),
    (2, 10, 'Active', 91),
    (2, 30, 'Active', 88),
    (3, 20, 'Dropped', NULL),
    (3, 40, 'Active', 67),
    (4, 10, 'Active', 79),
    (4, 30, 'Completed', 93),
    (5, 40, 'Active', 58),
    (6, 30, 'Active', 71);

CREATE VIEW ActiveEnrolments AS
SELECT S.name, C.course_name, E.status
FROM Enrolment AS E
JOIN Student AS S ON E.student_id = S.student_id
JOIN Course AS C ON E.course_id = C.course_id
WHERE E.status = 'Active';
