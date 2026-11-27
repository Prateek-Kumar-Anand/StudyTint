# R8 renames and removes everything it can. Only what is found by name at run time is listed here.

# The C engine: native code looks up this class and its methods by name (Java_com_student_smartstudy_CEngine_*).
-keep class com.student.smartstudy.CEngine { *; }

# Chaquopy (Python) reaches its own Java classes by name from Python and native code.
-keep class com.chaquo.python.** { *; }

# Hide the original file names but keep line numbers, so a crash report can still be decoded with
# app/build/outputs/mapping/release/mapping.txt (keep that file private).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-repackageclasses ''
