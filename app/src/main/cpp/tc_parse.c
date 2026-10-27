/* Turbo C front end, part 2: parser and checker.
 *
 * A one-pass recursive descent parser for C (C89 plus the Borland keywords) that keeps types and
 * scopes, and prints the diagnostics Turbo C++ 3.0 prints for the same mistakes.  It never
 * generates code: the program is run afterwards by the interpreter.
 *
 * When this checker is unsure whether something is wrong it stays silent, so that a correct
 * program is never refused.
 */
#include <ctype.h>
#include <setjmp.h>
#include <stdarg.h>
#include <stdlib.h>
#include <string.h>
#include "tc_int.h"

/* ================================================================== types */

typedef struct Type Type;
typedef struct Tag Tag;

typedef struct Param { char *name; Type *type; } Param;
typedef struct Member { char *name; Type *type; struct Member *next; } Member;

enum { TY_VOID = 1, TY_CHAR, TY_SHORT, TY_INT, TY_LONG, TY_FLOAT, TY_DOUBLE, TY_LDOUBLE,
       TY_PTR, TY_ARRAY, TY_FUNC, TY_STRUCT, TY_UNION, TY_ENUM, TY_ERR };

struct Tag {
    struct Tag *next;
    char *name;
    int kind;               /* TY_STRUCT, TY_UNION, TY_ENUM */
    int complete;
    Member *members;
    int nmembers;
    int all_scalar;         /* every member is a scalar (used to count initializers) */
};

struct Type {
    int kind;
    int uns;
    int cv;                 /* 1 const, 2 volatile */
    Type *base;             /* pointee, element or return type */
    long len;               /* array length or -1 */
    Param *params;
    int nparams;
    int proto;
    int variadic;
    Tag *tag;
};

static Type *t_void, *t_char, *t_uchar, *t_short, *t_ushort, *t_int, *t_uint, *t_long, *t_ulong,
            *t_float, *t_double, *t_ldouble, *t_err;

static Type *ty_new(int kind)
{
    Type *t = (Type *)tc_alloc(sizeof(Type));
    t->kind = kind;
    t->len = -1;
    return t;
}

static void types_init(void)
{
    t_void = ty_new(TY_VOID);
    t_char = ty_new(TY_CHAR);
    t_uchar = ty_new(TY_CHAR); t_uchar->uns = 1;
    t_short = ty_new(TY_SHORT);
    t_ushort = ty_new(TY_SHORT); t_ushort->uns = 1;
    t_int = ty_new(TY_INT);
    t_uint = ty_new(TY_INT); t_uint->uns = 1;
    t_long = ty_new(TY_LONG);
    t_ulong = ty_new(TY_LONG); t_ulong->uns = 1;
    t_float = ty_new(TY_FLOAT);
    t_double = ty_new(TY_DOUBLE);
    t_ldouble = ty_new(TY_LDOUBLE);
    t_err = ty_new(TY_ERR);
}

static Type *ty_ptr(Type *base) { Type *t = ty_new(TY_PTR); t->base = base; return t; }
static Type *ty_array(Type *base, long len) { Type *t = ty_new(TY_ARRAY); t->base = base; t->len = len; return t; }

static Type *ty_qual(Type *t, int cv)
{
    Type *c;
    if (!cv || (t->cv & cv) == cv) return t;
    c = (Type *)tc_alloc(sizeof(Type));
    *c = *t;
    c->cv |= cv;
    return c;
}

static int is_int_t(const Type *t) { return t->kind == TY_CHAR || t->kind == TY_SHORT || t->kind == TY_INT || t->kind == TY_LONG || t->kind == TY_ENUM; }
static int is_flt_t(const Type *t) { return t->kind == TY_FLOAT || t->kind == TY_DOUBLE || t->kind == TY_LDOUBLE; }
static int is_arith_t(const Type *t) { return is_int_t(t) || is_flt_t(t); }
static int is_ptr_t(const Type *t) { return t->kind == TY_PTR; }
static int is_agg_t(const Type *t) { return t->kind == TY_STRUCT || t->kind == TY_UNION; }
static int is_scalar_t(const Type *t) { return is_arith_t(t) || is_ptr_t(t); }
static int is_err_t(const Type *t) { return t->kind == TY_ERR; }

/* arrays and functions turn into pointers when used as values */
static Type *decay(Type *t)
{
    if (t->kind == TY_ARRAY) return ty_ptr(t->base);
    if (t->kind == TY_FUNC) return ty_ptr(t);
    return t;
}

static int ty_rank(const Type *t)
{
    switch (t->kind) {
    case TY_LDOUBLE: return 7;
    case TY_DOUBLE: return 6;
    case TY_FLOAT: return 5;
    case TY_LONG: return t->uns ? 4 : 3;
    case TY_INT: case TY_ENUM: return t->uns ? 2 : 1;
    default: return 1;
    }
}

static Type *arith_conv(Type *a, Type *b)
{
    int ra = ty_rank(a), rb = ty_rank(b), r = ra > rb ? ra : rb;
    switch (r) {
    case 7: return t_ldouble;
    case 6: return t_double;
    case 5: return t_float;
    case 4: return t_ulong;
    case 3: return t_long;
    case 2: return t_uint;
    default: return t_int;
    }
}

static int ty_same(const Type *a, const Type *b)
{
    if (a == b) return 1;
    if (a->kind != b->kind) return 0;
    switch (a->kind) {
    case TY_CHAR: case TY_SHORT: case TY_INT: case TY_LONG: return a->uns == b->uns;
    case TY_PTR: return ty_same(a->base, b->base);
    case TY_ARRAY: return ty_same(a->base, b->base) && (a->len == b->len || a->len < 0 || b->len < 0);
    case TY_STRUCT: case TY_UNION: case TY_ENUM: return a->tag == b->tag;
    case TY_FUNC: {
        int i;
        if (!ty_same(a->base, b->base)) return 0;
        if (a->proto && b->proto) {
            if (a->nparams != b->nparams || a->variadic != b->variadic) return 0;
            for (i = 0; i < a->nparams; i++) if (!ty_same(a->params[i].type, b->params[i].type)) return 0;
        }
        return 1;
    }
    default: return 1;
    }
}

static long ty_size(const Type *t)
{
    switch (t->kind) {
    case TY_CHAR: return 1;
    case TY_SHORT: case TY_INT: case TY_ENUM: return 2;
    case TY_LONG: case TY_FLOAT: return 4;
    case TY_DOUBLE: return 8;
    case TY_LDOUBLE: return 10;
    case TY_PTR: return 2;
    case TY_ARRAY: return t->len < 0 ? 0 : t->len * ty_size(t->base);
    case TY_STRUCT: case TY_UNION: {
        long s = 0;
        Member *m;
        if (!t->tag) return 0;
        for (m = t->tag->members; m; m = m->next) {
            long ms = ty_size(m->type);
            if (t->kind == TY_UNION) { if (ms > s) s = ms; } else s += ms;
        }
        return s;
    }
    default: return 1;
    }
}

static void ty_name(const Type *t, char *buf, size_t cap)
{
    char inner[200];
    const char *q = "";
    switch (t->kind) {
    case TY_VOID: snprintf(buf, cap, "void"); return;
    case TY_CHAR: snprintf(buf, cap, "%schar", t->uns ? "unsigned " : ""); return;
    case TY_SHORT: snprintf(buf, cap, "%sshort", t->uns ? "unsigned " : ""); return;
    case TY_INT: snprintf(buf, cap, "%s", t->uns ? "unsigned int" : "int"); return;
    case TY_LONG: snprintf(buf, cap, "%slong", t->uns ? "unsigned " : ""); return;
    case TY_FLOAT: snprintf(buf, cap, "float"); return;
    case TY_DOUBLE: snprintf(buf, cap, "double"); return;
    case TY_LDOUBLE: snprintf(buf, cap, "long double"); return;
    case TY_STRUCT: snprintf(buf, cap, "struct %s", t->tag && t->tag->name ? t->tag->name : ""); return;
    case TY_UNION: snprintf(buf, cap, "union %s", t->tag && t->tag->name ? t->tag->name : ""); return;
    case TY_ENUM: snprintf(buf, cap, "enum %s", t->tag && t->tag->name ? t->tag->name : ""); return;
    case TY_PTR:
        ty_name(t->base, inner, sizeof inner);
        if (t->base->kind == TY_PTR) snprintf(buf, cap, "%s*", inner);
        else snprintf(buf, cap, "%s *", inner);
        (void)q;
        return;
    case TY_ARRAY:
        ty_name(t->base, inner, sizeof inner);
        if (t->len >= 0) snprintf(buf, cap, "%s [%ld]", inner, t->len);
        else snprintf(buf, cap, "%s []", inner);
        return;
    case TY_FUNC:
        ty_name(t->base, inner, sizeof inner);
        snprintf(buf, cap, "%s ()", inner);
        return;
    default: snprintf(buf, cap, "int"); return;
    }
}

/* ================================================================== symbols and scopes */

enum { SY_VAR = 1, SY_FUNC, SY_TYPEDEF, SY_ENUMC };
enum { SC_NONE = 0, SC_TYPEDEF, SC_EXTERN, SC_STATIC, SC_AUTO, SC_REGISTER };

typedef struct Sym {
    struct Sym *hnext;      /* hash chain */
    struct Sym *snext;      /* locals of a scope, in declaration order */
    char *name;
    int kind;
    Type *type;
    int storage;
    int line;
    int is_lib;
    int is_local, is_param;
    int defined;            /* function has a body / variable has a definition */
    int implicit;           /* function made up from a call */
    int has_init;
    int ref, read, assigned, addr, defined_val, warned_use;
    int linkref;            /* referenced from code: must exist at link time */
    long long enumval;
    int reported;
} Sym;

#define NBUCKET 128

typedef struct Scope {
    struct Scope *parent;
    Sym *buckets[NBUCKET];
    Sym *first, *last;      /* locals in declaration order */
    Tag *tags;
    int is_global;
} Scope;

static unsigned shash(const char *s)
{
    unsigned h = 5381;
    while (*s) h = h * 33 + (unsigned char)*s++;
    return h % NBUCKET;
}

#define MAXCTL 64
#define MAXLAB 64
#define MAXCASE 256

typedef struct Label { char *name; int line; int defined; int used_line; } Label;

typedef struct Ctl {
    int is_switch;
    int has_break;
    int has_default;
    long long cases[MAXCASE];
    int ncases;
} Ctl;

