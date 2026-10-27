/* extra standard headers for C Lab */
#include <stdio.h>
#include <unistd.h>
#include "../interpreter.h"

extern void StdioPrintf(struct ParseState *, struct Value *, struct Value **, int);
extern void StdioScanf(struct ParseState *, struct Value *, struct Value **, int);

#define L(s) s "\n"

static const char TypesDefs[] =
    L("typedef unsigned long size_t;")
    L("typedef long ptrdiff_t;")
    L("typedef int wchar_t;")
    L("typedef long ssize_t;")
    L("typedef long intptr_t;")
    L("typedef unsigned long uintptr_t;");

static const char LimitsDefs[] =
    L("#define CHAR_BIT 8")
    L("#define SCHAR_MIN (-128)")
    L("#define SCHAR_MAX 127")
    L("#define UCHAR_MAX 255")
    L("#define CHAR_MIN (-128)")
    L("#define CHAR_MAX 127")
    L("#define SHRT_MIN (-32768)")
    L("#define SHRT_MAX 32767")
    L("#define USHRT_MAX 65535")
    L("#define INT_MIN (-2147483647-1)")
    L("#define INT_MAX 2147483647")
    L("#define UINT_MAX 4294967295U")
    L("#define LONG_MIN (-9223372036854775807L-1L)")
    L("#define LONG_MAX 9223372036854775807L")
    L("#define ULONG_MAX 18446744073709551615UL")
    L("#define LLONG_MIN (-9223372036854775807L-1L)")
    L("#define LLONG_MAX 9223372036854775807L")
    L("#define ULLONG_MAX 18446744073709551615UL")
    L("#define PATH_MAX 4096")
    L("#define MB_LEN_MAX 4");

static const char FloatDefs[] =
    L("#define FLT_RADIX 2")
    L("#define FLT_DIG 6")
    L("#define DBL_DIG 15")
    L("#define LDBL_DIG 15")
    L("#define FLT_MANT_DIG 24")
    L("#define DBL_MANT_DIG 53")
    L("#define FLT_EPSILON 1.19209290e-07")
    L("#define DBL_EPSILON 2.2204460492503131e-16")
    L("#define LDBL_EPSILON 2.2204460492503131e-16")
    L("#define FLT_MIN 1.17549435e-38")
    L("#define DBL_MIN 2.2250738585072014e-308")
    L("#define LDBL_MIN 2.2250738585072014e-308")
    L("#define FLT_MAX 3.40282347e+38")
    L("#define DBL_MAX 1.7976931348623157e+308")
    L("#define LDBL_MAX 1.7976931348623157e+308")
    L("#define FLT_MAX_EXP 128")
    L("#define DBL_MAX_EXP 1024");

static const char StdintDefs[] =
    L("typedef char int8_t;")
    L("typedef unsigned char uint8_t;")
    L("typedef short int16_t;")
    L("typedef unsigned short uint16_t;")
    L("typedef int int32_t;")
    L("typedef unsigned int uint32_t;")
    L("typedef long int64_t;")
    L("typedef unsigned long uint64_t;")
    L("typedef long intmax_t;")
    L("typedef unsigned long uintmax_t;")
    L("typedef signed char int_least8_t;")
    L("typedef int int_fast8_t;")
    L("typedef int int_fast16_t;")
    L("typedef int int_fast32_t;")
    L("typedef long int_fast64_t;")
    L("#define INT8_MIN (-128)")
    L("#define INT8_MAX 127")
    L("#define UINT8_MAX 255")
    L("#define INT16_MIN (-32768)")
    L("#define INT16_MAX 32767")
    L("#define UINT16_MAX 65535")
    L("#define INT32_MIN (-2147483647-1)")
    L("#define INT32_MAX 2147483647")
    L("#define UINT32_MAX 4294967295U")
    L("#define INT64_MIN (-9223372036854775807L-1L)")
    L("#define INT64_MAX 9223372036854775807L")
    L("#define UINT64_MAX 18446744073709551615UL")
    L("#define INTMAX_MAX 9223372036854775807L")
    L("#define INTMAX_MIN (-9223372036854775807L-1L)")
    L("#define UINTMAX_MAX 18446744073709551615UL")
    L("#define SIZE_MAX 18446744073709551615UL")
    L("#define INTPTR_MAX 9223372036854775807L")
    L("#define PTRDIFF_MAX 9223372036854775807L");

