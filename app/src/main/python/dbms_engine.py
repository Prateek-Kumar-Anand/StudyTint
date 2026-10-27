"""SQL engine for the DBMS Lab: real SQLite (through Python's sqlite3) with student-friendly results.

Every public function returns a JSON string so the Kotlin side only has to parse text.
"""
import json
import re
import sqlite3
import threading
import time

MAX_CELL = 300          # characters kept per cell
_conns = {}             # database path -> connection (kept open so BEGIN ... COMMIT can span several runs)
_lock = threading.RLock()
_current = {"conn": None}


# ----------------------------------------------------------------------------- connections

def _connect(path):
    conn = _conns.get(path)
    if conn is None:
        conn = sqlite3.connect(path, isolation_level=None, check_same_thread=False, timeout=5)
        _harden(conn)
        conn.execute("PRAGMA foreign_keys = ON")
        _conns[path] = conn
    return conn


def _harden(conn):
    """A database file can come from anywhere (an imported .db, a pasted script), so the connection is locked down:
    no ATTACH (it could create files anywhere in the app's storage), no extensions, no raw schema edits, no unsafe
    functions inside views and triggers, and limits on how big one value or statement may be."""
    try:
        conn.enable_load_extension(False)
    except Exception:
        pass
    for name, value in (("SQLITE_LIMIT_ATTACHED", 0), ("SQLITE_LIMIT_LENGTH", 100_000_000),
                        ("SQLITE_LIMIT_SQL_LENGTH", 5_000_000), ("SQLITE_LIMIT_VARIABLE_NUMBER", 999)):
        try:
            conn.setlimit(getattr(sqlite3, name), value)
        except Exception:
            pass
    try:
        conn.setconfig(sqlite3.SQLITE_DBCONFIG_DEFENSIVE, True)
        conn.setconfig(sqlite3.SQLITE_DBCONFIG_TRUSTED_SCHEMA, False)
    except Exception:
        pass
    try:
        conn.set_authorizer(_authorizer)
    except Exception:
        pass


_DENIED = {getattr(sqlite3, n) for n in ("SQLITE_ATTACH", "SQLITE_DETACH") if hasattr(sqlite3, n)}


def _authorizer(action, arg1, arg2, db_name, source):
    if action in _DENIED:
        return sqlite3.SQLITE_DENY
    return sqlite3.SQLITE_OK


_RE_VACUUM_INTO = re.compile(r"^\s*VACUUM\b.*\bINTO\b", re.I | re.S)


def close_db(path):
    """Closes a database (an open transaction is rolled back)."""
    with _lock:
        conn = _conns.pop(path, None)
        if conn is not None:
            try:
                conn.close()
            except Exception:
                pass
    return "ok"


def version():
    return sqlite3.sqlite_version


def stop():
    """Interrupts the statement that is running right now."""
    conn = _current.get("conn")
    if conn is not None:
        try:
            conn.interrupt()
        except Exception:
            pass
    return "ok"


# ----------------------------------------------------------------------------- splitting a script into statements

def _skip_ws_comments(s, i):
    n = len(s)
    while i < n:
        if s[i].isspace():
            i += 1
        elif s.startswith("--", i):
            j = s.find("\n", i)
            i = n if j < 0 else j + 1
        elif s.startswith("/*", i):
            j = s.find("*/", i + 2)
            i = n if j < 0 else j + 2
        else:
            break
    return i


def split_statements(text):
    """Splits a script into [(statement, line_number)]. Semicolons inside strings, comments and CREATE TRIGGER
    bodies do not end a statement. Lines such as .tables / .schema (sqlite shell style) are statements too."""
    out = []
    n = len(text)
    pos = 0
    while True:
        start = _skip_ws_comments(text, pos)
        if start >= n:
            break
        line = text.count("\n", 0, start) + 1
        if text[start] == "." and start + 1 < n and text[start + 1].isalpha():
            end = text.find("\n", start)
            end = n if end < 0 else end
            out.append((text[start:end].strip(), line))
            pos = end
            continue
        semi = text.find(";", start)
        stmt_end = None
        while semi >= 0:
            if sqlite3.complete_statement(text[start:semi + 1]):
                stmt_end = semi + 1
                break
            semi = text.find(";", semi + 1)
        if stmt_end is None:
            stmt_end = n
        out.append((text[start:stmt_end].strip(), line))
        pos = stmt_end
    return out


