#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/resource.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

int clab_run_program(const char *src);

#define MAX_OUT (256 * 1024)
#define MAX_IN (60 * 1024)

static long now_ms(void)
{
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec * 1000L + ts.tv_nsec / 1000000L;
}

JNIEXPORT jbyteArray JNICALL
Java_com_student_smartstudy_CEngine_run(JNIEnv *env, jobject thiz,
    jbyteArray jsrc, jbyteArray jin, jstring jdir, jint timeoutMs)
{
    jsize srcLen = (*env)->GetArrayLength(env, jsrc);
    jsize inLen = (*env)->GetArrayLength(env, jin);
    char *src = (char *)malloc((size_t)srcLen + 1);
    char *in = (char *)malloc((size_t)inLen + 1);
    const char *dirChars = (*env)->GetStringUTFChars(env, jdir, NULL);
    char *dir = strdup(dirChars ? dirChars : "/");
    char *out = (char *)malloc(MAX_OUT + 512);
    size_t outLen = 0;
    char status[160];
    int inPipe[2], outPipe[2];
    pid_t pid;
    int timedOut = 0, truncated = 0, st = 0;
    jbyteArray result;

    if (dirChars) (*env)->ReleaseStringUTFChars(env, jdir, dirChars);
    (*env)->GetByteArrayRegion(env, jsrc, 0, srcLen, (jbyte *)src);
    (*env)->GetByteArrayRegion(env, jin, 0, inLen, (jbyte *)in);
    src[srcLen] = 0;
    if (inLen > MAX_IN) inLen = MAX_IN;

    signal(SIGPIPE, SIG_IGN);
    snprintf(status, sizeof(status), "could not start program");

    if (pipe(inPipe) != 0 || pipe(outPipe) != 0) goto finish;

    if (inLen > 0) {
        ssize_t w = write(inPipe[1], in, (size_t)inLen);
        (void)w;
    }
    close(inPipe[1]);

    pid = fork();
    if (pid < 0) {
        close(inPipe[0]); close(outPipe[0]); close(outPipe[1]);
        goto finish;
    }
    if (pid == 0) {
        struct rlimit rl;
        int code;
        setpgid(0, 0);
        dup2(inPipe[0], 0);
        dup2(outPipe[1], 1);
        dup2(outPipe[1], 2);
        close(inPipe[0]); close(outPipe[0]); close(outPipe[1]);
        if (dir[0]) { if (chdir(dir) != 0) { } }
        rl.rlim_cur = rl.rlim_max = (rlim_t)(timeoutMs / 1000 + 3);
        setrlimit(RLIMIT_CPU, &rl);
        rl.rlim_cur = rl.rlim_max = 20 * 1024 * 1024;
        setrlimit(RLIMIT_FSIZE, &rl);
        signal(SIGPIPE, SIG_DFL);
        { static char obuf[1 << 17]; setvbuf(stdout, obuf, _IOFBF, sizeof(obuf)); }
        code = clab_run_program(src);
        fflush(NULL);
        _exit(code & 0xff);
    }

    close(inPipe[0]);
    close(outPipe[1]);
    {
        long deadline = now_ms() + timeoutMs;
        char buf[4096];
        for (;;) {
            long left = deadline - now_ms();
            struct pollfd pfd;
            int pr;
            if (left <= 0) { timedOut = 1; break; }
            pfd.fd = outPipe[0]; pfd.events = POLLIN; pfd.revents = 0;
            pr = poll(&pfd, 1, (int)left);
            if (pr < 0) { if (errno == EINTR) continue; break; }
            if (pr == 0) { timedOut = 1; break; }
            {
                ssize_t n = read(outPipe[0], buf, sizeof(buf));
                if (n < 0) { if (errno == EINTR) continue; break; }
                if (n == 0) break;
                if (outLen + (size_t)n > MAX_OUT) {
                    size_t room = MAX_OUT - outLen;
                    memcpy(out + outLen, buf, room);
                    outLen += room;
                    truncated = 1;
                    break;
                }
                memcpy(out + outLen, buf, (size_t)n);
                outLen += (size_t)n;
            }
        }
    }
    if (timedOut || truncated) kill(-pid, SIGKILL), kill(pid, SIGKILL);
    close(outPipe[0]);
    while (waitpid(pid, &st, 0) < 0 && errno == EINTR) { }

    if (timedOut)
        snprintf(status, sizeof(status), "stopped: ran longer than %d seconds", timeoutMs / 1000);
    else if (truncated)
        snprintf(status, sizeof(status), "stopped: too much output");
    else if (WIFSIGNALED(st))
        snprintf(status, sizeof(status), "crashed (signal %d)", WTERMSIG(st));
    else
        snprintf(status, sizeof(status), "exited with code %d", WEXITSTATUS(st));

finish:
    {
        size_t sl = strlen(status);
        size_t total = outLen + 1 + sl;
        result = (*env)->NewByteArray(env, (jsize)total);
        if (result) {
            (*env)->SetByteArrayRegion(env, result, 0, (jsize)outLen, (jbyte *)out);
            (*env)->SetByteArrayRegion(env, result, (jsize)outLen, 1, (jbyte *)"\0");
            (*env)->SetByteArrayRegion(env, result, (jsize)(outLen + 1), (jsize)sl, (jbyte *)status);
        }
    }
    free(src); free(in); free(dir); free(out);
    return result;
}
