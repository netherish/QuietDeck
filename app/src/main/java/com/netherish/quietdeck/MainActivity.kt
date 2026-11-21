package com.netherish.quietdeck

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.matchParentSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val Bg = Color(0xFF050914)
private val Surface = Color(0xFF121A2E)
private val SurfaceSoft = Color(0xFF18223A)
private val Line = Color(0xFF263552)
private val TextPrimary = Color(0xFFEAF2FF)
private val TextMuted = Color(0xFF9FB3D1)
private val Cyan = Color(0xFF00E5FF)
private val Amber = Color(0xFFFFB300)
private val Mint = Color(0xFF7BE495)
private val Rose = Color(0xFFFF6B6B)

private data class Preset(
    val glyph: String,
    val title: String,
    val subtitle: String,
    val filter: Int,
    val ringer: Int,
    val accent: Color
)

private data class DeckEvent(
    val time: String,
    val title: String,
    val detail: String,
    val accent: Color
)

private data class VolumeSnapshot(
    val media: Int,
    val mediaMax: Int,
    val ring: Int,
    val ringMax: Int,
    val alarm: Int,
    val alarmMax: Int
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Cyan,
                    secondary = Amber,
                    tertiary = Mint,
                    background = Bg,
                    surface = Surface,
                    onBackground = TextPrimary,
                    onSurface = TextPrimary,
                    error = Rose
                )
            ) {
                QuietDeckScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuietDeckScreen() {
    val context = LocalContext.current
    val notificationManager = remember { context.getSystemService(NotificationManager::class.java) }
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }

    var filter by remember {
        mutableStateOf(notificationManager?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL)
    }
    var ringer by remember {
        mutableStateOf(audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL)
    }
    var hasPolicyAccess by remember {
        mutableStateOf(notificationManager?.isNotificationPolicyAccessGranted == true)
    }
    var volumes by remember { mutableStateOf(readVolumes(audioManager)) }
    var events by remember {
        mutableStateOf(
            listOf(DeckEvent(formatTime(), "Deck ready", "System state loaded", Mint))
        )
    }

    fun record(title: String, detail: String, accent: Color = Cyan) {
        events = (listOf(DeckEvent(formatTime(), title, detail, accent)) + events).takeLast(12)
    }

    fun refresh() {
        hasPolicyAccess = notificationManager?.isNotificationPolicyAccessGranted == true
        filter = notificationManager?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
        ringer = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
        volumes = readVolumes(audioManager)
    }

    fun applyFilter(target: Int, label: String) {
        if (!hasPolicyAccess) {
            openPolicySettings(context)
            record("Access needed", "Grant DND access in Android Settings", Amber)
            return
        }
        try {
            notificationManager?.setInterruptionFilter(target)
            filter = target
            record("DND: $label", "Notification policy updated", Cyan)
        } catch (_: SecurityException) {
            hasPolicyAccess = false
            record("Change blocked", "Android refused the DND change", Rose)
        }
    }

    fun applyRinger(target: Int) {
        try {
            audioManager?.setRingerMode(target)
            ringer = target
            record("Ringer: ${ringerLabel(target)}", "Sound mode updated", Amber)
        } catch (_: Exception) {
            record("Ringer unchanged", "Android refused the sound change", Rose)
        }
    }

    fun applyPreset(preset: Preset) {
        applyRinger(preset.ringer)
        applyFilter(preset.filter, preset.title)
        record("Preset: ${preset.title}", preset.subtitle, preset.accent)
    }

    fun setVolume(stream: Int, value: Int, label: String) {
        try {
            audioManager?.setStreamVolume(stream, value, 0)
            refresh()
            record("$label volume", "Set to $value", Mint)
        } catch (_: Exception) {
            record("$label volume", "Android refused the volume change", Rose)
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val presets = listOf(
        Preset("◎", "Normal", "Everything on", NotificationManager.INTERRUPTION_FILTER_ALL, AudioManager.RINGER_MODE_NORMAL, Cyan),
        Preset("◈", "Work", "Priority interruptions", NotificationManager.INTERRUPTION_FILTER_PRIORITY, AudioManager.RINGER_MODE_VIBRATE, Amber),
        Preset("☾", "Sleep", "Alarms only", NotificationManager.INTERRUPTION_FILTER_ALARMS, AudioManager.RINGER_MODE_SILENT, Color(0xFFB388FF)),
        Preset("➤", "Driving", "Reduce distractions", NotificationManager.INTERRUPTION_FILTER_PRIORITY, AudioManager.RINGER_MODE_VIBRATE, Mint),
        Preset("●", "Meeting", "Total silence", NotificationManager.INTERRUPTION_FILTER_NONE, AudioManager.RINGER_MODE_SILENT, Rose),
        Preset("✦", "Night Owl", "Quiet but reachable", NotificationManager.INTERRUPTION_FILTER_PRIORITY, AudioManager.RINGER_MODE_VIBRATE, Color(0xFFFF8A65))
    )

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("QuietDeck", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("System sound console", fontSize = 11.sp, color = TextMuted)
                    }
                },
                actions = {
                    TextButton(onClick = { refresh(); record("State refreshed", "System values reloaded", TextMuted) }) {
                        Text("Refresh", color = Cyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Bg,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            AuroraBackground()

            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(
                            brush = Brush.horizontalGradient(listOf(Cyan, Amber, Mint)),
                            shape = RoundedCornerShape(99.dp)
                        )
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "CURRENT STATE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )
                        Spacer(Modifier.height(10.dp))
                        DNDial(filter, ringer)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            policySummary(notificationManager),
                            color = TextMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RingerChip("Normal", ringer == AudioManager.RINGER_MODE_NORMAL, Cyan) {
                                applyRinger(AudioManager.RINGER_MODE_NORMAL)
                            }
                            RingerChip("Vibrate", ringer == AudioManager.RINGER_MODE_VIBRATE, Amber) {
                                applyRinger(AudioManager.RINGER_MODE_VIBRATE)
                            }
                            RingerChip("Silent", ringer == AudioManager.RINGER_MODE_SILENT, Rose) {
                                applyRinger(AudioManager.RINGER_MODE_SILENT)
                            }
                        }
                    }
                }

                if (!hasPolicyAccess) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1835)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("DND access is off", fontWeight = FontWeight.Bold, color = Amber)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Android requires notification policy access before QuietDeck can read or change Do Not Disturb.",
                                color = TextPrimary,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    openPolicySettings(context)
                                    record("Policy settings opened", "Grant access and tap Refresh", Amber)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Bg)
                            ) {
                                Text("Grant DND access")
                            }
                        }
                    }
                }

                Text("Presets", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PresetCard(presets[0], Modifier.weight(1f)) { applyPreset(presets[0]) }
                    PresetCard(presets[1], Modifier.weight(1f)) { applyPreset(presets[1]) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PresetCard(presets[2], Modifier.weight(1f)) { applyPreset(presets[2]) }
                    PresetCard(presets[3], Modifier.weight(1f)) { applyPreset(presets[3]) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PresetCard(presets[4], Modifier.weight(1f)) { applyPreset(presets[4]) }
                    PresetCard(presets[5], Modifier.weight(1f)) { applyPreset(presets[5]) }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Stream volumes", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        VolumeSlider("Media", volumes.media, volumes.mediaMax, Cyan) {
                            setVolume(AudioManager.STREAM_MUSIC, it, "Media")
                        }
                        Spacer(Modifier.height(10.dp))
                        VolumeSlider("Ring", volumes.ring, volumes.ringMax, Amber) {
                            setVolume(AudioManager.STREAM_RING, it, "Ring")
                        }
                        Spacer(Modifier.height(10.dp))
                        VolumeSlider("Alarm", volumes.alarm, volumes.alarmMax, Mint) {
                            setVolume(AudioManager.STREAM_ALARM, it, "Alarm")
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Action log", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        events.take(6).forEach { event ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .size(8.dp)
                                            .background(event.accent, RoundedCornerShape(99.dp))
                                    )
                                    Spacer(Modifier.size(8.dp))
                                    Column {
                                        Text(event.title, fontSize = 13.sp, color = TextPrimary)
                                        Text(event.detail, fontSize = 11.sp, color = TextMuted)
                                    }
                                }
                                Text(event.time, fontSize = 11.sp, color = TextMuted)
                            }
                            Spacer(Modifier.height(9.dp))
                        }
                    }
                }

                Text(
                    "No account. No network. Built for daily use — even better as a system app.",
                    color = TextMuted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun AuroraBackground() {
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(
            brush = Brush.radialGradient(listOf(Cyan.copy(alpha = 0.14f), Color.Transparent)),
            center = Offset(size.width * 0.08f, size.height * 0.02f),
            radius = size.minDimension * 0.65f
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(Amber.copy(alpha = 0.10f), Color.Transparent)),
            center = Offset(size.width * 0.95f, size.height * 0.28f),
            radius = size.minDimension * 0.55f
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0xFFB388FF).copy(alpha = 0.08f), Color.Transparent)),
            center = Offset(size.width * 0.2f, size.height * 0.9f),
            radius = size.minDimension * 0.6f
        )
    }
}