# ----------------------------------------------------------------------------- helpers

def _q(name):
    return '"' + str(name).replace('"', '""') + '"'


def _cell(v):
    if v is None:
        return None
    if isinstance(v, bytes):
        s = "x'" + v[:64].hex().upper() + ("..." if len(v) > 64 else "") + "'"
        return s
    if isinstance(v, float):
        if v != v:
            return "NaN"
        if v in (float("inf"), float("-inf")):
            return "Inf" if v > 0 else "-Inf"
        return repr(v)
    s = str(v)
    if len(s) > MAX_CELL:
        s = s[:MAX_CELL] + "…"
    return s


def _words(stmt):
    """First words of a statement, upper-cased, comments skipped."""
    i = _skip_ws_comments(stmt, 0)
    return re.findall(r"[A-Za-z_]+", stmt[i:i + 80].upper())


_OK_MESSAGES = {
    ("CREATE", "TABLE"): "Table created",
    ("CREATE", "TEMP"): "Table created",
    ("CREATE", "TEMPORARY"): "Table created",
    ("CREATE", "VIEW"): "View created",
    ("CREATE", "INDEX"): "Index created",
    ("CREATE", "UNIQUE"): "Index created",
    ("CREATE", "TRIGGER"): "Trigger created",
    ("CREATE", "VIRTUAL"): "Virtual table created",
    ("DROP", "TABLE"): "Table dropped",
    ("DROP", "VIEW"): "View dropped",
    ("DROP", "INDEX"): "Index dropped",
    ("DROP", "TRIGGER"): "Trigger dropped",
    ("ALTER", "TABLE"): "Table altered",
    ("BEGIN", None): "Transaction started",
    ("COMMIT", None): "Transaction committed",
    ("END", None): "Transaction committed",
    ("ROLLBACK", None): "Transaction rolled back",
    ("SAVEPOINT", None): "Savepoint set",
    ("RELEASE", None): "Savepoint released",
    ("VACUUM", None): "Database compacted",
    ("ANALYZE", None): "Statistics updated",
    ("PRAGMA", None): "OK",
}


def _ok_message(words, rowcount):
    kind = words[0] if words else ""
    if kind in ("INSERT", "REPLACE"):
        n = max(rowcount, 0)
        return "%d row%s inserted" % (n, "" if n == 1 else "s")
    if kind == "UPDATE":
        n = max(rowcount, 0)
        return "%d row%s updated" % (n, "" if n == 1 else "s")
    if kind == "DELETE":
        n = max(rowcount, 0)
        return "%d row%s deleted" % (n, "" if n == 1 else "s")
    key = (kind, words[1] if len(words) > 1 else None)
    if key in _OK_MESSAGES:
        return _OK_MESSAGES[key]
    if (kind, None) in _OK_MESSAGES:
        return _OK_MESSAGES[(kind, None)]
    return "OK"


# ----------------------------------------------------------------------------- error hints (SQLite vs MySQL / Oracle habits)

