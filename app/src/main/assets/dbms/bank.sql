-- Bank: Account, Txn (practise BEGIN / COMMIT / ROLLBACK, triggers and constraints)
CREATE TABLE Account (
    account_id INTEGER PRIMARY KEY,
    holder TEXT NOT NULL,
    branch TEXT NOT NULL,
    balance REAL NOT NULL DEFAULT 0 CHECK (balance >= 0)
);

CREATE TABLE Txn (
    txn_id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NOT NULL REFERENCES Account(account_id),
    kind TEXT NOT NULL CHECK (kind IN ('DEPOSIT', 'WITHDRAW', 'TRANSFER_IN', 'TRANSFER_OUT')),
    amount REAL NOT NULL CHECK (amount > 0),
    made_on TEXT NOT NULL DEFAULT (date('now'))
);

-- Every new Txn row updates the account balance automatically
CREATE TRIGGER apply_txn AFTER INSERT ON Txn
BEGIN
    UPDATE Account
    SET balance = balance + CASE WHEN NEW.kind IN ('DEPOSIT', 'TRANSFER_IN') THEN NEW.amount ELSE -NEW.amount END
    WHERE account_id = NEW.account_id;
END;

INSERT INTO Account (account_id, holder, branch) VALUES
    (1, 'Aarav Sharma', 'Delhi'),
    (2, 'Meera Iyer', 'Chennai'),
    (3, 'Kabir Khan', 'Mumbai'),
    (4, 'Riya Patel', 'Ahmedabad');

INSERT INTO Txn (account_id, kind, amount, made_on) VALUES
    (1, 'DEPOSIT', 5000, '2025-01-03'),
    (2, 'DEPOSIT', 8500, '2025-01-04'),
    (3, 'DEPOSIT', 30250, '2025-01-04'),
    (1, 'WITHDRAW', 2000, '2025-01-10'),
    (4, 'DEPOSIT', 450, '2025-01-12');

-- Try:  BEGIN;  UPDATE Account SET balance = balance - 500 WHERE account_id = 1;  ROLLBACK;
