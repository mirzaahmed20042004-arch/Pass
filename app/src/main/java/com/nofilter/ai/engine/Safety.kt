package com.nofilter.ai.engine

/**
 * Minimal on-device safety layer. The app answers virtually everything,
 * but a small set of requests that could cause severe real-world harm are
 * declined with a short, respectful message.
 */
object Safety {

    private val patterns = listOf(
        Regex("\\b(make|build|create|assemble)\\b.{0,30}\\b(bomb|explosive|grenade|molotov|napalm|detonator)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(bomb|explosive)\\b.{0,30}\\b(instructions|how to|recipe|guide)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(synthes|synthesis|manufactur|cook)\\b.{0,30}\\b(meth|heroin|cocaine|fentanyl|lsd|mdma)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(child|kid|minor|underage)\\b.{0,25}\\b(porn|sex|nude|csam)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(csam|child sexual|child porn)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(hack|break|get) into\\b.{0,25}\\b(someone|somebody|other people|account)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(steal|exfiltrate)\\b.{0,25}\\b(passwords?|credentials|data)\\b.{0,20}\\b(without|no)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(create|write|make)\\b.{0,30}\\b(malware|ransomware|keylogger|rootkit|virus|trojan)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(kill|murder|hurt|harm)\\b.{0,20}\\b(someone|somebody|people|person)\\b", RegexOption.IGNORE_CASE),
        Regex("\\bhow to\\b.{0,30}\\b(commit suicide|kill myself|end my life)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(buy|sell|order)\\b.{0,25}\\b(weapons?|guns?|ammo)\\b.{0,20}\\b(illegally|without license|online black market)\\b", RegexOption.IGNORE_CASE)
    )

    fun check(query: String): String? {
        for (p in patterns) {
            if (p.containsMatchIn(query)) {
                return "I can't help with that — it could cause serious harm to people. " +
                    "If you're working on something legitimate in security or safety, I'm happy to help with the defensive, legal side of it (e.g. how to protect systems, safety engineering, or academic research)."
            }
        }
        return null
    }
}
