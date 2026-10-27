/* Turbo C front end, part 1: memory, lexer and preprocessor.
 *
 * Lexical problems (unterminated strings, illegal characters, ...) are not printed here.  They
 * travel through the token stream as T_ERR tokens so the parser can print them at the right moment
 * and with the right " in function xxx" ending, like a one-pass compiler does.
 */
#include <ctype.h>
#include <stdarg.h>
#include <stdlib.h>
#include <string.h>
#include "tc_int.h"

/* ================================================================== memory */

typedef struct Chunk {
    struct Chunk *next;
    size_t used, cap;
    /* data follows */
} Chunk;

static Chunk *g_chunks;

void *tc_alloc(size_t n)
{
    void *p;
    n = (n + 15) & ~(size_t)15;
    if (!g_chunks || g_chunks->used + n > g_chunks->cap) {
        size_t cap = n > 65536 ? n : 65536;
        Chunk *c = (Chunk *)calloc(1, sizeof(Chunk) + cap);
        if (!c) { fprintf(stderr, "out of memory\n"); exit(1); }
        c->cap = cap;
        c->next = g_chunks;
        g_chunks = c;
    }
    p = (char *)(g_chunks + 1) + g_chunks->used;
    g_chunks->used += n;
    return p;
}

char *tc_strndup(const char *s, size_t n)
{
    char *r = (char *)tc_alloc(n + 1);
    memcpy(r, s, n);
    r[n] = 0;
    return r;
}

char *tc_strdup(const char *s) { return tc_strndup(s, strlen(s)); }

typedef struct IEnt { struct IEnt *next; char s[1]; } IEnt;
static IEnt **g_itab;
#define ITAB 4096

char *tc_intern(const char *s)
{
    unsigned h = 5381;
    const char *q;
    IEnt *e;
    if (!g_itab) g_itab = (IEnt **)tc_alloc(sizeof(IEnt *) * ITAB);
    for (q = s; *q; q++) h = h * 33 + (unsigned char)*q;
    h %= ITAB;
    for (e = g_itab[h]; e; e = e->next)
        if (strcmp(e->s, s) == 0) return e->s;
    e = (IEnt *)tc_alloc(sizeof(IEnt) + strlen(s));
    strcpy(e->s, s);
    e->next = g_itab[h];
    g_itab[h] = e;
    return e->s;
}

void tc_free_all(void)
{
    Chunk *c = g_chunks;
    while (c) { Chunk *n = c->next; free(c); c = n; }
    g_chunks = NULL;
    g_itab = NULL;
}

void tv_push(TokVec *tv, const Tok *t)
{
    if (tv->n == tv->cap) {
        size_t nc = tv->cap ? tv->cap * 2 : 256;
        Tok *nv = (Tok *)tc_alloc(nc * sizeof(Tok));
        if (tv->n) memcpy(nv, tv->v, tv->n * sizeof(Tok));
        tv->v = nv;
        tv->cap = nc;
    }
    tv->v[tv->n++] = *t;
}

/* ================================================================== lexer */

typedef struct Lexer {
    const char *s;
    size_t n, i;
    int line;
    int bol;
    int quiet;              /* do not report errors (skipped #if groups, headers) */
    int hdr;                /* tokens come from a library header */
    int hdr_line;           /* line of the #include that pulled it in */
    TokVec pend;            /* queued error tokens */
    size_t pend_i;
    Tok peeked;
    int have_peek;
    Tok defer;              /* a token held back while its error tokens are delivered */
    int have_defer;
} Lexer;

static void lerr(Lexer *L, int line, int flags, const char *fmt, ...)
{
    Tok t;
    char buf[256];
    va_list ap;
    if (L->quiet || L->hdr) return;
    va_start(ap, fmt);
    vsnprintf(buf, sizeof buf, fmt, ap);
    va_end(ap);
    memset(&t, 0, sizeof t);
    t.kind = T_ERR;
    t.line = line;
    t.flags = flags;
    t.text = tc_strdup(buf);
    tv_push(&L->pend, &t);
}

static int hexval(int c)
{
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}

/* reads one escape sequence; s[*i] is the character after the backslash */
static int read_escape(const char *s, size_t n, size_t *i)
{
    int c = (unsigned char)s[*i];
    int v;
    (*i)++;
    switch (c) {
    case 'n': return '\n';
    case 't': return '\t';
    case 'r': return '\r';
    case 'a': return 7;
    case 'b': return 8;
    case 'f': return 12;
    case 'v': return 11;
    case 'x': case 'X':
        v = 0;
        while (*i < n && hexval((unsigned char)s[*i]) >= 0) { v = v * 16 + hexval((unsigned char)s[*i]); (*i)++; }
        return v & 0xff;
    default:
        if (c >= '0' && c <= '7') {
            int k = 1;
            v = c - '0';
            while (k < 3 && *i < n && s[*i] >= '0' && s[*i] <= '7') { v = v * 8 + (s[*i] - '0'); (*i)++; k++; }
            return v & 0xff;
        }
        return c;
    }
}

static struct { const char *name; int kind; } kKw[] = {
    {"auto", K_AUTO}, {"break", K_BREAK}, {"case", K_CASE}, {"char", K_CHAR}, {"const", K_CONST},
    {"continue", K_CONTINUE}, {"default", K_DEFAULT}, {"do", K_DO}, {"double", K_DOUBLE},
    {"else", K_ELSE}, {"enum", K_ENUM}, {"extern", K_EXTERN}, {"float", K_FLOAT}, {"for", K_FOR},
    {"goto", K_GOTO}, {"if", K_IF}, {"int", K_INT}, {"long", K_LONG}, {"register", K_REGISTER},
    {"return", K_RETURN}, {"short", K_SHORT}, {"signed", K_SIGNED}, {"sizeof", K_SIZEOF},
    {"static", K_STATIC}, {"struct", K_STRUCT}, {"switch", K_SWITCH}, {"typedef", K_TYPEDEF},
    {"union", K_UNION}, {"unsigned", K_UNSIGNED}, {"void", K_VOID}, {"volatile", K_VOLATILE},
    {"while", K_WHILE},
    {"near", K_NEAR}, {"far", K_FAR}, {"huge", K_HUGE}, {"cdecl", K_CDECL}, {"pascal", K_PASCAL},
    {"interrupt", K_INTERRUPT},
    {"_near", K_NEAR}, {"_far", K_FAR}, {"_huge", K_HUGE}, {"_cdecl", K_CDECL}, {"_pascal", K_PASCAL},
    {"_interrupt", K_INTERRUPT},
    {"__near", K_NEAR}, {"__far", K_FAR}, {"__huge", K_HUGE}, {"__cdecl", K_CDECL},
    {"__pascal", K_PASCAL}, {"__interrupt", K_INTERRUPT},
    {NULL, 0}
};

