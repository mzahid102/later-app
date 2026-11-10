package app.later

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        TaskStore.init(this)
        Reminders.ensureChannel(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        setContent {
            LaterTheme {
                MainScreen(onCapture = { startActivity(Intent(this, CaptureActivity::class.java)) })
            }
        }
    }
}

private sealed interface ListRow {
    data class Head(val text: String) : ListRow
    data class Item(val task: Task) : ListRow
}

private val tabs = listOf("Today", "Upcoming", "Done", "Stats")

@Composable
fun MainScreen(onCapture: () -> Unit) {
    val p = LocalPalette.current
    val tasks by TaskStore.tasks.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf<String?>(null) }
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(30_000); value = System.currentTimeMillis() }
    }

    Box(Modifier.fillMaxSize().background(p.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 20.dp), verticalAlignment = Alignment.Bottom) {
                Text("Later", color = p.ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(LocalDate.now().format(headerDateFmt), color = p.muted, fontSize = 14.sp)
            }
            Tabs(tab) { tab = it; expanded = null }
            Spacer(Modifier.height(8.dp))

            if (tab == 3) {
                Stats(tasks, now)
            } else {
                val rows = when (tab) {
                    0 -> todayRows(tasks, now)
                    1 -> upcomingRows(tasks, now)
                    else -> doneRows(tasks)
                }
                if (rows.isEmpty()) Empty(tab) else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 140.dp)) {
                        items(rows, key = { r -> if (r is ListRow.Head) "h:" + r.text else (r as ListRow.Item).task.id }) { r ->
                            when (r) {
                                is ListRow.Head -> Text(
                                    r.text, color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 18.dp, bottom = 2.dp, start = 2.dp)
                                )
                                is ListRow.Item -> TaskRow(r.task, now, expanded == r.task.id) {
                                    expanded = if (expanded == r.task.id) null else r.task.id
                                }
                            }
                        }
                    }
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 22.dp)
                .size(72.dp)
                .shadow(10.dp, CircleShape, spotColor = p.accent)
                .clip(CircleShape)
                .background(p.accent)
                .clickable(onClick = onCapture)
                .semantics { contentDescription = "Add a task by voice"; role = Role.Button },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painterResource(R.drawable.ic_mic), contentDescription = null,
                colorFilter = ColorFilter.tint(p.onAccent), modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
private fun Tabs(selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.soft).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                    .background(if (sel) p.surface else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp)
                    .semantics { role = Role.Tab },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label, fontSize = 14.sp, color = if (sel) p.ink else p.muted,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

private fun todayRows(tasks: List<Task>, now: Long): List<ListRow> {
    val out = mutableListOf<ListRow>()
    fun section(name: String, list: List<Task>) {
        if (list.isNotEmpty()) { out += ListRow.Head(name); list.forEach { out += ListRow.Item(it) } }
    }
    val open = tasks.filter { it.done == null }.sortedBy { it.due }
    val sod = startOfDay(now)
    val today = open.filter { it.due >= sod && dayDiff(it.due, now) == 0L }
    section("Overdue", open.filter { it.due < sod })
    section("Morning", today.filter { it.due.toLdt().hour < 12 })
    section("Afternoon", today.filter { it.due.toLdt().hour in 12..16 })
    section("Evening", today.filter { it.due.toLdt().hour >= 17 })
    section("Done today", tasks.filter { it.done != null && dayDiff(it.done, now) == 0L }.sortedByDescending { it.done })
    return out
}

private fun upcomingRows(tasks: List<Task>, now: Long): List<ListRow> {
    val out = mutableListOf<ListRow>()
    tasks.filter { it.done == null && dayDiff(it.due, now) > 0 }
        .sortedBy { it.due }
        .groupBy { it.due.toLdt().toLocalDate() }
        .forEach { (date, list) ->
            val label = if (date == LocalDate.now().plusDays(1)) "Tomorrow" else date.format(longDateFmt)
            out += ListRow.Head(label)
            list.forEach { out += ListRow.Item(it) }
        }
    return out
}

private fun doneRows(tasks: List<Task>): List<ListRow> =
    tasks.filter { it.done != null }.sortedByDescending { it.done }.take(60).map { ListRow.Item(it) }