static const char InttypesDefs[] =
    L("#define PRId8 \"d\"")
    L("#define PRId16 \"d\"")
    L("#define PRId32 \"d\"")
    L("#define PRId64 \"ld\"")
    L("#define PRIi32 \"i\"")
    L("#define PRIi64 \"li\"")
    L("#define PRIu8 \"u\"")
    L("#define PRIu16 \"u\"")
    L("#define PRIu32 \"u\"")
    L("#define PRIu64 \"lu\"")
    L("#define PRIx32 \"x\"")
    L("#define PRIx64 \"lx\"")
    L("#define PRIX32 \"X\"")
    L("#define PRIX64 \"lX\"")
    L("#define PRIo32 \"o\"")
    L("#define PRIo64 \"lo\"")
    L("#define SCNd32 \"d\"")
    L("#define SCNd64 \"ld\"")
    L("#define SCNu32 \"u\"")
    L("#define SCNu64 \"lu\"");

static const char AssertDefs[] =
    L("void assert(int c) { if (!c) { fprintf(stderr, \"Assertion failed\\n\"); exit(134); } }");

static const char LocaleDefs[] =
    L("#define LC_ALL 6")
    L("#define LC_NUMERIC 4")
    L("char *setlocale(int c, char *l) { return \"C\"; }");

static const char SignalDefs[] =
    L("typedef int sig_atomic_t;")
    L("#define SIGINT 2")
    L("#define SIGTERM 15")
    L("#define SIGSEGV 11")
    L("#define SIGABRT 6")
    L("#define SIGFPE 8")
    L("#define SIG_DFL 0")
    L("#define SIG_IGN 1")
    L("int signal(int s, int h) { return 0; }")
    L("int raise(int s) { return 0; }");

static const char ConioDefs[] =
    L("int getch() { return getchar(); }")
    L("int getche() { return getchar(); }")
    L("int ungetch(int c) { return c; }")
    L("int putch(int c) { putchar(c); return c; }")
    L("int cputs(char *s) { printf(\"%s\", s); return 0; }")
    L("int kbhit() { return 1; }")
    L("void clrscr() { }")
    L("void clreol() { }")
    L("void delline() { }")
    L("void insline() { }")
    L("void gotoxy(int x, int y) { }")
    L("int wherex() { return 1; }")
    L("int wherey() { return 1; }")
    L("void textcolor(int c) { }")
    L("void textbackground(int c) { }")
    L("void textattr(int a) { }")
    L("void textmode(int m) { }")
    L("void window(int a, int b, int c, int d) { }")
    L("void highvideo() { }")
    L("void lowvideo() { }")
    L("void normvideo() { }")
    L("#define BLACK 0")
    L("#define BLUE 1")
    L("#define GREEN 2")
    L("#define CYAN 3")
    L("#define RED 4")
    L("#define MAGENTA 5")
    L("#define BROWN 6")
    L("#define LIGHTGRAY 7")
    L("#define DARKGRAY 8")
    L("#define LIGHTBLUE 9")
    L("#define LIGHTGREEN 10")
    L("#define LIGHTCYAN 11")
    L("#define LIGHTRED 12")
    L("#define LIGHTMAGENTA 13")
    L("#define YELLOW 14")
    L("#define WHITE 15")
    L("#define BLINK 128");

static const char DosDefs[] =
    L("void sound(int hz) { }")
    L("void nosound() { }");

/* Turbo C library functions that the standard headers do not have */
static const char TcStdlibDefs[] =
    L("char *itoa(int v, char *s, int r) { char t[40]; int i = 0, n = 0; long u = v; if (r == 10 && v < 0) { s[n++] = '-'; u = -u; } else if (v < 0) u = u & 4294967295; if (u == 0) t[i++] = '0'; while (u > 0) { int d = u % r; t[i++] = d < 10 ? '0' + d : 'a' + d - 10; u = u / r; } while (i > 0) s[n++] = t[--i]; s[n] = 0; return s; }")
    L("char *ltoa(long v, char *s, int r) { char t[70]; int i = 0, n = 0; long u = v; if (r == 10 && v < 0) { s[n++] = '-'; u = -u; } if (u == 0) t[i++] = '0'; while (u > 0) { int d = u % r; t[i++] = d < 10 ? '0' + d : 'a' + d - 10; u = u / r; } while (i > 0) s[n++] = t[--i]; s[n] = 0; return s; }")
    L("int random(int n) { return rand() % n; }")
    L("void randomize() { srand((int)time(NULL)); }");

