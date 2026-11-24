#!/usr/bin/env bash
# Removes files that were renamed or moved (old copies cause "Conflicting declarations" build errors).
# Run from the repository root:  bash tools/remove_old_files.sh   then commit and push.
# The same list is applied automatically before every build (see outdatedFiles in app/build.gradle.kts).
set -u
cd "$(dirname "$0")/.."
for f in \
  "app/src/main/assets/Artificial_Intelligence_Foundations_Study_Notes.json" \
  "app/src/main/assets/C_Arrays_Strings_Functions_Recursion_Notes.json" \
  "app/src/main/assets/C_Operators_Control_Flow_Type_Conversion_Notes.json" \
  "app/src/main/assets/C_Pointers_Structures_Memory_Files_Preprocessor_Notes.json" \
  "app/src/main/assets/Database_Foundations_and_Design_Detailed_Study_Notes.json" \
  "app/src/main/assets/Database_Security_and_Emerging_Trends_Detailed_Study_Notes.json" \
  "app/src/main/assets/Introduction_to_Machine_Learning_and_AI_Ethics_Study_Notes.json" \
  "app/src/main/assets/Introduction_to_Problem_Solving_and_C_Fundamentals_Detailed_Study_Notes.json" \
  "app/src/main/assets/Knowledge_Representation_and_Expert_Systems_Study_Notes.json" \
  "app/src/main/assets/Normalization_and_Transaction_Management_Detailed_Study_Notes.json" \
  "app/src/main/assets/Python_Advanced_Libraries_Exceptions_System_Scripting_Requests_NumPy_Pandas_Study_Notes.json" \
  "app/src/main/assets/Python_Data_Structures_and_Functions_Detailed_Study_Notes.json" \
  "app/src/main/assets/Python_OOP_Modules_File_IO_and_Log_Analysis_Study_Notes.json" \
  "app/src/main/assets/Python_Programming_Fundamentals_Detailed_Study_Notes.json" \
  "app/src/main/assets/Relational_Algebra_and_SQL_Mastery_Detailed_Study_Notes.json" \
  "app/src/main/assets/Solving_and_Search_Basics_AI_Study_Notes.json" \
  "app/src/main/assets/counting_and_recurrence_relations_detailed_notes.json" \
  "app/src/main/assets/db_questions.json" \
  "app/src/main/assets/elementary_graph_theory_detailed_notes.json" \
  "app/src/main/assets/math_questions.json" \
  "app/src/main/assets/matrix_algebra_comprehensive_detailed_notes.json" \
  "app/src/main/assets/questions-math.json" \
  "app/src/main/assets/rich_ai_0.json" \
  "app/src/main/assets/rich_ai_1.json" \
  "app/src/main/assets/rich_ai_2.json" \
  "app/src/main/assets/rich_ai_3.json" \
  "app/src/main/assets/rich_db_0.json" \
  "app/src/main/assets/rich_db_1.json" \
  "app/src/main/assets/rich_db_2.json" \
  "app/src/main/assets/rich_db_3.json" \
  "app/src/main/assets/rich_ma_0.json" \
  "app/src/main/assets/rich_ma_1.json" \
  "app/src/main/assets/rich_ma_2.json" \
  "app/src/main/assets/rich_ma_3.json" \
  "app/src/main/assets/sets_relations_functions_detailed_notes.json" \
  "app/src/main/cpp/clab_engine.c" \
  "app/src/main/cpp/clab_jni.c" \
  "app/src/main/cpp/clab_proc.c" \
  "app/src/main/cpp/clab_proc.h" \
  "app/src/main/cpp/tc.h" \
  "app/src/main/cpp/tc_int.h" \
  "app/src/main/cpp/tc_lib.c" \
  "app/src/main/cpp/tc_parse.c" \
  "app/src/main/cpp/tc_pp.c" \
  "app/src/main/cpp/tc_tools/gen_tc_lib.py" \
  "app/src/main/cpp/tc_tools/tc_headers.txt" \
  "app/src/main/java/com/student/smartstudy/CLab.kt" \
  "app/src/main/java/com/student/smartstudy/Console.kt" \
  "app/src/main/java/com/student/smartstudy/Dashboard.kt" \
  "app/src/main/java/com/student/smartstudy/Data.kt" \
  "app/src/main/java/com/student/smartstudy/DbEngine.kt" \
  "app/src/main/java/com/student/smartstudy/DbLab.kt" \
  "app/src/main/java/com/student/smartstudy/Motion.kt" \
  "app/src/main/java/com/student/smartstudy/Profile.kt" \
  "app/src/main/java/com/student/smartstudy/PyLab.kt" \
  "app/src/main/java/com/student/smartstudy/PyOnline.kt" \
  "app/src/main/java/com/student/smartstudy/Rich.kt" \
  "app/src/main/java/com/student/smartstudy/RichData.kt" \
  "app/src/main/java/com/student/smartstudy/Ui.kt" \
  "app/src/main/java/com/student/smartstudy/Updates.kt" \
  "app/src/main/python/dbms_engine.py" \
  "app/src/main/python/pylab_runner.py" \
  "app/src/main/assets/dbms" \
  "app/src/main/python/__pycache__" \
  "app/src/main/java/com/student/smartstudy/AppScreens.kt" \
  "app/src/main/java/com/student/smartstudy/CProgrammingLab.kt" \
  "app/src/main/java/com/student/smartstudy/DbmsEngine.kt" \
  "app/src/main/java/com/student/smartstudy/DbmsLab.kt" \
  "app/src/main/java/com/student/smartstudy/NotesLoader.kt" \
  "app/src/main/java/com/student/smartstudy/NotesRenderer.kt" \
  "app/src/main/java/com/student/smartstudy/PressAnimation.kt" \
  "app/src/main/java/com/student/smartstudy/PythonLab.kt" \
  "app/src/main/java/com/student/smartstudy/PythonOnlineLibraries.kt" \
  "app/src/main/java/com/student/smartstudy/ReleaseNotesAndMigrations.kt" \
  "app/src/main/java/com/student/smartstudy/ScoreDashboard.kt" \
  "app/src/main/java/com/student/smartstudy/SubjectsAndQuizData.kt" \
  "app/src/main/java/com/student/smartstudy/TerminalConsole.kt" \
  "app/src/main/java/com/student/smartstudy/UserProfile.kt" \
  "app/src/main/assets/maths_unit1_notes.json" \
  "app/src/main/assets/maths_unit2_notes.json" \
  "app/src/main/assets/maths_unit3_notes.json" \
  "app/src/main/assets/maths_unit4_notes.json" \
  "app/src/main/assets/dbms_unit1_notes.json" \
  "app/src/main/assets/dbms_unit2_notes.json" \
  "app/src/main/assets/dbms_unit3_notes.json" \
  "app/src/main/assets/dbms_unit4_notes.json" \
  "app/src/main/assets/ai_unit1_notes.json" \
  "app/src/main/assets/ai_unit2_notes.json" \
  "app/src/main/assets/ai_unit3_notes.json" \
  "app/src/main/assets/ai_unit4_notes.json" \
  "app/src/main/assets/python_unit1_notes.json" \
  "app/src/main/assets/python_unit2_notes.json" \
  "app/src/main/assets/python_unit3_notes.json" \
  "app/src/main/assets/python_unit4_notes.json" \
  "app/src/main/assets/c_unit1_notes.json" \
  "app/src/main/assets/c_unit2_notes.json" \
  "app/src/main/assets/c_unit3_notes.json" \
  "app/src/main/assets/c_unit4_notes.json" \
  "app/src/main/assets/maths_questions.json" \
  "app/src/main/assets/dbms_questions.json" \
  "app/src/main/assets/ai_questions.json" \
  "app/src/main/assets/python_questions.json" \
  "app/src/main/assets/c_questions.json" \
  "app/src/main/assets/questions-c.json"
do
  if [ -e "$f" ]; then git rm -r -q -f "$f" 2>/dev/null || rm -rf "$f"; echo "removed $f"; fi
done
# The old key file name is replaced by app/studytint_signing_key.jks (same key); remove the old copy only if the new one is present.
if [ -f app/studytint_signing_key.jks ] && [ -f app/studytint.jks ]; then git rm -q -f app/studytint.jks 2>/dev/null || rm -f app/studytint.jks; echo "removed app/studytint.jks"; fi
echo "Done. Commit and push the changes."
