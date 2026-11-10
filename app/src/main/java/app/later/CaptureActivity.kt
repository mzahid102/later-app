package app.later

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID

/**
 * Opened from the widget mic or the app's mic button.
 * Starts voice input right away, then shows the parsed tasks to confirm.
 */
class CaptureActivity : ComponentActivity() {
    private var text by mutableStateOf("")
    private var drafts by mutableStateOf<List<Parser.Draft>>(emptyList())
    private var status by mutableStateOf("")
    private var added = 0

    private val speech = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val words = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (res.resultCode == RESULT_OK && !words.isNullOrBlank()) {
            text = if (text.isBlank()) words else "$text $words"
            parseNow()
        } else if (text.isBlank()) {
            status = "Nothing recorded. Tap the mic again or type."
        }
    }

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { finishAdded() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        TaskStore.init(this)
        Reminders.ensureChannel(this)
        setContent {
            LaterTheme {
                CaptureSheet(
                    text = text,
                    drafts = drafts,
                    status = status,
                    onText = { text = it; drafts = emptyList(); status = "" },
                    onMic = ::startVoice,
                    onCancel = ::finish,
                    onPrimary = ::primary,
                )
            }
        }
        if (savedInstanceState == null) startVoice()
    }

    private fun startVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "What do you need to do?")
        try {
            speech.launch(intent)
        } catch (_: ActivityNotFoundException) {
            status = "Voice input isn’t available on this phone. Type instead."
        }
    }

    private fun parseNow() {
        drafts = Parser.parse(text)
        status = if (drafts.isEmpty()) "Couldn’t find a task in that. Try saying it differently."
        else "Check these, then add them."
    }

    private fun primary() {
        if (drafts.isEmpty()) {
            if (text.isBlank()) status = "Say or type something first." else parseNow()
            return
        }
        val now = System.currentTimeMillis()
        TaskStore.add(drafts.map { Task(UUID.randomUUID().toString(), it.title, it.due.millis(), now) })
        added = drafts.size
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else finishAdded()
    }

    private fun finishAdded() {
        Toast.makeText(this, if (added == 1) "Task added" else "$added tasks added", Toast.LENGTH_SHORT).show()
        finish()
    }
}

@Composable
private fun CaptureSheet(
    text: String,
    drafts: List<Parser.Draft>,
    status: String,
    onText: (String) -> Unit,
    onMic: () -> Unit,
    onCancel: () -> Unit,
    onPrimary: () -> Unit,
) {
    val p = LocalPalette.current
    val now = System.currentTimeMillis()
    Box(
        Modifier.fillMaxSize().clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCancel
        ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(p.surface)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .navigationBarsPadding()
                .imePadding()
                .padding(20.dp)
        ) {
            Text("What do you need to do?", color = p.ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Say it however you like, like “call the bank tomorrow morning, and send the invoice this evening”.",
                color = p.muted, fontSize = 14.sp
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = text,
                onValueChange = onText,
                placeholder = { Text("Speak or type") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = p.bg, unfocusedContainerColor = p.bg,
                    focusedBorderColor = p.accent, unfocusedBorderColor = p.line,
                )
            )
            if (status.isNotEmpty()) {
                Text(status, color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
            drafts.forEach { d ->
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Top) {
                    Text(d.title, color = p.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Text(fmtWhen(d.due.millis(), now), color = p.muted, fontSize = 13.sp)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(p.soft).clickable(onClick = onMic)
                        .semantics { contentDescription = "Record again" },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painterResource(R.drawable.ic_mic), null,
                        colorFilter = ColorFilter.tint(p.ink), modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    "Cancel", color = p.ink, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(p.soft)
                        .clickable(onClick = onCancel).padding(horizontal = 16.dp, vertical = 13.dp)
                )
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(p.accent)
                        .clickable(onClick = onPrimary).padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        when (drafts.size) { 0 -> "Create tasks"; 1 -> "Add task"; else -> "Add ${drafts.size} tasks" },
                        color = p.onAccent, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