def _hint(stmt, err):
    msg = str(err)
    low = msg.lower()
    words = _words(stmt)
    first = words[0] if words else ""
    if first in ("USE",) or words[:2] in (["CREATE", "DATABASE"], ["DROP", "DATABASE"], ["CREATE", "SCHEMA"]):
        return "SQLite keeps one database per file. Use the database menu at the top to create, switch or delete databases."
    if first in ("GRANT", "REVOKE", "DELIMITER", "CALL", "EXEC", "EXECUTE") or words[:2] in (
            ["CREATE", "PROCEDURE"], ["CREATE", "FUNCTION"], ["CREATE", "USER"], ["DROP", "PROCEDURE"]):
        return "SQLite has no stored procedures, users or GRANT/REVOKE. Views and triggers are supported."
    if "auto_increment" in low:
        return "In SQLite write  id INTEGER PRIMARY KEY AUTOINCREMENT  (a plain INTEGER PRIMARY KEY already auto-numbers)."
    if 'near "top"' in low or re.match(r"\s*SELECT\s+(DISTINCT\s+)?TOP\s+\d+", stmt, re.I):
        return "SQLite has no TOP n. Put  LIMIT n  at the end of the query."
    if "no such function" in low:
        fn = low.rsplit(":", 1)[-1].strip()
        table = {
            "now": "Use datetime('now') (or date('now')).",
            "curdate": "Use date('now').",
            "getdate": "Use datetime('now').",
            "sysdate": "Use datetime('now').",
            "concat": "Join text with ||, e.g.  first_name || ' ' || last_name.",
            "len": "Use LENGTH(x).",
            "isnull": "Use IFNULL(x, y) or COALESCE(x, y).",
            "nvl": "Use IFNULL(x, y) or COALESCE(x, y).",
            "if": "Use CASE WHEN ... THEN ... ELSE ... END (or IIF(cond, a, b)).",
            "datediff": "Use julianday(a) - julianday(b) for a difference in days.",
            "year": "Use strftime('%Y', date_column).",
            "month": "Use strftime('%m', date_column).",
            "day": "Use strftime('%d', date_column).",
            "to_char": "Use strftime(format, date_column).",
            "substr": None,
        }
        h = table.get(fn)
        if h:
            return h
    if "not authorized" in low:
        return "This command is blocked in the lab for safety (ATTACH and loading extensions are not allowed)."
    if "ambiguous column name" in low:
        return "More than one table has that column. Prefix it with the table or alias, e.g.  S.name."
    if low.startswith("no such column") and '"' in stmt:
        return "Text values go in single quotes ('Aarav'). Double quotes mean a column or table name."
    if "foreign key constraint failed" in low:
        return "The row points to a parent row that does not exist, or you are deleting/changing a parent that still has child rows."
    if "unique constraint failed" in low:
        return "That value already exists in a PRIMARY KEY or UNIQUE column."
    if "not null constraint failed" in low:
        return "A column that must have a value was left empty (NULL)."
    if "check constraint failed" in low:
        return "A value breaks a CHECK rule of the table."
    if "datatype mismatch" in low:
        return "An INTEGER PRIMARY KEY column only accepts whole numbers."
    if "incomplete input" in low:
        return "The statement is not finished. Check for a missing closing bracket, quote or keyword."
    if "syntax error" in low:
        return "Check spelling, commas, brackets and quotes near the word shown in the message."
    return ""


# ----------------------------------------------------------------------------- MySQL / shell style helpers

_RE_SHOW_TABLES = re.compile(r"^\s*SHOW\s+(FULL\s+)?TABLES\s*;?\s*$", re.I)
_RE_DESC = re.compile(r"^\s*(?:DESC|DESCRIBE|SHOW\s+(?:FULL\s+)?(?:COLUMNS|FIELDS)\s+FROM)\s+[`\"\[]?([\w$ ]+?)[`\"\]]?\s*;?\s*$", re.I)
_RE_SHOW_CREATE = re.compile(r"^\s*SHOW\s+CREATE\s+(?:TABLE|VIEW)\s+[`\"\[]?([\w$ ]+?)[`\"\]]?\s*;?\s*$", re.I)
_RE_TRUNCATE = re.compile(r"^\s*TRUNCATE(?:\s+TABLE)?\s+[`\"\[]?([\w$]+)[`\"\]]?\s*;?\s*$", re.I)


