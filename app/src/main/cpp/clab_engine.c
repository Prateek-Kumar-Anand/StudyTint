/* C Lab engine: runs a C program with the embedded picoc interpreter.
 *
 * Students often leave out #include lines.  Before the program is parsed we
 *   1. load every header the program asks for with #include,
 *   2. load every header whose functions / constants / types the program uses
 *      without including it (printf -> stdio.h, sqrt -> math.h, bool -> stdbool.h ...),
 *   3. forget any library name the program declares itself (a global called
 *      "index", a "#define M_PI", its own "typedef ... bool", ...), so the
 *      program's own definition wins instead of a "redeclaration" error,
 *   4. turn old-style "main() { ... }" into "int main() { ... }".
 */
#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "picoc/picoc.h"
#include "picoc/interpreter.h"

extern void IncludeFile(Picoc *pc, char *Filename);

/* stdin used by the interpreter (see picoc/cstdlib/stdio.c). NULL = the process stdin. */
FILE *clab_stdin_file = NULL;

#define CLAB_STACK (128000 * 16)

/* ------------------------------------------------------------------ text helpers */

static int is_ident(int c) { return isalnum((unsigned char)c) || c == '_'; }
static int is_ident_start(int c) { return isalpha((unsigned char)c) || c == '_'; }

/* Copy of src with comments, string literals and character literals blanked out
 * (newlines are kept so line numbers still match). */
static char *clean_copy(const char *src)
{
    size_t n = strlen(src), i = 0;
    char *out = (char *)malloc(n + 1);
    if (!out) return NULL;
    memcpy(out, src, n + 1);
    while (i < n) {
        char c = out[i];
        if (c == '/' && i + 1 < n && out[i + 1] == '/') {
            while (i < n && out[i] != '\n') out[i++] = ' ';
        } else if (c == '/' && i + 1 < n && out[i + 1] == '*') {
            out[i++] = ' '; out[i++] = ' ';
            while (i < n && !(out[i] == '*' && i + 1 < n && out[i + 1] == '/')) {
                if (out[i] != '\n') out[i] = ' ';
                i++;
            }
            if (i < n) { out[i++] = ' '; if (i < n) out[i++] = ' '; }
        } else if (c == '"' || c == '\'') {
            char q = c;
            /* keep the quotes themselves so "#include "x.h"" style lines stay readable */
            i++;
            while (i < n && out[i] != q && out[i] != '\n') {
                if (out[i] == '\\' && i + 1 < n && out[i + 1] != '\n') out[i++] = ' ';
                out[i++] = ' ';
            }
            if (i < n && out[i] == q) i++;
        } else {
            i++;
        }
    }
    return out;
}

/* ------------------------------------------------------------------ name sets */

#define MAX_NAMES 1024
#define NAME_LEN 48

#define MAX_SPANS 64

struct NameSet {
    char (*name)[NAME_LEN];
    unsigned char *kind;     /* NK_* */
    int count;
    /* statements that are just a prototype ("int abs(int);"): offset range + the function name */
    int nspan;
    size_t span_from[MAX_SPANS], span_to[MAX_SPANS];
    char span_name[MAX_SPANS][NAME_LEN];
};

enum { NK_OTHER = 0, NK_PROTO = 1, NK_FUNC = 2 };

static void set_init(struct NameSet *s)
{
    s->name = (char (*)[NAME_LEN])calloc(MAX_NAMES, NAME_LEN);
    s->kind = (unsigned char *)calloc(MAX_NAMES, 1);
    s->count = 0;
    s->nspan = 0;
}

static void set_free(struct NameSet *s) { free(s->name); free(s->kind); s->name = NULL; s->kind = NULL; s->count = 0; }

static int set_find(const struct NameSet *s, const char *nm)
{
    int i;
    for (i = 0; i < s->count; i++)
        if (strcmp(s->name[i], nm) == 0) return i;
    return -1;
}

static void set_add(struct NameSet *s, const char *nm, size_t len, int kind)
{
    char tmp[NAME_LEN];
    int at;
    if (len == 0 || len >= NAME_LEN || !s->name) return;
    memcpy(tmp, nm, len);
    tmp[len] = 0;
    at = set_find(s, tmp);
    if (at >= 0) {
        /* a real definition beats a prototype */
        if (kind > s->kind[at]) s->kind[at] = (unsigned char)kind;
        return;
    }
    if (s->count >= MAX_NAMES) return;
    memcpy(s->name[s->count], tmp, len + 1);
    s->kind[s->count] = (unsigned char)kind;
    s->count++;
}