@Composable
private fun DNDial(filter: Int, ringer: Int) {
    val progress = when (filter) {
        NotificationManager.INTERRUPTION_FILTER_ALL -> 0.06f
        NotificationManager.INTERRUPTION_FILTER_PRIORITY -> 0.42f
        NotificationManager.INTERRUPTION_FILTER_ALARMS -> 0.72f
        else -> 1f
    }

    Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            drawArc(
                color = Line,
                startAngle = -90f,
                sweepAngle = 300f,
                useCenter = false,
                style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(Cyan, Amber, Rose)),
                startAngle = -90f,
                sweepAngle = 300f * progress,
                useCenter = false,
                style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("DO NOT DISTURB", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(filterLabel(filter), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(5.dp))
            Text(ringerLabel(ringer), fontSize = 12.sp, color = if (ringer == AudioManager.RINGER_MODE_SILENT) Rose else Mint)
        }
    }
}

@Composable
private fun RingerChip(label: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (active) accent.copy(alpha = 0.14f) else Color.Transparent,
            contentColor = if (active) accent else TextMuted
        ),
        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
    ) {
        Text(label, fontSize = 12.sp)
    }
}

@Composable
private fun PresetCard(preset: Preset, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SurfaceSoft)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(preset.glyph, fontSize = 22.sp, color = preset.accent)
            Spacer(Modifier.height(8.dp))
            Text(preset.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(preset.subtitle, fontSize = 11.sp, color = TextMuted)
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(preset.accent, RoundedCornerShape(99.dp))
            )
        }
    }
}