@Composable
private fun Empty(tab: Int) {
    val p = LocalPalette.current
    val (title, body) = when (tab) {
        0 -> "Nothing for today" to "Tap the mic and say what you need to remember."
        1 -> "Nothing scheduled ahead" to "Tasks for tomorrow and later show up here."
        else -> "No finished tasks yet" to "Tick a task to move it here."
    }
    Column(Modifier.fillMaxWidth().padding(top = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = p.ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(body, color = p.muted, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun TaskRow(t: Task, now: Long, expanded: Boolean, onExpand: () -> Unit) {
    val p = LocalPalette.current
    val done = t.done
    val late = done == null && t.due < now
    val meta = when {
        done != null -> "Done " + fmtWhen(done, now).replaceFirstChar { it.lowercase() }
        late -> "Overdue, " + fmtWhen(t.due, now).replaceFirstChar { it.lowercase() }
        else -> fmtWhen(t.due, now)
    }
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onExpand).padding(vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                CheckCircle(done != null) { TaskStore.toggleDone(t.id) }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f).padding(top = 4.dp)) {
                    Text(
                        t.title, fontSize = 16.sp, color = if (done != null) p.muted else p.ink,
                        textDecoration = if (done != null) TextDecoration.LineThrough else null
                    )
                    Text(meta, fontSize = 13.sp, color = if (late) p.warn else p.muted)
                }
            }
            if (expanded) {
                Row(
                    Modifier.padding(start = 40.dp, top = 10.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (done == null) {
                        Chip("In 1 hour") { TaskStore.reschedule(t.id, System.currentTimeMillis() + HOUR) }
                        Chip("This evening") {
                            var d = LocalDate.now().atTime(18, 0)
                            if (d.millis() < System.currentTimeMillis()) d = d.plusDays(1)
                            TaskStore.reschedule(t.id, d.millis())
                        }
                        Chip("Tomorrow") {
                            TaskStore.reschedule(t.id, LocalDate.now().plusDays(1).atTime(LocalTime.of(9, 0)).millis())
                        }
                    }
                    Chip("Delete") { TaskStore.delete(t.id) }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
    }
}

@Composable
private fun CheckCircle(checked: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClick)
            .semantics {
                contentDescription = if (checked) "Mark not done" else "Mark done"
                role = Role.Checkbox
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(22.dp)) {
            val r = size.minDimension / 2
            if (checked) {
                drawCircle(p.accent, r)
                val path = Path().apply {
                    moveTo(size.width * 0.28f, size.height * 0.52f)
                    lineTo(size.width * 0.44f, size.height * 0.68f)
                    lineTo(size.width * 0.73f, size.height * 0.36f)
                }
                drawPath(path, p.onAccent, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            } else {
                drawCircle(p.muted, r - 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx()))
            }
        }
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Text(
        label, fontSize = 13.sp, color = p.ink,
        modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, p.line, RoundedCornerShape(50))
            .background(p.surface).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun Stats(tasks: List<Task>, now: Long) {
    val p = LocalPalette.current
    val done = tasks.mapNotNull { t -> t.done?.let { t to it } }
    val today = LocalDate.now()
    val weekStart = startOfDay(now) - 6 * 24 * HOUR
    val thisWeek = done.count { it.second >= weekStart }
    val onTime = done.count { (t, at) -> at <= t.due + 30 * MINUTE }
    val rate = if (done.isEmpty()) "-" else "${onTime * 100 / done.size}%"
    val doneDays = done.map { it.second.toLdt().toLocalDate() }
    val daySet = doneDays.toSet()
    var day = if (today in daySet) today else today.minusDays(1)
    var streak = 0
    while (day in daySet) { streak++; day = day.minusDays(1) }
    val openCount = tasks.count { it.done == null }
    val week = (6 downTo 0).map { back -> val d = today.minusDays(back.toLong()); d to doneDays.count { it == d } }
    val max = (week.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 140.dp)) {
        Column(
            Modifier.clip(RoundedCornerShape(14.dp)).background(p.line).padding(1.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                StatCell("$thisWeek", "Done this week", Modifier.weight(1f))
                StatCell("$streak", "Day streak", Modifier.weight(1f))
            }
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                StatCell(rate, "Done on time", Modifier.weight(1f))
                StatCell("$openCount", "Still open", Modifier.weight(1f))
            }
        }
        Text(
            "Last 7 days", color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 26.dp, bottom = 10.dp, start = 2.dp)
        )
        Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            week.forEach { (d, n) ->
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier.fillMaxWidth()
                                .fillMaxHeight(maxOf(0.03f, n.toFloat() / max * 0.95f))
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                                .background(if (n > 0) p.accent else p.line)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(d.dayOfWeek.name.take(1), color = p.muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier) {
    val p = LocalPalette.current
    Column(modifier.fillMaxHeight().background(p.surface).padding(16.dp)) {
        Text(value, color = p.ink, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = p.muted, fontSize = 13.sp)
    }
}