static const char *kKeywords[] = {
    "auto", "break", "case", "char", "const", "continue", "default", "do", "double", "else", "enum",
    "extern", "float", "for", "goto", "if", "int", "long", "register", "return", "short", "signed",
    "sizeof", "static", "struct", "switch", "typedef", "union", "unsigned", "void", "volatile", "while",
    NULL
};

static int is_keyword(const char *p, size_t n)
{
    int i;
    for (i = 0; kKeywords[i]; i++)
        if (strlen(kKeywords[i]) == n && strncmp(kKeywords[i], p, n) == 0) return 1;
    return 0;
}

static const char *skip_back_space(const char *start, const char *p)
{
    while (p > start && isspace((unsigned char)p[-1])) p--;
    return p;
}

/* Collect the names declared at file scope in `text` (already comment/string free):
 * functions (definition or prototype), variables, typedef names, enum constants and #defines. */
static void collect_declared(const char *text, struct NameSet *out)
{
    const char *p = text;
    int brace = 0, paren = 0;
    int enum_level = -1;        /* brace level of an enum body we are inside, or -1 */
    int pending_enum = 0;       /* saw the keyword "enum" and are waiting for '{' */
    const char *stmt_start = text;
    while (*p) {
        char c = *p;
        if (c == '#') {
            const char *line = p + 1;
            while (*line == ' ' || *line == '\t') line++;
            if (strncmp(line, "define", 6) == 0 && !is_ident(line[6])) {
                const char *q = line + 6;
                const char *s;
                while (*q == ' ' || *q == '\t') q++;
                s = q;
                while (is_ident(*q)) q++;
                set_add(out, s, (size_t)(q - s), NK_OTHER);
            }
            /* skip the (possibly continued) directive line */
            while (*p && *p != '\n') {
                if (*p == '\\' && p[1] == '\n') p++;
                p++;
            }
            stmt_start = p;
            continue;
        }
        if (c == '{') {
            brace++;
            if (pending_enum) { enum_level = brace; pending_enum = 0; }
            p++; continue;
        }
        if (c == '}') {
            if (enum_level == brace) enum_level = -1;
            if (brace > 0) brace--;
            p++;
            if (brace == 0) stmt_start = p;
            continue;
        }
        if (c == '(') { paren++; p++; continue; }
        if (c == ')') { if (paren > 0) paren--; p++; continue; }
        if (c == ';') { pending_enum = 0; p++; if (brace == 0 && paren == 0) stmt_start = p; continue; }
        if (is_ident_start(c)) {
            const char *s = p;
            size_t n;
            while (is_ident(*p)) p++;
            n = (size_t)(p - s);
            if (n == 4 && strncmp(s, "enum", 4) == 0) { pending_enum = 1; continue; }
            if (is_keyword(s, n) || paren > 0) continue;
            {
                const char *pb = skip_back_space(text, s);
                char prev = (pb > text) ? pb[-1] : '\n';
                const char *nx = p;
                char next;
                while (isspace((unsigned char)*nx)) nx++;
                next = *nx;
                if (enum_level >= 0 && brace == enum_level) {
                    if ((prev == '{' || prev == ',') && (next == ',' || next == '=' || next == '}'))
                        set_add(out, s, n, NK_OTHER);
                    continue;
                }
                if (brace != 0) continue;
                if (!(is_ident(prev) || prev == '*' || prev == '}' || prev == ',')) continue;
                if (next == '(') {
                    /* function: find the matching ')' and look at what follows */
                    int depth = 0;
                    const char *q = nx;
                    while (*q) {
                        if (*q == '(') depth++;
                        else if (*q == ')' && --depth == 0) { q++; break; }
                        q++;
                    }
                    while (isspace((unsigned char)*q)) q++;
                    if (*q == '{' || is_ident_start(*q)) {
                        set_add(out, s, n, NK_FUNC);   /* also K&R style */
                    } else {
                        set_add(out, s, n, NK_PROTO);
                        if (*q == ';' && out->nspan < MAX_SPANS && n < NAME_LEN) {
                            out->span_from[out->nspan] = (size_t)(stmt_start - text);
                            out->span_to[out->nspan] = (size_t)(q + 1 - text);
                            memcpy(out->span_name[out->nspan], s, n);
                            out->span_name[out->nspan][n] = 0;
                            out->nspan++;
                        }
                    }
                } else if (next == ';' || next == '=' || next == ',' || next == '[') {
                    set_add(out, s, n, NK_OTHER);
                }
            }
            continue;
        }
        p++;
    }
}