static int keyword_kind(const char *s)
{
    int i;
    for (i = 0; kKw[i].name; i++)
        if (strcmp(kKw[i].name, s) == 0) return kKw[i].kind;
    return T_ID;
}

static void lex_number(Lexer *L, Tok *t)
{
    const char *s = L->s;
    size_t st = L->i, i = L->i, n = L->n;
    int isfloat = 0, hex = 0, badnum = 0;
    int flags = 0;
    char *spell;

    if (s[i] == '0' && i + 1 < n && (s[i + 1] == 'x' || s[i + 1] == 'X')) {
        hex = 1;
        i += 2;
        if (!(i < n && hexval((unsigned char)s[i]) >= 0)) badnum = 1;
        while (i < n && hexval((unsigned char)s[i]) >= 0) i++;
    } else {
        while (i < n && isdigit((unsigned char)s[i])) i++;
        if (i < n && s[i] == '.') {
            isfloat = 1;
            i++;
            while (i < n && isdigit((unsigned char)s[i])) i++;
            if (i < n && s[i] == '.' && i + 1 < n && isdigit((unsigned char)s[i + 1])) {
                lerr(L, L->line, 0, "Too many decimal points");
                i++;
                while (i < n && (isdigit((unsigned char)s[i]) || s[i] == '.')) i++;
            }
        }
        if (i < n && (s[i] == 'e' || s[i] == 'E')) {
            size_t j = i + 1;
            if (j < n && (s[j] == '+' || s[j] == '-')) j++;
            if (j < n && isdigit((unsigned char)s[j])) {
                isfloat = 1;
                i = j;
                while (i < n && isdigit((unsigned char)s[i])) i++;
                if (i < n && (s[i] == 'e' || s[i] == 'E')) {
                    size_t k = i + 1;
                    if (k < n && (s[k] == '+' || s[k] == '-')) k++;
                    if (k < n && isdigit((unsigned char)s[k])) {
                        lerr(L, L->line, 0, "Too many exponents");
                        i = k;
                        while (i < n && isdigit((unsigned char)s[i])) i++;
                    }
                }
            }
        }
    }
    {
        size_t numend = i;
        if (isfloat) {
            if (i < n && (s[i] == 'f' || s[i] == 'F')) { flags |= TF_FSUFFIX; i++; }
            else if (i < n && (s[i] == 'l' || s[i] == 'L')) { flags |= TF_LDOUBLE; i++; }
        } else {
            while (i < n && (s[i] == 'u' || s[i] == 'U' || s[i] == 'l' || s[i] == 'L')) {
                if (s[i] == 'u' || s[i] == 'U') flags |= TF_UNSIGNED; else flags |= TF_LONG;
                i++;
            }
        }
        if (i < n && (isalnum((unsigned char)s[i]) || s[i] == '_')) {
            badnum = 1;
            while (i < n && (isalnum((unsigned char)s[i]) || s[i] == '_')) i++;
        }
        spell = tc_strndup(s + st, i - st);
        if (badnum) lerr(L, L->line, 0, "Incorrect number format");
        t->spell = spell;
        t->text = spell;
        if (isfloat) {
            char *tmp = tc_strndup(s + st, numend - st);
            t->kind = T_FLT;
            t->fval = strtod(tmp, NULL);
        } else {
            char *tmp = tc_strndup(s + st, numend - st);
            unsigned long long v;
            if (hex) v = strtoull(tmp + 2, NULL, 16);
            else if (tmp[0] == '0' && tmp[1]) {
                size_t k;
                for (k = 1; tmp[k]; k++)
                    if (tmp[k] == '8' || tmp[k] == '9') { lerr(L, L->line, 0, "Illegal octal digit"); break; }
                v = strtoull(tmp, NULL, 8);
            } else v = strtoull(tmp, NULL, 10);
            t->kind = T_INT;
            t->ival = (long long)v;
            if (v > 0x7fffffffULL && !(flags & TF_LONG)) flags |= TF_LONG;
            if (v > 0x7fffffffULL && (hex || tmp[0] == '0') && v <= 0xffffffffULL) flags |= TF_UNSIGNED;
        }
        t->flags = flags;
    }
    L->i = i;
}

