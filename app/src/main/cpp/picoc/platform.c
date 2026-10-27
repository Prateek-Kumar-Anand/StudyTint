/* picoc's interface to the underlying platform. most platform-specific code
 * is in platform/platform_XX.c and platform/library_XX.c */

#include <stdio.h>
#include <stdlib.h>
#include <stdio_ext.h>
#include <ctype.h>
#include <string.h>
#include <unistd.h>
#include "picoc.h"
#include "interpreter.h"


static void PrintSourceTextErrorLine(IOFILE *Stream, const char *FileName,
        const char *SourceText, int Line, int CharacterPos);

#ifdef DEBUGGER
static int gEnableDebugger = true;
#else
static int gEnableDebugger = false;
#endif


/* initialize everything */
void PicocInitialize(Picoc *pc, int StackSize)
{
    memset(pc, '\0', sizeof(*pc));
    PlatformInit(pc);
    BasicIOInit(pc);
    HeapInit(pc, StackSize);
    TableInit(pc);
    VariableInit(pc);
    LexInit(pc);
    TypeInit(pc);
    IncludeInit(pc);
    LibraryInit(pc);
    PlatformLibraryInit(pc);
#ifdef DEBUGGER
    DebugInit(pc);
#endif
}

/* free memory */
void PicocCleanup(Picoc *pc)
{
#ifdef DEBUGGER
    DebugCleanup(pc);
#endif
    IncludeCleanup(pc);
    ParseCleanup(pc);
    LexCleanup(pc);
    VariableCleanup(pc);
    TypeCleanup(pc);
    TableStrFree(pc);
    HeapCleanup(pc);
    PlatformCleanup(pc);
}

/* platform-dependent code for running programs */
#if defined(UNIX_HOST) || defined(WIN32)

#define CALL_MAIN_NO_ARGS_RETURN_VOID "main();"
#define CALL_MAIN_WITH_ARGS_RETURN_VOID "main(__argc,__argv);"
#define CALL_MAIN_NO_ARGS_RETURN_INT "__exit_value = main();"
#define CALL_MAIN_WITH_ARGS_RETURN_INT "__exit_value = main(__argc,__argv);"

void PicocCallMain(Picoc *pc, int argc, char **argv)
{
    /* check if the program wants arguments */
    struct Value *FuncValue = NULL;

    if (!VariableDefined(pc, TableStrRegister(pc, "main")))
        ProgramFailNoParser(pc, "main() is not defined");

    VariableGet(pc, NULL, TableStrRegister(pc, "main"), &FuncValue);
    if (FuncValue->Typ->Base != TypeFunction)
        ProgramFailNoParser(pc, "main is not a function - can't call it");

    if (FuncValue->Val->FuncDef.NumParams != 0) {
        /* define the arguments */
        VariableDefinePlatformVar(pc, NULL, "__argc", &pc->IntType,
            (union AnyValue*)&argc, false);
        VariableDefinePlatformVar(pc, NULL, "__argv", pc->CharPtrPtrType,
            (union AnyValue*)&argv, false);
    }

    if (FuncValue->Val->FuncDef.ReturnType == &pc->VoidType) {
        if (FuncValue->Val->FuncDef.NumParams == 0)
            PicocParse(pc, "startup", CALL_MAIN_NO_ARGS_RETURN_VOID,
                strlen(CALL_MAIN_NO_ARGS_RETURN_VOID), true, true, false,
                gEnableDebugger);
        else
            PicocParse(pc, "startup", CALL_MAIN_WITH_ARGS_RETURN_VOID,
                strlen(CALL_MAIN_WITH_ARGS_RETURN_VOID), true, true, false,
                gEnableDebugger);
    } else {
        VariableDefinePlatformVar(pc, NULL, "__exit_value", &pc->IntType,
            (union AnyValue *)&pc->PicocExitValue, true);

        if (FuncValue->Val->FuncDef.NumParams == 0)
            PicocParse(pc, "startup", CALL_MAIN_NO_ARGS_RETURN_INT,
                strlen(CALL_MAIN_NO_ARGS_RETURN_INT), true, true, false,
                gEnableDebugger);
        else
            PicocParse(pc, "startup", CALL_MAIN_WITH_ARGS_RETURN_INT,
                strlen(CALL_MAIN_WITH_ARGS_RETURN_INT), true, true, false,
                gEnableDebugger);
    }
}
#endif