typedef struct Ex {
    Type *t;
    int lval;               /* designates an object */
    int cnst;               /* integer constant expression */
    long long cv;
    int side;               /* has a side effect */
    int is_assign;          /* top operator is a plain = */
    int paren;
    int line;
    Sym *sym;               /* plain identifier */
    int is_str;
} Ex;

typedef struct P {
    Tok *t;
    size_t pos, n;
    int last_line;
    FILE *out;
    int errors, warnings;
    int printed_header;
    Scope *scope, *global;
    int in_fn;
    char *fn_name;
    Sym *fn_sym;
    Type *fn_ret;
    int fn_ret_void;
    int fn_has_retval, fn_has_retvoid;
    int in_hdr;
    int float_used;
    int stop;
    jmp_buf jb;
    jmp_buf top;
    Ctl ctl[MAXCTL];
    int nctl;
    Label labs[MAXLAB];
    int nlabs;
    int last_jump;
    Sym *refs[512];
    int nrefs;
    int has_main;
    int nsilent;
    int eof_err;
} P;

static Scope *scope_new(P *p, int global)
{
    Scope *s = (Scope *)tc_alloc(sizeof(Scope));
    s->parent = p->scope;
    s->is_global = global;
    return s;
}

static Sym *scope_find(Scope *s, const char *name)
{
    Sym *y;
    for (y = s->buckets[shash(name)]; y; y = y->hnext)
        if (y->name == name) return y;
    return NULL;
}

static Sym *lookup(P *p, const char *name)
{
    Scope *s;
    for (s = p->scope; s; s = s->parent) {
        Sym *y = scope_find(s, name);
        if (y) return y;
    }
    return NULL;
}

static Sym *sym_add(P *p, Scope *s, const char *name, int kind, Type *t)
{
    Sym *y = (Sym *)tc_alloc(sizeof(Sym));
    unsigned h = shash(name);
    y->name = (char *)name;
    y->kind = kind;
    y->type = t;
    y->is_lib = p->in_hdr;
    y->hnext = s->buckets[h];
    s->buckets[h] = y;
    if (!s->is_global) {
        if (s->last) s->last->snext = y; else s->first = y;
        s->last = y;
    }
    return y;
}

static Tag *tag_find_scope(Scope *s, const char *name)
{
    Tag *t;
    for (t = s->tags; t; t = t->next) if (t->name && t->name == name) return t;
    return NULL;
}

static Tag *tag_lookup(P *p, const char *name)
{
    Scope *s;
    for (s = p->scope; s; s = s->parent) {
        Tag *t = tag_find_scope(s, name);
        if (t) return t;
    }
    return NULL;
}

/* ================================================================== diagnostics */

static void put_line(P *p, const char *kind, int line, const char *msg, int suffix)
{
    if (!p->printed_header) {
        fprintf(p->out, "Compiling %s:\n", TC_FILE_NAME);
        p->printed_header = 1;
    }
    if (suffix && p->in_fn && p->fn_name)
        fprintf(p->out, "%s %s %d: %s in function %s\n", kind, TC_FILE_NAME, line, msg, p->fn_name);
    else
        fprintf(p->out, "%s %s %d: %s\n", kind, TC_FILE_NAME, line, msg);
}

static void stop_compile(P *p) { p->stop = 1; longjmp(p->top, 1); }

static void check_limits(P *p, int line)
{
    if (p->errors >= 25 || p->warnings >= 100) {
        put_line(p, "Error", line, "Too many error or warning messages", 0);
        p->errors++;
        stop_compile(p);
    }
}

static void err_at(P *p, int line, const char *fmt, ...)
{
    char buf[400];
    va_list ap;
    if (p->in_hdr || p->nsilent) return;
    va_start(ap, fmt);
    vsnprintf(buf, sizeof buf, fmt, ap);
    va_end(ap);
    put_line(p, "Error", line, buf, 1);
    p->errors++;
    check_limits(p, line);
}

static void warn_at(P *p, int line, const char *fmt, ...)
{
    char buf[400];
    va_list ap;
    if (p->in_hdr || p->nsilent) return;
    va_start(ap, fmt);
    vsnprintf(buf, sizeof buf, fmt, ap);
    va_end(ap);
    put_line(p, "Warning", line, buf, 1);
    p->warnings++;
    check_limits(p, line);
}

/* ================================================================== token access */

static Tok *cur(P *p)
{
    for (;;) {
        Tok *t = &p->t[p->pos];
        if (t->kind != T_ERR) return t;
        if (!(t->flags & TF_REPORTED)) {
            t->flags |= TF_REPORTED;
            if (t->flags & TF_WARN) warn_at(p, t->line, "%s", t->text);
            else err_at(p, t->line, "%s", t->text);
            if (t->flags & TF_FATAL) stop_compile(p);
        }
        p->pos++;
    }
}

/* k-th token ahead (0 = current), skipping error tokens, without reporting anything */
static Tok *peekn(P *p, int k)
{
    size_t i = p->pos;
    for (;;) {
        Tok *t = &p->t[i];
        if (t->kind == T_EOF) return t;
        if (t->kind != T_ERR) {
            if (k == 0) return t;
            k--;
        }
        i++;
    }
}

static void adv(P *p)
{
    Tok *t = cur(p);
    if (t->kind != T_EOF) p->pos++;
}

static int is(P *p, int kind) { return cur(p)->kind == kind; }

static int accept(P *p, int kind)
{
    if (is(p, kind)) { adv(p); return 1; }
    return 0;
}

static int cur_line(P *p) { return cur(p)->line; }

/* a hard syntax error: report and unwind to the nearest statement / declaration */
static void hard_error(P *p, const char *msg)
{
    err_at(p, cur_line(p), "%s", msg);
    longjmp(p->jb, 1);
}

/* skip to the end of the broken statement */
static void sync(P *p)
{
    int depth = 0;
    for (;;) {
        Tok *t = cur(p);
        if (t->kind == T_EOF) return;
        if (t->kind == '{') depth++;
        else if (t->kind == '}') {
            if (depth == 0) return;
            depth--;
            if (depth == 0) { adv(p); return; }
            adv(p);
            continue;
        } else if (t->kind == ';' && depth == 0) { adv(p); return; }
        adv(p);
    }
}

/* ================================================================== expressions */

typedef struct Spec { Type *type; int storage; int any; } Spec;

static int starts_type(P *p);
static int parse_specifiers(P *p, Spec *sp);
static Type *parse_type_name(P *p);
static Ex parse_assign(P *p);
static Ex parse_expr(P *p);
static Ex parse_cond(P *p);
static Ex parse_unary(P *p);

static Ex ex_make(Type *t, int line)
{
    Ex e;
    memset(&e, 0, sizeof e);
    e.t = t;
    e.line = line;
    return e;
}

static Type *func_type(Type *ret)
{
    Type *t = ty_new(TY_FUNC);
    t->base = ret;
    return t;
}

static void note_ref(P *p, Sym *s)
{
    s->ref = 1;
    if (!s->is_local && !s->linkref && p->nrefs < 512 && (s->kind == SY_FUNC || s->kind == SY_VAR)) {
        s->linkref = 1;
        p->refs[p->nrefs++] = s;
    }
}

static int tracked_scalar(const Sym *s)
{
    return s->is_local && !s->is_param && s->kind == SY_VAR && is_scalar_t(s->type) &&
           s->storage != SC_STATIC && s->storage != SC_EXTERN;
}

static void note_read(P *p, Sym *s, int line)
{
    s->ref = 1;
    s->read = 1;
    if (tracked_scalar(s) && !s->defined_val && !s->warned_use) {
        s->warned_use = 1;
        warn_at(p, line, "Possible use of '%s' before definition", s->name);
    }
}

/* the expression is used for its value */
static void use_val(P *p, Ex *e)
{
    if (is_flt_t(e->t)) p->float_used = 1;
    if (e->sym && e->sym->kind == SY_VAR) note_read(p, e->sym, e->line);
}

static void note_assign(Ex *e)
{
    if (e->sym && e->sym->kind == SY_VAR) { e->sym->assigned = 1; e->sym->defined_val = 1; }
}

/* result of the cast/assignment rules between two types */
enum { CV_ASSIGN, CV_INIT, CV_RETURN, CV_ARG };

static void conv_fail(P *p, int ctx, int line, Type *to, Type *from, Sym *fn, int argno, const char *pname)
{
    char a[160], b[160];
    ty_name(from, a, sizeof a);
    ty_name(to, b, sizeof b);
    if (ctx == CV_ARG) {
        if (fn && pname && pname[0]) err_at(p, line, "Type mismatch in parameter '%s' in call to '%s'", pname, fn->name);
        else if (fn) err_at(p, line, "Type mismatch in parameter %d in call to '%s'", argno, fn->name);
        else err_at(p, line, "Type mismatch in parameter %d", argno);
    } else {
        err_at(p, line, "Cannot convert '%s' to '%s'", a, b);
    }
}

static void conv_check(P *p, Type *to, const Ex *from, int ctx, int line, Sym *fn, int argno, const char *pname)
{
    Type *F = decay(from->t), *T = to;
    if (is_err_t(F) || is_err_t(T)) return;
    if (T->kind == TY_VOID || T->kind == TY_ARRAY || T->kind == TY_FUNC) return;
    if (F->kind == TY_VOID) { conv_fail(p, ctx, line, T, F, fn, argno, pname); return; }
    if (is_arith_t(T)) {
        if (is_arith_t(F)) {
            if (F->kind == TY_LONG && (T->kind == TY_INT || T->kind == TY_SHORT || T->kind == TY_CHAR) && !from->cnst)
                warn_at(p, line, "Conversion may lose significant digits");
            return;
        }
        if (is_ptr_t(F)) {
            if (is_int_t(T)) warn_at(p, line, "Non-portable pointer conversion");
            else conv_fail(p, ctx, line, T, F, fn, argno, pname);
            return;
        }
        conv_fail(p, ctx, line, T, F, fn, argno, pname);
        return;
    }
    if (is_ptr_t(T)) {
        if (is_ptr_t(F)) {
            Type *a = T->base, *b = F->base;
            if (a->kind == TY_VOID || b->kind == TY_VOID) return;
            if (a->kind == TY_FUNC || b->kind == TY_FUNC) return;
            if (ty_same(a, b)) return;
            if (is_int_t(a) && is_int_t(b) && a->kind == b->kind) return;
            warn_at(p, line, "Suspicious pointer conversion");
            return;
        }
        if (is_int_t(F)) {
            if (from->cnst && from->cv == 0) return;
            warn_at(p, line, "Non-portable pointer conversion");
            return;
        }
        conv_fail(p, ctx, line, T, F, fn, argno, pname);
        return;
    }
    if (is_agg_t(T)) {
        if (is_agg_t(F) && F->tag == T->tag) return;
        conv_fail(p, ctx, line, T, F, fn, argno, pname);
        return;
    }
}

