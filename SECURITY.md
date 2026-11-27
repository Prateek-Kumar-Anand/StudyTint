# StudyTint security audit (v1.5.2, re-checked in the latest release)

Scope: the Android app, its C engine (picoc + Turbo C checker), the Python Lab and DBMS Lab engines, the build and CI setup.
Method: code review of every place that touches the network, files, native code, imported data or the build. The Android build
could not be run during the audit; Python engines were tested, Kotlin was type-checked against stand-in declarations.

## Fixed in 1.5.2

| # | Finding | Risk | Fix |
|---|---------|------|-----|
| 1 | Library downloads followed redirects and URLs from PyPI's answer to any https host | A hijacked answer could send the app to a malicious server; the checksum came from the same answer | Only `pypi.org`, `*.pypi.org` and `files.pythonhosted.org` are contacted; redirects are followed by hand and every hop is checked |
| 2 | A download was accepted when PyPI gave no checksum | Unverified code could be saved and later imported | Files without a SHA-256 are never offered, and the checksum must match |
| 3 | C programs could open, overwrite, rename or delete any file the app can reach (absolute paths, `..`) | A pasted program could wipe the profile, databases or scripts | `fopen`, `freopen`, `remove` and `rename` only accept relative paths without `..`; core dumps disabled |
| 4 | SQL lab: `ATTACH DATABASE`, `VACUUM INTO` and extensions could create or read files anywhere in the app's storage; imported `.db` files could carry hostile triggers or views | File overwrite, data theft from other databases | `ATTACH` blocked (limit 0 + authorizer), `VACUUM INTO` refused, extensions off, defensive mode and untrusted schema on, size limits |
| 5 | Importing a file read it fully into memory | Out-of-memory crash on a huge file | Import reads at most 40 MB |
| 6 | The CI build was a debuggable APK | Anyone with USB debugging or `run-as` could read the app's private data; slower | CI builds a non-debuggable release APK signed with the same key (updates install over old versions) |
| 7 | CI gave every job write access to the repository | A compromised action or a malicious pull request could modify the repo | Build job is read-only; only the release job, only for tags, can write |
| 8 | No explicit network and backup policy | Cleartext or user-installed CAs, data copied to cloud or a new phone | `network_security_config` (https, system CAs only) and `data_extraction_rules` (nothing copied) |
| 9 | A Python program could allocate memory until Android killed the whole app | Crash and lost state | Watchdog stops the program above about 45% of the phone's RAM |
| 10 | Phone and SD card storage for Python libraries can be written by other apps on older Android | Code injection into `sys.path` | The storage screen now warns; internal storage (default) is not affected |

## Second review (latest release)

Every file that reads, writes, downloads, unpacks or executes something was read again, including the new Score and profile code.
No new high-risk hole was found. These smaller ones were fixed:

| # | Finding | Fix |
|---|---------|-----|
| 11 | C programs could call `getenv` and read the app's environment (paths, class paths) | `getenv` now always answers "not set" |
| 12 | CI wrote the private key to disk with default permissions and left it on the runner | Key file is created owner-only and deleted at the end of the job; secrets with line breaks are refused |
| 13 | A build without private-key secrets was signed with the public key silently | The build now prints a visible warning saying so |
| 14 | Python Lab module clean-up compared folder names without a trailing separator (`/libs` also matched `/libs_other`) | Compared with a trailing separator |
| 15 | Compiled `.pyc` files and key files could be committed by accident | `.gitignore` now excludes `__pycache__`, `*.pyc`, `*.keystore`, `release.jks`, `.env`; stray `.pyc` files removed |