static const char TcStringDefs[] =
    L("int stricmp(char *a, char *b) { while (*a && tolower(*a) == tolower(*b)) { a++; b++; } return tolower(*a) - tolower(*b); }")
    L("int strcmpi(char *a, char *b) { while (*a && tolower(*a) == tolower(*b)) { a++; b++; } return tolower(*a) - tolower(*b); }")
    L("int strnicmp(char *a, char *b, size_t n) { while (n > 1 && *a && tolower(*a) == tolower(*b)) { a++; b++; n--; } return n == 0 ? 0 : tolower(*a) - tolower(*b); }")
    L("int strncmpi(char *a, char *b, size_t n) { while (n > 1 && *a && tolower(*a) == tolower(*b)) { a++; b++; n--; } return n == 0 ? 0 : tolower(*a) - tolower(*b); }")
    L("char *strupr(char *s) { char *p = s; while (*p) { *p = toupper(*p); p++; } return s; }")
    L("char *strlwr(char *s) { char *p = s; while (*p) { *p = tolower(*p); p++; } return s; }")
    L("char *strrev(char *s) { int i = 0, j = strlen(s) - 1; while (i < j) { char c = s[i]; s[i] = s[j]; s[j] = c; i++; j--; } return s; }")
    L("char *strset(char *s, int c) { char *p = s; while (*p) { *p = c; p++; } return s; }")
    L("char *strnset(char *s, int c, size_t n) { char *p = s; while (*p && n > 0) { *p = c; p++; n--; } return s; }")
    L("char *stpcpy(char *d, char *s) { while (*s) { *d = *s; d++; s++; } *d = 0; return d; }");

static const char TcMathDefs[] =
    L("float pow10(int p) { return pow(10.0, p); }")
    L("float fabsl(float x) { return fabs(x); }")
    L("float sqrtl(float x) { return sqrt(x); }")
    L("float sinl(float x) { return sin(x); }")
    L("float cosl(float x) { return cos(x); }")
    L("float tanl(float x) { return tan(x); }")
    L("float expl(float x) { return exp(x); }")
    L("float logl(float x) { return log(x); }")
    L("float powl(float x, float y) { return pow(x, y); }")
    L("float floorl(float x) { return floor(x); }")
    L("float ceill(float x) { return ceil(x); }");

static const char TcStdioDefs[] =
    L("int flushall() { fflush(stdout); return 0; }")
    L("int fgetchar() { return getchar(); }")
    L("int unlink(char *p) { return remove(p); }");

static void TcDelay(struct ParseState *Parser, struct Value *ReturnValue,
    struct Value **Param, int NumArgs)
{
    long ms = Param[0]->Val->Integer;
    if (ms > 1000) ms = 1000;
    if (ms > 0) usleep((useconds_t)ms * 1000);
}

static struct LibraryFunction ConioFunctions[] = {
    {StdioPrintf, "int cprintf(char *, ...);"},
    {StdioScanf, "int cscanf(char *, ...);"},
    {NULL, NULL}
};

static struct LibraryFunction DosFunctions[] = {
    {TcDelay, "void delay(int);"},
    {NULL, NULL}
};

static const char Iso646Defs[] =
    L("#define and &&")
    L("#define or ||")
    L("#define not !")
    L("#define xor ^")
    L("#define bitand &")
    L("#define bitor |")
    L("#define compl ~")
    L("#define and_eq &=")
    L("#define or_eq |=")
    L("#define xor_eq ^=")
    L("#define not_eq !=");

