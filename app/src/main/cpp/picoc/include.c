/* picoc include system - can emulate system includes from built-in libraries
 * or it can include and parse files if the system has files */

#include "picoc.h"
#include "interpreter.h"


/* initialize the built-in include libraries */
extern const char *ExtraStdioDefs(void);

void IncludeInit(Picoc *pc)
{
    IncludeRegister(pc, "ctype.h", NULL, &StdCtypeFunctions[0], NULL);
    IncludeRegister(pc, "errno.h", &StdErrnoSetupFunc, NULL, NULL);
# ifndef NO_FP
    IncludeRegister(pc, "math.h", &ExtraSetupMath, &MathFunctions[0], ExtraMathDefs());
# endif
    IncludeRegister(pc, "stdbool.h", &StdboolSetupFunc, NULL, StdboolDefs);
    IncludeRegister(pc, "stdio.h", &ExtraSetupStdio, &StdioFunctions[0], ExtraStdioDefs());
    IncludeRegister(pc, "stdlib.h", &ExtraSetupStdlib, &StdlibFunctions[0], ExtraStdlibDefs());
    IncludeRegister(pc, "string.h", &ExtraSetupString, &StringFunctions[0], ExtraStringDefs());
    IncludeRegister(pc, "time.h", &ExtraSetupTime, &StdTimeFunctions[0], StdTimeDefs);
    IncludeRegister(pc, "unistd.h", &ExtraSetupUnistd, &UnistdFunctions[0], UnistdDefs);
    ExtraIncludeInit(pc);
}

/* clean up space used by the include system */
void IncludeCleanup(Picoc *pc)
{
    struct IncludeLibrary *ThisInclude = pc->IncludeLibList;
    struct IncludeLibrary *NextInclude;

    while (ThisInclude != NULL) {
        NextInclude = ThisInclude->NextLib;
        HeapFreeMem(pc, ThisInclude);
        ThisInclude = NextInclude;
    }

    pc->IncludeLibList = NULL;
}

/* register a new build-in include file */
void IncludeRegister(Picoc *pc, const char *IncludeName,
    void (*SetupFunction)(Picoc *pc), struct LibraryFunction *FuncList,
    const char *SetupCSource)
{
    struct IncludeLibrary *NewLib = HeapAllocMem(pc, sizeof(struct IncludeLibrary));
    NewLib->IncludeName = TableStrRegister(pc, IncludeName);
    NewLib->SetupFunction = SetupFunction;
    NewLib->FuncList = FuncList;
    NewLib->SetupCSource = SetupCSource;
    NewLib->NextLib = pc->IncludeLibList;
    pc->IncludeLibList = NewLib;
}

/* include all of the system headers */
void PicocIncludeAllSystemHeaders(Picoc *pc)
{
    struct IncludeLibrary *ThisInclude = pc->IncludeLibList;

    for (; ThisInclude != NULL; ThisInclude = ThisInclude->NextLib)
        IncludeFile(pc, ThisInclude->IncludeName);
}

/* include one of a number of predefined libraries, or perhaps an actual file */
void IncludeFile(Picoc *pc, char *FileName)
{
    struct IncludeLibrary *LInclude;

    FileName = (char *)TableStrRegister(pc, FileName);

    /* scan for the include file name to see if it's in our list
        of predefined includes */
    for (LInclude = pc->IncludeLibList; LInclude != NULL;
            LInclude = LInclude->NextLib) {
        if (strcmp(LInclude->IncludeName, FileName) == 0) {
            /* found it - protect against multiple inclusion */
            if (!VariableDefined(pc, FileName)) {
                VariableDefine(pc, NULL, FileName, NULL, &pc->VoidType, false);

                /* run an extra startup function if there is one */
                if (LInclude->SetupFunction != NULL)
                    (*LInclude->SetupFunction)(pc);

                /* parse the setup C source code - may define types etc. */
                if (LInclude->SetupCSource != NULL)
                    PicocParse(pc, FileName, LInclude->SetupCSource,
                        strlen(LInclude->SetupCSource), true, true, false, false);

                /* set up the library functions */
                if (LInclude->FuncList != NULL)
                    LibraryAdd(pc, LInclude->FuncList);
            }

            return;
        }
    }

    /* unknown header: ignore it silently (C Lab has no file system headers) */
}

