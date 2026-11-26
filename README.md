<div align="center">

<img src="app/src/main/res/drawable-nodpi/logo_full.png" alt="StudyTint" width="220">

<br>

# StudyTint

**Notes. Quizzes. Three live coding labs. One pocket-sized study companion.**

Built for first-semester **BCA (AI & ML)** students. Works fully offline.

<br>

![Version](https://img.shields.io/badge/version-1.02.1-D4A017?style=for-the-badge)
![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack_Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Python](https://img.shields.io/badge/Python-3.12-3776AB?style=for-the-badge&logo=python&logoColor=white)
![SQLite](https://img.shields.io/badge/SQL-SQLite-003B57?style=for-the-badge&logo=sqlite&logoColor=white)
![C](https://img.shields.io/badge/C-Turbo_C_style-2FA37A?style=for-the-badge&logo=c&logoColor=white)

[**Features**](#-features) · [**Subjects**](#-subjects) · [**Install**](#-install) · [**Your data**](#-your-data-and-privacy) · [**Build**](#-build-from-source) · [**Project layout**](#-project-layout) · [**Contribute**](#-adding-notes-and-questions) · [**Release**](#-shipping-an-update) · [**Security**](#-security)

</div>

---

## ✨ Features

<table>
<tr>
<td width="50%" valign="top">

### 📖 Study
Detailed, exam-oriented notes for every unit: definitions, worked examples, common-mistake warnings, exam tips, revision tables and practice questions. Comparison tables and truth tables are real tables with a header row and grid lines. Your reading progress is remembered.

</td>
<td width="50%" valign="top">

### 🎯 Practise
Multiple-choice quizzes by unit or by topic, with an explanation after every answer: **1,380+ questions** (Maths 488, C 372, Python 200+, DBMS 175, AI 150+).

</td>
</tr>
<tr>
<td valign="top">

### 📊 Score
A card-based dashboard: a reading-progress bar, a ranked list of your **strongest and weakest topics** with progress rings, and a petal chart of how each subject is going, so you know what to revise next.

</td>
<td valign="top">

### 🏠 Home and profile
A compact Home screen with your profile (photo kept on the device only) and a **Daily activity** dot grid showing the days you studied. A white and gold look with dark status-bar icons, so the clock and battery are always visible.

</td>
</tr>
</table>

### 🧪 Three labs, built in

| Lab | What you can do | Under the hood |
|---|---|---|
| **C Lab** | Write and run C with no `#include` lines. Errors and warnings appear the **Turbo C way** (`Undefined symbol 'x' in function main`), including linker errors and run-time messages. Type input right in the output box, like a terminal. | A native C engine (picoc plus a Turbo C style checker) called through JNI, run in a separate process |
| **Python Lab** | Write and run Python with real error messages. Import your own `.py`, `.zip`, `.whl` or folders. **Get online** searches PyPI and saves pure-Python libraries, checksum-verified. | [Chaquopy](https://chaquo.com/chaquopy/), Python 3.12 |
| **DBMS Lab** | Write SQL and see results as a table. JOINs (including RIGHT and FULL), window functions, views, triggers and transactions. `SHOW TABLES`, `DESC` and `TRUNCATE` work like MySQL. Sample **College, Company, Library and Bank** databases, or bring your own `.sql` / `.db`. | SQLite through Python's `sqlite3` |

<br>

## 📚 Subjects

Five subjects, four units each, **570+ study sections** and **68 tables** in total.

| Subject | Units |
|---|---|
| **Python** | Programming fundamentals · Data structures and functions · OOP, modules and file I/O · Advanced libraries and exceptions (NumPy, pandas, requests) |
| **Maths** | Sets, relations and functions · Counting and recurrence · Matrix algebra · Elementary graph theory |
| **C** | Problem solving and C fundamentals · Operators, control flow and type conversion · Arrays, strings, functions and recursion · Pointers, structures, memory and files |
| **Exploration in AI** | AI foundations · Problem solving and search · Knowledge representation and expert systems · Machine learning and AI ethics |
| **DBMS** | Database foundations and design · Relational algebra and SQL · Normalization and transactions · Security and emerging trends (NoSQL, distributed systems, data warehousing, vector databases, blockchain, DPDP Act) |

<br>

## 📲 Install

1. Open the repository's **Actions** tab, pick the latest successful **Build APK** run and download the **StudyTint-apk** artifact. Tagged versions (such as `v1.02.1`) are also published under **Releases**.
2. Unzip if needed and open `StudyTint-vX.Y.Z-buildN.apk` on your phone.
3. Allow *Install unknown apps* when Android asks.

Every build is signed with the same key and carries a higher version code, so a new APK **installs over the old one and keeps your data**.

> **Requirements:** Android 7.0 (API 24) or newer on a 64-bit device (arm64-v8a or x86_64).

<br>

## 🔒 Your data and privacy

StudyTint has **no accounts, no cloud and no analytics**. Everything stays on your phone.

| What | Where it is kept |
|---|---|
| Profile, reading progress, quiz results, daily activity | The app's private settings |
| Profile photo | Private app files |
| SQL databases and query history | Private app files (`files/dbms/`) |
| C Lab temporary files | Private app cache |
| Python scripts and libraries | Internal storage by default, or an app folder on shared storage or the SD card |

- The only network use is the optional PyPI library download in Python Lab (HTTPS, PyPI hosts only).
- Cloud backup and phone-to-phone transfer are switched off, so **uninstalling the app or changing phones removes your progress**.
- The app does not add its own encryption. Android's app sandbox keeps other apps out, but files in the shared-storage or SD-card Python folders can be read by other apps.

<br>

## 🛠 Build from source

You need **JDK 17**, **Python 3.12** on your `PATH` (Chaquopy uses it at build time) and **Gradle 8.7**. The Android SDK is set up automatically on CI.

```bash
git clone <your-fork-url> StudyTint
cd StudyTint
gradle assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

Or push the repository to GitHub: the **Build APK** workflow does the same and attaches the APK to the run.

### Continuous integration

The workflow in `.github/workflows/android.yml`:

- builds a non-debuggable release APK and names it `StudyTint-v<version>-build<run>.apk`;
- uploads the APK (**StudyTint-apk**) and the logs (**build-logs**, including an extracted `errors.log`) as artifacts;
- publishes a **Gradle Build Scan** for every run (the link is in the job summary and the build log);
- creates a GitHub Release with the APK when you push a `v*` tag.

> **Updating an older copy?** Files were renamed and moved into folders. If old files are still in your repository the build fails with *Conflicting declarations*. Run `bash tools/remove_old_files.sh` once (or delete them by hand). The build also removes them automatically.

<br>

## 🗂 Project layout

```text
StudyTint/
├── version.properties                 version name and code (single source of truth)
├── .github/workflows/android.yml      CI: build, artifacts, build scan, release on tags
├── tools/remove_old_files.sh          removes files left over from older layouts
├── docs/PROJECT_TREE.md               every file, what it does, how it handles problems
└── app/src/main/
    ├── java/com/student/smartstudy/
    │   ├── MainActivity.kt            entry point: theme and navigation host
    │   ├── navigation/                screen list, routing and transitions
    │   ├── data/                      progress, profile, subjects, question banks, changelog and migrations
    │   ├── notes/                     note model, JSON loader, renderer (including tables)
    │   ├── ui/                        theme, shared widgets, Home, study, quiz, Score, profile
    │   └── labs/                      C Lab, Python Lab (+ PyPI downloads), DBMS Lab, terminal output box
    ├── cpp/                           native C engine (picoc, Turbo C checker, JNI)
    ├── python/                        dbms_query_engine.py, python_lab_runner.py
    └── assets/
        ├── notes/                     <subject>_unit<N>_notes.json
        ├── questions/                 <subject>_questions.json
        └── sample_databases/          *_database.sql
```

See [`docs/PROJECT_TREE.md`](docs/PROJECT_TREE.md) for the full file-by-file tree, including how each part deals with minor and major problems.

<br>

## 🧩 Adding notes and questions

- **File names** follow one pattern: `<subject>_unit<N>_notes.json` (for example `python_unit2_notes.json`) and `<subject>_questions.json`, with subjects `python`, `maths`, `c`, `ai` and `dbms`.
- **Notes** are JSON files in `app/src/main/assets/notes/`: a `title`, an `intro` and a list of `sec` sections made of simple blocks (paragraphs, headings, lists, code, examples, notes, key-value pairs, glossary, Q&A and tables). Add new sections at the end of a file so existing reading progress stays correct.
- **Tables** are a block `{ "t": "tb", "c": "caption", "h": ["Column 1", "Column 2"], "r": [["a", "b"], ["c", "d"]] }`.
- **Questions** live in `app/src/main/assets/questions/` as `{ "u": unit, "q": question, "o": [4 options], "a": answer index, "s": explanation, "t": topic }`. They are loaded by `loadQuestionBanks` in `data/QuizBank.kt`. To give a subject a bank, add a file and one `loadBank(...)` line.

<br>

## 🚀 Shipping an update

1. Raise `VERSION_CODE` and set `VERSION_NAME` in `version.properties`.
2. Add a release entry (newest first) to the changelog in `data/ReleaseNotesAndMigrations.kt`, so users see **What's new**.
3. If the shape of saved data changes, raise `DATA_VERSION` and add a step to `migrate`.
4. Push. To publish a GitHub Release with the APK, tag it:

```bash
git tag v1.02.1 && git push origin v1.02.1
```

<br>

## 🔐 Security

StudyTint runs your code on your phone, so it was audited and hardened (details in [`SECURITY.md`](SECURITY.md)):

- Library downloads come **only from PyPI** and must match their SHA-256 checksum.
- C programs can only touch files **in their own folder** and run in a separate process, so a crash cannot take the app down.
- The SQL lab blocks `ATTACH` and extensions, so an imported database cannot reach other files.
- Runaway Python programs are stopped by a time and memory watchdog.
- The release build is **not debuggable**, uses HTTPS with system certificates only, and has no cloud backup.

### Use your own signing key (recommended)

The repository includes a fallback signing key so builds work out of the box, but anyone with the repository has that key. To build with a key only you hold, create a keystore and add four **repository secrets** (*Settings, Secrets and variables, Actions*):

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | your `.jks` file, base64-encoded (`base64 -w0 release.jks`) |
| `KEYSTORE_PASSWORD` | the keystore password |
| `KEY_ALIAS` | the key alias |
| `KEY_PASSWORD` | the key password (optional if it equals the keystore password) |

The workflow picks them up automatically. Switching keys changes the app's signature, so Android needs **one fresh install** after the change.

> Only import Python libraries, scripts and `.whl` files you trust. Code you import runs with the app's permissions, as in any code lab.

<br>

## 🩺 Troubleshooting

| Problem | What to do |
|---|---|
| Build fails with *Conflicting declarations* | Old files are still in the repository. Run `bash tools/remove_old_files.sh`, commit and push. |
| The APK will not install over the old app | The signing key changed. Uninstall the old app once, then install the new APK (progress is lost). |
| The build fails and you need the reason | Download **build-logs** from the run and open `errors.log`, or follow the Build Scan link in the job summary. |
| Clock and battery icons are invisible | Use version 1.02.1 or newer, which sets dark status-bar icons. |

<br>

---

<div align="center">

Made for students who want to learn it, write it, run it and revise it, all in one app.

<sub>StudyTint · Kotlin, Jetpack Compose, Chaquopy and C</sub>

</div>