/* Lexes one real token; problems found on the way are queued in L->pend. */
static void lex_inner(Lexer *L, Tok *t)
{
    const char *s = L->s;
    size_t n = L->n;
    int space = 0;

    memset(t, 0, sizeof *t);
again:
    for (;;) {
        char c;
        if (L->i >= n) break;
        c = s[L->i];
        if (c == '\n') { L->line++; L->bol = 1; L->i++; space = 1; continue; }
        if (c == ' ' || c == '\t' || c == '\r' || c == '\f' || c == '\v' || c == 0x1a) { L->i++; space = 1; continue; }
        if (c == '\\' && L->i + 1 < n && (s[L->i + 1] == '\n' || (s[L->i + 1] == '\r' && L->i + 2 < n && s[L->i + 2] == '\n'))) {
            L->i += (s[L->i + 1] == '\r') ? 3 : 2;
            L->line++;
            space = 1;
            continue;
        }
        if (c == '/' && L->i + 1 < n && s[L->i + 1] == '*') {
            int start = L->line;
            size_t i = L->i + 2;
            int closed = 0;
            while (i < n) {
                if (s[i] == '*' && i + 1 < n && s[i + 1] == '/') { i += 2; closed = 1; break; }
                if (s[i] == '\n') L->line++;
                i++;
            }
            L->i = i;
            space = 1;
            if (!closed) {
                lerr(L, L->line, TF_FATAL, "Unexpected end of file in comment started on line %d", start);
                break;
            }
            continue;
        }
        if (c == '/' && L->i + 1 < n && s[L->i + 1] == '/') {
            size_t i = L->i + 2;
            while (i < n && s[i] != '\n') {
                if (s[i] == '\\' && i + 1 < n && s[i + 1] == '\n') { L->line++; i += 2; continue; }
                i++;
            }
            L->i = i;
            space = 1;
            continue;
        }
        break;
    }
    t->space = space;
    t->bol = L->bol;
    t->line = L->hdr ? L->hdr_line : L->line;
    if (L->hdr) t->flags |= TF_HEADER;

    if (L->i >= n) {
        t->kind = T_EOF;
        return;
    }
    L->bol = 0;

    {
        unsigned char c = (unsigned char)s[L->i];
        size_t i = L->i;

        if (isalpha(c) || c == '_') {
            while (i < n && (isalnum((unsigned char)s[i]) || s[i] == '_')) i++;
            t->kind = T_ID;
            { char *tmp = tc_strndup(s + L->i, i - L->i); t->text = tc_intern(tmp); }
            L->i = i;
            return;
        }
        if (isdigit(c) || (c == '.' && i + 1 < n && isdigit((unsigned char)s[i + 1]))) {
            int line = t->line, fl = t->flags, sp = t->space, bl = t->bol;
            lex_number(L, t);
            t->line = line; t->flags |= fl; t->space = sp; t->bol = bl;
            return;
        }
        if (c == '"') {
            size_t j = i + 1;
            size_t cap = 64, len = 0;
            char *buf = (char *)tc_alloc(cap);
            int closed = 0;
            while (j < n && s[j] != '\n') {
                if (s[j] == '"') { closed = 1; j++; break; }
                if (s[j] == '\\' && j + 1 < n) {
                    int v;
                    j++;
                    if (s[j] == '\n') { L->line++; j++; continue; }
                    v = read_escape(s, n, &j);
                    if (len + 1 >= cap) { char *nb = (char *)tc_alloc(cap * 2); memcpy(nb, buf, len); buf = nb; cap *= 2; }
                    buf[len++] = (char)v;
                    continue;
                }
                if (len + 1 >= cap) { char *nb = (char *)tc_alloc(cap * 2); memcpy(nb, buf, len); buf = nb; cap *= 2; }
                buf[len++] = s[j++];
            }
            if (!closed) lerr(L, t->line, 0, "Unterminated string or character constant");
            buf[len] = 0;
            t->kind = T_STR;
            t->text = buf;
            t->slen = (int)len;
            t->spell = tc_strndup(s + i, j - i);
            L->i = j;
            return;
        }
        if (c == '\'') {
            size_t j = i + 1;
            int count = 0, closed = 0;
            unsigned long long v = 0;
            while (j < n && s[j] != '\n') {
                if (s[j] == '\'') { closed = 1; j++; break; }
                if (s[j] == '\\' && j + 1 < n) { j++; v = (v << 8) | (read_escape(s, n, &j) & 0xff); }
                else { v = (v << 8) | (unsigned char)s[j]; j++; }
                count++;
            }
            if (!closed) lerr(L, t->line, 0, "Unterminated string or character constant");
            else if (count == 0 || count > 2) lerr(L, t->line, 0, "Character constant too long");
            t->kind = T_CHR;
            if (count == 1) { v = (unsigned long long)(long long)(signed char)v; }
            t->ival = (int)v;
            t->spell = tc_strndup(s + i, j - i);
            t->text = t->spell;
            L->i = j;
            return;
        }
        {
            static const struct { const char *op; int kind; } ops[] = {
                {"<<=", T_SHLEQ}, {">>=", T_SHREQ}, {"...", T_ELLIPSIS},
                {"->", T_ARROW}, {"++", T_INC}, {"--", T_DEC}, {"<<", T_SHL}, {">>", T_SHR},
                {"<=", T_LE}, {">=", T_GE}, {"==", T_EQ}, {"!=", T_NE}, {"&&", T_ANDAND},
                {"||", T_OROR}, {"*=", T_MULEQ}, {"/=", T_DIVEQ}, {"%=", T_MODEQ},
                {"+=", T_ADDEQ}, {"-=", T_SUBEQ}, {"&=", T_ANDEQ}, {"^=", T_XOREQ},
                {"|=", T_OREQ}, {"##", T_HASHHASH}, {NULL, 0}
            };
            int k;
            for (k = 0; ops[k].op; k++) {
                size_t len = strlen(ops[k].op);
                if (i + len <= n && strncmp(s + i, ops[k].op, len) == 0) {
                    t->kind = ops[k].kind;
                    t->text = tc_intern(ops[k].op);
                    L->i = i + len;
                    return;
                }
            }
        }
        if (strchr("{}()[];,.:?~!%^&*-+=|/<>#", c)) {
            char tmp[2];
            tmp[0] = (char)c; tmp[1] = 0;
            t->kind = c;
            t->text = tc_intern(tmp);
            L->i = i + 1;
            return;
        }
        /* an illegal character: report it and go on with the next token */
        {
            int line = t->line;
            if (c >= 0x80) {
                char u[3];
                u[0] = (char)(0xC0 | (c >> 6)); u[1] = (char)(0x80 | (c & 0x3f)); u[2] = 0;
                lerr(L, line, 0, "Illegal character '%s' (0x%x)", u, c);
            } else if (c >= 0x20 && c < 0x7f) {
                lerr(L, line, 0, "Illegal character '%c' (0x%x)", c, c);
            } else {
                lerr(L, line, 0, "Illegal character '^%c' (0x%x)", c + '@', c);
            }
            L->i = i + 1;
            space = 1;
            goto again;
        }
    }
}