/* Does `text` use `name` as a whole word?  If needcall, only count "name(" */
static int word_used(const char *text, const char *name, int needcall)
{
    size_t n = strlen(name);
    const char *p = text;
    while ((p = strstr(p, name)) != NULL) {
        int startOk = (p == text) || !is_ident(p[-1]);
        int endOk = !is_ident(p[n]);
        if (startOk && endOk) {
            if (!needcall) return 1;
            {
                const char *q = p + n;
                while (*q == ' ' || *q == '\t') q++;
                if (*q == '(') return 1;
            }
        }
        p += n;
    }
    return 0;
}

/* ------------------------------------------------------------------ header table */

/* Headers that may be pulled in automatically, with the names their setup code defines
 * directly (variables/constants that cannot be found by reading their prototypes). */
struct AutoHeader {
    const char *header;
    const char *extra;   /* space separated, whole-word triggers */
};

static const struct AutoHeader kHeaders[] = {
    {"stdio.h",   "EOF stdin stdout stderr NULL BUFSIZ SEEK_SET SEEK_CUR SEEK_END FILENAME_MAX"},
    {"stdlib.h",  "EXIT_SUCCESS EXIT_FAILURE RAND_MAX"},
    {"string.h",  ""},
    {"ctype.h",   ""},
    {"math.h",    "M_E M_LOG2E M_LOG10E M_LN2 M_LN10 M_PI M_PI_2 M_PI_4 M_1_PI M_2_PI M_2_SQRTPI M_SQRT2 M_SQRT1_2 HUGE_VAL INFINITY NAN"},
    {"stdbool.h", "true false"},
    {"time.h",    "CLOCKS_PER_SEC"},
    {"limits.h",  ""},
    {"float.h",   ""},
    {"stdint.h",  ""},
    {"stddef.h",  "size_t ptrdiff_t"},
    {"assert.h",  ""},
    {"conio.h",   ""},
    {"unistd.h",  ""},
    {"errno.h",   "errno EDOM ERANGE EINVAL ENOENT"},
    {NULL, NULL}
};

static struct IncludeLibrary *find_lib(Picoc *pc, const char *name)
{
    struct IncludeLibrary *l;
    for (l = pc->IncludeLibList; l; l = l->NextLib)
        if (strcmp(l->IncludeName, name) == 0) return l;
    return NULL;
}

/* Is any name this header provides used by the program (and not declared by it)? */
static int header_needed(const struct AutoHeader *h, struct IncludeLibrary *lib,
                         const char *clean, const struct NameSet *user)
{
    struct NameSet provided;
    char *extra;
    char *tok;
    int i, needed = 0;

    set_init(&provided);
    if (lib->FuncList) {
        int k;
        for (k = 0; lib->FuncList[k].Prototype; k++)
            collect_declared(lib->FuncList[k].Prototype, &provided);
    }
    if (lib->SetupCSource) {
        char *csrc = clean_copy(lib->SetupCSource);
        if (csrc) { collect_declared(csrc, &provided); free(csrc); }
    }
    for (i = 0; i < provided.count && !needed; i++) {
        int u = set_find(user, provided.name[i]);
        if (u >= 0 && user->kind[u] != NK_PROTO) continue;   /* the program defines its own */
        if (word_used(clean, provided.name[i], provided.kind[i] != NK_OTHER)) needed = 1;
    }
    set_free(&provided);
    if (needed) return 1;

    extra = strdup(h->extra);
    if (!extra) return 0;
    for (tok = strtok(extra, " "); tok && !needed; tok = strtok(NULL, " ")) {
        int u = set_find(user, tok);
        if (u >= 0 && user->kind[u] != NK_PROTO) continue;
        if (word_used(clean, tok, 0)) needed = 1;
    }
    free(extra);
    return needed;
}

/* "#include <name>" / "#include "name"" lines of the program */
static void include_requested(Picoc *pc, const char *src)
{
    const char *p = src;
    while (*p) {
        const char *line = p;
        while (*line == ' ' || *line == '\t') line++;
        if (*line == '#') {
            line++;
            while (*line == ' ' || *line == '\t') line++;
            if (strncmp(line, "include", 7) == 0) {
                char name[64];
                int n = 0;
                line += 7;
                while (*line == ' ' || *line == '\t') line++;
                if (*line == '<' || *line == '"') {
                    char close = (*line == '<') ? '>' : '"';
                    line++;
                    while (*line && *line != close && *line != '\n' && n < (int)sizeof(name) - 1)
                        name[n++] = *line++;
                    name[n] = 0;
                    if (n > 0) IncludeFile(pc, name);
                }
            }
        }
        while (*p && *p != '\n') p++;
        if (*p == '\n') p++;
    }
}

