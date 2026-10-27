#define _GNU_SOURCE 1
#include "clab_proc.h"

#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/resource.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

int clab_run_program(const char *src);
extern int clab_tc_mode;
extern FILE *clab_stdin_file;

#define MAX_OUT (256 * 1024)
#define IDLE_MS 300          /* fallback "waiting for input" guess when stdin cannot be hooked */

struct ClabProc {
    pid_t pid;
    int in_fd, out_fd, note_fd;
    int hooked;              /* the child reports when it blocks on stdin */
    int waiting;
    int out_eof, in_closed;
    int timed_out, truncated, user_killed;
    long active_ms, last_ms, idle_since;
    int timeout_ms;
    size_t total;
};

static long now_ms(void)
{
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec * 1000L + ts.tv_nsec / 1000000L;
}

/* ------------------------------------------------------------------ child side */

static int g_note_fd = -1;

/* Called by stdio whenever the program needs more input than is buffered. */
static int stdin_refill(int fd, char *buf, size_t n)
{
    struct pollfd pfd;
    int told = 0;
    ssize_t r;
    fflush(stdout);                         /* show the prompt before we block */
    pfd.fd = 0; pfd.events = POLLIN; pfd.revents = 0;
    if (poll(&pfd, 1, 0) == 0 && g_note_fd >= 0) {
        ssize_t w = write(g_note_fd, "W", 1);
        (void)w;
        told = 1;
    }
    do { r = read(fd, buf, n); } while (r < 0 && errno == EINTR);
    if (told) {
        ssize_t w = write(g_note_fd, "R", 1);
        (void)w;
    }
    return r < 0 ? -1 : (int)r;
}

#ifdef __ANDROID__
/* funopen() is in every Android libc; bind it weakly so a missing symbol can never break the build. */
extern FILE *clab_funopen(const void *cookie, int (*rd)(void *, char *, int), void *wr, void *seek, void *cl)
    __asm__("funopen") __attribute__((weak));

static int cookie_read(void *c, char *buf, int n) { (void)c; return stdin_refill(0, buf, (size_t)n); }

static FILE *make_hooked_stdin(void)
{
    if (clab_funopen == NULL) return NULL;
    return clab_funopen(NULL, cookie_read, NULL, NULL, NULL);
}
#else
static ssize_t cookie_read(void *c, char *buf, size_t n) { (void)c; return stdin_refill(0, buf, n); }

static FILE *make_hooked_stdin(void)
{
    cookie_io_functions_t io;
    memset(&io, 0, sizeof io);
    io.read = cookie_read;
    return fopencookie(NULL, "r", io);
}
#endif

static void child_main(const char *src, const char *dir, int timeout_ms, int note_fd, int mode)
{
    struct rlimit rl;
    int code;
    FILE *in;
    static char obuf[1 << 17];

    if (dir && dir[0] && chdir(dir) != 0) { }
    rl.rlim_cur = rl.rlim_max = (rlim_t)(timeout_ms / 1000 + 3);
    setrlimit(RLIMIT_CPU, &rl);
    rl.rlim_cur = rl.rlim_max = 20 * 1024 * 1024;
    setrlimit(RLIMIT_FSIZE, &rl);
    rl.rlim_cur = rl.rlim_max = 0;
    setrlimit(RLIMIT_CORE, &rl);               /* no core dumps of the app's memory */
    signal(SIGPIPE, SIG_DFL);
    /* line buffered, like a terminal; the buffer is also flushed whenever input is requested */
    setvbuf(stdout, obuf, _IOLBF, sizeof(obuf));

    g_note_fd = note_fd;
    in = make_hooked_stdin();
    if (in) {
        clab_stdin_file = in;
        { ssize_t w = write(note_fd, "H", 1); (void)w; }
    }
    clab_tc_mode = mode;
    code = clab_run_program(src);
    fflush(NULL);
    _exit(code & 0xff);
}

/* ------------------------------------------------------------------ parent side */

ClabProc *clab_proc_start(const char *src, const char *dir, int timeout_ms, int mode)
{
    int inP[2], outP[2], noteP[2];
    ClabProc *p;
    pid_t pid;

    signal(SIGPIPE, SIG_IGN);
    if (pipe(inP) != 0) return NULL;
    if (pipe(outP) != 0) { close(inP[0]); close(inP[1]); return NULL; }
    if (pipe(noteP) != 0) { close(inP[0]); close(inP[1]); close(outP[0]); close(outP[1]); return NULL; }

    pid = fork();
    if (pid < 0) {
        close(inP[0]); close(inP[1]); close(outP[0]); close(outP[1]); close(noteP[0]); close(noteP[1]);
        return NULL;
    }
    if (pid == 0) {
        setpgid(0, 0);
        dup2(inP[0], 0);
        dup2(outP[1], 1);
        dup2(outP[1], 2);
        close(inP[0]); close(inP[1]); close(outP[0]); close(outP[1]); close(noteP[0]);
        child_main(src, dir, timeout_ms, noteP[1], mode);
        _exit(127);
    }

    close(inP[0]); close(outP[1]); close(noteP[1]);
    p = (ClabProc *)calloc(1, sizeof(*p));
    if (!p) { kill(-pid, SIGKILL); kill(pid, SIGKILL); waitpid(pid, NULL, 0); close(inP[1]); close(outP[0]); close(noteP[0]); return NULL; }
    p->pid = pid;
    p->in_fd = inP[1];
    p->out_fd = outP[0];
    p->note_fd = noteP[0];
    p->timeout_ms = timeout_ms;
    p->last_ms = p->idle_since = now_ms();
    fcntl(p->out_fd, F_SETFL, fcntl(p->out_fd, F_GETFL) | O_NONBLOCK);
    fcntl(p->note_fd, F_SETFL, fcntl(p->note_fd, F_GETFL) | O_NONBLOCK);
    fcntl(p->in_fd, F_SETFL, fcntl(p->in_fd, F_GETFL) | O_NONBLOCK);
    return p;
}