static const char MathDefs[] =
    L("#define HUGE_VAL 1.7976931348623157e+308")
    L("#define INFINITY 1.7976931348623157e+308")
    L("#define FP_NAN 0")
    L("#define FP_INFINITE 1")
    L("#define FP_ZERO 2")
    L("#define FP_NORMAL 4")
    L("float fmax(float a, float b) { return a > b ? a : b; }")
    L("float fmin(float a, float b) { return a < b ? a : b; }")
    L("float fdim(float a, float b) { return a > b ? a - b : 0.0; }")
    L("float hypot(float x, float y) { return sqrt(x * x + y * y); }")
    L("float cbrt(float x) { return x < 0 ? -pow(-x, 1.0 / 3.0) : pow(x, 1.0 / 3.0); }")
    L("float trunc(float x) { return x < 0 ? ceil(x) : floor(x); }")
    L("float rint(float x) { return round(x); }")
    L("float nearbyint(float x) { return round(x); }")
    L("long lround(float x) { return round(x); }")
    L("long llround(float x) { return round(x); }")
    L("long lrint(float x) { return round(x); }")
    L("float log2(float x) { return log(x) / 0.6931471805599453; }")
    L("float log1p(float x) { return log(1.0 + x); }")
    L("float exp2(float x) { return pow(2.0, x); }")
    L("float expm1(float x) { return exp(x) - 1.0; }")
    L("float asinh(float x) { return log(x + sqrt(x * x + 1.0)); }")
    L("float acosh(float x) { return log(x + sqrt(x * x - 1.0)); }")
    L("float atanh(float x) { return 0.5 * log((1.0 + x) / (1.0 - x)); }")
    L("float copysign(float x, float y) { return (y < 0) == (x < 0) ? x : -x; }")
    L("float fma(float a, float b, float c) { return a * b + c; }")
    L("float fabsf(float x) { return fabs(x); }")
    L("float sqrtf(float x) { return sqrt(x); }")
    L("float powf(float x, float y) { return pow(x, y); }")
    L("float sinf(float x) { return sin(x); }")
    L("float cosf(float x) { return cos(x); }")
    L("float tanf(float x) { return tan(x); }")
    L("float atanf(float x) { return atan(x); }")
    L("float expf(float x) { return exp(x); }")
    L("float logf(float x) { return log(x); }")
    L("float floorf(float x) { return floor(x); }")
    L("float ceilf(float x) { return ceil(x); }")
    L("float roundf(float x) { return round(x); }")
    L("float fmodf(float x, float y) { return fmod(x, y); }")
    L("int isnan(float x) { return x != x; }")
    L("int isinf(float x) { return x > 1.7976931348623157e+308 || x < -1.7976931348623157e+308; }")
    L("int isfinite(float x) { return x == x && x <= 1.7976931348623157e+308 && x >= -1.7976931348623157e+308; }")
    L("int signbit(float x) { return x < 0; }");

static const char StdlibExtraDefs[] =
    L("#define RAND_MAX 2147483647")
    L("#define EXIT_SUCCESS 0")
    L("#define EXIT_FAILURE 1")
    L("#define MB_CUR_MAX 1")
    L("typedef struct { int quot; int rem; } div_t;")
    L("typedef struct { long quot; long rem; } ldiv_t;")
    L("div_t div(int a, int b) { div_t r; r.quot = a / b; r.rem = a % b; return r; }")
    L("ldiv_t ldiv(long a, long b) { ldiv_t r; r.quot = a / b; r.rem = a % b; return r; }")
    L("long llabs(long x) { return x < 0 ? -x : x; }")
    L("long atoll(char *s) { return atol(s); }")
    L("long strtoll(char *s, char **e, int b) { return strtol(s, e, b); }")
    L("unsigned long strtoull(char *s, char **e, int b) { return strtoul(s, e, b); }")
    L("float strtof(char *s, char **e) { return strtod(s, e); }")
;

static const char StringExtraDefs[] =
    L("int strcasecmp(char *a, char *b) { while (*a && tolower(*a) == tolower(*b)) { a++; b++; } return tolower(*a) - tolower(*b); }")
    L("int strncasecmp(char *a, char *b, size_t n) { while (n > 1 && *a && tolower(*a) == tolower(*b)) { a++; b++; n--; } return n == 0 ? 0 : tolower(*a) - tolower(*b); }")
    L("size_t strnlen(char *s, size_t m) { size_t i = 0; while (i < m && s[i]) i++; return i; }");

/* ---- setup helpers ---- */
static void SetupTypes(Picoc *pc) { }
static void SetupStddef(Picoc *pc) { IncludeFile(pc, "__types.h"); StdlibSetupFunc(pc); }
static void SetupStdint(Picoc *pc) { IncludeFile(pc, "__types.h"); }
static void SetupInttypes(Picoc *pc) { IncludeFile(pc, "stdint.h"); }
static void SetupWithStdio(Picoc *pc) { IncludeFile(pc, "stdio.h"); IncludeFile(pc, "stdlib.h"); }
static void SetupStdarg(Picoc *pc) { IncludeFile(pc, "stdio.h"); }
static void SetupConio(Picoc *pc) { IncludeFile(pc, "stdio.h"); }
static void SetupMath(Picoc *pc) { MathSetupFunc(pc); }
static void SetupStdio(Picoc *pc) { IncludeFile(pc, "__types.h"); StdioSetupFunc(pc); }
static void SetupStdlib(Picoc *pc) { IncludeFile(pc, "__types.h"); IncludeFile(pc, "time.h"); StdlibSetupFunc(pc); }
static void SetupString(Picoc *pc) { IncludeFile(pc, "__types.h"); IncludeFile(pc, "ctype.h"); StringSetupFunc(pc); }
static void SetupTime(Picoc *pc) { IncludeFile(pc, "__types.h"); StdTimeSetupFunc(pc); }
static void SetupUnistd(Picoc *pc) { IncludeFile(pc, "__types.h"); }