@Composable
private fun VolumeSlider(label: String, value: Int, max: Int, accent: Color, onChange: (Int) -> Unit) {
    val safeMax = max.coerceAtLeast(1)
    Text("$label  $value / $safeMax", fontSize = 12.sp, color = TextMuted)
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(it.roundToInt()) },
        valueRange = 0f..safeMax.toFloat(),
        steps = (safeMax - 1).coerceAtLeast(0),
        colors = SliderDefaults.colors(
            thumbColor = accent,
            activeTrackColor = accent,
            inactiveTrackColor = Line
        )
    )
}

private fun openPolicySettings(context: Context) {
    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // Some OEM skins hide this page. The user can still reach it from Settings.
    }
}

private fun readVolumes(audioManager: AudioManager?): VolumeSnapshot {
    if (audioManager == null) {
        return VolumeSnapshot(0, 1, 0, 1, 0, 1)
    }
    return VolumeSnapshot(
        media = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
        mediaMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1),
        ring = audioManager.getStreamVolume(AudioManager.STREAM_RING),
        ringMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING).coerceAtLeast(1),
        alarm = audioManager.getStreamVolume(AudioManager.STREAM_ALARM),
        alarmMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM).coerceAtLeast(1)
    )
}

private fun filterLabel(filter: Int): String = when (filter) {
    NotificationManager.INTERRUPTION_FILTER_ALL -> "Off"
    NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "Priority"
    NotificationManager.INTERRUPTION_FILTER_ALARMS -> "Alarms only"
    NotificationManager.INTERRUPTION_FILTER_NONE -> "Total silence"
    else -> "Custom"
}

private fun ringerLabel(ringer: Int): String = when (ringer) {
    AudioManager.RINGER_MODE_NORMAL -> "Normal ringer"
    AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
    AudioManager.RINGER_MODE_SILENT -> "Silent"
    else -> "Unknown"
}

private fun policySummary(manager: NotificationManager?): String {
    if (manager == null) return "System audio service unavailable"
    return when (manager.currentInterruptionFilter) {
        NotificationManager.INTERRUPTION_FILTER_ALL -> "All notifications can reach you"
        NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "Only priority notifications can reach you"
        NotificationManager.INTERRUPTION_FILTER_ALARMS -> "Only alarms can sound"
        NotificationManager.INTERRUPTION_FILTER_NONE -> "Notifications are silenced"
        else -> "Custom interruption filter"
    }
}

private fun formatTime(): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
