package app.later

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

const val MINUTE = 60_000L
const val HOUR = 60 * MINUTE

fun Long.toLdt(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())
fun LocalDateTime.millis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun startOfDay(ms: Long): Long =
    ms.toLdt().toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** Calendar days from [b] to [a]: 0 = same day, 1 = a is tomorrow. */
fun dayDiff(a: Long, b: Long): Long = ChronoUnit.DAYS.between(b.toLdt().toLocalDate(), a.toLdt().toLocalDate())

private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val weekdayFmt = DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())
private val shortDateFmt = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
val longDateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())
val headerDateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())

fun fmtWhen(ms: Long, now: Long): String {
    val d = ms.toLdt()
    val t = d.format(timeFmt)
    return when (val diff = dayDiff(ms, now)) {
        0L -> "Today, $t"
        1L -> "Tomorrow, $t"
        -1L -> "Yesterday, $t"
        in 2L..6L -> "${d.format(weekdayFmt)}, $t"
        else -> "${d.format(shortDateFmt)}, $t"
    }
}