/* ---- gcc-style diagnostics ---- */

/* render a picoc-style format (supports %t for types) into buf */
static void ClabFormat(char *buf, size_t cap, const char *Format, va_list Args)
{
    char *mem = NULL;
    size_t len = 0;
    FILE *m = open_memstream(&mem, &len);
    buf[0] = 0;
    if (m == NULL) { snprintf(buf, cap, "%s", Format); return; }
    PlatformVPrintf(m, Format, Args);
    fclose(m);
    if (mem) { snprintf(buf, cap, "%s", mem); free(mem); }
}

/* translate picoc's wording into the wording gcc uses */
static void ClabWording(const char *in, char *out, size_t cap)
{
    const char *p = in;
    char tmp[512];
    const char *colon = strstr(p, ": ");
    size_t n;
    if (colon && (strncmp(p, "Expression", 10) == 0 || strncmp(p, "VariableGet", 11) == 0)) p = colon + 2;
    snprintf(tmp, sizeof(tmp), "%s", p);
    n = strlen(tmp);
    if (n > 14 && strcmp(tmp + n - 14, "' is undefined") == 0) {
        tmp[n - 14] = 0;
        if (strstr(in, "FuncName") || strstr(in, "MacroName"))
            snprintf(out, cap, "implicit declaration of function %s'", tmp);
        else
            snprintf(out, cap, "%s' undeclared (first use in this function)", tmp);
        return;
    }
    if (n > 20 && strcmp(tmp + n - 20, "' is already defined") == 0) {
        tmp[n - 20] = 0;
        snprintf(out, cap, "redeclaration of %s'", tmp);
        return;
    }
    if (strcmp(tmp, "semicolon expected") == 0) { snprintf(out, cap, "expected ';'"); return; }
    if (n > 9 && strcmp(tmp + n - 9, " expected") == 0) {
        tmp[n - 9] = 0;
        snprintf(out, cap, "expected %s", tmp);
        return;
    }
    snprintf(out, cap, "%s", tmp);
}


/* ---- Turbo C mode: run-time messages the way the Turbo C run-time library prints them ---- */
extern int clab_tc_mode;

void ClabTcAbort(const char *what)
{
    fflush(stdout);
    if (what && *what) printf("%s", what);
    printf("\nAbnormal program termination\n");
    fflush(stdout);
    _exit(3);
}

/* a failure of the interpreter itself (not a mistake the Turbo C checker could see) */
static void ClabTcFail(Picoc *pc, int Line, const char *Raw)
{
    char msg[600];
    ClabWording(Raw, msg, sizeof(msg));
    if (strstr(Raw, "NULL pointer") || strstr(msg, "NULL pointer")) {
        fflush(stdout);
        printf("\nNull pointer assignment\n");
        fflush(stdout);
        _exit(255);
    }
    if (strstr(msg, "out of memory")) {
        ClabTcAbort("Stack overflow!");
    }
    fflush(stdout);
    /* Turbo C accepted the program, so this is a limit of the C Lab interpreter, not a C error */
    if (Line > 0)
        PlatformPrintf(pc->CStdOut, "\nC Lab could not run line %d: %s\n(Turbo C accepts this, but the C Lab interpreter does not support it yet.)\n", Line, msg);
    else
        PlatformPrintf(pc->CStdOut, "\nC Lab could not run the program: %s\n", msg);
}

static const char *ClabLineStart(const char *Src, int Line)
{
    int c = 1;
    while (*Src && c < Line) { if (*Src == '\n') c++; Src++; }
    return Src;
}

static void ClabPrintSnippet(Picoc *pc, const char *Src, int Line, int Col)
{
    char num[24];
    const char *LinePos = ClabLineStart(Src, Line), *CPos;
    int i;
    snprintf(num, sizeof(num), "%5d", Line);
    PlatformPrintf(pc->CStdOut, "%s | ", num);
    for (CPos = LinePos; *CPos != '\n' && *CPos != '\0' && *CPos != '\r'; CPos++)
        PrintCh(*CPos, pc->CStdOut);
    PrintCh('\n', pc->CStdOut);
    PlatformPrintf(pc->CStdOut, "      | ");
    for (i = 0, CPos = LinePos; i < Col && *CPos != '\n' && *CPos != '\0'; i++, CPos++)
        PrintCh(*CPos == '\t' ? '\t' : ' ', pc->CStdOut);
    PlatformPrintf(pc->CStdOut, "^\n");
}