/* redirect headers: <malloc.h> -> stdlib.h etc. */
static void SetupToStdlib(Picoc *pc) { IncludeFile(pc, "stdlib.h"); }
static void SetupToString(Picoc *pc) { IncludeFile(pc, "string.h"); }
static void SetupToMath(Picoc *pc) { IncludeFile(pc, "math.h"); }
static void SetupToTime(Picoc *pc) { IncludeFile(pc, "time.h"); }
static void SetupToTypes(Picoc *pc) { IncludeFile(pc, "__types.h"); IncludeFile(pc, "stdlib.h"); }
static void SetupEmpty(Picoc *pc) { }

static void Reg(Picoc *pc, const char *name, void (*setup)(Picoc *), const char *src)
{
    IncludeRegister(pc, name, setup, NULL, src);
}

void ExtraIncludeInit(Picoc *pc)
{
    Reg(pc, "__types.h", SetupTypes, TypesDefs);
    Reg(pc, "stddef.h", SetupStddef, NULL);
    Reg(pc, "limits.h", SetupEmpty, LimitsDefs);
    Reg(pc, "float.h", SetupEmpty, FloatDefs);
    Reg(pc, "stdint.h", SetupStdint, StdintDefs);
    Reg(pc, "inttypes.h", SetupInttypes, InttypesDefs);
    Reg(pc, "assert.h", SetupWithStdio, AssertDefs);
    Reg(pc, "stdarg.h", SetupStdarg, NULL);
    Reg(pc, "locale.h", SetupEmpty, LocaleDefs);
    Reg(pc, "signal.h", SetupEmpty, SignalDefs);
    IncludeRegister(pc, "conio.h", SetupConio, ConioFunctions, ConioDefs);
    IncludeRegister(pc, "dos.h", SetupConio, DosFunctions, DosDefs);
    Reg(pc, "process.h", SetupToStdlib, NULL);
    Reg(pc, "io.h", SetupWithStdio, NULL);
    Reg(pc, "mem.h", SetupToString, NULL);
    Reg(pc, "alloc.h", SetupToStdlib, NULL);
    Reg(pc, "dir.h", SetupEmpty, NULL);
    Reg(pc, "bios.h", SetupEmpty, NULL);
    Reg(pc, "iso646.h", SetupEmpty, Iso646Defs);
    Reg(pc, "malloc.h", SetupToStdlib, NULL);
    Reg(pc, "memory.h", SetupToString, NULL);
    Reg(pc, "strings.h", SetupToString, NULL);
    Reg(pc, "tgmath.h", SetupToMath, NULL);
    Reg(pc, "sys/types.h", SetupToTypes, NULL);
    Reg(pc, "sys/time.h", SetupToTime, NULL);
    Reg(pc, "sys/stat.h", SetupEmpty, NULL);
    Reg(pc, "fcntl.h", SetupEmpty, NULL);
    Reg(pc, "stdnoreturn.h", SetupEmpty, "#define noreturn\n");
    Reg(pc, "stdalign.h", SetupEmpty, NULL);
    Reg(pc, "setjmp.h", SetupEmpty, NULL);
    Reg(pc, "wchar.h", SetupToTypes, NULL);
    Reg(pc, "complex.h", SetupEmpty, NULL);
}

/* C source added to the standard headers (used by IncludeInit) */
static char MathAll[8192], StdlibAll[8192], StringAll[8192];
extern const char StdioDefs[];
static char StdioAll[8192];
const char *ExtraStdioDefs(void) { snprintf(StdioAll, sizeof StdioAll, "%s%s", StdioDefs, TcStdioDefs); return StdioAll; }
const char *ExtraMathDefs(void) { snprintf(MathAll, sizeof MathAll, "%s%s", MathDefs, TcMathDefs); return MathAll; }
const char *ExtraStdlibDefs(void) { snprintf(StdlibAll, sizeof StdlibAll, "%s%s", StdlibExtraDefs, TcStdlibDefs); return StdlibAll; }
const char *ExtraStringDefs(void) { snprintf(StringAll, sizeof StringAll, "%s%s", StringExtraDefs, TcStringDefs); return StringAll; }
void ExtraSetupStdio(Picoc *pc) { SetupStdio(pc); }
void ExtraSetupStdlib(Picoc *pc) { SetupStdlib(pc); }
void ExtraSetupString(Picoc *pc) { SetupString(pc); }
void ExtraSetupTime(Picoc *pc) { SetupTime(pc); }
void ExtraSetupUnistd(Picoc *pc) { SetupUnistd(pc); }
void ExtraSetupMath(Picoc *pc) { SetupMath(pc); }
