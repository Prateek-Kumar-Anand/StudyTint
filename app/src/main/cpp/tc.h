/* Turbo C front end for C Lab.
 *
 * Reads the whole program first, the way Turbo C++ 3.0 does, and prints its messages in the
 * Turbo C format ("Error NONAME00.C 5: Undefined symbol 'x' in function main").  Only a program
 * with no errors (and no linker errors) is handed to the interpreter.
 */
#ifndef TC_H
#define TC_H

#include <stdio.h>

typedef struct TcResult {
    int errors;          /* compile errors              */
    int warnings;        /* compile warnings            */
    int link_errors;     /* "Linker Error: ..." lines   */
    int lines;           /* lines in the program        */
    int float_used;      /* program uses floating point (otherwise scanf("%f") fails like in Turbo C) */
    int has_main;
} TcResult;

#define TC_FILE_NAME "NONAME00.C"

/* Checks the program and prints Turbo C style messages to out.  Returns 1 if it may run. */
int tc_check(const char *src, FILE *out, TcResult *res);

#endif
