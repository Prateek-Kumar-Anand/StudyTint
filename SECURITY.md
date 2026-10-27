# StudyTint security audit (v1.5.2)

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

## Stability and speed changes

- Notes (2.7 MB of JSON) load on a background thread instead of blocking start-up; screens update when ready.
- Console output draws only its last 30,000 characters; diagnostic colouring skips ordinary lines.
- Syntax colouring is skipped for texts over 40,000 characters so typing stays responsive.
- Press animations no longer recompose cards on every frame.

## Open items (need you)

1. **The signing key is public.** `app/studytint.jks` and its password are in the repository. Anyone who has them can sign an APK
   that installs as an update to yours. Add your own key as GitHub secrets (README, "Security"); this changes the signature, so
   Android requires one fresh install. The code is ready: the workflow and Gradle use the secrets when present.
2. **GitHub Actions are pinned by version tag, not commit hash.** A tag can be moved by its owner. Pinning needs the current
   commit hashes, which could not be looked up from the audit environment.
3. **Code you import runs with the app's permissions.** Python libraries, `.py` files, `.whl` files and scripts run inside the app and can read its
   private files. This is how a code lab works; only import code you trust.
4. **The C child process inherits the app's open file descriptors.** Programs have no way to reach them (no raw descriptor functions),
   and closing them safely after `fork` on Android is not reliable, so this is documented rather than changed.
5. Code shrinking (R8) is off on purpose: the C engine is looked up by name through JNI, and an untested rules file could break it.
