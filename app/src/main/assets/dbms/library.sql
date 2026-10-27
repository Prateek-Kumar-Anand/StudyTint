-- Library: Author, Book, Member, Loan (many-to-many, dates, aggregates, triggers)
CREATE TABLE Author (
    author_id INTEGER PRIMARY KEY,
    author_name TEXT NOT NULL,
    country TEXT
);

CREATE TABLE Book (
    book_id INTEGER PRIMARY KEY,
    title TEXT NOT NULL,
    author_id INTEGER NOT NULL REFERENCES Author(author_id),
    genre TEXT,
    price REAL CHECK (price >= 0),
    copies INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE Member (
    member_id INTEGER PRIMARY KEY,
    member_name TEXT NOT NULL,
    joined TEXT NOT NULL
);

CREATE TABLE Loan (
    loan_id INTEGER PRIMARY KEY AUTOINCREMENT,
    book_id INTEGER NOT NULL REFERENCES Book(book_id),
    member_id INTEGER NOT NULL REFERENCES Member(member_id),
    issued TEXT NOT NULL,
    returned TEXT
);

INSERT INTO Author VALUES
    (1, 'R. K. Narayan', 'India'),
    (2, 'Jane Austen', 'England'),
    (3, 'Ruskin Bond', 'India'),
    (4, 'George Orwell', 'England'),
    (5, 'Chimamanda Adichie', 'Nigeria');

INSERT INTO Book VALUES
    (1, 'Malgudi Days', 1, 'Fiction', 250, 3),
    (2, 'The Guide', 1, 'Fiction', 320, 2),
    (3, 'Pride and Prejudice', 2, 'Classic', 280, 4),
    (4, 'Emma', 2, 'Classic', 260, 1),
    (5, 'The Blue Umbrella', 3, 'Children', 180, 5),
    (6, 'Nineteen Eighty-Four', 4, 'Dystopian', 350, 3),
    (7, 'Animal Farm', 4, 'Dystopian', 210, 2);

INSERT INTO Member VALUES
    (1, 'Aditi', '2024-01-15'),
    (2, 'Bharat', '2024-02-20'),
    (3, 'Charu', '2024-03-05'),
    (4, 'Dev', '2024-05-11');

INSERT INTO Loan (book_id, member_id, issued, returned) VALUES
    (1, 1, '2025-01-05', '2025-01-19'),
    (3, 1, '2025-02-01', '2025-02-15'),
    (6, 2, '2025-02-10', NULL),
    (5, 3, '2025-03-01', '2025-03-08'),
    (6, 3, '2025-03-12', NULL),
    (2, 1, '2025-03-20', NULL),
    (7, 2, '2025-04-02', '2025-04-16');

-- A trigger: a loan can only be issued while copies are left
CREATE TRIGGER check_copies BEFORE INSERT ON Loan
WHEN (SELECT copies FROM Book WHERE book_id = NEW.book_id) <= 0
BEGIN
    SELECT RAISE(ABORT, 'No copies left of this book');
END;