static int is_modifiable(const Ex *e)
{
    if (!e->lval) return 0;
    if (e->t->kind == TY_ARRAY || e->t->kind == TY_FUNC) return 0;
    return 1;
}

static int check_modifiable(P *p, const Ex *e, int line)
{
    if (is_err_t(e->t)) return 0;
    if (!is_modifiable(e)) { err_at(p, line, "Lvalue required"); return 0; }
    if (e->t->cv & 1) { err_at(p, line, "Cannot modify a const object"); return 0; }
    return 1;
}

static long long fold(int op, long long a, long long b, int *ok)
{
    *ok = 1;
    switch (op) {
    case '*': return a * b;
    case '/': if (b == 0) { *ok = 0; return 0; } return a / b;
    case '%': if (b == 0) { *ok = 0; return 0; } return a % b;
    case '+': return a + b;
    case '-': return a - b;
    case T_SHL: return a << (b & 31);
    case T_SHR: return a >> (b & 31);
    case '<': return a < b;
    case '>': return a > b;
    case T_LE: return a <= b;
    case T_GE: return a >= b;
    case T_EQ: return a == b;
    case T_NE: return a != b;
    case '&': return a & b;
    case '^': return a ^ b;
    case '|': return a | b;
    case T_ANDAND: return a && b;
    case T_OROR: return a || b;
    }
    *ok = 0;
    return 0;
}

static int bin_prec(int k)
{
    switch (k) {
    case T_OROR: return 1;
    case T_ANDAND: return 2;
    case '|': return 3;
    case '^': return 4;
    case '&': return 5;
    case T_EQ: case T_NE: return 6;
    case '<': case '>': case T_LE: case T_GE: return 7;
    case T_SHL: case T_SHR: return 8;
    case '+': case '-': return 9;
    case '*': case '/': case '%': return 10;
    }
    return 0;
}

/* result of  l op r  (both already used as values) */
static Ex binop(P *p, int op, Ex l, Ex r, int line)
{
    Ex e = ex_make(t_int, line);
    Type *lt = decay(l.t), *rt = decay(r.t);
    int is_rel = (op == '<' || op == '>' || op == T_LE || op == T_GE || op == T_EQ || op == T_NE);
    int is_log = (op == T_ANDAND || op == T_OROR);
    e.side = l.side || r.side;
    if (is_err_t(lt) || is_err_t(rt)) { e.t = (is_rel || is_log) ? t_int : t_err; return e; }

    if (lt->kind == TY_VOID || rt->kind == TY_VOID) {
        err_at(p, line, "Not an allowed type");
        e.t = t_err;
        return e;
    }
    if (is_agg_t(lt) || is_agg_t(rt)) {
        err_at(p, line, "Illegal structure operation");
        e.t = t_err;
        return e;
    }
    if (is_log) { e.t = t_int; if (l.cnst && r.cnst) { int ok; e.cnst = 1; e.cv = fold(op, l.cv, r.cv, &ok); } return e; }

    if (is_rel) {
        if (is_ptr_t(lt) || is_ptr_t(rt)) {
            if (is_ptr_t(lt) && is_ptr_t(rt)) {
                if (!(lt->base->kind == TY_VOID || rt->base->kind == TY_VOID || ty_same(lt->base, rt->base) ||
                      (is_int_t(lt->base) && is_int_t(rt->base) && lt->base->kind == rt->base->kind)))
                    warn_at(p, line, "Suspicious pointer conversion");
            } else if (is_int_t(lt) && !(l.cnst && l.cv == 0)) warn_at(p, line, "Non-portable pointer comparison");
            else if (is_int_t(rt) && !(r.cnst && r.cv == 0)) warn_at(p, line, "Non-portable pointer comparison");
            else if (is_flt_t(lt) || is_flt_t(rt)) err_at(p, line, "Illegal use of floating point");
        } else if (l.cnst && r.cnst) {
            int ok;
            e.cnst = 1;
            e.cv = fold(op, l.cv, r.cv, &ok);
        }
        e.t = t_int;
        return e;
    }

    switch (op) {
    case '*': case '/': case '%':
        if (is_ptr_t(lt) || is_ptr_t(rt)) { err_at(p, line, "Illegal use of pointer"); e.t = t_err; return e; }
        if (op == '%' && (is_flt_t(lt) || is_flt_t(rt))) { err_at(p, line, "Illegal use of floating point"); e.t = t_err; return e; }
        if ((op == '/' || op == '%') && r.cnst && r.cv == 0 && is_int_t(rt)) warn_at(p, line, "Division by zero");
        e.t = arith_conv(lt, rt);
        if (l.cnst && r.cnst && is_int_t(lt) && is_int_t(rt)) {
            int ok;
            long long v = fold(op, l.cv, r.cv, &ok);
            if (ok) { e.cnst = 1; e.cv = v; }
        }
        return e;
    case '+':
        if (is_ptr_t(lt) && is_ptr_t(rt)) { err_at(p, line, "Invalid pointer addition"); e.t = t_err; return e; }
        if (is_ptr_t(lt)) { if (is_flt_t(rt)) err_at(p, line, "Illegal use of floating point"); e.t = lt; return e; }
        if (is_ptr_t(rt)) { if (is_flt_t(lt)) err_at(p, line, "Illegal use of floating point"); e.t = rt; return e; }
        e.t = arith_conv(lt, rt);
        if (l.cnst && r.cnst && is_int_t(lt) && is_int_t(rt)) { int ok; e.cnst = 1; e.cv = fold(op, l.cv, r.cv, &ok); }
        return e;
    case '-':
        if (is_ptr_t(lt) && is_ptr_t(rt)) { e.t = t_int; return e; }
        if (is_ptr_t(lt)) { if (is_flt_t(rt)) err_at(p, line, "Illegal use of floating point"); e.t = lt; return e; }
        if (is_ptr_t(rt)) { err_at(p, line, "Illegal pointer subtraction"); e.t = t_err; return e; }
        e.t = arith_conv(lt, rt);
        if (l.cnst && r.cnst && is_int_t(lt) && is_int_t(rt)) { int ok; e.cnst = 1; e.cv = fold(op, l.cv, r.cv, &ok); }
        return e;
    case T_SHL: case T_SHR: case '&': case '|': case '^':
        if (is_ptr_t(lt) || is_ptr_t(rt)) { err_at(p, line, "Illegal use of pointer"); e.t = t_err; return e; }
        if (is_flt_t(lt) || is_flt_t(rt)) { err_at(p, line, "Illegal use of floating point"); e.t = t_err; return e; }
        e.t = (op == T_SHL || op == T_SHR) ? arith_conv(lt, t_int) : arith_conv(lt, rt);
        if (l.cnst && r.cnst) { int ok; e.cnst = 1; e.cv = fold(op, l.cv, r.cv, &ok); }
        return e;
    }
    return e;
}

static Ex parse_primary(P *p)
{
    Tok *t = cur(p);
    int line = t->line;
    Ex e = ex_make(t_int, line);

    switch (t->kind) {
    case T_INT:
        if (t->flags & TF_LONG) e.t = (t->flags & TF_UNSIGNED) ? t_ulong : t_long;
        else e.t = (t->flags & TF_UNSIGNED) ? t_uint : t_int;
        e.cnst = 1;
        e.cv = t->ival;
        adv(p);
        return e;
    case T_FLT:
        e.t = (t->flags & TF_FSUFFIX) ? t_float : (t->flags & TF_LDOUBLE) ? t_ldouble : t_double;
        p->float_used = 1;
        adv(p);
        return e;
    case T_CHR:
        e.cnst = 1;
        e.cv = t->ival;
        adv(p);
        return e;
    case T_STR: {
        long len = 0;
        while (is(p, T_STR)) { len += cur(p)->slen; adv(p); }
        e.t = ty_array(t_char, len + 1);
        e.lval = 1;
        e.is_str = 1;
        return e;
    }
    case T_ID: {
        Sym *s = lookup(p, t->text);
        char *name = t->text;
        if (!s) {
            if (peekn(p, 1)->kind == '(') {
                Type *ft = func_type(t_int);
                s = sym_add(p, p->global, name, SY_FUNC, ft);
                s->implicit = 1;
                s->line = line;
                s->is_lib = 0;
            } else {
                err_at(p, line, "Undefined symbol '%s'", name);
                adv(p);
                e.t = t_err;
                e.lval = 1;
                return e;
            }
        }
        adv(p);
        switch (s->kind) {
        case SY_ENUMC:
            e.cnst = 1;
            e.cv = s->enumval;
            s->ref = 1;
            return e;
        case SY_TYPEDEF:
            err_at(p, line, "Expression syntax");
            longjmp(p->jb, 1);
        case SY_FUNC:
            note_ref(p, s);
            e.t = s->type;
            e.sym = s;
            return e;
        default:
            note_ref(p, s);
            e.t = s->type;
            e.lval = 1;
            e.sym = s;
            return e;
        }
    }
    case '(': {
        adv(p);
        e = parse_expr(p);
        if (!is(p, ')')) hard_error(p, "Expression syntax");
        adv(p);
        e.paren = 1;
        return e;
    }
    default:
        hard_error(p, "Expression syntax");
    }
    return e;
}

static Member *find_member(Tag *tag, const char *name)
{
    Member *m;
    for (m = tag->members; m; m = m->next) if (m->name == name) return m;
    return NULL;
}