/* Reads the next token of this lexer: error tokens come before the token they were found with. */
static void lex_raw(Lexer *L, Tok *t)
{
    if (L->pend_i < L->pend.n) { *t = L->pend.v[L->pend_i++]; return; }
    L->pend.n = 0;
    L->pend_i = 0;
    if (L->have_defer) { *t = L->defer; L->have_defer = 0; return; }
    lex_inner(L, t);
    if (L->pend.n > 0) {
        L->defer = *t;
        L->have_defer = 1;
        *t = L->pend.v[L->pend_i++];
    }
}

static Tok *lex_peek(Lexer *L)
{
    if (!L->have_peek) { lex_raw(L, &L->peeked); L->have_peek = 1; }
    return &L->peeked;
}

static void lex_take(Lexer *L, Tok *t)
{
    if (L->have_peek) { *t = L->peeked; L->have_peek = 0; }
    else lex_raw(L, t);
}

/* ================================================================== preprocessor */

typedef struct Macro {
    struct Macro *next;
    char *name;
    int is_func;
    int nparams;
    char **params;
    TokVec body;
    int disabled;
    int line;
} Macro;

typedef struct Cond {
    int active;             /* tokens of the current branch are used */
    int taken;              /* some branch was already used          */
    int seen_else;
    int line;
    int parent_skip;
} Cond;

#define MAXINC 16
#define MTAB 512

typedef struct Pp {
    Lexer *lx[MAXINC];
    int nlx;
    Macro *mtab[MTAB];
    Cond cond[64];
    int ncond;
    int skipping;
    TokVec out;
    const char *included[64];
    int nincl;
    int fatal;
} Pp;

static unsigned mhash(const char *s)
{
    unsigned h = 5381;
    while (*s) h = h * 33 + (unsigned char)*s++;
    return h % MTAB;
}

static Macro *macro_find(Pp *p, const char *name)
{
    Macro *m;
    for (m = p->mtab[mhash(name)]; m; m = m->next)
        if (m->name == name) return m;
    return NULL;
}

static void macro_remove(Pp *p, const char *name)
{
    Macro **pp = &p->mtab[mhash(name)];
    while (*pp) {
        if ((*pp)->name == name) { *pp = (*pp)->next; return; }
        pp = &(*pp)->next;
    }
}

static Tok g_eof;

static Tok *rpeek(Pp *p)
{
    for (;;) {
        Lexer *L;
        Tok *t;
        if (p->nlx == 0) return &g_eof;
        L = p->lx[p->nlx - 1];
        L->quiet = p->skipping;
        t = lex_peek(L);
        if (t->kind == T_EOF && p->nlx > 1) { p->nlx--; continue; }
        return t;
    }
}

static void rnext(Pp *p, Tok *t)
{
    Tok *pk = rpeek(p);
    if (p->nlx == 0) { *t = g_eof; return; }
    *t = *pk;
    p->lx[p->nlx - 1]->have_peek = 0;
}

static void emit_err(Pp *p, int line, int flags, const char *fmt, ...)
{
    Tok t;
    char buf[300];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buf, sizeof buf, fmt, ap);
    va_end(ap);
    memset(&t, 0, sizeof t);
    t.kind = T_ERR;
    t.line = line;
    t.flags = flags;
    t.text = tc_strdup(buf);
    tv_push(&p->out, &t);
}

/* spelling of a token as it would appear in the source */
static void tok_spell(const Tok *t, char *buf, size_t cap)
{
    switch (t->kind) {
    case T_ID: case T_INT: case T_FLT: case T_CHR: case T_STR:
        snprintf(buf, cap, "%s", t->spell ? t->spell : (t->text ? t->text : ""));
        break;
    default:
        if (t->text) snprintf(buf, cap, "%s", t->text);
        else if (t->kind > 0 && t->kind < 256) snprintf(buf, cap, "%c", t->kind);
        else buf[0] = 0;
    }
}

static void relex(const char *text, Tok *t)
{
    Lexer L;
    memset(&L, 0, sizeof L);
    L.s = text; L.n = strlen(text); L.line = 1; L.bol = 0; L.quiet = 1;
    lex_raw(&L, t);
}

/* ---- macro expansion ---- */

static void expand_into(Pp *p, const Tok *in, size_t n, TokVec *dest, int use_raw);

static int tok_same(const Tok *a, const Tok *b)
{
    char x[256], y[256];
    if (a->kind != b->kind) return 0;
    tok_spell(a, x, sizeof x);
    tok_spell(b, y, sizeof y);
    return strcmp(x, y) == 0;
}

static void stringify(const TokVec *arg, Tok *out, int line)
{
    char buf[1024];
    size_t k, len = 0;
    memset(out, 0, sizeof *out);
    for (k = 0; k < arg->n; k++) {
        char sp[256];
        size_t m;
        tok_spell(&arg->v[k], sp, sizeof sp);
        if (k > 0 && arg->v[k].space && len + 1 < sizeof buf) buf[len++] = ' ';
        for (m = 0; sp[m] && len + 2 < sizeof buf; m++) {
            if ((arg->v[k].kind == T_STR || arg->v[k].kind == T_CHR) && (sp[m] == '"' || sp[m] == '\\'))
                buf[len++] = '\\';
            buf[len++] = sp[m];
        }
    }
    buf[len] = 0;
    out->kind = T_STR;
    out->text = tc_strdup(buf);
    out->slen = (int)len;
    {
        char *sp = (char *)tc_alloc(len + 3);
        sp[0] = '"'; memcpy(sp + 1, buf, len); sp[len + 1] = '"'; sp[len + 2] = 0;
        out->spell = sp;
    }
    out->line = line;
}

