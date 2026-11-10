package app.later

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

/**
 * Turns a spoken note into tasks with times. Runs fully on the phone.
 */
object Parser {
    data class Draft(val title: String, val due: LocalDateTime)

    private val I = RegexOption.IGNORE_CASE
    private fun rx(p: String) = Regex(p, I)

    private val splitter = rx(
        """\s*(?:,\s*(?:and|then|also)\s+|\band then\b|\bafter that\b|\balso\b|;|\.\s+|\n|\band (?=(?:i|i'll|i need|i have|remind)\b))\s*"""
    )
    private val numberWords = mapOf(
        "a" to 1.0, "an" to 1.0, "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0, "five" to 5.0,
        "ten" to 10.0, "fifteen" to 15.0, "twenty" to 20.0, "thirty" to 30.0, "forty" to 40.0, "forty five" to 45.0,
    )
    private const val NUM = """(\d+|an?|one|two|three|four|five|ten|fifteen|twenty|thirty|forty five|forty|half an?)"""
    private val relative = rx("""\bin $NUM (minute|min|hour|hr|day|week)s?\b""")
    private val vague = rx("""\b(after a while|in a while|in a bit|later on|later)\b""")
    private val clock = rx(
        """\b(?:at|by|around)\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm|a\.m\.|p\.m\.)?|\b(\d{1,2})(?::(\d{2}))?\s*(am|pm|a\.m\.|p\.m\.)"""
    )
    private val weekday = rx("""\b(next )?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""")

    private val strip = listOf(
        """\b(i need to|i have to|i've got to|i got to|i gotta|i should|i must|i want to|i'm going to|i am going to|i'm gonna|i am gonna|i'll|i will|remind me to|remind me|don't forget to|dont forget to|make sure to|make sure i|please)\b""",
        """\b(the )?day after tomorrow\b""", """\b(this|in the|tomorrow) (morning|afternoon|evening)\b""",
        """\btomorrow\b""", """\btoday\b""", """\btonight\b""", """\bat night\b""",
        """\b(morning|afternoon|evening)\b""", """\bafter work\b""",
        """\b(this )?weekend\b""", """\bnext week\b""",
        """\b(on |this |next )?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""",
        """\bin $NUM (minute|min|hour|hr|day|week)s?\b""",
        """\b(after a while|in a while|in a bit|later on|later)\b""",
        """\b(?:at|by|around)\s+\d{1,2}(?::\d{2})?\s*(am|pm|a\.m\.|p\.m\.)?""",
        """\b\d{1,2}(?::\d{2})?\s*(am|pm)\b""",
        """\b(at |by |around )?(noon|midday|lunch ?time|lunch)\b""",
    ).map { rx(it) }

    fun parse(text: String, now: LocalDateTime = LocalDateTime.now()): List<Draft> =
        text.split(splitter)
            .map { it.trim().trim(',', '.', ' ') }
            .filter { it.length > 2 }
            .map { one(it, now) }

    private fun nextDow(from: LocalDate, dow: DayOfWeek, allowToday: Boolean): LocalDate {
        var d = if (allowToday) from else from.plusDays(1)
        while (d.dayOfWeek != dow) d = d.plusDays(1)
        return d
    }

    private fun one(part: String, now: LocalDateTime): Draft {
        val s = part.lowercase()
        val today = now.toLocalDate()
        var date: LocalDate? = null
        var time: LocalTime? = null
        var exact: LocalDateTime? = null

        when {
            rx("""\bday after tomorrow\b""").containsMatchIn(s) -> date = today.plusDays(2)
            rx("""\btomorrow\b""").containsMatchIn(s) -> date = today.plusDays(1)
            rx("""\bnext week\b""").containsMatchIn(s) -> date = today.plusWeeks(1)
            rx("""\bweekend\b""").containsMatchIn(s) -> {
                date = nextDow(today, DayOfWeek.SATURDAY, true); time = LocalTime.of(10, 0)
            }
            rx("""\btoday\b""").containsMatchIn(s) -> date = today
        }
        val wd = weekday.find(s)
        if (wd != null) date = nextDow(today, DayOfWeek.valueOf(wd.groupValues[2].uppercase()), false)

        val rel = relative.find(s)
        if (rel != null) {
            val w = rel.groupValues[1]
            val n = when {
                w.startsWith("half") -> 0.5
                else -> numberWords[w] ?: w.toDoubleOrNull() ?: 1.0
            }
            val unit = rel.groupValues[2]
            val minutes = when {
                unit.startsWith("min") -> n
                unit.startsWith("h") -> n * 60
                unit.startsWith("d") -> n * 1440
                else -> n * 10080
            }
            exact = now.plusMinutes(minutes.roundToLong())
        }
        if (exact == null && vague.containsMatchIn(s)) exact = now.plusHours(3)

        when {
            rx("""\b(tonight|at night)\b""").containsMatchIn(s) -> { time = LocalTime.of(21, 0); if (date == null) date = today }
            rx("""\b(evening|after work)\b""").containsMatchIn(s) -> time = LocalTime.of(18, 0)
            rx("""\bafternoon\b""").containsMatchIn(s) -> time = LocalTime.of(14, 0)
            rx("""\bmorning\b""").containsMatchIn(s) -> time = LocalTime.of(9, 0)
            rx("""\b(noon|midday)\b""").containsMatchIn(s) -> time = LocalTime.of(12, 0)
            rx("""\blunch""").containsMatchIn(s) -> time = LocalTime.of(13, 0)
        }

        val c = clock.find(s)
        if (c != null) {
            val g = c.groupValues
            val hStr = g[1].ifEmpty { g[4] }
            val mStr = g[2].ifEmpty { g[5] }.ifEmpty { "0" }
            val ap = g[3].ifEmpty { g[6] }
            var h = hStr.toIntOrNull() ?: -1
            val m = mStr.toIntOrNull() ?: 0
            if (h in 0..23 && m in 0..59) {
                val partOfDay = time
                when {
                    ap.startsWith("p") && h < 12 -> h += 12
                    ap.startsWith("a") && h == 12 -> h = 0
                    ap.isEmpty() && partOfDay != null && partOfDay.hour >= 12 && h < 12 -> h += 12
                    ap.isEmpty() && partOfDay == null && h in 1..7 -> h += 12
                }
                time = LocalTime.of(h.coerceIn(0, 23), m)
            }
        }

        val d = date
        val t = time
        val e = exact
        var due: LocalDateTime = when {
            t != null -> LocalDateTime.of(d ?: today, t)
            e != null -> e
            d != null && d != today -> LocalDateTime.of(d, LocalTime.of(9, 0))
            else -> now.plusHours(2)
        }
        if (due.isBefore(now)) due = if (t != null && (d == null || d == today)) due.plusDays(1) else now.plusHours(2)
        due = due.truncatedTo(ChronoUnit.MINUTES)

        return Draft(cleanTitle(part), due)
    }

    private fun cleanTitle(part: String): String {
        var title = part
        strip.forEach { title = title.replace(it, " ") }
        title = title.replace(Regex("""\s{2,}"""), " ").trim().trim(',', '.', ' ')
        repeat(2) {
            title = title.replace(rx("""^(to|and|that|so|then|um|uh|okay|ok|also)\s+"""), "")
                .replace(rx("""\s+(and|to|on|at|by|in|the|for)$"""), "")
                .trim()
        }
        if (title.isBlank()) title = part.trim()
        return title.replaceFirstChar { it.uppercase() }
    }
}