static Ex parse_postfix(P *p)
{
    Ex e = parse_primary(p);
    for (;;) {
        Tok *t = cur(p);
        int line = t->line;
        if (t->kind == '[') {
            Ex idx;
            Type *bt = decay(e.t);
            adv(p);
            use_val(p, &e);
            idx = parse_expr(p);
            use_val(p, &idx);
            if (!is(p, ']')) hard_error(p, "Expression syntax");
            adv(p);
            {
                Type *it = decay(idx.t);
                Ex r = ex_make(t_err, line);
                if (is_err_t(bt) || is_err_t(it)) { e = r; r.lval = 1; e.lval = 1; continue; }
                if (is_ptr_t(bt)) {
                    if (is_flt_t(it)) err_at(p, line, "Illegal use of floating point");
                    r.t = bt->base;
                } else if (is_ptr_t(it)) {
                    r.t = it->base;
                } else {
                    err_at(p, line, "Illegal use of pointer");
                }
                r.lval = 1;
                r.side = e.side || idx.side;
                e = r;
            }
        } else if (t->kind == '(') {
            Type *ft = e.t;
            Sym *fs = e.sym && e.sym->kind == SY_FUNC ? e.sym : NULL;
            int argno = 0;
            Ex r;
            adv(p);
            if (ft->kind == TY_PTR && ft->base->kind == TY_FUNC) ft = ft->base;
            if (ft->kind != TY_FUNC && !is_err_t(ft)) {
                err_at(p, line, "Call of non-function");
                ft = NULL;
            }
            if (fs && !fs->type->proto && ft) warn_at(p, line, "Call to function '%s' with no prototype", fs->name);
            if (e.sym && e.sym->kind == SY_VAR) note_read(p, e.sym, line);
            if (ft && ft->kind == TY_FUNC && is_flt_t(ft->base)) p->float_used = 1;
            if (!is(p, ')')) {
                for (;;) {
                    Ex a = parse_assign(p);
                    use_val(p, &a);
                    argno++;
                    if (ft && ft->kind == TY_FUNC && ft->proto) {
                        if (argno <= ft->nparams) {
                            Type *pt = ft->params[argno - 1].type;
                            if (is_flt_t(pt)) p->float_used = 1;
                            conv_check(p, pt, &a, CV_ARG, a.line, fs, argno, ft->params[argno - 1].name);
                        } else if (!ft->variadic && argno == ft->nparams + 1) {
                            err_at(p, line, fs ? "Too many parameters in call to '%s'" : "Too many parameters in call",
                                   fs ? fs->name : "");
                        }
                    }
                    if (accept(p, ',')) continue;
                    break;
                }
            }
            if (ft && ft->kind == TY_FUNC && ft->proto && argno < ft->nparams)
                err_at(p, line, fs ? "Too few parameters in call to '%s'" : "Too few parameters in call", fs ? fs->name : "");
            if (is(p, ')')) adv(p);
            else err_at(p, cur_line(p), "Function call missing )");
            r = ex_make(ft && ft->kind == TY_FUNC ? ft->base : t_err, line);
            r.side = 1;
            e = r;
        } else if (t->kind == '.' || t->kind == T_ARROW) {
            int arrow = (t->kind == T_ARROW);
            Type *bt;
            Ex r = ex_make(t_err, line);
            adv(p);
            if (!is(p, T_ID)) hard_error(p, "Expression syntax");
            {
                char *mname = cur(p)->text;
                int mline = cur(p)->line;
                adv(p);
                if (arrow) {
                    use_val(p, &e);
                    bt = decay(e.t);
                    if (is_err_t(bt)) { e = r; e.lval = 1; continue; }
                    if (!(is_ptr_t(bt) && is_agg_t(bt->base))) {
                        err_at(p, line, "Pointer to structure required on left side of -> or ->*");
                        e = r; e.lval = 1;
                        continue;
                    }
                    bt = bt->base;
                } else {
                    bt = e.t;
                    if (is_err_t(bt)) { e = r; e.lval = 1; continue; }
                    if (!is_agg_t(bt)) {
                        err_at(p, line, "Structure required on left side of . or .*");
                        e = r; e.lval = 1;
                        continue;
                    }
                    if (e.sym && e.sym->kind == SY_VAR) { e.sym->ref = 1; e.sym->read = 1; e.sym->assigned = 1; e.sym->defined_val = 1; }
                }
                if (!bt->tag || !bt->tag->complete) {
                    err_at(p, line, "Undefined structure '%s'", bt->tag && bt->tag->name ? bt->tag->name : "");
                    e = r; e.lval = 1;
                    continue;
                }
                {
                    Member *m = find_member(bt->tag, mname);
                    if (!m) { err_at(p, mline, "Undefined symbol '%s'", mname); e = r; e.lval = 1; continue; }
                    r.t = m->type;
                    if (bt->cv & 1) r.t = ty_qual(r.t, 1);
                    r.lval = arrow ? 1 : e.lval;
                    r.side = e.side;
                    e = r;
                }
            }
        } else if (t->kind == T_INC || t->kind == T_DEC) {
            Ex r;
            adv(p);
            if (e.sym && e.sym->kind == SY_VAR) { note_read(p, e.sym, line); }
            check_modifiable(p, &e, line);
            if (is_flt_t(e.t)) p->float_used = 1;
            note_assign(&e);
            r = ex_make(e.t, line);
            r.side = 1;
            e = r;
        } else {
            return e;
        }
    }
}

static int tok_is_type(P *p, Tok *t)
{
    switch (t->kind) {
    case K_VOID: case K_CHAR: case K_SHORT: case K_INT: case K_LONG: case K_FLOAT: case K_DOUBLE:
    case K_SIGNED: case K_UNSIGNED: case K_STRUCT: case K_UNION: case K_ENUM: case K_CONST:
    case K_VOLATILE:
        return 1;
    case T_ID: {
        Sym *y = lookup(p, t->text);
        return y && y->kind == SY_TYPEDEF;
    }
    }
    return 0;
}

static Ex parse_unary(P *p)
{
    Tok *t = cur(p);
    int line = t->line;
    Ex e;

    switch (t->kind) {
    case T_INC: case T_DEC: {
        adv(p);
        e = parse_unary(p);
        if (e.sym && e.sym->kind == SY_VAR) note_read(p, e.sym, line);
        check_modifiable(p, &e, line);
        if (is_flt_t(e.t)) p->float_used = 1;
        note_assign(&e);
        e.lval = 0; e.side = 1; e.cnst = 0; e.is_assign = 0; e.paren = 0;
        return e;
    }
    case '&': {
        Type *rt;
        adv(p);
        e = parse_unary(p);
        if (is_err_t(e.t)) { e.lval = 0; return e; }
        if (!(e.lval || e.t->kind == TY_FUNC)) err_at(p, line, "Must take address of a memory location");
        if (e.sym && e.sym->kind == SY_VAR) { e.sym->ref = 1; e.sym->addr = 1; e.sym->read = 1; e.sym->defined_val = 1; }
        rt = ty_ptr(e.t);
        e.t = rt; e.lval = 0; e.cnst = 0; e.sym = NULL; e.paren = 0; e.is_assign = 0;
        return e;
    }
    case '*': {
        Type *bt;
        adv(p);
        e = parse_unary(p);
        use_val(p, &e);
        bt = decay(e.t);
        if (is_err_t(bt)) { e.lval = 1; return e; }
        if (!is_ptr_t(bt)) { err_at(p, line, "Invalid indirection"); e.t = t_err; e.lval = 1; return e; }
        e.t = bt->base;
        e.lval = (bt->base->kind != TY_FUNC);
        e.cnst = 0; e.sym = NULL; e.paren = 0; e.is_assign = 0;
        return e;
    }
    case '+': case '-': case '~': case '!': {
        int op = t->kind;
        Type *ot;
        adv(p);
        e = parse_unary(p);
        use_val(p, &e);
        ot = decay(e.t);
        e.sym = NULL; e.lval = 0; e.paren = 0; e.is_assign = 0;
        if (is_err_t(ot)) return e;
        if (is_agg_t(ot)) { err_at(p, line, "Illegal structure operation"); e.t = t_err; return e; }
        if (op == '!') {
            if (e.cnst) e.cv = !e.cv;
            e.t = t_int;
            return e;
        }
        if (is_ptr_t(ot)) { err_at(p, line, "Illegal use of pointer"); e.t = t_err; return e; }
        if (op == '~' && is_flt_t(ot)) { err_at(p, line, "Illegal use of floating point"); e.t = t_err; return e; }
        e.t = arith_conv(ot, t_int);
        if (e.cnst) e.cv = (op == '-') ? -e.cv : (op == '~') ? ~e.cv : e.cv;
        return e;
    }
    case K_SIZEOF: {
        long sz;
        adv(p);
        if (is(p, '(') && tok_is_type(p, peekn(p, 1))) {
            Type *ty;
            adv(p);
            ty = parse_type_name(p);
            if (!accept(p, ')')) hard_error(p, "Expression syntax");
            e = ex_make(t_uint, line);
            e.cnst = 1;
            e.cv = ty_size(ty);
            return e;
        }
        e = parse_unary(p);
        sz = ty_size(e.t);
        if (e.sym) e.sym->ref = 1;
        e = ex_make(t_uint, line);
        e.cnst = 1;
        e.cv = sz;
        return e;
    }
    case '(': {
        if (tok_is_type(p, peekn(p, 1))) {
            Type *ty, *ft;
            Ex r;
            adv(p);
            ty = parse_type_name(p);
            if (!accept(p, ')')) hard_error(p, "Expression syntax");
            e = parse_unary(p);
            use_val(p, &e);
            ft = decay(e.t);
            r = ex_make(ty, line);
            r.side = e.side;
            if (ty->kind == TY_VOID) r.side = 1;
            else if (!is_err_t(ft) && !is_err_t(ty)) {
                char a[160], b[160];
                int bad = 0;
                if (ft->kind == TY_VOID) bad = 1;
                else if (is_agg_t(ty) || is_agg_t(ft)) bad = !(is_agg_t(ty) && is_agg_t(ft) && ty->tag == ft->tag);
                else if ((is_flt_t(ty) && is_ptr_t(ft)) || (is_ptr_t(ty) && is_flt_t(ft))) bad = 1;
                if (bad) {
                    ty_name(ft, a, sizeof a);
                    ty_name(ty, b, sizeof b);
                    err_at(p, line, "Cannot convert '%s' to '%s'", a, b);
                }
            }
            if (is_flt_t(ty)) p->float_used = 1;
            r.cnst = e.cnst && is_int_t(ty);
            r.cv = e.cv;
            return r;
        }
        break;
    }
    default:
        break;
    }
    return parse_postfix(p);
}

static Ex parse_binary(P *p, int minprec)
{
    Ex l = parse_unary(p);
    for (;;) {
        int k = cur(p)->kind, pr = bin_prec(k), line = cur_line(p);
        Ex r;
        if (pr == 0 || pr < minprec) return l;
        adv(p);
        use_val(p, &l);
        r = parse_binary(p, pr + 1);
        use_val(p, &r);
        l = binop(p, k, l, r, line);
    }
}