static void paste_tokens(TokVec *outv, const Tok *right, int line)
{
    char a[256], b[256], c[600];
    Tok r;
    if (outv->n == 0) { tv_push(outv, right); return; }
    tok_spell(&outv->v[outv->n - 1], a, sizeof a);
    tok_spell(right, b, sizeof b);
    snprintf(c, sizeof c, "%s%s", a, b);
    relex(c, &r);
    if (r.kind == T_EOF || r.kind == T_ERR) { tv_push(outv, right); return; }
    r.line = line;
    r.space = outv->v[outv->n - 1].space;
    outv->v[outv->n - 1] = r;
}

static void collect_args(Pp *p, const Tok *in, size_t *pos, size_t n, int use_raw,
                         TokVec *args, int *nargs, int maxargs, int *ok)
{
    /* *pos is the index of '(' in the list (or n when the '(' is still in the raw stream) */
    int depth = 0, cur = 0;
    size_t j = *pos;
    int from_raw = (j >= n);
    *ok = 0;
    memset(args, 0, sizeof(TokVec) * (size_t)maxargs);
    for (;;) {
        Tok t;
        if (!from_raw && j < n) t = in[j++];
        else if (use_raw) {
            from_raw = 1;
            rnext(p, &t);
            if (t.kind == T_EOF) { *nargs = cur + 1; return; }
            if (t.kind == T_ERR) { continue; }
        } else { *nargs = cur + 1; *pos = j; return; }
        if (depth == 0 && t.kind == '(') { depth = 1; continue; }
        if (t.kind == '(') depth++;
        else if (t.kind == ')') {
            depth--;
            if (depth == 0) { *nargs = cur + 1; *pos = j; *ok = 1; return; }
        } else if (t.kind == ',' && depth == 1) {
            if (cur + 1 < maxargs) cur++;
            continue;
        }
        if (cur < maxargs) tv_push(&args[cur], &t);
    }
}

static void expand_macro_call(Pp *p, Macro *m, const Tok *name, TokVec *args, int nargs,
                              TokVec *dest, int use_raw)
{
    TokVec sub;
    TokVec *exp = NULL;
    size_t k;
    int i;
    memset(&sub, 0, sizeof sub);

    if (m->is_func) {
        exp = (TokVec *)tc_alloc(sizeof(TokVec) * (size_t)(m->nparams + 1));
        for (i = 0; i < m->nparams; i++) {
            if (i < nargs) expand_into(p, args[i].v, args[i].n, &exp[i], 0);
        }
    }
    {
        int pending_paste = 0;
        for (k = 0; k < m->body.n; k++) {
            Tok t = m->body.v[k];
            int pi = -1;
            t.line = name->line;
            t.flags |= (name->flags & TF_HEADER);
            t.bol = 0;
            if (m->is_func && t.kind == '#' && k + 1 < m->body.n && m->body.v[k + 1].kind == T_ID) {
                for (i = 0; i < m->nparams; i++)
                    if (m->params[i] == m->body.v[k + 1].text) {
                        Tok s;
                        TokVec empty;
                        memset(&empty, 0, sizeof empty);
                        stringify(i < nargs ? &args[i] : &empty, &s, name->line);
                        if (pending_paste) { paste_tokens(&sub, &s, name->line); pending_paste = 0; }
                        else tv_push(&sub, &s);
                        k++;
                        goto next_body;
                    }
            }
            if (t.kind == T_HASHHASH) { pending_paste = 1; continue; }
            if (m->is_func && t.kind == T_ID) {
                for (i = 0; i < m->nparams; i++)
                    if (m->params[i] == t.text) { pi = i; break; }
            }
            if (pi >= 0) {
                int adj = pending_paste || (k + 1 < m->body.n && m->body.v[k + 1].kind == T_HASHHASH);
                TokVec *src = adj ? &args[pi] : &exp[pi];
                size_t q;
                if (pi >= nargs) src = NULL;
                if (src) {
                    for (q = 0; q < src->n; q++) {
                        Tok u = src->v[q];
                        u.line = name->line;
                        u.flags |= (name->flags & TF_HEADER);
                        u.bol = 0;
                        if (q == 0 && pending_paste) paste_tokens(&sub, &u, name->line);
                        else tv_push(&sub, &u);
                    }
                }
                pending_paste = 0;
            } else {
                if (pending_paste) { paste_tokens(&sub, &t, name->line); pending_paste = 0; }
                else tv_push(&sub, &t);
            }
        next_body:;
        }
    }
    m->disabled++;
    expand_into(p, sub.v, sub.n, dest, use_raw);
    m->disabled--;
}

static void expand_into(Pp *p, const Tok *in, size_t n, TokVec *dest, int use_raw)
{
    size_t i;
    for (i = 0; i < n; i++) {
        Tok t = in[i];
        if (t.kind == T_ID) {
            Macro *m = macro_find(p, t.text);
            if (m && !m->disabled) {
                if (!m->is_func) {
                    Tok nm = t;
                    TokVec none;
                    memset(&none, 0, sizeof none);
                    expand_macro_call(p, m, &nm, &none, 0, dest, use_raw);
                    continue;
                } else {
                    int have_paren = 0;
                    if (i + 1 < n) have_paren = (in[i + 1].kind == '(');
                    else if (use_raw) have_paren = (rpeek(p)->kind == '(');
                    if (have_paren) {
                        TokVec args[16];
                        int nargs = 0, ok = 0;
                        size_t pos = i + 1;
                        collect_args(p, in, &pos, n, use_raw, args, &nargs, 16, &ok);
                        if (!ok) {
                            Tok e;
                            memset(&e, 0, sizeof e);
                            e.kind = T_ERR; e.line = t.line;
                            e.text = tc_strdup("Macro argument syntax error");
                            tv_push(dest, &e);
                            i = pos > 0 ? pos - 1 : 0;
                            continue;
                        }
                        if (m->nparams == 0 && nargs == 1 && args[0].n == 0) nargs = 0;
                        if (nargs != m->nparams) {
                            Tok e;
                            char buf[200];
                            memset(&e, 0, sizeof e);
                            e.kind = T_ERR; e.line = t.line;
                            snprintf(buf, sizeof buf, "Wrong number of arguments in call of macro '%s'", m->name);
                            e.text = tc_strdup(buf);
                            tv_push(dest, &e);
                        } else {
                            expand_macro_call(p, m, &t, args, nargs, dest, use_raw);
                        }
                        i = pos - 1;
                        continue;
                    }
                }
            }
        }
        tv_push(dest, &t);
    }
}

