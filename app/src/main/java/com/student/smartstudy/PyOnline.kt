package com.student.smartstudy

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Python Lab "Get online": finds libraries on PyPI and saves them (with the libraries they need) into the
 * Python Lab libs folder, where they can be imported straight away.
 *
 * Only pure-Python wheels (py3-none-any) can run on the phone. Libraries that need compiled code
 * (NumPy, pandas, Pillow, SciPy ...) are reported as such instead of being downloaded.
 *
 * Wheels are saved as they are (a .whl is a zip that Python can import from), so removing a library is
 * just deleting its file.
 */
object PyOnline {
    data class Hit(val name: String, val version: String, val summary: String)

    class Result(val installed: List<String>, val kept: List<String>, val error: String?) {
        fun message(): String = buildString {
            if (installed.isNotEmpty()) append("Saved: ").append(installed.joinToString(", ")).append(". ")
            if (kept.isNotEmpty()) append("Already there: ").append(kept.joinToString(", ")).append(". ")
            if (error != null) append(error)
            if (installed.isEmpty() && kept.isEmpty() && error == null) append("Nothing to do.")
        }.trim()
        val ok: Boolean get() = error == null
    }

    private class OnlineException(message: String) : Exception(message)

    @Volatile var baseUrl = "https://pypi.org"
    /** Tests only: allow plain http against a local server. */
    @Volatile var allowHttp = false

    private const val UA = "StudyTint-PythonLab/1.3 (Android)"
    private const val MAX_WHEEL = 60L * 1024 * 1024
    private const val MAX_TEXT = 8 * 1024 * 1024
    private const val MAX_PACKAGES = 40
    private const val PY_VERSION = "3.12.0"
    private const val PY_MAJOR_MINOR = "3.12"

    // ------------------------------------------------------------------ browse / search

    fun popular(): List<Hit> = listOf(
        Hit("requests", "", "Fetch web pages and APIs (HTTP)"),
        Hit("beautifulsoup4", "", "Read and search HTML pages"),
        Hit("rich", "", "Colourful tables, progress bars and neat errors in the output"),
        Hit("tabulate", "", "Print tidy text tables"),
        Hit("tqdm", "", "Progress bars for loops"),
        Hit("colorama", "", "Coloured text in the output"),
        Hit("termcolor", "", "Simple coloured text"),
        Hit("click", "", "Build command-line programs"),
        Hit("python-dateutil", "", "Powerful date and time helpers"),
        Hit("arrow", "", "Friendlier dates and times"),
        Hit("pytz", "", "Time zones"),
        Hit("humanize", "", "Turn numbers and dates into readable text"),
        Hit("faker", "", "Fake names, addresses and data for testing"),
        Hit("sympy", "", "Symbolic maths: algebra, calculus, equations"),
        Hit("mpmath", "", "Arbitrary-precision maths"),
        Hit("networkx", "", "Graphs and networks (graph theory)"),
        Hit("more-itertools", "", "Extra tools for loops and iterators"),
        Hit("toolz", "", "Functional programming helpers"),
        Hit("attrs", "", "Write classes with less code"),
        Hit("pyparsing", "", "Build parsers for text"),
        Hit("prettytable", "", "Print tables as ASCII art"),
        Hit("openpyxl", "", "Read and write Excel .xlsx files"),
        Hit("pypdf", "", "Read, merge and split PDF files"),
        Hit("schedule", "", "Run things on a timetable"),
        Hit("emoji", "", "Emoji names and conversion"),
        Hit("pyfiglet", "", "Big ASCII-art text")
    )

