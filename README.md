<div align="center">

<img src="app/src/main/res/drawable-nodpi/logo_full.png" alt="StudyTint" width="240">

<br>

# StudyTint

**Notes. Quizzes. Three live coding labs. One pocket-sized study companion.**

Built for first-semester **BCA (AI & ML)** students, and it works offline.

<br>

![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack_Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Python](https://img.shields.io/badge/Python-3.12-3776AB?style=for-the-badge&logo=python&logoColor=white)
![SQLite](https://img.shields.io/badge/SQL-SQLite-003B57?style=for-the-badge&logo=sqlite&logoColor=white)
![C](https://img.shields.io/badge/C-Turbo_C_style-2FA37A?style=for-the-badge&logo=c&logoColor=white)

[**Features**](#-features) &nbsp;·&nbsp; [**Subjects**](#-subjects) &nbsp;·&nbsp; [**Install**](#-install) &nbsp;·&nbsp; [**Build**](#-build-from-source) &nbsp;·&nbsp; [**Release**](#-shipping-an-update) &nbsp;·&nbsp; [**Security**](#-security)

</div>

<br>

---

## ✨ Features

<table>
<tr>
<td width="50%" valign="top">

### 📖 Study
Detailed, exam-oriented notes for every unit, with definitions, worked examples, common-mistake warnings, exam tips, revision tables and practice questions. Your reading progress is remembered.

</td>
<td width="50%" valign="top">

### 🎯 Practise
Multiple-choice quizzes by unit or by topic, with an explanation after every answer. **1,380+ questions**: Maths (488), C (372), Python (200+), DBMS (175) and AI (150+).

</td>
</tr>
<tr>
<td valign="top">

### 📊 Score
A soft, card-based dashboard: a reading-progress bar, a **daily activity grid**, a ranked list of your **strongest and weakest topics** with progress rings, and a petal chart showing how each subject is going, so you know what to revise next.

</td>
<td valign="top">

### 👤 Profile
Add a profile photo, stored only on your device.

</td>
</tr>
</table>

### 🧪 Three labs, built in

| Lab | What you can do | Under the hood |
|---|---|---|
| **C Lab** | Write and run C with no `#include` lines. Errors and warnings appear the **Turbo C way** (`Undefined symbol 'x' in function main`), including linker errors and run-time messages. Type input right in the output box like a terminal. | A native C engine (picoc plus a Turbo C style checker) called through JNI |
| **Python Lab** | Write and run Python with real error messages. Import your own `.py`, `.zip`, `.whl` or folders. **Get online** searches PyPI and saves pure-Python libraries, checksum-verified. | [Chaquopy](https://chaquo.com/chaquopy/), Python 3.12 |
| **DBMS Lab** | Write SQL and see results as a table. JOINs (including RIGHT and FULL), window functions, views, triggers and transactions. `SHOW TABLES`, `DESC` and `TRUNCATE` work like MySQL. Sample **College, Company, Library and Bank** databases, or bring your own `.sql` / `.db`. | A current SQLite through Python's `sqlite3` |

<br>

## 📚 Subjects

<div align="center">

![Python](https://img.shields.io/badge/Python-7C6CF0?style=flat-square)
![Maths](https://img.shields.io/badge/Maths-F2894E?style=flat-square)
![C](https://img.shields.io/badge/C_Programming-2FA37A?style=flat-square)
![AI](https://img.shields.io/badge/Exploration_in_AI-C2457F?style=flat-square)
![DBMS](https://img.shields.io/badge/DBMS-3B9BE0?style=flat-square)

</div>

Five subjects, four units each, **570+ study sections** in total.

| Subject | Units |
|---|---|
| **Python** | Programming fundamentals · Data structures and functions · OOP, modules and file I/O · Advanced libraries and exceptions (NumPy, pandas, requests) |
| **Maths** | Sets, relations and functions · Counting and recurrence · Matrix algebra · Elementary graph theory |
| **C** | Problem solving and C fundamentals · Operators, control flow and type conversion · Arrays, strings, functions and recursion · Pointers, structures, memory and files |
| **Exploration in AI** | AI foundations · Problem solving and search · Knowledge representation and expert systems · Machine learning and AI ethics |
| **DBMS** | Database foundations and design · Relational algebra and SQL · Normalization and transactions · Security and emerging trends (NoSQL, distributed systems, data warehousing, vector databases, blockchain, DPDP Act) |

<br>

## 📲 Install

1. Open the repository's **Actions** tab, choose the latest successful **Build APK** run, and download the **StudyTint-apk** artifact.
   Tagged versions (`v1.5.2` and so on) are also published under **Releases**.
2. Unzip if needed and open `StudyTint-vX.Y.Z-buildN.apk` on your phone.
3. Allow *Install unknown apps* when Android asks.

Every build is signed with the same key and carries a higher version code, so a new APK **installs over the old one and keeps your data**.

> **Requirements:** Android 7.0 (API 24) or newer, on a 64-bit device (arm64-v8a or x86_64).

<br>

## 🛠 Build from source

You need **JDK 17**, **Python 3.12** on your `PATH` (Chaquopy uses it at build time) and **Gradle 8.7**. The Android SDK is picked up automatically on CI.

```bash
git clone <your-fork-url> StudyTint
cd StudyTint
gradle assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

Or just push the repository to GitHub: the **Build APK** workflow does the same thing and attaches the APK to the run.

<details>
<summary><b>Project layout</b></summary>

<br>

```text
StudyTint/
├── version.properties            single source of truth for version name and code
├── .github/workflows/android.yml CI: build, upload APK, release on tags
└── app/src/main/
    ├── java/com/student/smartstudy/
    │   ├── Ui.kt, Dashboard.kt   home, study, quiz and score screens
    │   ├── Rich.kt, RichData.kt  note renderer and loader
    │   ├── Data.kt               subjects, units and the question-bank loader
    │   ├── CLab.kt, Console.kt   C Lab and the terminal-style output
    │   ├── PyLab.kt, PyOnline.kt Python Lab and PyPI downloads
    │   ├── DbLab.kt, DbEngine.kt DBMS Lab
    │   └── Updates.kt            changelog and saved-data migrations
    ├── cpp/                      native C engine (picoc, Turbo C checker, JNI)
    ├── python/                   dbms_engine.py, pylab_runner.py
    └── assets/                   study notes (JSON), question banks, sample databases
```

</details>

<details>
<summary><b>Adding notes and questions</b></summary>

<br>

- **File names** follow one pattern: `<subject>_unit<N>_notes.json` (for example `python_unit2_notes.json`) and `<subject>_questions.json`, with subjects `python`, `maths`, `c`, `ai` and `dbms`. Files starting with `legacy_` are older copies that the app no longer loads.
- **Notes** are JSON files in `app/src/main/assets/`, each a `title`, an `intro` and a list of `sec` sections made of simple blocks (paragraphs, lists, code, notes, tables and Q&A). Add new sections at the end of a file so existing reading progress stays correct.
- **Questions** live in `maths_questions.json`, `c_questions.json`, `python_questions.json`, `dbms_questions.json` and `ai_questions.json`, as `{ "u": unit, "q": question, "o": [4 options], "a": answer index, "s": explanation, "t": topic }`. They are loaded by `loadQuestionBanks` in `Data.kt`. To give a subject a bank, add a file and one `loadBank(...)` line.

</details>

<br>

## 🚀 Shipping an update

1. Raise `VERSION_CODE` and set `VERSION_NAME` in `version.properties`.
2. Add a release entry (newest first) to the changelog in `Updates.kt`, so users see **What's new**.
3. If the shape of saved data changes, raise `DATA_VERSION` and add a step to `migrate`.
4. Push. To publish a GitHub Release with the APK, tag it:

```bash
git tag v1.5.3 && git push origin v1.5.3
```

<br>

## 🔐 Security

StudyTint runs your code on your phone, so it was audited and hardened (details in [`SECURITY.md`](SECURITY.md)):

- Library downloads come **only from PyPI** and must match their SHA-256 checksum.
- C programs can only touch files **in their own folder**.
- The SQL lab blocks `ATTACH` and extensions, so an imported database cannot reach other files.
- The release build is **not debuggable**, uses HTTPS with system certificates only, and has no cloud backup.
- Runaway Python programs are stopped before they can crash the app.

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

---

<div align="center">

Made for students who want to learn it, write it, run it and revise it, all in one app.

<sub>StudyTint · Kotlin, Jetpack Compose, Chaquopy and C</sub>

</div>