Checked and found sound: manifest (one exported screen, the launcher; no extra permissions beyond INTERNET), HTTPS-only network policy,
PyPI host allow-list with manual redirect checks (tightened since), SHA-256 verification, wheel file-name validation, library and script name cleaning
(no `/`, `\` or `..`), size limits on every import, database names restricted to letters, digits, space, `_` and `-`, SQL identifiers quoted,
no WebView, no custom permissions, no content providers or broadcast receivers, no secrets or tokens in the source, and no sensitive data in logs.

## File layout

If an older copy of the project is updated by copying files over it, the old file names stay behind and cause "Conflicting declarations" build errors. `tools/remove_old_files.sh` removes them, and the Gradle build deletes them automatically as well.

Every source file now has a full descriptive name (for example `NotesRenderer.kt`, `dbms_query_engine.py`, `sample_databases/`,
`studytint_signing_key.jks`), and no text file is stored on a single line.

## Encryption of saved data

Keys: a master AES-256 key is generated in the Android Keystore (not exportable). It wraps a random data key kept in
`files/vault/data.key`. Data is encrypted with AES-256-GCM, so changed or damaged data is rejected.

| Data | Saved as |
|---|---|
| Profile (name, father's name, LURN, roll number, course), reading progress, quiz results, activity | encrypted values in preferences |
| Profile photo | `profile_photo.enc` |
| Code in the three lab editors, DBMS query history, lab settings | encrypted values in preferences |
| Saved Python scripts (all storage locations) | `name.py.enc` |
| DBMS Lab databases | `name.db.enc`; readable only while the DBMS Lab is on screen |

**Not encrypted:** files that programs create while running (Python Lab `work` folder, C Lab cache), imported libraries
(Python has to read them), the app version bookkeeping, and the study material shipped in the APK. Names of settings,
databases and scripts are visible.

A database stays readable while it has an open transaction or temporary tables, or if the app is killed while the lab is
open; it is encrypted the next time the app starts or the lab is left.

If the keys cannot be used, nothing is overwritten or deleted and the app says that saved data is locked. The keys are
destroyed when the app is uninstalled or its storage is cleared, and there is no recovery.

**What encryption does not protect against:** code run inside the app (Python Lab scripts and libraries run with the app's
permissions and can ask for the data to be decrypted), an attacker who controls the running phone (root), and an update
signed with the app's signing key (see open item 1).

## Network and hidden internals

- The only addresses the app itself contacts are `https://pypi.org` and `https://files.pythonhosted.org`. The call to
  PyPI's search web page, the wider `*.pypi.org` rule and two settings that could redirect downloads or allow plain http
  were removed.
- CI no longer publishes a Gradle Build Scan.
- Release builds are processed by R8 (unused code removed, names obfuscated). This hides names in the APK; it does not
  hide anything that is in a public source repository.

## Stability and speed changes

- Notes (2.7 MB of JSON) load on a background thread instead of blocking start-up; screens update when ready.
- Console output draws only its last 30,000 characters; diagnostic colouring skips ordinary lines.
- Syntax colouring is skipped for texts over 40,000 characters so typing stays responsive.
- Press animations no longer recompose cards on every frame.

## Open items (need you)

1. **The signing key is public.** `app/studytint_signing_key.jks` and its password are in the repository. Anyone who has them can sign an APK
   that installs as an update to yours. Add your own key as GitHub secrets (README, "Security"); this changes the signature, so
   Android requires one fresh install. The code is ready: the workflow and Gradle use the secrets when present.
2. **GitHub Actions are pinned by version tag, not commit hash.** A tag can be moved by its owner. Pinning needs the current
   commit hashes, which could not be looked up from the audit environment.
3. **Code you import runs with the app's permissions.** Python libraries, `.py` files, `.whl` files and scripts run inside the app and can read its
   private files. This is how a code lab works; only import code you trust.
4. **The C child process inherits the app's open file descriptors.** Programs have no way to reach them (no raw descriptor functions),
   and closing them safely after `fork` on Android is not reliable, so this is documented rather than changed.
5. **C programs have no memory cap of their own.** A program that allocates without limit is stopped by Android, which may also close the app.
   A hard cap (`RLIMIT_AS`) is not applied because the child process starts as a copy of the app and a low cap would stop it at once.
6. The R8 rules (`app/proguard-rules.pro`) and the encryption on real hardware still need a test on a phone.