    /** Searches PyPI. Falls back to looking up the exact name when the search page gives nothing. */
    fun search(query: String): List<Hit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val fromPage = try {
            parseSearchPage(getText("$baseUrl/search/?q=" + URLEncoder.encode(q, "UTF-8"), "text/html"))
        } catch (e: OnlineException) {
            throw e
        } catch (e: IOException) {
            if (e is java.net.UnknownHostException || e is java.net.SocketTimeoutException || e is java.net.ConnectException) throw e
            emptyList()
        }
        if (fromPage.isNotEmpty()) return fromPage
        if (q.contains(' ')) return emptyList()        // several words: nothing sensible to look up by name
        return listOfNotNull(lookup(q))
    }

    /** Details of one exact library name, or null when PyPI has no such library. */
    fun lookup(name: String): Hit? {
        val n = normalize(name.trim().replace(Regex("[^A-Za-z0-9._-].*$"), ""))
        if (n.isEmpty()) return null
        val text = try { getText("$baseUrl/pypi/$n/json", "application/json") } catch (e: NotFound) { return null }
        val info = JSONObject(text).optJSONObject("info") ?: return null
        return Hit(str(info, "name").ifEmpty { n }, str(info, "version"), str(info, "summary"))
    }

    private val snippet = Regex(
        "<a class=\"package-snippet\"[^>]*href=\"/project/([^/\"]+)/\"[\\s\\S]*?package-snippet__name\">([^<]*)</span>" +
            "[\\s\\S]*?package-snippet__version\">([^<]*)</span>[\\s\\S]*?package-snippet__description\">([^<]*)</p>"
    )

    internal fun parseSearchPage(html: String): List<Hit> =
        snippet.findAll(html).take(25).map {
            Hit(unescape(it.groupValues[2]).trim(), unescape(it.groupValues[3]).trim(), unescape(it.groupValues[4]).trim())
        }.filter { it.name.isNotEmpty() }.toList()

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")

    // ------------------------------------------------------------------ what is installed

    private class Installed(val name: String, val version: String, val file: File)

    private fun installedWheels(libs: File): List<Installed> =
        (libs.listFiles() ?: emptyArray()).filter { it.isFile && it.name.endsWith(".whl") }.mapNotNull { f ->
            parseWheel(f.name)?.let { Installed(normalize(it.dist), it.version, f) }
        }

    /** Normalised names of the libraries installed from the internet. */
    fun installedNames(libs: File): Set<String> = installedWheels(libs).map { it.name }.toSet()

    fun normalize(n: String): String = n.lowercase().replace(Regex("[-_.]+"), "-")

    // ------------------------------------------------------------------ install

    /**
     * Saves [request] ("requests", or "requests>=2.30") and everything it needs into [libs].
     * [progress] receives short status lines. Never throws for normal problems: see [Result.error].
     */
    fun install(libs: File, request: String, progress: (String) -> Unit): Result {
        val installed = mutableListOf<String>()
        val kept = mutableListOf<String>()
        try {
            libs.mkdirs()
            val top = parseReq(request) ?: throw OnlineException("\"$request\" is not a library name.")
            val queue = ArrayDeque<Pair<Req, Boolean>>()      // requirement, is it the one the user asked for
            queue.addLast(top to true)
            val done = HashMap<String, String>()               // normalised name -> chosen version
            while (queue.isNotEmpty()) {
                val (req, isTop) = queue.removeFirst()
                val key = normalize(req.name)
                val chosen = done[key]
                if (chosen != null) {
                    if (!satisfies(chosen, req.specs, true)) {
                        throw OnlineException("Two libraries need different versions of ${req.name}; $chosen was kept.")
                    }
                    continue
                }
                if (done.size >= MAX_PACKAGES) throw OnlineException("That library needs too many other libraries (more than $MAX_PACKAGES).")

                val have = installedWheels(libs).filter { it.name == key }.maxByOrNull { parseVer(it.version) ?: Ver(listOf(0), false, 0, false) }
                val haveOk = have != null && satisfies(have.version, req.specs, true)
                val file: File
                val version: String
                if (haveOk && !isTop) {
                    file = have!!.file; version = have.version
                    kept += "${req.name} $version"
                } else {
                    progress("Looking up ${req.name}…")
                    val pick = choose(req)
                    if (haveOk && have!!.version == pick.version) {
                        file = have.file; version = have.version
                        kept += "${req.name} $version"
                    } else {
                        file = fetch(libs, pick, progress)
                        version = pick.version
                        installed += "${req.name} $version"
                        // drop older copies of the same library
                        installedWheels(libs).filter { it.name == key && it.file.name != file.name }.forEach { it.file.delete() }
                    }
                }
                done[key] = version
                for (line in requiresOf(file)) {
                    val dep = parseReq(line) ?: continue
                    if (dep.marker != null && !Marker.evaluate(dep.marker)) continue
                    queue.addLast(dep to false)
                }
            }
            return Result(installed, kept, null)
        } catch (e: OnlineException) {
            return Result(installed, kept, e.message ?: "Could not install.")
        } catch (e: java.net.UnknownHostException) {
            return Result(installed, kept, "No internet connection.")
        } catch (e: java.net.ConnectException) {
            return Result(installed, kept, "No internet connection.")
        } catch (e: java.net.SocketTimeoutException) {
            return Result(installed, kept, "The connection timed out. Try again.")
        } catch (e: IOException) {
            return Result(installed, kept, "Download failed: ${e.message ?: "network error"}")
        } catch (e: Exception) {
            return Result(installed, kept, "Could not install: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    // ------------------------------------------------------------------ choosing a file

    private class RemoteFile(val filename: String, val url: String, val sha256: String, val size: Long, val version: String)

    private fun choose(req: Req): RemoteFile {
        val text = try {
            getText("$baseUrl/simple/${normalize(req.name)}/", "application/vnd.pypi.simple.v1+json")
        } catch (e: NotFound) {
            throw OnlineException("There is no library called \"${req.name}\" on PyPI. Check the spelling.")
        }
        val files = JSONObject(text).optJSONArray("files") ?: throw OnlineException("PyPI sent an unexpected answer.")
        var any = false
        var anyPure = false
        val candidates = ArrayList<Pair<Ver, RemoteFile>>()
        val preCandidates = ArrayList<Pair<Ver, RemoteFile>>()
        for (i in 0 until files.length()) {
            val o = files.optJSONObject(i) ?: continue
            val filename = str(o, "filename")
            any = true
            val w = parseWheel(filename) ?: continue
            if (!isPure(w)) continue
            anyPure = true
            val y = o.opt("yanked")
            if (y != null && y != false && y != JSONObject.NULL) continue
            val rp = str(o, "requires-python")
            if (rp.isNotEmpty() && !satisfies(PY_VERSION, parseSpecs(rp), true)) continue
            val ver = parseVer(w.version) ?: continue
            if (!satisfies(w.version, req.specs, true)) continue
            val hashes = o.optJSONObject("hashes")
            val sha = if (hashes == null) "" else str(hashes, "sha256")
            val rf = RemoteFile(filename, str(o, "url"), sha, o.optLong("size", -1L), w.version)
            if (rf.url.isEmpty()) continue
            if (ver.pre) preCandidates += ver to rf else candidates += ver to rf
        }
        val list = if (candidates.isNotEmpty()) candidates else preCandidates
        if (list.isEmpty()) {
            throw OnlineException(
                when {
                    !any -> "PyPI has no downloads for \"${req.name}\"."
                    !anyPure -> "${req.name} needs compiled code (it is not pure Python), so it cannot run on the phone."
                    else -> "No version of ${req.name} fits Python $PY_MAJOR_MINOR" +
                        (if (req.specs.isNotEmpty()) " and the version asked for" else "") + "."
                }
            )
        }
        return list.maxByOrNull { it.first }!!.second
    }

    // ------------------------------------------------------------------ downloading

    private fun fetch(libs: File, f: RemoteFile, progress: (String) -> Unit): File {
        val name = safeFileName(f.filename)
        val dest = File(libs, name)
        val part = File(libs, "$name.part")
        val label = f.filename.substringBefore('-') + " " + f.version
        val c = open(f.url, null)
        try {
            val code = c.responseCode
            if (code !in 200..299) throw IOException("The server answered $code for $label")
            val declared = c.contentLengthLong
            val limit = minOf(MAX_WHEEL, if (f.size > 0) f.size + 1024 else MAX_WHEEL)
            if (declared > MAX_WHEEL) throw OnlineException("$label is too large (${formatBytes(declared)}).")
            val md = MessageDigest.getInstance("SHA-256")
            var total = 0L
            var lastShown = 0L
            c.inputStream.use { ins ->
                part.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > limit) throw OnlineException("$label is larger than expected; download stopped.")
                        md.update(buf, 0, n)
                        out.write(buf, 0, n)
                        if (total - lastShown > 256 * 1024) {
                            lastShown = total
                            progress("Downloading $label… ${formatBytes(total)}")
                        }
                    }
                }
            }
            if (f.size > 0 && total != f.size) throw IOException("$label was cut off (${formatBytes(total)} of ${formatBytes(f.size)})")
            val got = md.digest().joinToString("") { "%02x".format(it) }
            if (f.sha256.isNotEmpty() && !got.equals(f.sha256, ignoreCase = true)) {
                throw OnlineException("$label did not match its checksum, so it was not saved.")
            }
            if (!looksLikeWheel(part)) throw OnlineException("$label is not a valid library file.")
            dest.delete()
            if (!part.renameTo(dest)) throw IOException("Could not save $label (storage full or not writable?)")
            return dest
        } finally {
            c.disconnect()
            if (part.exists()) part.delete()
        }
    }

    private fun looksLikeWheel(f: File): Boolean = try {
        ZipFile(f).use { z -> z.entries().asSequence().any { it.name.endsWith(".dist-info/METADATA") } }
    } catch (e: Exception) { false }

    private fun safeFileName(n: String): String {
        val clean = n.substringAfterLast('/').substringAfterLast('\\')
        if (!Regex("[A-Za-z0-9._+-]+\\.whl").matches(clean)) throw OnlineException("Unexpected file name \"$n\".")
        return clean
    }

    // ------------------------------------------------------------------ wheel metadata

    private fun requiresOf(wheel: File): List<String> = try {
        ZipFile(wheel).use { z ->
            val e = z.entries().asSequence().firstOrNull { it.name.endsWith(".dist-info/METADATA") && it.name.count { ch -> ch == '/' } == 1 }
            if (e == null || e.size > 2_000_000) {
                emptyList()
            } else {
                val text = z.getInputStream(e).bufferedReader(Charsets.UTF_8).readText().replace("\r\n", "\n")
                text.substringBefore("\n\n").lines().filter { it.startsWith("Requires-Dist:") }.map { it.substringAfter(':').trim() }
            }
        }
    } catch (e: Exception) { emptyList() }

    // ------------------------------------------------------------------ HTTP

    private class NotFound : IOException("Not found")

    private fun open(url: String, accept: String?): HttpURLConnection {
        val u = java.net.URI(url).toURL()
        if (u.protocol != "https" && !(allowHttp && u.protocol == "http")) throw OnlineException("Only secure (https) downloads are allowed.")
        val c = u.openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", UA)
        if (accept != null) c.setRequestProperty("Accept", accept)
        return c
    }

    private fun getText(url: String, accept: String): String {
        val c = open(url, accept)
        try {
            val code = c.responseCode
            if (code == 404) throw NotFound()
            if (code !in 200..299) throw IOException("The server answered $code")
            val out = java.io.ByteArrayOutputStream()
            c.inputStream.use { ins ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_TEXT) throw OnlineException("The answer from PyPI was too large.")
                }
            }
            return out.toString("UTF-8")
        } finally {
            c.disconnect()
        }
    }

    private fun str(o: JSONObject, key: String): String = if (o.isNull(key)) "" else o.optString(key, "")

    private fun formatBytes(b: Long): String = when {
        b >= 1024 * 1024 -> "%.1f MB".format(b / 1024.0 / 1024.0)
        b >= 1024 -> "%.0f KB".format(b / 1024.0)
        else -> "$b B"
    }

    // ------------------------------------------------------------------ wheels

    private class Wheel(val dist: String, val version: String, val pyTags: List<String>, val abi: String, val plat: String)

    private fun parseWheel(filename: String): Wheel? {
        if (!filename.endsWith(".whl")) return null
        val p = filename.removeSuffix(".whl").split("-")
        if (p.size < 5) return null
        return Wheel(p[0], p[1], p[p.size - 3].split("."), p[p.size - 2], p[p.size - 1])
    }

    /** py3-none-any (or py2.py3, py3N, cp3N with no ABI): Python code only, so it runs anywhere. */
    private fun isPure(w: Wheel): Boolean {
        if (w.abi != "none" || w.plat != "any") return false
        return w.pyTags.any { t ->
            t == "py3" || Regex("py3(\\d+)").matchEntire(t)?.let { it.groupValues[1].toInt() <= 12 } == true ||
                Regex("cp3(\\d+)").matchEntire(t)?.let { it.groupValues[1].toInt() <= 12 } == true
        }
    }

    // ------------------------------------------------------------------ versions and requirements

    internal class Ver(val parts: List<Int>, val pre: Boolean, val post: Int, val dev: Boolean) : Comparable<Ver> {
        override fun compareTo(other: Ver): Int {
            val n = maxOf(parts.size, other.parts.size)
            for (i in 0 until n) {
                val a = parts.getOrElse(i) { 0 }
                val b = other.parts.getOrElse(i) { 0 }
                if (a != b) return a.compareTo(b)
            }
            // dev < pre < final < post
            fun rank(v: Ver) = if (v.dev) 0 else if (v.pre) 1 else 2
            if (rank(this) != rank(other)) return rank(this).compareTo(rank(other))
            return post.compareTo(other.post)
        }
    }

    private val verRe = Regex(
        "^v?(\\d+(?:\\.\\d+)*)((?:[-_.]?(?:a|b|c|rc|alpha|beta|pre|preview)[-_.]?\\d*)?)" +
            "((?:[-_.]?(?:post|rev|r)[-_.]?\\d*)?)((?:[-_.]?dev[-_.]?\\d*)?)(?:\\+[a-z0-9.]+)?$"
    )

    internal fun parseVer(s: String): Ver? {
        val m = verRe.matchEntire(s.trim().lowercase()) ?: return null
        val parts = m.groupValues[1].split(".").map { it.toIntOrNull() ?: return null }
        val postText = m.groupValues[3]
        val post = if (postText.isEmpty()) 0 else (Regex("\\d+").find(postText)?.value?.toIntOrNull() ?: 1)
        return Ver(parts, m.groupValues[2].isNotEmpty(), post, m.groupValues[4].isNotEmpty())
    }

    internal class Spec(val op: String, val version: String)

    private val specRe = Regex("^(===|==|!=|<=|>=|~=|<|>)\\s*([A-Za-z0-9.*+!_-]+)$")

    internal fun parseSpecs(text: String): List<Spec> =
        text.trim().trim('(', ')').split(",").map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull {
            specRe.matchEntire(it)?.let { m -> Spec(m.groupValues[1], m.groupValues[2]) }
        }

    internal fun satisfies(version: String, specs: List<Spec>, allowPre: Boolean): Boolean {
        if (specs.isEmpty()) return true
        val v = parseVer(version) ?: return false
        if (v.pre && !allowPre) return false
        return specs.all { s -> matches(v, version, s) }
    }

    private fun matches(v: Ver, raw: String, s: Spec): Boolean {
        if (s.op == "===") return raw == s.version
        if (s.version.endsWith(".*")) {
            val prefix = parseVer(s.version.removeSuffix(".*"))?.parts ?: return false
            val eq = prefix.indices.all { v.parts.getOrElse(it) { 0 } == prefix[it] }
            return when (s.op) { "==" -> eq; "!=" -> !eq; else -> false }
        }
        val t = parseVer(s.version) ?: return false
        val c = v.compareTo(t)
        return when (s.op) {
            "==" -> c == 0
            "!=" -> c != 0
            ">=" -> c >= 0
            "<=" -> c <= 0
            ">" -> c > 0
            "<" -> c < 0 && !(v.pre && !t.pre && sameRelease(v, t))
            "~=" -> {
                if (c < 0) false
                else {
                    val keep = (t.parts.size - 1).coerceAtLeast(1)
                    (0 until keep).all { v.parts.getOrElse(it) { 0 } == t.parts[it] }
                }
            }
            else -> false
        }
    }

    private fun sameRelease(a: Ver, b: Ver): Boolean {
        val n = maxOf(a.parts.size, b.parts.size)
        return (0 until n).all { a.parts.getOrElse(it) { 0 } == b.parts.getOrElse(it) { 0 } }
    }

    internal class Req(val name: String, val specs: List<Spec>, val marker: String?)

    private val reqRe = Regex("^([A-Za-z0-9][A-Za-z0-9._-]*)\\s*(?:\\[[^\\]]*\\])?\\s*(.*)$")

    internal fun parseReq(line: String): Req? {
        val semi = line.indexOf(';')
        val main = (if (semi >= 0) line.substring(0, semi) else line).trim()
        val marker = if (semi >= 0) line.substring(semi + 1).trim().ifEmpty { null } else null
        if (main.contains("@")) return null                      // a direct link, not a PyPI library
        val m = reqRe.matchEntire(main) ?: return null
        val specText = m.groupValues[2]
        val specs = parseSpecs(specText)
        if (specText.isNotBlank() && specs.isEmpty()) return null // something we do not understand
        return Req(m.groupValues[1], specs, marker)
    }

    // ------------------------------------------------------------------ environment markers

    /** Evaluates the part after ';' in a requirement for this device (Python 3.12 on Android). */
    internal object Marker {
        private val env = mapOf(
            "python_version" to PY_MAJOR_MINOR, "python_full_version" to PY_VERSION, "os_name" to "posix",
            "sys_platform" to "linux", "platform_system" to "Linux", "platform_machine" to "aarch64",
            "platform_python_implementation" to "CPython", "implementation_name" to "cpython",
            "implementation_version" to PY_VERSION, "platform_release" to "", "platform_version" to "", "extra" to ""
        )
        private val versionVars = setOf("python_version", "python_full_version", "implementation_version")
        private val tokenRe = Regex("\\s*(\\(|\\)|===|==|!=|<=|>=|~=|<|>|\"[^\"]*\"|'[^']*'|[A-Za-z_][A-Za-z0-9_.]*)")

        fun evaluate(marker: String): Boolean {
            return try {
                val toks = ArrayList<String>()
                var pos = 0
                while (pos < marker.length) {
                    if (marker.substring(pos).isBlank()) break
                    val m = tokenRe.matchAt(marker, pos) ?: return false
                    toks += m.groupValues[1]
                    pos = m.range.last + 1
                }
                val p = Parser(toks)
                val r = p.or()
                r && p.atEnd()
            } catch (e: Exception) {
                false
            }
        }

        private class Parser(val t: List<String>) {
            var i = 0
            fun atEnd() = i >= t.size
            fun or(): Boolean {
                var r = and()
                while (i < t.size && t[i] == "or") { i++; r = and() || r }
                return r
            }
            fun and(): Boolean {
                var r = atom()
                while (i < t.size && t[i] == "and") { i++; r = atom() && r }
                return r
            }
            fun atom(): Boolean {
                if (t[i] == "(") {
                    i++
                    val r = or()
                    if (t[i] != ")") throw IllegalStateException("missing )")
                    i++
                    return r
                }
                val left = t[i++]
                var op = t[i++]
                if (op == "not") { op = "not " + t[i++] }
                val right = t[i++]
                return compare(left, op, right)
            }
        }

        private fun isLiteral(s: String) = s.startsWith("\"") || s.startsWith("'")
        private fun valueOf(s: String) = if (isLiteral(s)) s.substring(1, s.length - 1) else env[s] ?: ""

        private fun compare(l: String, op: String, r: String): Boolean {
            val a = valueOf(l)
            val b = valueOf(r)
            val versionLike = (!isLiteral(l) && l in versionVars) || (!isLiteral(r) && r in versionVars)
            if (op == "in") return b.contains(a)
            if (op == "not in") return !b.contains(a)
            if (versionLike && op != "===") {
                return satisfies(a, listOf(Spec(op, b)), true)
            }
            return when (op) {
                "==", "===" -> a == b
                "!=" -> a != b
                "<" -> a < b
                "<=" -> a <= b
                ">" -> a > b
                ">=" -> a >= b
                else -> false
            }
        }
    }
}