/* ---- directives ---- */

static void recompute_skip(Pp *p)
{
    p->skipping = (p->ncond > 0 && !p->cond[p->ncond - 1].active);
}

/* #if expression evaluation */
typedef struct Ev { const Tok *t; size_t n, i; int bad; } Ev;

static long long ev_cond(Ev *e);

static const Tok *ev_cur(Ev *e) { static Tok eof; return e->i < e->n ? &e->t[e->i] : &eof; }

static long long ev_primary(Ev *e)
{
    const Tok *t = ev_cur(e);
    long long v;
    if (t->kind == T_INT || t->kind == T_CHR) { e->i++; return t->ival; }
    if (t->kind == T_ID) { e->i++; return 0; }
    if (t->kind == '(') {
        e->i++;
        v = ev_cond(e);
        if (ev_cur(e)->kind == ')') e->i++; else e->bad = 1;
        return v;
    }
    e->bad = 1;
    return 0;
}

static long long ev_unary(Ev *e)
{
    int k = ev_cur(e)->kind;
    if (k == '-') { e->i++; return -ev_unary(e); }
    if (k == '+') { e->i++; return ev_unary(e); }
    if (k == '!') { e->i++; return !ev_unary(e); }
    if (k == '~') { e->i++; return ~ev_unary(e); }
    return ev_primary(e);
}

static int ev_prec(int k)
{
    switch (k) {
    case '*': case '/': case '%': return 10;
    case '+': case '-': return 9;
    case T_SHL: case T_SHR: return 8;
    case '<': case '>': case T_LE: case T_GE: return 7;
    case T_EQ: case T_NE: return 6;
    case '&': return 5;
    case '^': return 4;
    case '|': return 3;
    case T_ANDAND: return 2;
    case T_OROR: return 1;
    }
    return 0;
}

static long long ev_bin(Ev *e, int minp)
{
    long long l = ev_unary(e);
    for (;;) {
        int k = ev_cur(e)->kind, pr = ev_prec(k);
        long long r;
        if (pr == 0 || pr < minp) return l;
        e->i++;
        r = ev_bin(e, pr + 1);
        switch (k) {
        case '*': l = l * r; break;
        case '/': l = r ? l / r : 0; break;
        case '%': l = r ? l % r : 0; break;
        case '+': l = l + r; break;
        case '-': l = l - r; break;
        case T_SHL: l = l << (r & 63); break;
        case T_SHR: l = l >> (r & 63); break;
        case '<': l = l < r; break;
        case '>': l = l > r; break;
        case T_LE: l = l <= r; break;
        case T_GE: l = l >= r; break;
        case T_EQ: l = l == r; break;
        case T_NE: l = l != r; break;
        case '&': l = l & r; break;
        case '^': l = l ^ r; break;
        case '|': l = l | r; break;
        case T_ANDAND: l = l && r; break;
        case T_OROR: l = l || r; break;
        }
    }
}

static long long ev_cond(Ev *e)
{
    long long c = ev_bin(e, 1);
    if (ev_cur(e)->kind == '?') {
        long long a, b;
        e->i++;
        a = ev_cond(e);
        if (ev_cur(e)->kind == ':') e->i++; else e->bad = 1;
        b = ev_cond(e);
        return c ? a : b;
    }
    return c;
}

static int eval_if(Pp *p, const Tok *lt, size_t n, int line)
{
    TokVec pre, ex;
    Ev e;
    size_t i;
    long long v;
    memset(&pre, 0, sizeof pre);
    memset(&ex, 0, sizeof ex);
    for (i = 0; i < n; i++) {
        Tok t = lt[i];
        if (t.kind == T_ERR) continue;
        if (t.kind == T_ID && strcmp(t.text, "defined") == 0) {
            int paren = 0;
            Tok r;
            const char *nm = NULL;
            i++;
            if (i < n && lt[i].kind == '(') { paren = 1; i++; }
            if (i < n && lt[i].kind == T_ID) nm = lt[i].text;
            if (paren && i + 1 < n && lt[i + 1].kind == ')') i++;
            memset(&r, 0, sizeof r);
            r.kind = T_INT; r.line = line;
            r.ival = (nm && (macro_find(p, nm) != NULL)) ? 1 : 0;
            tv_push(&pre, &r);
            continue;
        }
        tv_push(&pre, &t);
    }
    expand_into(p, pre.v, pre.n, &ex, 0);
    memset(&e, 0, sizeof e);
    e.t = ex.v; e.n = ex.n;
    v = ev_cond(&e);
    return v != 0;
}

static void define_macro(Pp *p, const Tok *lt, size_t n, int line)
{
    Macro *m;
    size_t i = 1;
    if (n < 2 || lt[1].kind != T_ID) {
        emit_err(p, line, 0, "Define directive needs an identifier");
        return;
    }
    m = (Macro *)tc_alloc(sizeof(Macro));
    m->name = lt[1].text;
    m->line = line;
    i = 2;
    if (i < n && lt[i].kind == '(' && !lt[i].space) {
        char *names[32];
        int np = 0;
        m->is_func = 1;
        i++;
        while (i < n && lt[i].kind != ')') {
            if (lt[i].kind == T_ID && np < 32) names[np++] = lt[i].text;
            else if (lt[i].kind != ',') { emit_err(p, line, 0, "Macro argument syntax error"); return; }
            i++;
        }
        if (i < n) i++;
        m->nparams = np;
        m->params = (char **)tc_alloc(sizeof(char *) * (size_t)(np + 1));
        memcpy(m->params, names, sizeof(char *) * (size_t)np);
    }
    for (; i < n; i++) {
        Tok t = lt[i];
        if (t.kind == T_ERR) continue;
        t.bol = 0;
        tv_push(&m->body, &t);
    }
    if (m->body.n) m->body.v[0].space = 0;
    {
        Macro *old = macro_find(p, m->name);
        if (old) {
            int same = old->is_func == m->is_func && old->nparams == m->nparams && old->body.n == m->body.n;
            size_t k;
            for (k = 0; same && k < old->body.n; k++) if (!tok_same(&old->body.v[k], &m->body.v[k])) same = 0;
            if (!same) emit_err(p, line, TF_WARN, "Redefinition of '%s' is not identical", m->name);
            macro_remove(p, m->name);
        }
    }
    {
        unsigned h = mhash(m->name);
        m->next = p->mtab[h];
        p->mtab[h] = m;
    }
}

