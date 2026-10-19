/* minimal, sandbox-safe unistd.h for C Lab */
#include <unistd.h>
#include <time.h>
#include "../interpreter.h"

const char UnistdDefs[] = "\
typedef int uid_t; \
typedef int gid_t; \
typedef int pid_t; \
typedef int off_t; \
typedef int useconds_t; \
";

void UnistdSetupFunc(Picoc *pc)
{
    IncludeFile(pc, "stddef.h");
}

static void UnistdSleep(struct ParseState *Parser, struct Value *ReturnValue,
    struct Value **Param, int NumArgs)
{
    int s = Param[0]->Val->Integer;
    if (s > 5) s = 5;
    if (s > 0) sleep(s);
    ReturnValue->Val->Integer = 0;
}

static void UnistdUsleep(struct ParseState *Parser, struct Value *ReturnValue,
    struct Value **Param, int NumArgs)
{
    int u = Param[0]->Val->Integer;
    if (u > 5000000) u = 5000000;
    if (u > 0) usleep(u);
    ReturnValue->Val->Integer = 0;
}

static void UnistdGetpid(struct ParseState *Parser, struct Value *ReturnValue,
    struct Value **Param, int NumArgs)
{
    ReturnValue->Val->Integer = getpid();
}

static void UnistdIsatty(struct ParseState *Parser, struct Value *ReturnValue,
    struct Value **Param, int NumArgs)
{
    ReturnValue->Val->Integer = 0;
}

struct LibraryFunction UnistdFunctions[] =
{
    {UnistdSleep, "unsigned int sleep(unsigned int);"},
    {UnistdUsleep, "int usleep(int);"},
    {UnistdGetpid, "int getpid(void);"},
    {UnistdIsatty, "int isatty(int);"},
    {NULL, NULL}
};
