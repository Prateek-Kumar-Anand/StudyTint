<div align="center">

# StudyTint

**A smart, offline study companion for Android.**
Detailed notes, practice quizzes, progress analytics and a built-in C compiler, all in one app.

![Platform](https://img.shields.io/badge/platform-Android%207.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Build](https://img.shields.io/badge/CI-GitHub%20Actions-2088FF?logo=githubactions&logoColor=white)

</div>

---

## Overview

StudyTint organises five university subjects into units and gives every unit a full set of
notes and a quiz bank. Everything is bundled inside the app, so it works without an internet
connection and your progress never leaves the device.

| Subject | Units |
| --- | --- |
| **Python** | Fundamentals · Data Structures & Functions · OOP, Modules & File I/O · Advanced Libraries & Exceptions |
| **Maths** | Sets, Relations & Functions · Counting & Recurrence · Matrix Algebra · Elementary Graph Theory |
| **C** | Problem Solving & Fundamentals · Operators & Control Flow · Arrays, Strings & Recursion · Pointers, Structures & Files |
| **Exploration in AI** | AI Foundations · Problem Solving & Search · Knowledge Representation · Machine Learning & Ethics |
| **DBMS** | Foundations & Design · Relational Algebra & SQL · Normalization & Transactions · Security & Emerging Trends |

## Features

- **Detailed notes.** Each unit is split into topics with definitions, worked examples, callouts,
  glossaries, tables and code blocks. Reading time and per-topic progress are tracked.
- **Practice quizzes.** Multiple-choice banks per unit with instant feedback, explanations and
  topic-wise drill-down.
- **Score and analysis.** Accuracy, quizzes taken, reading progress, plus your strongest and weakest topics.
- **C Lab.** Write, run and test C programs on your phone using an embedded
  [picoc](https://github.com/zsaleeba/picoc) interpreter, with a syntax-highlighted editor,
  sample programs, input support and a run timeout. In **Turbo C** mode (the default) a front end written for this app
  reads the whole program first, like Turbo C++ 3.0 compiling `NONAME00.C`, and prints Turbo C style errors, warnings
  and linker errors; only a program with no errors is run, and run-time failures print Turbo C's messages
  (`Divide error`, `Null pointer assignment`, ...). A **gcc style** switch keeps the earlier behaviour.
- **Python Lab.** Write and run real Python 3.12 (CPython via [Chaquopy](https://chaquo.com/chaquopy/)) with genuine
  tracebacks, `input()` support, a time limit and a Stop button. Choose where its files live (internal storage,
  phone storage or SD card), import pure-Python libraries from a folder, `.py`, `.zip` or `.whl`, and save scripts.
- **DBMS Lab.** Write SQL and get the results as a table, on a real, current SQLite (through the same Python engine). Run many
  statements at once or just the part you select, with errors explained in plain English (and hints for MySQL habits such as
  `AUTO_INCREMENT`, `TOP`, `NOW()`). Supports JOINs including RIGHT/FULL, window functions, views, triggers and `BEGIN` / `COMMIT` /
  `ROLLBACK`; `SHOW TABLES`, `DESC` and `TRUNCATE` work like MySQL. Keep several databases, start from College, Company, Library or
  Bank samples, browse tables, columns and keys, reuse past queries, open `.sql` / `.db` files and share a database as SQL.
- **Personal profile.** Your name, course and roll details are shown on the home screen.
- **Private by design.** No account. Data is stored locally. The internet is used only when you open **Get online** in Python Lab to download libraries from PyPI.

## Tech stack

| Area | Details |
| --- | --- |
| Language | Kotlin 1.9, C11 (native) |
| UI | Jetpack Compose, Material 3, `AnimatedContent` navigation |
| Native | NDK + CMake, picoc interpreter exposed through JNI |
| Python | CPython 3.12 through Chaquopy (`app/src/main/python`) |
| Content | JSON notes and question banks in `app/src/main/assets` |
| Persistence | `SharedPreferences` |
| Build | Gradle 8.7, Android Gradle Plugin 8.5, JDK 17 |
| Target | `minSdk 24`, `targetSdk 34`, ABIs `arm64-v8a`, `x86_64` (Chaquopy Python 3.12 has no 32-bit ARM) |

## Project structure

```text
StudyTint/
├── app/src/main/
│   ├── java/com/student/smartstudy/
│   │   ├── MainActivity.kt    # Entry point and progress storage
│   │   ├── Ui.kt              # Navigation, home, pick and quiz screens
│   │   ├── Rich.kt            # Notes renderer (topics, code blocks, callouts)
│   │   ├── RichData.kt        # JSON notes model and loader
│   │   ├── Data.kt            # Subjects, units and quiz banks
│   │   ├── Dashboard.kt       # Score and analysis screen
│   │   ├── Profile.kt         # Profile card and setup form
│   │   ├── CLab.kt            # C Lab editor and runner (Turbo C / gcc mode switch)
│   │   ├── PyLab.kt           # Python Lab: editor, storage choice, library import
│   │   ├── DbLab.kt           # DBMS Lab screen: editor, results table, tables, history
│   │   ├── DbEngine.kt        # DBMS Lab bridge to Python and database storage
│   │   ├── Updates.kt         # Data migrations and "What's new"
│   │   └── Motion.kt          # Shared tap and press animation
│   ├── python/                # Python Lab runner (pylab_runner.py) and SQL engine (dbms_engine.py)
│   ├── cpp/                   # picoc interpreter, Turbo C checker (tc_*.c), JNI bridge and CMake build
│   ├── assets/                # Notes and question banks (JSON); dbms/ holds the sample databases (SQL)
│   └── res/                   # Icons, colours, theme
├── .github/workflows/android.yml
├── version.properties         # App version (single source of truth)
└── build.gradle.kts
```

## Notes format

Notes are plain JSON, so adding or editing content needs no Kotlin changes.

```json
{
  "title": "Unit title",
  "intro": "One-line summary shown on the unit header.",
  "sec": [
    {
      "n": 1, "t": "Topic title", "k": "th", "s": "Short summary",
      "b": [
        { "t": "p",    "x": "A paragraph." },
        { "t": "code", "x": ["for i in range(3):", "    print(i)"] }
      ]
    }
  ]
}
```

Block types: `p` paragraph, `h` heading, `ul` / `ol` lists, `code` code block, `ln` formula lines,
`ex` example box, `note` callout, `kv` key-value table, `gl` glossary and `qa` question and answer.
Inside `ex` and `note` blocks, a line starting with the `\u0001` character is rendered as code.

Quiz banks are JSON arrays of objects with the keys `u` (unit), `q` (question), `o` (options),
`a` (answer index), `s` (solution) and `t` (topic).

## Build

### GitHub Actions

1. Push the repository to GitHub.
2. Open the **Actions** tab and run **Build APK** (it also runs on every push).
3. Download **StudyTint-debug-apk** from the run's artifacts.
4. If a build fails, download **build-logs** (`build.log`, `errors.log` and the Gradle reports).

### Locally

Requirements: JDK 17, Android SDK 34, and the NDK and CMake from the SDK Manager.

```bash
gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

You can also open the folder in Android Studio (Koala or newer) and run it on a device or emulator.

## Releasing an update

Every build is signed with the same key (`app/studytint.jks`), so a new APK installs over the old one and keeps
all data. To ship a new version:

1. In `version.properties`, raise `VERSION_CODE` by 1 and set `VERSION_NAME`.
2. Add a `Release` entry at the top of `changelog` in `Updates.kt`; users see it once as "What's new".
3. If saved data changes shape, raise `DATA_VERSION` and add a step in `Updates.migrate`.
4. Push. For a GitHub Release with the APK attached, tag the commit, for example `git tag v1.2.0 && git push --tags`.

CI builds add the run number to the version code, so each build is newer than the last. The included keystore is
meant for personal sideloading; use your own private keystore if you publish to an app store.

## Licence

The embedded picoc interpreter is distributed under its own licence, found in
`app/src/main/cpp/picoc/LICENSE`.