static void cond_check(P *p, Ex *e)
{
    use_val(p, e);
    if (e->is_assign) warn_at(p, e->line, "Possibly incorrect assignment");
}

static Ex parse_cond(P *p)
{
    Ex c = parse_binary(p, 1);
    if (is(p, '?')) {
        Ex a, b, r;
        Type *ta, *tb;
        int line = cur_line(p);
        adv(p);
        use_val(p, &c);
        a = parse_expr(p);
        use_val(p, &a);
        if (is(p, ':')) adv(p);
        else hard_error(p, "Expression syntax");
        b = parse_cond(p);
        use_val(p, &b);
        ta = decay(a.t); tb = decay(b.t);
        r = ex_make(ta, line);
        if (is_arith_t(ta) && is_arith_t(tb)) r.t = arith_conv(ta, tb);
        else if (is_ptr_t(ta) && !(a.cnst && a.cv == 0)) r.t = ta;
        else if (is_ptr_t(tb)) r.t = tb;
        r.side = c.side || a.side || b.side;
        r.cnst = c.cnst && a.cnst && b.cnst;
        r.cv = c.cv ? a.cv : b.cv;
        return r;
    }
    return c;
}

static int assign_binop(int k)
{
    switch (k) {
    case T_MULEQ: return '*';
    case T_DIVEQ: return '/';
    case T_MODEQ: return '%';
    case T_ADDEQ: return '+';
    case T_SUBEQ: return '-';
    case T_SHLEQ: return T_SHL;
    case T_SHREQ: return T_SHR;
    case T_ANDEQ: return '&';
    case T_XOREQ: return '^';
    case T_OREQ: return '|';
    }
    return 0;
}

static Ex parse_assign(P *p)
{
    Ex l = parse_cond(p);
    int k = cur(p)->kind;
    if (k == '=' || assign_binop(k)) {
        int line = cur_line(p);
        Ex r, out;
        adv(p);
        if (k != '=') use_val(p, &l);
        check_modifiable(p, &l, line);
        r = parse_assign(p);
        use_val(p, &r);
        if (k == '=') conv_check(p, l.t, &r, CV_ASSIGN, line, NULL, 0, NULL);
        else binop(p, assign_binop(k), l, r, line);
        if (is_flt_t(l.t)) p->float_used = 1;
        note_assign(&l);
        out = ex_make(l.t, line);
        out.side = 1;
        out.is_assign = (k == '=');
        return out;
    }
    return l;
}

static Ex parse_expr(P *p)
{
    Ex e = parse_assign(p);
    while (is(p, ',')) {
        Ex r;
        int line = cur_line(p);
        adv(p);
        use_val(p, &e);
        r = parse_assign(p);
        r.side = r.side || e.side;
        r.line = line;
        r.is_assign = 0;
        r.cnst = 0;
        r.sym = NULL;
        e = r;
    }
    return e;
}

static long long parse_const(P *p, int *ok)
{
    Ex e = parse_cond(p);
    if (!e.cnst) {
        if (!is_err_t(e.t)) err_at(p, e.line, "Constant expression required");
        *ok = 0;
        return 0;
    }
    *ok = 1;
    return e.cv;
}

/* ================================================================== declarations */

typedef struct FnInfo {
    Param *params;
    int nparams;
    int kr;                 /* identifier list instead of a prototype */
    int got;
} FnInfo;

enum { DM_NAMED, DM_ABSTRACT, DM_EITHER };

static int is_cc_word(int k)
{
    return k == K_NEAR || k == K_FAR || k == K_HUGE || k == K_CDECL || k == K_PASCAL || k == K_INTERRUPT;
}

static int decl_start(P *p)
{
    Tok *t = cur(p);
    switch (t->kind) {
    case K_TYPEDEF: case K_EXTERN: case K_STATIC: case K_AUTO: case K_REGISTER:
    case K_VOID: case K_CHAR: case K_SHORT: case K_INT: case K_LONG: case K_FLOAT: case K_DOUBLE:
    case K_SIGNED: case K_UNSIGNED: case K_STRUCT: case K_UNION: case K_ENUM: case K_CONST: case K_VOLATILE:
    case K_NEAR: case K_FAR: case K_HUGE: case K_CDECL: case K_PASCAL: case K_INTERRUPT:
        return 1;
    case T_ID: {
        Sym *y = lookup(p, t->text);
        if (y && y->kind == SY_TYPEDEF) {
            Tok *n = peekn(p, 1);
            return n->kind != ':';
        }
        return 0;
    }
    }
    return 0;
}

static int starts_type(P *p) { return decl_start(p); }

static Type *declarator(P *p, Type *base, char **name, int mode, FnInfo *fi);

static void skip_balanced_parens(P *p)
{
    /* the current token is just after '(' ; stops after the matching ')' */
    int depth = 1;
    for (;;) {
        Tok *t = &p->t[p->pos];
        if (t->kind == T_EOF) return;
        p->pos++;
        if (t->kind == '(') depth++;
        else if (t->kind == ')') { if (--depth == 0) return; }
    }
}

static Type *parse_params(P *p, FnInfo *fi)
{
    Type *ft = ty_new(TY_FUNC);
    Param *arr = (Param *)tc_alloc(sizeof(Param) * 64);
    int n = 0;
    adv(p);                                     /* '(' */
    if (is(p, ')')) {
        ft->proto = 0;
    } else if (is(p, K_VOID) && peekn(p, 1)->kind == ')') {
        adv(p);
        ft->proto = 1;
    } else if (is(p, T_ID) && !decl_start(p)) {
        /* K&R identifier list */
        for (;;) {
            if (!is(p, T_ID)) hard_error(p, "Declaration syntax error");
            if (n < 64) { arr[n].name = cur(p)->text; arr[n].type = t_int; n++; }
            adv(p);
            if (!accept(p, ',')) break;
        }
        ft->proto = 0;
        if (fi) fi->kr = 1;
    } else {
        ft->proto = 1;
        for (;;) {
            Spec sp;
            char *pn;
            Type *pt;
            if (accept(p, T_ELLIPSIS)) { ft->variadic = 1; break; }
            if (!parse_specifiers(p, &sp)) {
                if (n == 0) hard_error(p, "Declaration syntax error");
                hard_error(p, "Argument list syntax error");
            }
            pt = declarator(p, sp.type, &pn, DM_EITHER, NULL);
            if (pt->kind == TY_ARRAY) pt = ty_ptr(pt->base);
            else if (pt->kind == TY_FUNC) pt = ty_ptr(pt);
            if (n < 64) { arr[n].name = pn; arr[n].type = pt; n++; }
            if (!accept(p, ',')) break;
        }
    }
    if (!is(p, ')')) hard_error(p, "Argument list syntax error");
    adv(p);
    ft->params = arr;
    ft->nparams = n;
    if (fi) { fi->params = arr; fi->nparams = n; fi->got = 1; }
    return ft;
}

static Type *parse_suffix(P *p, Type *base, FnInfo *fi)
{
    if (is(p, '[')) {
        long len = -1;
        Type *inner;
        adv(p);
        if (!is(p, ']')) {
            int ok;
            long long v = parse_const(p, &ok);
            if (ok) {
                if (v <= 0) err_at(p, cur_line(p), "Array must have at least one element");
                else len = (long)v;
            }
        }
        if (is(p, ']')) adv(p);
        else err_at(p, cur_line(p), "Array bounds missing ]");
        inner = parse_suffix(p, base, fi);
        return ty_array(inner, len);
    }
    if (is(p, '(')) {
        Type *ft = parse_params(p, fi);
        ft->base = base;
        return ft;
    }
    return base;
}

static Type *declarator(P *p, Type *base, char **name, int mode, FnInfo *fi)
{
    *name = NULL;
    for (;;) {
        if (is(p, '*')) {
            adv(p);
            base = ty_ptr(base);
            for (;;) {
                int k = cur(p)->kind;
                if (k == K_CONST) { base = ty_qual(base, 1); adv(p); }
                else if (k == K_VOLATILE) { base = ty_qual(base, 2); adv(p); }
                else if (is_cc_word(k)) adv(p);
                else break;
            }
            continue;
        }
        if (is_cc_word(cur(p)->kind)) { adv(p); continue; }
        break;
    }
    if (is(p, '(')) {
        Tok *n1 = peekn(p, 1);
        int nested = (n1->kind == '*' || n1->kind == '(' || is_cc_word(n1->kind));
        if (!nested && n1->kind == T_ID && mode == DM_NAMED) {
            Sym *y = lookup(p, n1->text);
            nested = !(y && y->kind == SY_TYPEDEF);
        }
        if (nested) {
            size_t inner_start, end;
            Type *t2, *res;
            adv(p);
            inner_start = p->pos;
            skip_balanced_parens(p);
            t2 = parse_suffix(p, base, fi);
            end = p->pos;
            p->pos = inner_start;
            res = declarator(p, t2, name, mode, fi);
            if (!is(p, ')')) hard_error(p, "Declaration syntax error");
            p->pos = end;
            return res;
        }
    }
    if (is(p, T_ID) && mode != DM_ABSTRACT) {
        *name = cur(p)->text;
        adv(p);
    }
    return parse_suffix(p, base, fi);
}

static Type *parse_tag_spec(P *p, int kw);

