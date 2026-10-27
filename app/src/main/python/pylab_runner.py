"""Runs user code for the Python Lab with real CPython behaviour and tracebacks."""
import builtins
import collections
import ctypes
import importlib
import io
import linecache
import os
import platform
import sys
import threading
import time
import traceback
import warnings

MAX_OUT = 256 * 1024
FILENAME = "main.py"


class _Stop(BaseException):
    """Raised inside the running program to stop it."""


class _Out(io.TextIOBase):
    """Collects the program's output; take() hands the new part to the screen while the program runs."""

    def __init__(self):
        self.parts = []
        self.size = 0
        self.taken = 0
        self.truncated = False
        self.lock = threading.Lock()

    def writable(self):
        return True

    def isatty(self):
        return False

    def write(self, s):
        if not isinstance(s, str):
            raise TypeError("write() argument must be str, not %s" % type(s).__name__)
        with self.lock:
            if self.truncated:
                return len(s)
            room = MAX_OUT - self.size
            if len(s) > room:
                self.parts.append(s[:max(room, 0)])
                self.parts.append("\n[output truncated]\n")
                self.size = MAX_OUT
                self.truncated = True
                over = True
            else:
                self.parts.append(s)
                self.size += len(s)
                over = False
        if over:
            _raise_in(threading.get_ident(), _Stop)
        return len(s)

    def flush(self):
        pass

    def take(self):
        with self.lock:
            text = "".join(self.parts[self.taken:])
            self.taken = len(self.parts)
        return text


class _In(io.TextIOBase):
    """stdin for the program: each line is typed by the user into the output box while the program waits."""

    def readable(self):
        return True

    def isatty(self):
        return False

    def readline(self, size=-1):
        line = _next_line()
        if size is not None and size >= 0 and len(line) > size:
            _lines.appendleft(line[size:])
            line = line[:size]
        return line

    def read(self, size=-1):
        if size is not None and size >= 0:
            buf = ""
            while len(buf) < size:
                line = _next_line()
                if not line:
                    break
                buf += line
            if len(buf) > size:
                _lines.appendleft(buf[size:])
                buf = buf[:size]
            return buf
        chunks = []
        while True:
            line = _next_line()
            if not line:
                return "".join(chunks)
            chunks.append(line)

    def readlines(self, hint=-1):
        out = []
        while True:
            line = _next_line()
            if not line:
                return out
            out.append(line)


_state = {"tid": None, "reason": None, "waiting": False, "waited": 0.0, "wait_start": None, "eof": False, "out": None}
_real_sleep = time.sleep
_cv = threading.Condition()
_lines = collections.deque()


def _next_line():
    """Blocks until the user sends a line (or closes input). Returns "" at end of input."""
    with _cv:
        t0 = time.monotonic()
        _state["waiting"] = True
        _state["wait_start"] = t0
        try:
            while not _lines and not _state["eof"]:
                _cv.wait(0.05)          # short waits so Stop / time limit can interrupt
            if _lines:
                return _lines.popleft()
            return ""
        finally:
            _state["waiting"] = False
            _state["wait_start"] = None
            _state["waited"] += time.monotonic() - t0


def send(line):
    """Called by the screen when the user presses Enter in the output box."""
    with _cv:
        line = str(line)
        if not line.endswith("\n"):
            line += "\n"
        _lines.append(line)
        _cv.notify_all()


def close_input():
    """Called by the screen for the EOF button (like Ctrl+D)."""
    with _cv:
        _state["eof"] = True
        _cv.notify_all()


def poll():
    """Returns [new output, waiting for input?] for the screen."""
    out = _state.get("out")
    text = out.take() if out is not None else ""
    return [text, bool(_state["waiting"])]


def _raise_in(tid, exc):
    ctypes.pythonapi.PyThreadState_SetAsyncExc(ctypes.c_ulong(tid), ctypes.py_object(exc))


def _sleep(seconds):
    end = time.monotonic() + seconds
    while True:
        left = end - time.monotonic()
        if left <= 0:
            return
        _real_sleep(min(left, 0.05))


def version():
    return platform.python_version()


def stop():
    tid = _state.get("tid")
    if tid is not None:
        _state["reason"] = "user"
        _raise_in(tid, _Stop)


def _rss_mb():
    try:
        with open("/proc/self/statm") as f:
            return int(f.read().split()[1]) * os.sysconf("SC_PAGE_SIZE") / 1048576.0
    except Exception:
        return 0.0


