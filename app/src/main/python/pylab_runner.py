"""Runs user code for the Python Lab with real CPython behaviour and tracebacks."""
import builtins
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
    def __init__(self):
        self.parts = []
        self.size = 0
        self.truncated = False

    def writable(self):
        return True

    def isatty(self):
        return False

    def write(self, s):
        if not isinstance(s, str):
            raise TypeError("write() argument must be str, not %s" % type(s).__name__)
        if self.truncated:
            return len(s)
        room = MAX_OUT - self.size
        if len(s) > room:
            self.parts.append(s[:max(room, 0)])
            self.parts.append("\n[output truncated]\n")
            self.size = MAX_OUT
            self.truncated = True
            _raise_in(threading.get_ident(), _Stop)
            return len(s)
        self.parts.append(s)
        self.size += len(s)
        return len(s)

    def flush(self):
        pass

    def getvalue(self):
        return "".join(self.parts)


_state = {"tid": None, "reason": None}
_real_sleep = time.sleep


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


def _timeout():
    tid = _state.get("tid")
    if tid is not None:
        _state["reason"] = "timeout"
        _raise_in(tid, _Stop)


def _print_error(exc_type, exc, tb):
    # drop this runner's own frames so only the user's frames show
    while tb is not None and tb.tb_frame.f_code.co_filename == __file__:
        tb = tb.tb_next
    traceback.print_exception(exc_type, exc, tb)


def run(code, stdin_text, work_dir, lib_paths, timeout):
    out = _Out()
    saved = (sys.stdout, sys.stderr, sys.stdin, list(sys.path), os.getcwd(), time.sleep,
             getattr(sys, "argv", None), set(sys.modules))
    status_code = 0
    status = None
    timer = None
    try:
        sys.stdout = out
        sys.stderr = out
        sys.stdin = io.StringIO(stdin_text)
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
        timer = threading.Timer(float(timeout), _timeout)
        timer.daemon = True
        timer.start()
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
        if timer is not None:
            timer.cancel()
        reason = _state.get("reason")
        _state["tid"] = None
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
    elif reason == "timeout":
        status = "stopped: ran longer than %d seconds" % int(timeout)
    elif reason == "user":
        status = "stopped by you"
    else:
        status = "exited with code %d" % status_code
    return [out.getvalue(), status]
