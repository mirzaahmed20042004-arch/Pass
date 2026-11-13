package com.nofilter.ai.engine

/** Code generator & stack-trace debugger expert (100% offline, zero API keys). */
object CodeAssist {

    private val LANG_HINTS = listOf(
        "kotlin" to listOf("fun ", "val ", "var ", "println(", "->", "override fun"),
        "java" to listOf("public class", "system.out.println", "import java."),
        "python" to listOf("def ", "import ", "print(", "elif ", "self."),
        "javascript" to listOf("console.log", "const ", "let ", "=>", "function "),
        "html" to listOf("<html", "<div", "<!doctype"),
        "css" to listOf("{", "}", "margin:", "color:"),
        "sql" to listOf("select ", "from ", "where ", "insert into"),
        "xml" to listOf("<?xml", "<android."),
        "bash" to listOf("#!/bin/", "sudo ", "apt ", "echo $")
    )

    private val ERROR_FIXES = listOf(
        Regex("null ?pointer", RegexOption.IGNORE_CASE) to "NullPointerException → guard nullable references with safe calls (`?.`), Elvis defaults (`?:`), or `requireNotNull(...)`.",
        Regex("cannot find symbol", RegexOption.IGNORE_CASE) to "Missing import or identifier typo — verify imports and exact casing.",
        Regex("unresolved reference", RegexOption.IGNORE_CASE) to "Unresolved reference — check imports, visibility modifiers, and module classpath.",
        Regex("index out of bounds|IndexOutOfBounds", RegexOption.IGNORE_CASE) to "Index out of bounds — check `index in list.indices` or use `getOrNull(index)`.",
        Regex("class cast", RegexOption.IGNORE_CASE) to "ClassCastException — use safe cast `as?` or check `if (x is TargetType)` first.",
        Regex("network|SocketTimeout|UnknownHost|NetworkOnMainThread", RegexOption.IGNORE_CASE) to "Network error — run HTTP off the UI thread, verify `INTERNET` permission in `AndroidManifest.xml`, and wrap calls in `try/catch` with timeouts.",
        Regex("permission denied", RegexOption.IGNORE_CASE) to "Permission denied — declare the permission in `AndroidManifest.xml` and request runtime permission on Android 6+.",
        Regex("outofmemory|OutOfMemory", RegexOption.IGNORE_CASE) to "OutOfMemoryError — stream files in chunks and downsample bitmaps with `BitmapFactory.Options.inSampleSize`.",
        Regex("inflation|inflate", RegexOption.IGNORE_CASE) to "Layout inflation error — verify XML attribute names, theme attributes (`?attr/...`), and custom view constructors.",
        Regex("404|not found", RegexOption.IGNORE_CASE) to "HTTP 404 — check base URL, route path, and URL-encoded parameters.",
        Regex("401|403|unauthorized|forbidden", RegexOption.IGNORE_CASE) to "HTTP 401/403 — inspect auth headers, token expiration, and permissions.",
        Regex("500|internal server error", RegexOption.IGNORE_CASE) to "HTTP 500 — server-side exception; check request payload schema and server logs."
    )

    fun analyze(q: String): String? {
        val lower = q.lowercase()

        // Code generation templates for common coding requests
        val asksCode = Regex("\\b(write|generate|show|example|code|function|script|program|snippet)\\b", RegexOption.IGNORE_CASE).containsMatchIn(q)
        if (asksCode) {
            when {
                "fibonacci" in lower -> return """
                    💻 **Fibonacci (Iterative `O(n)` time, `O(1)` space — Python & Kotlin):**
                    ```python
                    def fib(n: int) -> int:
                        a, b = 0, 1
                        for _ in range(n):
                            a, b = b, a + b
                        return a
                    ```
                    ```kotlin
                    fun fib(n: Int): Long {
                        var a = 0L; var b = 1L
                        repeat(n) { val next = a + b; a = b; b = next }
                        return a
                    }
                    ```
                """.trimIndent()

                "binary search" in lower -> return """
                    💻 **Binary Search (`O(log n)`):**
                    ```python
                    def binary_search(arr: list[int], target: int) -> int:
                        lo, hi = 0, len(arr) - 1
                        while lo <= hi:
                            mid = (lo + hi) // 2
                            if arr[mid] == target:
                                return mid
                            elif arr[mid] < target:
                                lo = mid + 1
                            else:
                                hi = mid - 1
                        return -1
                    ```
                """.trimIndent()

                ("fetch" in lower || "http" in lower || "rest api" in lower) && ("python" in lower || "js" in lower || "javascript" in lower || "kotlin" in lower) -> return """
                    💻 **Clean HTTP JSON Fetch (Zero External Keys):**
                    ```javascript
                    async function fetchJson(url) {
                      const controller = new AbortController();
                      const timer = setTimeout(() => controller.abort(), 8000);
                      try {
                        const res = await fetch(url, { signal: controller.signal });
                        if (!res.ok) throw new Error(`HTTP ${'$'}{res.status}`);
                        return await res.json();
                      } finally {
                        clearTimeout(timer);
                      }
                    }
                    ```
                """.trimIndent()

                "palindrome" in lower -> return """
                    💻 **Palindrome Check (`O(n)`):**
                    ```python
                    def is_palindrome(s: str) -> bool:
                        cleaned = ''.join(ch.lower() for ch in s if ch.isalnum())
                        return cleaned == cleaned[::-1]
                    ```
                """.trimIndent()
            }
        }

        val hasCodeFence = q.contains("```")
        val looksLikeError = q.contains(Regex("error|exception|traceback|failed|cannot|unresolved|denied", RegexOption.IGNORE_CASE))
        if (!hasCodeFence && !looksLikeError) return null

        val sb = StringBuilder()
        var lang: String? = null
        for ((l, hints) in LANG_HINTS) {
            if (hints.count { q.contains(it, ignoreCase = true) } >= 2) { lang = l; break }
        }
        if (lang != null) {
            sb.append("**Detected language:** `$lang`\n\n")
        }
        val lines = q.lineSequence().filter { it.isNotBlank() }.count()
        sb.append("Analyzed **$lines** non-empty lines. ")

        var matchedFix = false
        for ((re, fix) in ERROR_FIXES) {
            if (re.containsMatchIn(q)) {
                if (!matchedFix) sb.append("\n\n**Likely fix:**\n")
                sb.append("• ").append(fix).append("\n")
                matchedFix = true
            }
        }
        if (!matchedFix && hasCodeFence) {
            sb.append("\n\n**Code review checklist:** verify null-safety, empty/boundary inputs, resource closing (`.use {}`), and off-Main-Thread I/O.")
        }
        if (looksLikeError && !matchedFix) {
            sb.append("\n\nPaste the **first 3 lines of the stack trace** and I'll pinpoint the exact line and cause.")
        }
        return sb.toString().trim()
    }
}