static int parse_specifiers(P *p, Spec *sp)
{
    int nlong = 0, nshort = 0, nsigned = 0, nuns = 0, base = 0, cv = 0, any = 0, errored = 0;
    Type *named = NULL;
    memset(sp, 0, sizeof *sp);
    for (;;) {
        Tok *t = cur(p);
        int line = t->line;
        switch (t->kind) {
        case K_TYPEDEF: case K_EXTERN: case K_STATIC: case K_AUTO: case K_REGISTER: {
            int sc = t->kind == K_TYPEDEF ? SC_TYPEDEF : t->kind == K_EXTERN ? SC_EXTERN :
                     t->kind == K_STATIC ? SC_STATIC : t->kind == K_AUTO ? SC_AUTO : SC_REGISTER;
            if (sp->storage && !errored) { err_at(p, line, "Too many storage classes in declaration"); errored = 1; }
            else sp->storage = sc;
            adv(p); any = 1;
            continue;
        }
        case K_CONST: cv |= 1; adv(p); any = 1; continue;
        case K_VOLATILE: cv |= 2; adv(p); any = 1; continue;
        case K_NEAR: case K_FAR: case K_HUGE: case K_CDECL: case K_PASCAL: case K_INTERRUPT:
            adv(p); any = 1; continue;
        case K_VOID: case K_CHAR: case K_INT: case K_FLOAT: case K_DOUBLE:
            if (base || named) {
                if (!errored) { err_at(p, line, "Too many types in declaration"); errored = 1; }
            } else base = t->kind;
            adv(p); any = 1;
            continue;
        case K_SHORT: nshort++; adv(p); any = 1; continue;
        case K_LONG: nlong++; adv(p); any = 1; continue;
        case K_SIGNED: nsigned++; adv(p); any = 1; continue;
        case K_UNSIGNED: nuns++; adv(p); any = 1; continue;
        case K_STRUCT: case K_UNION: case K_ENUM: {
            Type *tt = parse_tag_spec(p, t->kind);
            if (base || named) {
                if (!errored) { err_at(p, line, "Too many types in declaration"); errored = 1; }
            } else named = tt;
            any = 1;
            continue;
        }
        case T_ID: {
            Sym *y = lookup(p, t->text);
            if (y && y->kind == SY_TYPEDEF && !base && !named && !nlong && !nshort && !nsigned && !nuns) {
                named = y->type;
                adv(p); any = 1;
                continue;
            }
            break;
        }
        default:
            break;
        }
        break;
    }
    if (!any) return 0;
    if (named) {
        if ((nlong || nshort || nsigned || nuns) && !errored) err_at(p, cur_line(p), "Too many types in declaration");
        sp->type = ty_qual(named, cv);
    } else {
        Type *t;
        if (nlong > 1 && !errored) { err_at(p, cur_line(p), "Too many types in declaration"); errored = 1; }
        if (((nshort && nlong) || (nsigned && nuns)) && !errored) err_at(p, cur_line(p), "Conflicting type modifiers");
        switch (base) {
        case K_VOID: t = t_void; break;
        case K_FLOAT: t = t_float; break;
        case K_DOUBLE: t = nlong ? t_ldouble : t_double; break;
        case K_CHAR: t = nuns ? t_uchar : t_char; break;
        default:
            t = nshort ? (nuns ? t_ushort : t_short) : nlong ? (nuns ? t_ulong : t_long) : (nuns ? t_uint : t_int);
        }
        sp->type = ty_qual(t, cv);
    }
    sp->any = 1;
    return 1;
}

static Type *parse_type_name(P *p)
{
    Spec sp;
    char *nm;
    if (!parse_specifiers(p, &sp)) hard_error(p, "Expression syntax");
    return declarator(p, sp.type, &nm, DM_ABSTRACT, NULL);
}

static Sym *declare_name(P *p, char *name, int kind, Type *t, int storage, int line, int is_def, int has_init)
{
    Scope *sc = p->scope;
    Sym *old = scope_find(sc, name);
    Sym *y;
    if (storage == SC_EXTERN && !sc->is_global && kind == SY_VAR) {
        Sym *g = scope_find(p->global, name);
        if (g) return g;
    }
    if (old) {
        if (old->kind != kind) {
            err_at(p, line, "Multiple declaration for '%s'", name);
            return old;
        }
        if (kind == SY_TYPEDEF) return old;
        if (kind == SY_FUNC) {
            if (!ty_same(old->type->base, t->base) ||
                (old->type->proto && t->proto && !ty_same(old->type, t)))
                err_at(p, line, "Type mismatch in redeclaration of '%s'", name);
            else if (is_def && old->defined && !old->is_lib)
                err_at(p, line, "Multiple declaration for '%s'", name);
            if (t->proto && !old->type->proto) old->type = t;
            if (is_def) old->defined = 1;
            old->implicit = 0;
            return old;
        }
        /* variables */
        if (sc->is_global) {
            if (!ty_same(old->type, t)) err_at(p, line, "Type mismatch in redeclaration of '%s'", name);
            else if (has_init && old->has_init) err_at(p, line, "Multiple declaration for '%s'", name);
            if (old->type->kind == TY_ARRAY && old->type->len < 0 && t->kind == TY_ARRAY && t->len >= 0) old->type = t;
            if (storage != SC_EXTERN) old->defined = 1;
            return old;
        }
        err_at(p, line, "Multiple declaration for '%s'", name);
        return old;
    }
    y = sym_add(p, sc, name, kind, t);
    y->storage = storage;
    y->line = line;
    y->is_local = !sc->is_global;
    if (kind == SY_FUNC) y->defined = is_def;
    else if (kind == SY_VAR) y->defined = (storage != SC_EXTERN);
    if (kind == SY_VAR && y->is_local && (storage == SC_STATIC)) y->defined_val = 1;
    return y;
}

static Type *parse_tag_spec(P *p, int kw)
{
    int line = cur_line(p);
    int kind = kw == K_STRUCT ? TY_STRUCT : kw == K_UNION ? TY_UNION : TY_ENUM;
    char *name = NULL;
    Tag *tag = NULL;
    Type *ty;
    adv(p);
    if (is(p, T_ID)) { name = cur(p)->text; adv(p); }
    if (is(p, '{')) {
        if (name) tag = tag_find_scope(p->scope, name);
        if (tag && tag->complete && tag->kind == kind) {
            err_at(p, line, "Multiple declaration for '%s'", name);
            tag = NULL;
        }
        if (!tag) {
            tag = (Tag *)tc_alloc(sizeof(Tag));
            tag->name = name;
            tag->kind = kind;
            tag->next = p->scope->tags;
            p->scope->tags = tag;
        }
        adv(p);
        if (kind == TY_ENUM) {
            long long next = 0;
            while (!is(p, '}')) {
                char *cn;
                int cl = cur_line(p);
                Sym *y;
                if (!is(p, T_ID)) hard_error(p, "Enum syntax error");
                cn = cur(p)->text;
                adv(p);
                if (accept(p, '=')) {
                    int ok;
                    long long v = parse_const(p, &ok);
                    if (ok) next = v;
                }
                y = declare_name(p, cn, SY_ENUMC, t_int, SC_NONE, cl, 0, 0);
                y->enumval = next;
                y->defined = 1;
                next++;
                if (!accept(p, ',')) break;
            }
            if (!accept(p, '}')) hard_error(p, "Enum syntax error");
        } else {
            tag->all_scalar = 1;
            while (!is(p, '}') && !is(p, T_EOF)) {
                Spec msp;
                if (!parse_specifiers(p, &msp)) hard_error(p, "Declaration syntax error");
                if (is(p, ';')) { adv(p); continue; }
                for (;;) {
                    char *mn;
                    int ml = cur_line(p);
                    Type *mt = declarator(p, msp.type, &mn, DM_NAMED, NULL);
                    if (accept(p, ':')) { int ok; parse_const(p, &ok); }
                    if (!mn) hard_error(p, "Declaration syntax error");
                    {
                        Member *m;
                        for (m = tag->members; m; m = m->next)
                            if (m->name == mn) { err_at(p, ml, "Multiple declaration for '%s'", mn); break; }
                        m = (Member *)tc_alloc(sizeof(Member));
                        m->name = mn;
                        m->type = mt;
                        if (!tag->members) tag->members = m;
                        else { Member *q = tag->members; while (q->next) q = q->next; q->next = m; }
                        tag->nmembers++;
                        if (!is_scalar_t(mt)) tag->all_scalar = 0;
                    }
                    if (!accept(p, ',')) break;
                }
                if (!accept(p, ';')) err_at(p, cur_line(p), "Declaration missing ;");
            }
            if (!accept(p, '}')) { err_at(p, cur_line(p), "Declaration terminated incorrectly"); }
        }
        tag->complete = 1;
    } else {
        if (!name) hard_error(p, "Declaration syntax error");
        tag = tag_lookup(p, name);
        if (!tag || tag->kind != kind) {
            tag = (Tag *)tc_alloc(sizeof(Tag));
            tag->name = name;
            tag->kind = kind;
            tag->next = p->scope->tags;
            p->scope->tags = tag;
            if (kind == TY_ENUM) tag->complete = 1;
        }
    }
    ty = ty_new(kind);
    ty->tag = tag;
    return ty;
}

/* ---- initializers ---- */

static int init_list(P *p, Type *t)
{
    Type *et = NULL;
    long limit = -1;
    Member *mem = NULL;
    int n = 0, elided = 0;
    int line = cur_line(p);
    adv(p);                                     /* '{' */
    if (t->kind == TY_ARRAY) { et = t->base; limit = t->len; }
    else if (is_agg_t(t) && t->tag && t->tag->complete) {
        mem = t->tag->members;
        limit = (t->kind == TY_STRUCT && t->tag->all_scalar) ? t->tag->nmembers : (t->kind == TY_UNION ? 1 : -1);
    } else { et = t; limit = 1; }
    while (!is(p, '}') && !is(p, T_EOF)) {
        Type *pt = et ? et : (mem ? mem->type : NULL);
        if (is(p, '{')) {
            if (pt) init_list(p, pt); else init_list(p, t_int);
        } else {
            Ex e = parse_assign(p);
            use_val(p, &e);
            if (pt && is_scalar_t(pt)) conv_check(p, pt, &e, CV_INIT, e.line, NULL, 0, NULL);
            else if (pt && pt->kind == TY_ARRAY && e.is_str) { /* char array from a string */ }
            else elided = 1;
        }
        n++;
        if (mem && !et) mem = mem->next;
        if (!accept(p, ',')) break;
    }
    if (!is(p, '}')) {
        err_at(p, cur_line(p), "Initializer syntax error");
        return n;
    }
    adv(p);
    if (!elided && limit >= 0 && n > limit) err_at(p, line, "Too many initializers");
    return elided ? -1 : n;
}

static void parse_init(P *p, Type *t, Sym *sym)
{
    if (is(p, '{')) {
        int n = init_list(p, t);
        if (t->kind == TY_ARRAY && t->len < 0 && n >= 0) t->len = n;
    } else {
        Ex e = parse_assign(p);
        use_val(p, &e);
        if (t->kind == TY_ARRAY) {
            if (e.is_str && t->len < 0) t->len = e.t->len;
        } else {
            conv_check(p, t, &e, CV_INIT, e.line, NULL, 0, NULL);
        }
    }
    if (sym) { sym->has_init = 1; sym->assigned = 1; sym->defined_val = 1; }
}