def _memory_limit_mb():
    """About 45% of the phone's RAM, kept between 600 MB and 2.5 GB: a runaway program is stopped before Android kills the app."""
    try:
        with open("/proc/meminfo") as f:
            kb = int(f.readline().split()[1])
        return max(600.0, min(2500.0, kb / 1024.0 * 0.45))
    except Exception:
        return 1200.0


def _watchdog(timeout, t_start, stop_evt):
    """Stops the program when it has been *running* for `timeout` seconds (waiting for input does not count)
    or when it uses far too much memory."""
    mem_limit = _memory_limit_mb()
    while not stop_evt.wait(0.05):
        if _rss_mb() > mem_limit:
            tid = _state.get("tid")
            if tid is not None:
                _state["reason"] = "memory"
                _raise_in(tid, _Stop)
            return
        waited = _state["waited"]
        ws = _state["wait_start"]
        if ws is not None:
            waited += time.monotonic() - ws
        if time.monotonic() - t_start - waited > timeout:
            tid = _state.get("tid")
            if tid is not None:
                _state["reason"] = "timeout"
                _raise_in(tid, _Stop)
            return


def _print_error(exc_type, exc, tb):
    # drop this runner's own frames so only the user's frames show
    while tb is not None and tb.tb_frame.f_code.co_filename == __file__:
        tb = tb.tb_next
    traceback.print_exception(exc_type, exc, tb)


def run(code, work_dir, lib_paths, timeout):
    out = _Out()
    _state.update({"out": out, "waiting": False, "waited": 0.0, "wait_start": None, "eof": False, "reason": None})
    with _cv:
        _lines.clear()
    saved = (sys.stdout, sys.stderr, sys.stdin, list(sys.path), os.getcwd(), time.sleep,
             getattr(sys, "argv", None), set(sys.modules))
    status_code = 0
    status = None
    watch = None
    watch_stop = threading.Event()
    try:
        sys.stdout = out
        sys.stderr = out
        sys.stdin = _In()
        time.sleep = _sleep
        os.makedirs(work_dir, exist_ok=True)
        os.chdir(work_dir)
        os.environ["PYLAB_HOME"] = os.path.dirname(work_dir.rstrip("/"))
        for p in reversed([x for x in lib_paths.split("\n") if x]):
            if p not in sys.path:
                sys.path.insert(0, p)
        if work_dir not in sys.path:
            sys.path.insert(0, work_dir)
        importlib.invalidate_caches()
        sys.argv = [FILENAME]
        warnings.simplefilter("default")
        linecache.cache[FILENAME] = (len(code), None, code.splitlines(True), FILENAME)
        _state["tid"] = threading.get_ident()
        _state["reason"] = None
        watch = threading.Thread(target=_watchdog, args=(float(timeout), time.monotonic(), watch_stop), daemon=True)
        watch.start()
        g = {"__name__": "__main__", "__file__": FILENAME, "__builtins__": builtins, "__doc__": None}
        try:
            compiled = compile(code, FILENAME, "exec")
            exec(compiled, g)
        except _Stop:
            pass
        except SystemExit as e:
            c = e.code
            if c is None:
                status_code = 0
            elif isinstance(c, int):
                status_code = c
            else:
                out.write(str(c) + "\n")
                status_code = 1
        except KeyboardInterrupt as e:
            _print_error(type(e), e, e.__traceback__)
            status_code = 130
        except BaseException as e:  # noqa: BLE001 - report everything like python does
            _print_error(type(e), e, e.__traceback__)
            status_code = 1
    finally:
        watch_stop.set()
        reason = _state.get("reason")
        _state["tid"] = None
        _state["waiting"] = False
        sys.stdout, sys.stderr, sys.stdin = saved[0], saved[1], saved[2]
        sys.path[:] = saved[3]
        try:
            os.chdir(saved[4])
        except OSError:
            pass
        time.sleep = saved[5]
        if saved[6] is not None:
            sys.argv = saved[6]
        # forget modules loaded from the lab folders so the next run starts fresh
        roots = [os.path.abspath(p) for p in lib_paths.split("\n") if p] + [os.path.abspath(work_dir)]
        for name in list(sys.modules):
            if name in saved[7]:
                continue
            f = getattr(sys.modules[name], "__file__", None)
            if f and any(os.path.abspath(f).startswith(r) for r in roots):
                del sys.modules[name]
        linecache.cache.pop(FILENAME, None)
        importlib.invalidate_caches()
    if out.truncated:
        status = "stopped: too much output"
    elif reason == "memory":
        status = "stopped: used too much memory"
    elif reason == "timeout":
        status = "stopped: ran longer than %d seconds" % int(timeout)
    elif reason == "user":
        status = "stopped by you"
    else:
        status = "exited with code %d" % status_code
    return [out.take(), status]