static void lower_copy(const char *s, char *out, size_t cap)
{
    size_t i = 0;
    for (; s[i] && i + 1 < cap; i++) {
        char c = s[i];
        out[i] = (c == '\\') ? '/' : (char)tolower((unsigned char)c);
    }
    out[i] = 0;
}

static void do_include(Pp *p, const Tok *lt, size_t n, int line)
{
    char name[128], shown[128], low[128];
    size_t i = 1;
    const char *text;
    int k;
    name[0] = 0;
    if (n >= 2 && lt[1].kind == T_STR) {
        snprintf(name, sizeof name, "%s", lt[1].text);
        snprintf(shown, sizeof shown, "%s", lt[1].text);
    } else if (n >= 2 && lt[1].kind == '<') {
        size_t len = 0;
        int closed = 0;
        for (i = 2; i < n; i++) {
            char sp[128];
            if (lt[i].kind == '>') { closed = 1; break; }
            if (lt[i].kind == T_ERR) continue;
            tok_spell(&lt[i], sp, sizeof sp);
            if (len + strlen(sp) + 1 < sizeof name) { strcpy(name + len, sp); len += strlen(sp); }
        }
        if (!closed) { emit_err(p, line, 0, "Bad file name format in include directive"); return; }
        snprintf(shown, sizeof shown, "%s", name);
    } else {
        emit_err(p, line, 0, "Bad file name format in include directive");
        return;
    }
    if (!name[0]) { emit_err(p, line, 0, "Bad file name format in include directive"); return; }
    lower_copy(name, low, sizeof low);
    for (k = 0; k < p->nincl; k++)
        if (strcmp(p->included[k], low) == 0) return;       /* include guard */
    text = tc_header_text(low);
    if (!text) {
        emit_err(p, line, TF_FATAL, "Unable to open include file '%s'", shown);
        return;
    }
    if (p->nincl < 64) p->included[p->nincl++] = tc_strdup(low);
    if (p->nlx < MAXINC) {
        Lexer *L = (Lexer *)tc_alloc(sizeof(Lexer));
        L->s = text; L->n = strlen(text); L->line = 1; L->bol = 1; L->quiet = 1; L->hdr = 1; L->hdr_line = line;
        p->lx[p->nlx++] = L;
    }
}

static void do_directive(Pp *p, const Tok *hash)
{
    TokVec lt;
    Tok t;
    const char *dn;
    int line = hash->line;
    memset(&lt, 0, sizeof lt);
    for (;;) {
        Tok *pk = rpeek(p);
        if (pk->kind == T_EOF) break;
        if (pk->bol && pk->kind != T_ERR) break;
        rnext(p, &t);
        tv_push(&lt, &t);
    }
    /* error tokens that arrived inside the directive line are real problems: keep them */
    {
        size_t i;
        for (i = 0; i < lt.n; i++)
            if (lt.v[i].kind == T_ERR && !p->skipping) tv_push(&p->out, &lt.v[i]);
    }
    {
        /* drop error tokens from the working list */
        size_t i, w = 0;
        for (i = 0; i < lt.n; i++) if (lt.v[i].kind != T_ERR) lt.v[w++] = lt.v[i];
        lt.n = w;
    }
    if (lt.n == 0) return;                                  /* null directive */
    if (lt.v[0].kind != T_ID) {
        if (!p->skipping) emit_err(p, line, 0, "Unknown preprocessor directive: '%s'", lt.v[0].text ? lt.v[0].text : "?");
        return;
    }
    dn = lt.v[0].text;

    /* conditionals are processed even while skipping */
    if (strcmp(dn, "if") == 0 || strcmp(dn, "ifdef") == 0 || strcmp(dn, "ifndef") == 0) {
        Cond *c;
        int val = 0;
        if (p->ncond >= 64) return;
        c = &p->cond[p->ncond++];
        memset(c, 0, sizeof *c);
        c->line = line;
        c->parent_skip = p->skipping;
        if (!p->skipping) {
            if (strcmp(dn, "if") == 0) val = eval_if(p, lt.v + 1, lt.n - 1, line);
            else {
                if (lt.n < 2 || lt.v[1].kind != T_ID) {
                    emit_err(p, line, 0, "Bad %s directive syntax", dn);
                } else {
                    int def = macro_find(p, lt.v[1].text) != NULL;
                    val = (strcmp(dn, "ifdef") == 0) ? def : !def;
                }
            }
        }
        c->active = !p->skipping && val;
        c->taken = c->active || p->skipping;
        recompute_skip(p);
        return;
    }
    if (strcmp(dn, "elif") == 0) {
        Cond *c;
        if (p->ncond == 0) { emit_err(p, line, 0, "Misplaced elif directive"); return; }
        c = &p->cond[p->ncond - 1];
        if (c->taken) c->active = 0;
        else {
            int val;
            p->skipping = c->parent_skip;
            val = !c->parent_skip && eval_if(p, lt.v + 1, lt.n - 1, line);
            c->active = val;
            if (val) c->taken = 1;
        }
        recompute_skip(p);
        return;
    }
    if (strcmp(dn, "else") == 0) {
        Cond *c;
        if (p->ncond == 0) { emit_err(p, line, 0, "Misplaced else directive"); return; }
        c = &p->cond[p->ncond - 1];
        c->seen_else = 1;
        c->active = !c->taken;
        c->taken = 1;
        recompute_skip(p);
        return;
    }
    if (strcmp(dn, "endif") == 0) {
        if (p->ncond == 0) { emit_err(p, line, 0, "Misplaced endif directive"); return; }
        p->ncond--;
        recompute_skip(p);
        return;
    }
    if (p->skipping) return;

    if (strcmp(dn, "define") == 0) { define_macro(p, lt.v, lt.n, line); return; }
    if (strcmp(dn, "undef") == 0) {
        if (lt.n < 2 || lt.v[1].kind != T_ID) emit_err(p, line, 0, "Bad undef directive syntax");
        else macro_remove(p, lt.v[1].text);
        return;
    }
    if (strcmp(dn, "include") == 0) { do_include(p, lt.v, lt.n, line); return; }
    if (strcmp(dn, "error") == 0) {
        char buf[256];
        size_t i, len = 0;
        buf[0] = 0;
        for (i = 1; i < lt.n; i++) {
            char sp[128];
            tok_spell(&lt.v[i], sp, sizeof sp);
            if (i > 1 && lt.v[i].space && len + 1 < sizeof buf) buf[len++] = ' ';
            if (len + strlen(sp) + 1 < sizeof buf) { strcpy(buf + len, sp); len += strlen(sp); }
        }
        emit_err(p, line, 0, "Error directive: %s", buf);
        return;
    }
    if (strcmp(dn, "pragma") == 0 || strcmp(dn, "line") == 0 || strcmp(dn, "warn") == 0) return;
    emit_err(p, line, 0, "Unknown preprocessor directive: '%s'", dn);
}