static void ClabReport(Picoc *pc, const char *FileName, const char *SourceText,
    int Line, int Col, const char *Raw)
{
    char msg[600];
    const char *fn = FileName ? FileName : "main.c";
    if (clab_tc_mode) { ClabTcFail(pc, Line, Raw); return; }
    ClabWording(Raw, msg, sizeof(msg));
    if (Col < 0) Col = 0;
    if (SourceText != NULL && strstr(msg, "undeclared (first use") != NULL &&
        ClabLineStart(SourceText, Line)[Col] == '(') {
        char name[200];
        const char *q1 = strchr(msg, '\'');
        const char *q2 = q1 ? strchr(q1 + 1, '\'') : NULL;
        if (q1 && q2 && (size_t)(q2 - q1) < sizeof(name)) {
            memcpy(name, q1, (size_t)(q2 - q1) + 1);
            name[q2 - q1 + 1] = 0;
            snprintf(msg, sizeof(msg), "implicit declaration of function %s", name);
        }
    }
    if (strstr(msg, "undeclared") || strstr(msg, "implicit declaration") ||
        strncmp(msg, "expected", 8) == 0 || strstr(msg, "redeclaration") ||
        strstr(msg, "incompatible types") || strstr(msg, "not expected here")) {
        /* a real compiler stops before the program ever runs */
        __fpurge(stdout);
    }
    /* missing ';' : gcc points at the end of the previous line */
    if (SourceText != NULL && Line > 1 &&
        (strstr(Raw, "not expected here") || strstr(Raw, "statement expected") ||
         strstr(Raw, "bad type declaration"))) {
        int pl = Line - 1;
        while (pl > 1) {
            const char *ls = ClabLineStart(SourceText, pl), *e = ls;
            while (*e && *e != '\n') e++;
            while (e > ls && (e[-1] == ' ' || e[-1] == '\t' || e[-1] == '\r')) e--;
            if (e == ls) pl--; else break;
        }
        {
            const char *ls = ClabLineStart(SourceText, pl), *e = ls;
            while (*e && *e != '\n') e++;
            while (e > ls && (e[-1] == ' ' || e[-1] == '\t' || e[-1] == '\r')) e--;
            if (e > ls) {
                char last = e[-1];
                if (isalnum((unsigned char)last) || last == '_' || last == '"' ||
                    last == '\'' || last == ']') {
                    const char *t = ClabLineStart(SourceText, Line), *w = ls;
                    char tok[64];
                    int k = 0, decl;
                    while (*t == ' ' || *t == '\t') t++;
                    while ((isalnum((unsigned char)*t) || *t == '_') && k < 60) tok[k++] = *t++;
                    tok[k] = 0;
                    while (*w == ' ' || *w == '\t') w++;
                    decl = !strncmp(w, "int ", 4) || !strncmp(w, "char ", 5) ||
                        !strncmp(w, "float ", 6) || !strncmp(w, "double ", 7) ||
                        !strncmp(w, "long ", 5) || !strncmp(w, "short ", 6) ||
                        !strncmp(w, "unsigned ", 9) || !strncmp(w, "const ", 6);
                    if (tok[0]) {
                        PlatformPrintf(pc->CStdOut, "%s:%d:%d: error: expected %s before '%s'\n",
                            fn, pl, (int)(e - ls) + 1, decl ? "',' or ';'" : "';'", tok);
                        ClabPrintSnippet(pc, SourceText, pl, (int)(e - ls));
                        return;
                    }
                }
            }
        }
    }
    PlatformPrintf(pc->CStdOut, "%s:%d:%d: error: %s\n", fn, Line, Col + 1, msg);
    if (SourceText != NULL) ClabPrintSnippet(pc, SourceText, Line, Col);
}

void PrintSourceTextErrorLine(IOFILE *Stream, const char *FileName,
    const char *SourceText, int Line, int CharacterPos)
{
    (void)Stream; (void)FileName; (void)SourceText; (void)Line; (void)CharacterPos;
}

