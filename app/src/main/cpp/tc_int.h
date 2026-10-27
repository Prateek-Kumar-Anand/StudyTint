/* Internal declarations shared by the Turbo C front end (tc_pp.c, tc_lib.c, tc_parse.c). */
#ifndef TC_INT_H
#define TC_INT_H

#include <stddef.h>
#include <stdio.h>
#include "tc.h"

/* ------------------------------------------------------------------ memory */

void *tc_alloc(size_t n);                 /* zeroed, freed by tc_free_all() */
char *tc_strdup(const char *s);
char *tc_strndup(const char *s, size_t n);
char *tc_intern(const char *s);           /* same text -> same pointer */
void tc_free_all(void);

/* ------------------------------------------------------------------ tokens */

enum {
    T_EOF = 0,
    /* single character punctuators use their character code (1..255) */
    T_ID = 256, T_INT, T_FLT, T_CHR, T_STR, T_ERR,
    T_ARROW, T_INC, T_DEC, T_SHL, T_SHR, T_LE, T_GE, T_EQ, T_NE, T_ANDAND, T_OROR,
    T_MULEQ, T_DIVEQ, T_MODEQ, T_ADDEQ, T_SUBEQ, T_SHLEQ, T_SHREQ, T_ANDEQ, T_XOREQ, T_OREQ,
    T_ELLIPSIS, T_HASHHASH,
    K_AUTO, K_BREAK, K_CASE, K_CHAR, K_CONST, K_CONTINUE, K_DEFAULT, K_DO, K_DOUBLE, K_ELSE,
    K_ENUM, K_EXTERN, K_FLOAT, K_FOR, K_GOTO, K_IF, K_INT, K_LONG, K_REGISTER, K_RETURN,
    K_SHORT, K_SIGNED, K_SIZEOF, K_STATIC, K_STRUCT, K_SWITCH, K_TYPEDEF, K_UNION, K_UNSIGNED,
    K_VOID, K_VOLATILE, K_WHILE,
    K_NEAR, K_FAR, K_HUGE, K_CDECL, K_PASCAL, K_INTERRUPT      /* Borland extensions */
};

#define TF_UNSIGNED 1       /* integer constant with u suffix (or too big for long) */
#define TF_LONG     2       /* l suffix                                             */
#define TF_FSUFFIX  4       /* f suffix on a floating constant                      */
#define TF_LDOUBLE  8       /* l suffix on a floating constant                      */
#define TF_HEADER   16      /* token comes from a library header                    */
#define TF_FATAL    32      /* T_ERR: stop compiling                                */
#define TF_WARN     64      /* T_ERR: this is a warning                             */
#define TF_REPORTED 128     /* T_ERR: already reported                              */
#define TF_NOSPACE_PAREN 256

typedef struct Tok {
    int kind;
    int line;
    int bol;                /* first token on its line */
    int space;              /* white space before it   */
    int flags;
    char *text;             /* identifier / punctuator spelling / string bytes / error text */
    char *spell;            /* original spelling of numbers, characters and strings */
    int slen;               /* length of string bytes */
    long long ival;
    double fval;
} Tok;

typedef struct TokVec {
    Tok *v;
    size_t n, cap;
} TokVec;

void tv_push(TokVec *tv, const Tok *t);

/* ------------------------------------------------------------------ preprocessor */

typedef struct PpResult {
    TokVec toks;            /* fully expanded tokens, ends with T_EOF */
    int lines;              /* number of lines in the user's file     */
    int last_line;          /* line of the last non-blank character   */
} PpResult;

void tc_preprocess(const char *src, PpResult *out);

/* library headers: text of the header, or NULL if Turbo C has no such header */
const char *tc_header_text(const char *name);
/* is name a function or variable that the Turbo C run-time library provides? */
int tc_lib_has_symbol(const char *name);
void tc_lib_init(void);

/* ------------------------------------------------------------------ diagnostics */

typedef struct Diag {
    FILE *out;
    int errors, warnings;
    int stopped;
    int nlines;
} Diag;

#endif
