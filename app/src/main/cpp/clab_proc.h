/* C Lab process control: runs a program in a child process and lets the UI stream its output,
 * see when it is waiting for a line of input, and send that input. Plain C (no JNI). */
#ifndef CLAB_PROC_H
#define CLAB_PROC_H

#include <stddef.h>

#define CLAB_WAITING  1   /* the program is blocked waiting for input */
#define CLAB_FINISHED 2   /* the program has ended; call clab_proc_finish() */

typedef struct ClabProc ClabProc;

/* Starts the program.  timeout_ms counts only time spent running, not time spent waiting for input. */
ClabProc *clab_proc_start(const char *src, const char *dir, int timeout_ms);

/* Waits up to wait_ms for output.  Returns the number of bytes copied into buf (may be 0). */
int clab_proc_poll(ClabProc *p, char *buf, int cap, int wait_ms, int *flags);

/* Sends text to the program's stdin. */
void clab_proc_write(ClabProc *p, const char *data, int len);

/* Closes the program's stdin (like Ctrl+D): reads now return end-of-file. */
void clab_proc_close_input(ClabProc *p);

/* Stops the program. */
void clab_proc_kill(ClabProc *p);

/* Reaps the process, writes a one-line status into status and frees p. */
void clab_proc_finish(ClabProc *p, char *status, size_t cap);

#endif