def _translate(stmt):
    """Returns (sql, params, note) for statements SQLite does not have but students type often, else None."""
    s = stmt.strip()
    if _RE_SHOW_TABLES.match(s) or s.lower() in (".tables", ".table"):
        return ("SELECT name AS \"Tables\", type AS \"Type\" FROM sqlite_master "
                "WHERE type IN ('table','view') AND name NOT LIKE 'sqlite_%' ORDER BY name", (), "")
    m = _RE_DESC.match(s)
    if m:
        t = m.group(1).strip()
        return ("SELECT name AS \"Field\", type AS \"Type\", CASE WHEN \"notnull\" THEN 'NO' ELSE 'YES' END AS \"Null\", "
                "CASE WHEN pk > 0 THEN 'PRI' ELSE '' END AS \"Key\", dflt_value AS \"Default\" "
                "FROM pragma_table_info(?)", (t,), "table:" + t)
    m = _RE_SHOW_CREATE.match(s)
    if m:
        t = m.group(1).strip()
        return ("SELECT name AS \"Name\", sql AS \"Create statement\" FROM sqlite_master "
                "WHERE type IN ('table','view') AND name = ? COLLATE NOCASE", (t,), "")
    m = _RE_TRUNCATE.match(s)
    if m:
        t = m.group(1)
        return ("DELETE FROM " + _q(t), (), "SQLite has no TRUNCATE, so DELETE FROM was run instead.")
    if s.lower().startswith(".schema"):
        arg = s[7:].strip()
        if arg:
            return ("SELECT sql AS \"Schema\" FROM sqlite_master WHERE tbl_name = ? COLLATE NOCASE AND sql IS NOT NULL "
                    "ORDER BY type = 'table' DESC, name", (arg,), "")
        return ("SELECT sql AS \"Schema\" FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' "
                "ORDER BY type = 'table' DESC, name", (), "")
    return None


# ----------------------------------------------------------------------------- running a script

def _result_error(stmt, line, exc, ms):
    code = getattr(exc, "sqlite_errorname", "") or ""
    return {
        "kind": "error", "sql": stmt, "line": line, "ms": ms,
        "message": str(exc), "code": code, "hint": _hint(stmt, exc),
    }


def run(path, text, timeout_sec=15, max_rows=1000, stop_on_error=True, line_offset=0):
    """Runs every statement in `text`. Result: {"results": [...], "in_tx": bool, "skipped": n, "timed_out": bool}"""
    results = []
    skipped = 0
    timed_out = [False]
    with _lock:
        conn = _connect(path)
        _current["conn"] = conn

        def _timeout():
            timed_out[0] = True
            try:
                conn.interrupt()
            except Exception:
                pass

        timer = threading.Timer(max(1, int(timeout_sec)), _timeout)
        timer.daemon = True
        timer.start()
        try:
            statements = split_statements(text)
            for idx, (stmt, line) in enumerate(statements):
                line += line_offset
                t0 = time.perf_counter()
                words = _words(stmt)
                try:
                    tr = _translate(stmt)
                    note = ""
                    params = ()
                    sql = stmt
                    if tr is not None:
                        sql, params, note = tr
                        if note.startswith("table:"):
                            tname = note[6:]
                            note = ""
                            row = conn.execute(
                                "SELECT 1 FROM sqlite_master WHERE type IN ('table','view') AND name = ? COLLATE NOCASE",
                                (tname,)).fetchone()
                            if row is None:
                                raise sqlite3.OperationalError("Table '%s' doesn't exist" % tname)
                    if _RE_VACUUM_INTO.match(stmt):
                        raise sqlite3.OperationalError("VACUUM INTO is turned off here because it writes a file outside the lab")
                    cur = conn.execute(sql, params)
                    if cur.description is not None:
                        cols = [d[0] for d in cur.description]
                        rows = []
                        total = 0
                        for r in cur:
                            total += 1
                            if len(rows) < max_rows:
                                rows.append([_cell(v) for v in r])
                        ms = round((time.perf_counter() - t0) * 1000, 2)
                        results.append({
                            "kind": "rows", "sql": stmt, "line": line, "ms": ms, "columns": cols,
                            "rows": rows, "total": total, "truncated": total > len(rows),
                            "message": note, "verb": words[0] if words else "",
                        })
                    else:
                        ms = round((time.perf_counter() - t0) * 1000, 2)
                        msg = _ok_message(_words(sql) if tr is not None else words, cur.rowcount)
                        if note:
                            msg += " · " + note
                        warn = ""
                        if words[:1] == ["CREATE"] and re.search(r"\bAUTO_INCREMENT\b", stmt, re.I):
                            warn = ("SQLite does not know AUTO_INCREMENT, so the column will not number itself. "
                                    "Write  id INTEGER PRIMARY KEY AUTOINCREMENT  instead.")
                        results.append({
                            "kind": "ok", "sql": stmt, "line": line, "ms": ms, "message": msg, "hint": warn,
                            "affected": max(cur.rowcount, 0), "verb": words[0] if words else "",
                        })
                except (sqlite3.Error, sqlite3.Warning) as e:
                    ms = round((time.perf_counter() - t0) * 1000, 2)
                    if timed_out[0]:
                        results.append({
                            "kind": "error", "sql": stmt, "line": line, "ms": ms, "code": "TIMEOUT",
                            "message": "Stopped: the query ran longer than %d seconds." % int(timeout_sec),
                            "hint": "Look for a join without an ON condition or a recursive query that never ends.",
                        })
                    elif "interrupted" in str(e).lower():
                        results.append({
                            "kind": "error", "sql": stmt, "line": line, "ms": ms, "code": "STOPPED",
                            "message": "Stopped by you.", "hint": "",
                        })
                    else:
                        results.append(_result_error(stmt, line, e, ms))
                    if stop_on_error or timed_out[0] or results[-1]["code"] == "STOPPED":
                        skipped = len(statements) - idx - 1
                        break
                except Exception as e:  # defensive: never let the screen crash
                    results.append({"kind": "error", "sql": stmt, "line": line, "ms": 0,
                                    "message": "%s: %s" % (type(e).__name__, e), "code": "", "hint": ""})
                    if stop_on_error:
                        skipped = len(statements) - idx - 1
                        break
        finally:
            timer.cancel()
            _current["conn"] = None
        in_tx = bool(conn.in_transaction)
    return json.dumps({"results": results, "in_tx": in_tx, "skipped": skipped, "timed_out": timed_out[0]})


