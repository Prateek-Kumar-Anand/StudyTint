import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

// Version comes from version.properties so an update is a one-file change.
val versionProps = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }
// CI builds add the run number, so every CI build is newer than the previous one and installs over it.
val ciBuild = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
android {
    namespace = "com.student.smartstudy"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.student.smartstudy"
        minSdk = 24; targetSdk = 34
        versionCode = versionProps.getProperty("VERSION_CODE").trim().toInt() * 10000 + ciBuild
        versionName = versionProps.getProperty("VERSION_NAME").trim()
        // Chaquopy 17 / Python 3.12 only ships arm64-v8a and x86_64 (32-bit ARM was dropped),
        // so armeabi-v7a must not be listed or Gradle fails at configuration time.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    // A fixed signing key keeps every build installable over the previous one (updates keep your data).
    signingConfigs {
        create("studytint") {
            // SECURITY: the key in the repository is public. For a build nobody else can sign, put your own key in
            // GitHub Secrets (see the README) and these variables take over; the repository key is only the fallback.
            storeFile = System.getenv("STUDYTINT_KEYSTORE")?.takeIf { it.isNotBlank() }?.let { file(it) } ?: file("studytint_signing_key.jks")
            storePassword = System.getenv("STUDYTINT_STORE_PASSWORD") ?: "studytint"
            keyAlias = System.getenv("STUDYTINT_KEY_ALIAS") ?: "studytint"
            keyPassword = System.getenv("STUDYTINT_KEY_PASSWORD") ?: "studytint"
        }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("studytint") }
        // The release build is what CI ships: not debuggable (nobody can attach a debugger or read the app's data with run-as)
        // and noticeably smoother than a debug build. Code shrinking stays off because the C engine is called through JNI by name.
        release {
            signingConfig = signingConfigs.getByName("studytint")
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    lint { abortOnError = false }
}
chaquopy {
    defaultConfig {
        version = "3.12"
        buildPython("python3")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
}

// Some files were renamed to full, descriptive names (for example Ui.kt is now AppScreens.kt). If the old files are still in the
// repository (for example the new files were copied over the old project without deleting anything), the old and new copies
// declare the same things and the build fails with "Conflicting declarations". These leftovers are deleted before every build.
// They are listed in tools/remove_old_files.sh too, which also removes them from Git.
val outdatedFiles = listOf(
    "src/main/assets/Artificial_Intelligence_Foundations_Study_Notes.json",
    "src/main/assets/C_Arrays_Strings_Functions_Recursion_Notes.json",
    "src/main/assets/C_Operators_Control_Flow_Type_Conversion_Notes.json",
    "src/main/assets/C_Pointers_Structures_Memory_Files_Preprocessor_Notes.json",
    "src/main/assets/Database_Foundations_and_Design_Detailed_Study_Notes.json",
    "src/main/assets/Database_Security_and_Emerging_Trends_Detailed_Study_Notes.json",
    "src/main/assets/Introduction_to_Machine_Learning_and_AI_Ethics_Study_Notes.json",
    "src/main/assets/Introduction_to_Problem_Solving_and_C_Fundamentals_Detailed_Study_Notes.json",
    "src/main/assets/Knowledge_Representation_and_Expert_Systems_Study_Notes.json",
    "src/main/assets/Normalization_and_Transaction_Management_Detailed_Study_Notes.json",
    "src/main/assets/Python_Advanced_Libraries_Exceptions_System_Scripting_Requests_NumPy_Pandas_Study_Notes.json",
    "src/main/assets/Python_Data_Structures_and_Functions_Detailed_Study_Notes.json",
    "src/main/assets/Python_OOP_Modules_File_IO_and_Log_Analysis_Study_Notes.json",
    "src/main/assets/Python_Programming_Fundamentals_Detailed_Study_Notes.json",
    "src/main/assets/Relational_Algebra_and_SQL_Mastery_Detailed_Study_Notes.json",
    "src/main/assets/Solving_and_Search_Basics_AI_Study_Notes.json",
    "src/main/assets/counting_and_recurrence_relations_detailed_notes.json",
    "src/main/assets/db_questions.json",
    "src/main/assets/elementary_graph_theory_detailed_notes.json",
    "src/main/assets/math_questions.json",
    "src/main/assets/matrix_algebra_comprehensive_detailed_notes.json",
    "src/main/assets/questions-math.json",
    "src/main/assets/rich_ai_0.json",
    "src/main/assets/rich_ai_1.json",
    "src/main/assets/rich_ai_2.json",
    "src/main/assets/rich_ai_3.json",
    "src/main/assets/rich_db_0.json",
    "src/main/assets/rich_db_1.json",
    "src/main/assets/rich_db_2.json",
    "src/main/assets/rich_db_3.json",
    "src/main/assets/rich_ma_0.json",
    "src/main/assets/rich_ma_1.json",
    "src/main/assets/rich_ma_2.json",
    "src/main/assets/rich_ma_3.json",
    "src/main/assets/sets_relations_functions_detailed_notes.json",
    "src/main/cpp/clab_engine.c",
    "src/main/cpp/clab_jni.c",
    "src/main/cpp/clab_proc.c",
    "src/main/cpp/clab_proc.h",
    "src/main/cpp/tc.h",
    "src/main/cpp/tc_int.h",
    "src/main/cpp/tc_lib.c",
    "src/main/cpp/tc_parse.c",
    "src/main/cpp/tc_pp.c",
    "src/main/cpp/tc_tools/gen_tc_lib.py",
    "src/main/cpp/tc_tools/tc_headers.txt",
    "src/main/java/com/student/smartstudy/CLab.kt",
    "src/main/java/com/student/smartstudy/Console.kt",
    "src/main/java/com/student/smartstudy/Dashboard.kt",
    "src/main/java/com/student/smartstudy/Data.kt",
    "src/main/java/com/student/smartstudy/DbEngine.kt",
    "src/main/java/com/student/smartstudy/DbLab.kt",
    "src/main/java/com/student/smartstudy/Motion.kt",
    "src/main/java/com/student/smartstudy/Profile.kt",
    "src/main/java/com/student/smartstudy/PyLab.kt",
    "src/main/java/com/student/smartstudy/PyOnline.kt",
    "src/main/java/com/student/smartstudy/Rich.kt",
    "src/main/java/com/student/smartstudy/RichData.kt",
    "src/main/java/com/student/smartstudy/Ui.kt",
    "src/main/java/com/student/smartstudy/Updates.kt",
    "src/main/python/dbms_engine.py",
    "src/main/python/pylab_runner.py",
    "src/main/assets/dbms",
    "src/main/python/__pycache__",
)
val removeOutdatedFiles = tasks.register("removeOutdatedFiles") {
    doLast {
        outdatedFiles.forEach { path ->
            val f = file(path)
            if (f.exists()) { f.deleteRecursively(); println("Removed outdated file: $path") }
        }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(removeOutdatedFiles) }