/* Declaration inside a block (or the init part of a for): consumes the ';' */
static void parse_declaration(P *p)
{
    Spec sp;
    if (!parse_specifiers(p, &sp)) hard_error(p, "Declaration syntax error");
    if (accept(p, ';')) return;
    for (;;) {
        char *name;
        int line = cur_line(p);
        FnInfo fi;
        Type *t;
        Sym *s;
        memset(&fi, 0, sizeof fi);
        t = declarator(p, sp.type, &name, DM_NAMED, &fi);
        if (!name) hard_error(p, "Declaration syntax error");
        if (sp.storage == SC_TYPEDEF) {
            s = declare_name(p, name, SY_TYPEDEF, t, SC_TYPEDEF, line, 0, 0);
            s->defined = 1;
        } else if (t->kind == TY_FUNC) {
            declare_name(p, name, SY_FUNC, t, sp.storage, line, 0, 0);
        } else {
            if (t->kind == TY_VOID) err_at(p, line, "Not an allowed type");
            s = declare_name(p, name, SY_VAR, t, sp.storage, line, 0, is(p, '='));
            if (accept(p, '=')) {
                parse_init(p, t, s);
            }
        }
        if (accept(p, ',')) continue;
        break;
    }
    if (!accept(p, ';')) err_at(p, cur_line(p), "Declaration missing ;");
}

/* ================================================================== statements */

static Ctl *ctl_push(P *p, int is_switch)
{
    Ctl *c;
    if (p->nctl >= MAXCTL) return &p->ctl[MAXCTL - 1];
    c = &p->ctl[p->nctl++];
    memset(c, 0, sizeof *c);
    c->is_switch = is_switch;
    return c;
}

static int in_switch(P *p)
{
    int i;
    for (i = p->nctl - 1; i >= 0; i--) if (p->ctl[i].is_switch) return i;
    return -1;
}

static void label_use(P *p, char *name, int line)
{
    int i;
    for (i = 0; i < p->nlabs; i++)
        if (p->labs[i].name == name) { if (!p->labs[i].used_line) p->labs[i].used_line = line; return; }
    if (p->nlabs < MAXLAB) {
        p->labs[p->nlabs].name = name;
        p->labs[p->nlabs].used_line = line;
        p->nlabs++;
    }
}

static void label_def(P *p, char *name, int line)
{
    int i;
    for (i = 0; i < p->nlabs; i++)
        if (p->labs[i].name == name) {
            if (p->labs[i].defined) err_at(p, line, "Multiple declaration for '%s'", name);
            p->labs[i].defined = 1;
            p->labs[i].line = line;
            return;
        }
    if (p->nlabs < MAXLAB) {
        p->labs[p->nlabs].name = name;
        p->labs[p->nlabs].defined = 1;
        p->labs[p->nlabs].line = line;
        p->nlabs++;
    }
}

/* warnings for the variables of a scope that is closing (reverse order of declaration) */
static void report_scope(P *p, Scope *sc, int line)
{
    Sym *arr[256];
    int n = 0, i;
    Sym *y;
    for (y = sc->first; y && n < 256; y = y->snext) arr[n++] = y;
    for (i = n - 1; i >= 0; i--) {
        y = arr[i];
        if (y->kind != SY_VAR || y->reported) continue;
        y->reported = 1;
        if (y->is_param) {
            if (!y->ref) warn_at(p, line, "Parameter '%s' is never used", y->name);
        } else if (y->storage == SC_EXTERN) {
            continue;
        } else if (!y->ref) {
            if (y->has_init && is_scalar_t(y->type)) warn_at(p, line, "'%s' is assigned a value that is never used", y->name);
            else warn_at(p, line, "'%s' is declared but never used", y->name);
        } else if (tracked_scalar(y) && y->assigned && !y->read && !y->addr) {
            warn_at(p, line, "'%s' is assigned a value that is never used", y->name);
        }
    }
}

static int stmt(P *p);
static int compound(P *p, int fn_body, int *endline);

static void expect_semi(P *p, const char *msg)
{
    if (is(p, ';')) adv(p);
    else err_at(p, cur_line(p), "%s", msg);
}

static int stmt_inner(P *p, int *jump)
{
    Tok *t = cur(p);
    int line = t->line;

    switch (t->kind) {
    case '{': {
        int endl;
        return compound(p, 0, &endl);
    }
    case ';':
        adv(p);
        return 1;
    case K_IF: {
        Ex c;
        int r1, r2 = 1, has_else = 0;
        adv(p);
        if (is(p, '(')) adv(p); else err_at(p, cur_line(p), "If statement missing (");
        c = parse_expr(p);
        cond_check(p, &c);
        if (is(p, ')')) adv(p); else err_at(p, cur_line(p), "If statement missing )");
        r1 = stmt(p);
        if (is(p, K_ELSE)) { adv(p); r2 = stmt(p); has_else = 1; }
        return has_else ? (r1 || r2) : 1;
    }
    case K_WHILE: {
        Ex c;
        int idx, r, hb;
        adv(p);
        if (is(p, '(')) adv(p); else err_at(p, cur_line(p), "While statement missing (");
        c = parse_expr(p);
        cond_check(p, &c);
        if (is(p, ')')) adv(p); else err_at(p, cur_line(p), "While statement missing )");
        ctl_push(p, 0);
        idx = p->nctl - 1;
        r = stmt(p);
        (void)r;
        hb = p->ctl[idx].has_break;
        p->nctl = idx;
        return (c.cnst && c.cv) ? hb : 1;
    }
    case K_DO: {
        Ex c;
        int idx, hb;
        adv(p);
        ctl_push(p, 0);
        idx = p->nctl - 1;
        stmt(p);
        hb = p->ctl[idx].has_break;
        p->nctl = idx;
        if (!is(p, K_WHILE)) {
            err_at(p, cur_line(p), "Do statement must have while");
            return 1;
        }
        adv(p);
        if (is(p, '(')) adv(p); else err_at(p, cur_line(p), "Do while statement missing (");
        c = parse_expr(p);
        cond_check(p, &c);
        if (is(p, ')')) adv(p); else err_at(p, cur_line(p), "Do while statement missing )");
        expect_semi(p, "Do while statement missing ;");
        return (c.cnst && c.cv) ? hb : 1;
    }
    case K_FOR: {
        int has_cond = 0, forever = 0, idx, hb;
        adv(p);
        if (is(p, '(')) adv(p); else err_at(p, cur_line(p), "For statement missing (");
        if (is(p, ';')) adv(p);
        else if (decl_start(p)) {
            err_at(p, line, "Declaration is not allowed here");
            parse_declaration(p);
        } else {
            Ex e = parse_expr(p);
            use_val(p, &e);
            if (is(p, ';')) adv(p); else err_at(p, cur_line(p), "For statement missing ;");
        }
        if (is(p, ';')) adv(p);
        else {
            Ex c = parse_expr(p);
            cond_check(p, &c);
            has_cond = 1;
            forever = c.cnst && c.cv;
            if (is(p, ';')) adv(p); else err_at(p, cur_line(p), "For statement missing ;");
        }
        if (!is(p, ')')) {
            Ex e = parse_expr(p);
            use_val(p, &e);
        }
        if (is(p, ')')) adv(p); else err_at(p, cur_line(p), "For statement missing )");
        ctl_push(p, 0);
        idx = p->nctl - 1;
        stmt(p);
        hb = p->ctl[idx].has_break;
        p->nctl = idx;
        return (!has_cond || forever) ? hb : 1;
    }
    case K_SWITCH: {
        Ex c;
        int idx, r, hb, hd;
        adv(p);
        if (is(p, '(')) adv(p); else err_at(p, cur_line(p), "Switch statement missing (");
        c = parse_expr(p);
        use_val(p, &c);
        if (is(p, ')')) adv(p); else err_at(p, cur_line(p), "Switch statement missing )");
        ctl_push(p, 1);
        idx = p->nctl - 1;
        r = stmt(p);
        hb = p->ctl[idx].has_break;
        hd = p->ctl[idx].has_default;
        p->nctl = idx;
        return (hd && !hb && !r) ? 0 : 1;
    }
    case K_CASE: {
        int sw = in_switch(p), ok;
        long long v;
        adv(p);
        if (sw < 0) err_at(p, line, "Case outside of switch");
        v = parse_const(p, &ok);
        if (ok && sw >= 0) {
            int i;
            for (i = 0; i < p->ctl[sw].ncases; i++)
                if (p->ctl[sw].cases[i] == v) { err_at(p, line, "Duplicate case"); break; }
            if (p->ctl[sw].ncases < MAXCASE) p->ctl[sw].cases[p->ctl[sw].ncases++] = v;
        }
        if (is(p, ':')) adv(p); else err_at(p, cur_line(p), "Case statement missing :");
        if (is(p, '}')) return 1;
        return stmt(p);
    }
    case K_DEFAULT: {
        int sw = in_switch(p);
        adv(p);
        if (sw < 0) err_at(p, line, "Default outside of switch");
        else if (p->ctl[sw].has_default) err_at(p, line, "Too many default cases");
        else p->ctl[sw].has_default = 1;
        if (is(p, ':')) adv(p); else err_at(p, cur_line(p), "Case statement missing :");
        if (is(p, '}')) return 1;
        return stmt(p);
    }
    case K_BREAK:
        adv(p);
        if (p->nctl == 0) err_at(p, line, "Misplaced break");
        else p->ctl[p->nctl - 1].has_break = 1;
        expect_semi(p, "Statement missing ;");
        *jump = 1;
        return 0;
    case K_CONTINUE: {
        int i, found = 0;
        adv(p);
        for (i = p->nctl - 1; i >= 0; i--) if (!p->ctl[i].is_switch) { found = 1; break; }
        if (!found) err_at(p, line, "Misplaced continue");
        expect_semi(p, "Statement missing ;");
        *jump = 1;
        return 0;
    }
    case K_RETURN: {
        adv(p);
        if (!is(p, ';')) {
            Ex e = parse_expr(p);
            use_val(p, &e);
            if (p->fn_ret_void) warn_at(p, line, "Void functions may not return a value");
            else if (p->fn_ret) conv_check(p, p->fn_ret, &e, CV_RETURN, line, NULL, 0, NULL);
            if (is_flt_t(e.t)) p->float_used = 1;
            p->fn_has_retval = 1;
        } else {
            p->fn_has_retvoid = 1;
        }
        expect_semi(p, "Statement missing ;");
        *jump = 1;
        return 0;
    }
    case K_GOTO:
        adv(p);
        if (is(p, T_ID)) { label_use(p, cur(p)->text, line); adv(p); }
        else err_at(p, line, "Goto statement missing label");
        expect_semi(p, "Statement missing ;");
        *jump = 1;
        return 0;
    case K_ELSE:
        err_at(p, line, "Misplaced else");
        adv(p);
        return stmt(p);
    case T_ID:
        if (peekn(p, 1)->kind == ':' && !decl_start(p)) {
            label_def(p, t->text, line);
            adv(p); adv(p);
            if (is(p, '}')) return 1;
            return stmt(p);
        }
        break;
    default:
        break;
    }
    if (decl_start(p)) {
        err_at(p, line, "Declaration is not allowed here");
        parse_declaration(p);
        return 1;
    }
    {
        Ex e = parse_expr(p);
        use_val(p, &e);
        if (!e.side && !is_err_t(e.t)) warn_at(p, line, "Code has no effect");
        expect_semi(p, "Statement missing ;");
    }
    return 1;
}