void clab_proc_kill(ClabProc *p)
{
    if (!p) return;
    p->user_killed = 1;
    kill(-p->pid, SIGKILL);
    kill(p->pid, SIGKILL);
}

static void kill_now(ClabProc *p)
{
    kill(-p->pid, SIGKILL);
    kill(p->pid, SIGKILL);
}

void clab_proc_write(ClabProc *p, const char *data, int len)
{
    if (!p || p->in_closed || len <= 0) return;
    while (len > 0) {
        ssize_t w = write(p->in_fd, data, (size_t)len);
        if (w < 0) {
            if (errno == EINTR) continue;
            break;                      /* pipe full or closed: drop */
        }
        data += w; len -= (int)w;
    }
    p->waiting = 0;
    p->idle_since = now_ms();
}

void clab_proc_close_input(ClabProc *p)
{
    if (!p || p->in_closed) return;
    p->in_closed = 1;
    close(p->in_fd);
    p->waiting = 0;
    p->idle_since = now_ms();
}

int clab_proc_poll(ClabProc *p, char *buf, int cap, int wait_ms, int *flags)
{
    struct pollfd fds[2];
    int nfds = 0, got = 0, drained = 0;
    long now = now_ms();
    char nb[64];
    ssize_t r;

    *flags = 0;
    /* run time only counts while the program is not waiting for the user */
    if (!p->waiting) p->active_ms += now - p->last_ms;
    p->last_ms = now;
    if (!p->out_eof && !p->timed_out && !p->truncated && p->active_ms > p->timeout_ms) {
        p->timed_out = 1;
        kill_now(p);
    }

    if (!p->out_eof) { fds[nfds].fd = p->out_fd; fds[nfds].events = POLLIN; fds[nfds].revents = 0; nfds++; }
    fds[nfds].fd = p->note_fd; fds[nfds].events = POLLIN; fds[nfds].revents = 0; nfds++;
    if (wait_ms > 0) {
        int rc;
        do { rc = poll(fds, (nfds_t)nfds, wait_ms); } while (rc < 0 && errno == EINTR);
    }

    /* output first, so a prompt is never reported after the "waiting" signal that follows it */
    if (!p->out_eof) {
        for (;;) {
            if (got >= cap) break;
            r = read(p->out_fd, buf + got, (size_t)(cap - got));
            if (r > 0) {
                size_t room;
                p->total += (size_t)r;
                got += (int)r;
                if (p->total > MAX_OUT && !p->truncated) {
                    room = (size_t)(p->total - MAX_OUT);
                    got -= (int)(room < (size_t)got ? room : (size_t)got);
                    p->truncated = 1;
                    kill_now(p);
                    drained = 1;
                    break;
                }
                continue;
            }
            if (r == 0) { p->out_eof = 1; drained = 1; break; }
            if (errno == EINTR) continue;
            drained = 1;                /* EAGAIN: nothing more right now */
            break;
        }
    } else {
        drained = 1;
    }
    if (got > 0) p->idle_since = now;

    while ((r = read(p->note_fd, nb, sizeof nb)) > 0) {
        ssize_t i;
        for (i = 0; i < r; i++) {
            if (nb[i] == 'H') p->hooked = 1;
            else if (nb[i] == 'W') p->waiting = 1;
            else if (nb[i] == 'R') p->waiting = 0;
        }
    }
    if (!p->hooked && !p->out_eof && !p->in_closed && now - p->idle_since > IDLE_MS)
        p->waiting = 1;                 /* stdin could not be hooked: guess from silence */

    if (p->waiting && drained && !p->out_eof) *flags |= CLAB_WAITING;
    if (p->out_eof) *flags |= CLAB_FINISHED;
    return got;
}

void clab_proc_finish(ClabProc *p, char *status, size_t cap)
{
    int st = 0, tries;
    if (!p) { snprintf(status, cap, "could not start program"); return; }
    for (tries = 0; tries < 100; tries++) {
        pid_t w = waitpid(p->pid, &st, WNOHANG);
        if (w == p->pid) break;
        if (w < 0 && errno != EINTR) break;
        usleep(5000);
    }
    if (tries >= 100) {
        kill_now(p);
        while (waitpid(p->pid, &st, 0) < 0 && errno == EINTR) { }
    }
    if (p->user_killed)
        snprintf(status, cap, "stopped by you");
    else if (p->timed_out)
        snprintf(status, cap, "stopped: ran longer than %d seconds", p->timeout_ms / 1000);
    else if (p->truncated)
        snprintf(status, cap, "stopped: too much output");
    else if (WIFSIGNALED(st))
        snprintf(status, cap, "crashed (signal %d)", WTERMSIG(st));
    else
        snprintf(status, cap, "exited with code %d", WEXITSTATUS(st));
    if (!p->in_closed) close(p->in_fd);
    close(p->out_fd);
    close(p->note_fd);
    free(p);
}