/* exit with a message */
void ProgramFail(struct ParseState *Parser, const char *Message, ...)
{
    va_list Args;
    char buf[600];
    va_start(Args, Message);
    ClabFormat(buf, sizeof(buf), Message, Args);
    va_end(Args);
    ClabReport(Parser->pc, Parser->FileName, Parser->SourceText, Parser->Line,
        Parser->CharacterPos, buf);
    PlatformExit(Parser->pc, 1);
}

/* exit with a message, when we're not parsing a program */
void ProgramFailNoParser(Picoc *pc, const char *Message, ...)
{
    va_list Args;
    char buf[600], msg[600];
    size_t n;

    va_start(Args, Message);
    ClabFormat(buf, sizeof(buf), Message, Args);
    va_end(Args);
    n = strlen(buf);
    while (n && (buf[n - 1] == '\n' || buf[n - 1] == '\r')) buf[--n] = 0;
    if (clab_tc_mode) { ClabTcFail(pc, 0, buf); PlatformExit(pc, 1); }
    ClabWording(buf, msg, sizeof(msg));
    PlatformPrintf(pc->CStdOut, "main.c: error: %s\n", msg);
    PlatformExit(pc, 1);
}

/* like ProgramFail() but gives descriptive error messages for assignment */
void AssignFail(struct ParseState *Parser, const char *Format,
    struct ValueType *Type1, struct ValueType *Type2, int Num1, int Num2,
    const char *FuncName, int ParamNo)
{
    char *mem = NULL;
    size_t len = 0;
    FILE *m = open_memstream(&mem, &len);
    char buf[700];

    if (m != NULL) {
        PlatformPrintf(m, "incompatible types when %s ",
            (FuncName == NULL) ? "assigning" : "passing");
        if (Type1 != NULL)
            PlatformPrintf(m, Format, Type1, Type2);
        else
            PlatformPrintf(m, Format, Num1, Num2);
        if (FuncName != NULL)
            PlatformPrintf(m, " (argument %d of '%s')", ParamNo, FuncName);
        fclose(m);
    }
    snprintf(buf, sizeof(buf), "%s", mem ? mem : "incompatible types");
    free(mem);
    ClabReport(Parser->pc, Parser->FileName, Parser->SourceText, Parser->Line,
        Parser->CharacterPos, buf);
    PlatformExit(Parser->pc, 1);
}

/* exit lexing with a message */
void LexFail(Picoc *pc, struct LexState *Lexer, const char *Message, ...)
{
    va_list Args;
    char buf[600];

    va_start(Args, Message);
    ClabFormat(buf, sizeof(buf), Message, Args);
    va_end(Args);
    ClabReport(pc, Lexer->FileName, Lexer->SourceText, Lexer->Line,
        Lexer->CharacterPos, buf);
    PlatformExit(pc, 1);
}

/* printf for compiler error reporting */
void PlatformPrintf(IOFILE *Stream, const char *Format, ...)
{
    va_list Args;

    va_start(Args, Format);
    PlatformVPrintf(Stream, Format, Args);
    va_end(Args);
}

void PlatformVPrintf(IOFILE *Stream, const char *Format, va_list Args)
{
    const char *FPos;

    for (FPos = Format; *FPos != '\0'; FPos++) {
        if (*FPos == '%') {
            FPos++;
            switch (*FPos) {
            case 's':
                PrintStr(va_arg(Args, char*), Stream);
                break;
            case 'd':
                PrintSimpleInt(va_arg(Args, int), Stream);
                break;
            case 'c':
                PrintCh(va_arg(Args, int), Stream);
                break;
            case 't':
                PrintType(va_arg(Args, struct ValueType*), Stream);
                break;
            case 'f':
                PrintFP(va_arg(Args, double), Stream);
                break;
            case '%':
                PrintCh('%', Stream);
                break;
            case '\0':
                FPos--;
                break;
            default:
                break;
            }
        } else
            PrintCh(*FPos, Stream);
    }
}

/* make a new temporary name. takes a static buffer of char [7] as a parameter.
 * should be initialized to "XX0000"
 * where XX can be any characters */
char *PlatformMakeTempName(Picoc *pc, char *TempNameBuffer)
{
    int CPos = 5;

    while (CPos > 1) {
        if (TempNameBuffer[CPos] < '9') {
            TempNameBuffer[CPos]++;
            return TableStrRegister(pc, TempNameBuffer);
        } else {
            TempNameBuffer[CPos] = '0';
            CPos--;
        }
    }

    return TableStrRegister(pc, TempNameBuffer);
}