static int stmt(P *p)
{
    int jump = 0;
    int r = stmt_inner(p, &jump);
    p->last_jump = jump;
    return r;
}

static int compound(P *p, int fn_body, int *endline)
{
    Scope *mine = p->scope, *sc = NULL;
    int my_nctl = p->nctl;
    volatile int alive = 1, dead_direct = 0;
    jmp_buf saved;

    memcpy(&saved, &p->jb, sizeof(jmp_buf));
    adv(p);                                     /* '{' */
    if (!fn_body) {
        sc = scope_new(p, 0);
        p->scope = sc;
        mine = sc;
    }
    *endline = cur_line(p);
    for (;;) {
        Tok *t;
        int is_label, r;
        if (setjmp(p->jb)) {
            p->scope = mine;
            p->nctl = my_nctl;
            sync(p);
            continue;
        }
        t = cur(p);
        if (t->kind == '}') { *endline = t->line; adv(p); break; }
        if (t->kind == T_EOF) {
            *endline = t->line;
            if (!p->eof_err) err_at(p, t->line, "Compound statement missing }");
            p->eof_err = 1;
            break;
        }
        if (decl_start(p)) { parse_declaration(p); continue; }
        is_label = (t->kind == K_CASE || t->kind == K_DEFAULT || (t->kind == T_ID && peekn(p, 1)->kind == ':'));
        if (is_label) { alive = 1; dead_direct = 0; }
        if (dead_direct) {
            warn_at(p, t->line, "Unreachable code");
            dead_direct = 0;
        }
        r = stmt(p);
        if (is_label) alive = r;
        else if (alive) alive = r;
        dead_direct = p->last_jump;
    }
    memcpy(&p->jb, &saved, sizeof(jmp_buf));
    if (!fn_body) {
        report_scope(p, sc, *endline);
        p->scope = sc->parent;
    }
    return alive;
}

/* ================================================================== external declarations */

static void define_function(P *p, char *name, Type *ft, int storage, int line)
{
    Sym *f;
    Scope *fs;
    int i, endline = line, alive;

    f = declare_name(p, name, SY_FUNC, ft, storage, line, 1, 0);
    if (ft->proto || !f->type->proto) f->type = ft;
    f->defined = 1;
    if (strcmp(name, "main") == 0) p->has_main = 1;

    fs = scope_new(p, 0);
    p->scope = fs;
    for (i = 0; i < ft->nparams; i++) {
        Param *pa = &ft->params[i];
        Sym *s;
        if (!pa->name) continue;
        if (scope_find(fs, pa->name)) { err_at(p, line, "Multiple declaration for '%s'", pa->name); continue; }
        s = sym_add(p, fs, pa->name, SY_VAR, pa->type);
        s->is_local = 1; s->is_param = 1; s->defined_val = 1; s->defined = 1; s->line = line;
    }
    p->in_fn = 1;
    p->fn_name = name;
    p->fn_sym = f;
    p->fn_ret = ft->base;
    p->fn_ret_void = (ft->base->kind == TY_VOID);
    p->fn_has_retval = p->fn_has_retvoid = 0;
    p->nlabs = 0;
    p->nctl = 0;

    alive = compound(p, 1, &endline);

    if (!p->fn_ret_void && alive) warn_at(p, endline, "Function should return a value");
    if (p->fn_has_retval && p->fn_has_retvoid && !p->fn_ret_void)
        warn_at(p, endline, "Both return and return of a value used");
    report_scope(p, fs, endline);
    for (i = 0; i < p->nlabs; i++)
        if (p->labs[i].defined && !p->labs[i].used_line)
            warn_at(p, endline, "'%s' is declared but never used", p->labs[i].name);
    for (i = 0; i < p->nlabs; i++)
        if (!p->labs[i].defined) err_at(p, endline, "Undefined label '%s'", p->labs[i].name);
    p->scope = fs->parent;
    p->in_fn = 0;
    p->fn_name = NULL;
    p->fn_sym = NULL;
}

static void parse_external(P *p)
{
    Tok *t = cur(p);
    Spec sp;
    int line;

    p->in_hdr = (t->flags & TF_HEADER) ? 1 : 0;
    if (t->kind == ';') { adv(p); return; }
    if (t->kind == '}') {
        err_at(p, t->line, "Unexpected }");
        adv(p);
        return;
    }
    line = t->line;
    if (!parse_specifiers(p, &sp)) {
        if (t->kind == T_ID && peekn(p, 1)->kind == '(') {
            memset(&sp, 0, sizeof sp);
            sp.type = t_int;
        } else {
            hard_error(p, "Declaration syntax error");
        }
    }
    if (accept(p, ';')) return;
    for (;;) {
        char *name;
        FnInfo fi;
        Type *ty;
        int dline = cur_line(p);
        memset(&fi, 0, sizeof fi);
        ty = declarator(p, sp.type, &name, DM_NAMED, &fi);
        if (!name) hard_error(p, "Declaration syntax error");
        if (sp.storage == SC_TYPEDEF) {
            Sym *s = declare_name(p, name, SY_TYPEDEF, ty, SC_TYPEDEF, dline, 0, 0);
            s->defined = 1;
        } else if (ty->kind == TY_FUNC) {
            int kr_decls = fi.kr && decl_start(p);
            if (is(p, '{') || kr_decls) {
                if (kr_decls) {
                    while (decl_start(p)) {
                        Spec ksp;
                        if (!parse_specifiers(p, &ksp)) break;
                        if (accept(p, ';')) continue;
                        for (;;) {
                            char *kn;
                            Type *kt = declarator(p, ksp.type, &kn, DM_NAMED, NULL);
                            int i;
                            if (kt->kind == TY_ARRAY) kt = ty_ptr(kt->base);
                            for (i = 0; kn && i < ty->nparams; i++)
                                if (ty->params[i].name == kn) ty->params[i].type = kt;
                            if (!accept(p, ',')) break;
                        }
                        if (!accept(p, ';')) err_at(p, cur_line(p), "Declaration missing ;");
                    }
                }
                if (!is(p, '{')) hard_error(p, "Declaration syntax error");
                /* a definition "void f() { }" already tells the compiler f takes no arguments */
                if (!fi.kr && ty->nparams == 0) ty->proto = 1;
                define_function(p, name, ty, sp.storage, dline);
                return;
            }
            declare_name(p, name, SY_FUNC, ty, sp.storage, dline, 0, 0);
        } else {
            Sym *s;
            if (ty->kind == TY_VOID) err_at(p, dline, "Not an allowed type");
            s = declare_name(p, name, SY_VAR, ty, sp.storage, dline, 0, is(p, '='));
            if (accept(p, '=')) parse_init(p, ty, s);
        }
        if (accept(p, ',')) continue;
        break;
    }
    (void)line;
    if (!accept(p, ';')) err_at(p, cur_line(p), "Declaration missing ;");
}

/* ================================================================== entry point */

int tc_check(const char *src, FILE *out, TcResult *res)
{
    P *p;
    PpResult pp;
    int link_errors = 0, i;

    memset(res, 0, sizeof *res);
    types_init();
    tc_lib_init();
    memset(&pp, 0, sizeof pp);
    tc_preprocess(src, &pp);

    p = (P *)tc_alloc(sizeof(P));
    p->t = pp.toks.v;
    p->n = pp.toks.n;
    p->last_line = pp.last_line;
    p->out = out;
    p->scope = NULL;
    p->global = scope_new(p, 1);
    p->scope = p->global;

    if (setjmp(p->top) == 0) {
        for (;;) {
            size_t before;
            if (cur(p)->kind == T_EOF) break;
            before = p->pos;
            if (setjmp(p->jb)) {
                p->scope = p->global;
                p->in_fn = 0;
                p->fn_name = NULL;
                p->nctl = 0;
                p->in_hdr = 0;
                sync(p);
                if (p->pos == before && cur(p)->kind != T_EOF) adv(p);
                continue;
            }
            parse_external(p);
            p->in_hdr = 0;
            if (p->pos == before && cur(p)->kind != T_EOF) adv(p);
        }
    }
    p->in_hdr = 0;
    p->in_fn = 0;
    p->fn_name = NULL;

    if (p->printed_header)
        fprintf(out, "\n  Errors: %d   Warnings: %d   Lines compiled: %d\n", p->errors, p->warnings, pp.lines);

    if (p->errors == 0 && !p->stop) {
        int header = 0;
        if (!p->has_main) {
            fprintf(out, "\nLinking %s:\n", "NONAME00.EXE");
            header = 1;
            fprintf(out, "Linker Error: Undefined symbol _main in module C0.ASM\n");
            link_errors++;
        }
        for (i = 0; i < p->nrefs; i++) {
            Sym *y = p->refs[i];
            if (y->defined || y->is_lib || y->kind == SY_ENUMC) continue;
            if (y->kind == SY_FUNC && tc_lib_has_symbol(y->name)) continue;
            if (y->kind == SY_VAR && y->storage != SC_EXTERN) continue;
            if (y->kind == SY_VAR && tc_lib_has_symbol(y->name)) continue;
            if (!header) { fprintf(out, "\nLinking %s:\n", "NONAME00.EXE"); header = 1; }
            fprintf(out, "Linker Error: Undefined symbol _%s in module %s\n", y->name, TC_FILE_NAME);
            link_errors++;
        }
    }

    res->errors = p->errors;
    res->warnings = p->warnings;
    res->link_errors = link_errors;
    res->lines = pp.lines;
    res->float_used = p->float_used;
    res->has_main = p->has_main;
    fflush(out);
    {
        int ok = (p->errors == 0 && link_errors == 0 && !p->stop);
        tc_free_all();
        return ok;
    }
}