# ----------------------------------------------------------------------------- schema

def schema(path):
    """Tables and views with columns, keys, indexes, triggers and row counts."""
    with _lock:
        conn = _connect(path)
        objs = conn.execute(
            "SELECT type, name, tbl_name, sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' ORDER BY name"
        ).fetchall()
        indexes, triggers = {}, {}
        for typ, name, tbl, sql in objs:
            if typ == "index":
                indexes.setdefault(tbl, []).append(name)
            elif typ == "trigger":
                triggers.setdefault(tbl, []).append(name)
        tables = []
        for typ, name, tbl, sql in objs:
            if typ not in ("table", "view"):
                continue
            cols = []
            try:
                for cid, cname, ctype, notnull, dflt, pk in conn.execute("PRAGMA table_info(%s)" % _q(name)):
                    cols.append({"name": cname, "type": ctype or "", "notnull": bool(notnull), "pk": int(pk), "default": dflt})
            except sqlite3.Error:
                pass
            fks = []
            if typ == "table":
                try:
                    for row in conn.execute("PRAGMA foreign_key_list(%s)" % _q(name)):
                        fks.append({"from": row[3], "table": row[2], "to": row[4]})
                except sqlite3.Error:
                    pass
            count = None
            try:
                count = conn.execute("SELECT COUNT(*) FROM %s" % _q(name)).fetchone()[0]
            except sqlite3.Error:
                pass
            tables.append({
                "name": name, "type": typ, "sql": sql or "", "columns": cols, "fks": fks, "rows": count,
                "indexes": indexes.get(name, []), "triggers": triggers.get(name, []),
            })
        return json.dumps({"tables": tables, "in_tx": bool(conn.in_transaction), "version": sqlite3.sqlite_version})


def dump(path):
    """The whole database as SQL text (CREATE + INSERT statements)."""
    with _lock:
        conn = _connect(path)
        return "\n".join(conn.iterdump()) + "\n"
