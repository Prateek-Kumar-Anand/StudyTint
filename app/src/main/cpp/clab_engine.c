/* C Lab engine: runs a C program with the embedded picoc interpreter. */
#include <ctype.h>
#include <stdio.h>
#include <string.h>
#include "picoc/picoc.h"

extern void IncludeFile(Picoc *pc, char *Filename);

#define CLAB_STACK (128000 * 16)

struct AutoHeader {
    const char *header;
    const char *needles;   /* space separated */
};

static const struct AutoHeader kAuto[] = {
    {"stdio.h", "printf scanf puts getchar putchar fgets fputs fopen fclose fprintf fscanf sprintf sscanf snprintf FILE EOF"},
    {"stdlib.h", "malloc calloc realloc free exit atoi atof atol rand srand abs system EXIT_SUCCESS EXIT_FAILURE RAND_MAX"},
    {"string.h", "strlen strcpy strncpy strcmp strncmp strcat strncat strstr strchr strrchr memset memcpy memcmp strtok strdup"},
    {"ctype.h", "isalpha isdigit isalnum isupper islower isspace toupper tolower ispunct"},
    {NULL, NULL}
};

static int is_ident(char c) { return isalnum((unsigned char)c) || c == '_'; }

/* 1 = used as a call/reference, 2 = defined by the program, 0 = absent */
static int usage_of(const char *src, const char *name)
{
    size_t n = strlen(name);
    int used = 0;
    const char *p = src;
    while ((p = strstr(p, name)) != NULL) {
        int startOk = (p == src) || !is_ident(p[-1]);
        int endOk = !is_ident(p[n]);
        if (startOk && endOk) {
            const char *q = p + n;
            while (*q == ' ' || *q == '\t') q++;
            if (*q == '(') {
                int depth = 0;
                while (*q) {
                    if (*q == '(') depth++;
                    else if (*q == ')' && --depth == 0) { q++; break; }
                    q++;
                }
                while (*q == ' ' || *q == '\t' || *q == '\r' || *q == '\n') q++;
                if (*q == '{') return 2;
            }
            used = 1;
        }
        p += n;
    }
    return used;
}

static void auto_include(Picoc *pc, const char *src)
{
    int i;
    for (i = 0; kAuto[i].header; i++) {
        char hdr[32];
        char list[512];
        char *tok;
        int needed = 0, blocked = 0;
        snprintf(hdr, sizeof(hdr), "<%s>", kAuto[i].header);
        if (strstr(src, hdr) != NULL) continue;
        snprintf(list, sizeof(list), "%s", kAuto[i].needles);
        for (tok = strtok(list, " "); tok; tok = strtok(NULL, " ")) {
            int u = usage_of(src, tok);
            if (u == 2) blocked = 1;
            else if (u == 1) needed = 1;
        }
        if (needed && !blocked) {
            char name[32];
            snprintf(name, sizeof(name), "%s", kAuto[i].header);
            IncludeFile(pc, name);
        }
    }
}

int clab_run_program(const char *src)
{
    Picoc pc;
    PicocInitialize(&pc, CLAB_STACK);
    if (PicocPlatformSetExitPoint(&pc)) {
        int code = pc.PicocExitValue;
        fflush(stdout);
        PicocCleanup(&pc);
        return code;
    }
    auto_include(&pc, src);
    PicocParse(&pc, "main.c", src, (int)strlen(src), 1, 0, 0, 0);
    PicocCallMain(&pc, 0, NULL);
    fflush(stdout);
    {
        int code = pc.PicocExitValue;
        PicocCleanup(&pc);
        return code;
    }
}