static void auto_include(Picoc *pc, const char *src, const char *clean, struct NameSet *user)
{
    int i;
    include_requested(pc, src);
    for (i = 0; kHeaders[i].header; i++) {
        struct IncludeLibrary *lib = find_lib(pc, kHeaders[i].header);
        char name[32];
        if (!lib) continue;
        if (header_needed(&kHeaders[i], lib, clean, user)) {
            snprintf(name, sizeof(name), "%s", kHeaders[i].header);
            IncludeFile(pc, name);
        }
    }
    /* size_t and friends live in an internal header that stdio/stdlib/string pull in */
    if (word_used(clean, "size_t", 0) || word_used(clean, "ssize_t", 0)) {
        char name[] = "__types.h";
        IncludeFile(pc, name);
    }
}

/* Forget library names the program declares itself so its own definition is used. */
static void forget_overridden(Picoc *pc, const struct NameSet *user)
{
    int i;
    for (i = 0; i < user->count; i++) {
        char *key;
        struct Value *v = NULL;
        if (user->kind[i] == NK_PROTO) continue;           /* a prototype of a library function is fine */
        if (strcmp(user->name[i], "main") == 0) continue;
        key = TableStrRegister(pc, user->name[i]);
        if (TableGet(&pc->GlobalTable, key, &v, NULL, NULL, NULL))
            TableDelete(pc, &pc->GlobalTable, key);
    }
}

/* The program re-declares a library function ("int abs(int);").  The interpreter already has
 * it, and a second declaration is an error, so blank those statements out (keeping newlines). */
static void blank_library_protos(Picoc *pc, char *prog, const struct NameSet *user)
{
    int i;
    for (i = 0; i < user->nspan; i++) {
        struct Value *v = NULL;
        size_t k;
        if (!TableGet(&pc->GlobalTable, TableStrRegister(pc, user->span_name[i]), &v, NULL, NULL, NULL)) continue;
        for (k = user->span_from[i]; k < user->span_to[i]; k++)
            if (prog[k] != '\n') prog[k] = ' ';
    }
}

static int brace_depth_at(const char *text, const char *upto)
{
    int depth = 0;
    const char *p;
    for (p = text; p < upto; p++) {
        if (*p == '{') depth++;
        else if (*p == '}' && depth > 0) depth--;
    }
    return depth;
}

/* "main() {" (no return type) -> "int main() {".  Returns a new string, or NULL if unchanged. */
static char *fix_old_main(const char *src, const char *clean)
{
    const char *p = clean;
    while ((p = strstr(p, "main")) != NULL) {
        const char *q = p + 4;
        const char *pb;
        char prev;
        if ((p > clean && is_ident(p[-1])) || is_ident(*q)) { p += 4; continue; }
        while (*q == ' ' || *q == '\t') q++;
        if (*q != '(') { p += 4; continue; }
        pb = skip_back_space(clean, p);
        prev = (pb > clean) ? pb[-1] : '\n';
        /* only at file level and with nothing (no type) in front of it */
        if (brace_depth_at(clean, p) == 0 &&
            (prev == '\n' || prev == ';' || prev == '}' || pb == clean || prev == '>')) {
            size_t off = (size_t)(p - clean);
            size_t n = strlen(src);
            char *fixed = (char *)malloc(n + 5);
            if (!fixed) return NULL;
            memcpy(fixed, src, off);
            memcpy(fixed + off, "int ", 4);
            memcpy(fixed + off + 4, src + off, n - off + 1);
            return fixed;
        }
        p += 4;
    }
    return NULL;
}

/* ------------------------------------------------------------------ entry point */

int clab_run_program(const char *src)
{
    Picoc pc;
    char *clean = clean_copy(src);
    char *fixed = NULL;
    char *prog = NULL;
    const char *program = src;
    struct NameSet user;
    int code;

    if (clab_stdin_file == NULL) clab_stdin_file = stdin;
    set_init(&user);
    if (clean) {
        collect_declared(clean, &user);
    }

    PicocInitialize(&pc, CLAB_STACK);
    if (PicocPlatformSetExitPoint(&pc)) {
        code = pc.PicocExitValue;
        fflush(stdout);
        PicocCleanup(&pc);
        set_free(&user); free(clean); free(fixed); free(prog);
        return code;
    }
    if (clean) {
        auto_include(&pc, src, clean, &user);
        forget_overridden(&pc, &user);
        prog = strdup(src);
        if (prog) {
            blank_library_protos(&pc, prog, &user);
            fixed = fix_old_main(prog, clean);
            program = fixed ? fixed : prog;
        }
    }
    PicocParse(&pc, "main.c", program, (int)strlen(program), 1, 0, 0, 0);
    PicocCallMain(&pc, 0, NULL);
    fflush(stdout);
    code = pc.PicocExitValue;
    PicocCleanup(&pc);
    set_free(&user); free(clean); free(fixed); free(prog);
    return code;
}