static int is_builtin_name(const char *s)
{
    return strcmp(s, "__LINE__") == 0 || strcmp(s, "__FILE__") == 0 ||
           strcmp(s, "__DATE__") == 0 || strcmp(s, "__TIME__") == 0;
}

static void predefine(Pp *p, const char *name, const char *value)
{
    Lexer L;
    Tok t;
    Macro *m = (Macro *)tc_alloc(sizeof(Macro));
    unsigned h;
    memset(&L, 0, sizeof L);
    L.s = value; L.n = strlen(value); L.line = 1; L.quiet = 1;
    m->name = tc_intern(name);
    for (;;) {
        lex_raw(&L, &t);
        if (t.kind == T_EOF) break;
        t.bol = 0;
        tv_push(&m->body, &t);
    }
    h = mhash(m->name);
    m->next = p->mtab[h];
    p->mtab[h] = m;
}

void tc_preprocess(const char *src, PpResult *res)
{
    Pp *p = (Pp *)tc_alloc(sizeof(Pp));
    Lexer *L = (Lexer *)tc_alloc(sizeof(Lexer));
    size_t i, len = strlen(src);
    int lines = 0, last_line = 1, line = 1;
    Tok t;

    L->s = src; L->n = len; L->line = 1; L->bol = 1;
    p->lx[p->nlx++] = L;

    predefine(p, "__MSDOS__", "1");
    predefine(p, "MSDOS", "1");
    predefine(p, "__TURBOC__", "0x0300");
    predefine(p, "__TCPLUSPLUS__", "0x0200");
    predefine(p, "__SMALL__", "1");
    predefine(p, "__STDC__", "1");
    predefine(p, "_Windows_never_defined_", "0");
    macro_remove(p, tc_intern("_Windows_never_defined_"));

    for (;;) {
        rnext(p, &t);
        if (t.kind == T_EOF) break;
        if (t.kind == '#' && t.bol) { do_directive(p, &t); continue; }
        if (t.kind == T_ERR) {
            if (!p->skipping) tv_push(&p->out, &t);
            continue;
        }
        if (p->skipping) continue;
        if (t.kind == T_ID && is_builtin_name(t.text)) {
            Tok r;
            memset(&r, 0, sizeof r);
            r.line = t.line;
            if (strcmp(t.text, "__LINE__") == 0) {
                char b[16];
                r.kind = T_INT; r.ival = t.line;
                snprintf(b, sizeof b, "%d", t.line);
                r.spell = r.text = tc_strdup(b);
            } else {
                const char *v = strcmp(t.text, "__FILE__") == 0 ? TC_FILE_NAME :
                                strcmp(t.text, "__DATE__") == 0 ? "Jan  1 2000" : "00:00:00";
                char b[64];
                r.kind = T_STR; r.text = tc_strdup(v); r.slen = (int)strlen(v);
                snprintf(b, sizeof b, "\"%s\"", v);
                r.spell = tc_strdup(b);
            }
            tv_push(&p->out, &r);
            continue;
        }
        if (t.kind == T_ID) {
            Macro *m = macro_find(p, t.text);
            if (m) {
                expand_into(p, &t, 1, &p->out, 1);
                continue;
            }
        }
        tv_push(&p->out, &t);
    }
    if (p->ncond > 0)
        emit_err(p, p->cond[p->ncond - 1].line, TF_FATAL, "Unexpected end of file in conditional started on line %d",
                 p->cond[p->ncond - 1].line);

    /* line numbers */
    for (i = 0; i < len; i++) {
        if (src[i] == '\n') { line++; }
        if (!isspace((unsigned char)src[i]) && src[i] != 0x1a) last_line = line;
    }
    lines = line - ((len > 0 && src[len - 1] == '\n') ? 1 : 0);
    if (lines < 1) lines = 1;
    if (last_line > lines) last_line = lines;

    /* keywords */
    for (i = 0; i < p->out.n; i++)
        if (p->out.v[i].kind == T_ID) p->out.v[i].kind = keyword_kind(p->out.v[i].text);

    memset(&t, 0, sizeof t);
    t.kind = T_EOF;
    t.line = last_line;
    tv_push(&p->out, &t);
    res->toks = p->out;
    res->lines = lines;
    res->last_line = last_line;
}
