package com.zeno.ai.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.zeno.ai.ChatMessage
import com.zeno.ai.OrbState
import com.zeno.ai.ZenoViewModel
import com.zeno.ai.ui.theme.ZBg
import com.zeno.ai.ui.theme.ZBg2
import com.zeno.ai.ui.theme.ZCard
import com.zeno.ai.ui.theme.ZCyan
import com.zeno.ai.ui.theme.ZError
import com.zeno.ai.ui.theme.ZMagenta
import com.zeno.ai.ui.theme.ZTextHi
import com.zeno.ai.ui.theme.ZTextLo
import com.zeno.ai.ui.theme.ZViolet

@Composable
fun ZenoApp(vm: ZenoViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ZBg, ZBg2, ZBg)))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Header(vm)
            vm.error?.let { msg -> ErrorBanner(msg) { vm.error = null } }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> HomeScreen(vm)
                    1 -> ChatScreen(vm)
                    else -> SettingsScreen(vm)
                }
            }
            TabBar(tab) { tab = it }
        }
    }
}

@Composable
private fun Header(vm: ZenoViewModel) {
    val dot = when (vm.orb) {
        OrbState.Idle -> ZTextLo
        OrbState.Listening -> ZCyan
        OrbState.Thinking -> ZViolet
        OrbState.Speaking -> ZMagenta
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "ZENO",
            color = ZCyan,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            letterSpacing = 6.sp,
        )
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.size(8.dp))
        Text(
            vm.orb.name.uppercase(),
            color = ZTextLo,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            letterSpacing = 2.sp,
        )
    }
}

@Composable
private fun ErrorBanner(msg: String, onDismiss: () -> Unit) {
    Text(
        msg,
        color = ZError,
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ZError.copy(alpha = 0.12f))
            .clickable { onDismiss() }
            .padding(12.dp),
    )
}

@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf("Voice", "Chat", "Settings")
    val shape = RoundedCornerShape(28.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(shape)
            .background(ZCard.copy(alpha = 0.85f))
            .border(1.dp, ZCyan.copy(alpha = 0.18f), shape)
            .padding(4.dp)
    ) {
        labels.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (sel) Brush.horizontalGradient(listOf(ZCyan.copy(alpha = 0.25f), ZViolet.copy(alpha = 0.25f)))
                        else SolidColor(Color.Transparent)
                    )
                    .clickable { onSelect(i) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (sel) ZCyan else ZTextLo,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Voice

@Composable
private fun HomeScreen(vm: ZenoViewModel) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.startListening() else vm.error = "Mic permission ivvandi"
    }
    val onTap: () -> Unit = {
        if (vm.orb == OrbState.Idle) {
            val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) vm.startListening() else launcher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            vm.stop()
        }
    }
    val status = when (vm.orb) {
        OrbState.Idle -> "Tap the orb and speak"
        OrbState.Listening -> "Listening…"
        OrbState.Thinking -> "Thinking…"
        OrbState.Speaking -> "Speaking… tap to stop"
    }
    val lastReply = vm.messages.lastOrNull { !it.isUser }?.text.orEmpty()

    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Text(status, color = ZTextLo, fontFamily = FontFamily.Monospace, fontSize = 14.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        Orb(
            state = vm.orb,
            level = vm.level,
            modifier = Modifier
                .size(280.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTap() },
        )
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val shown = if (vm.partial.isNotEmpty()) "“${vm.partial}”" else lastReply
            Text(
                shown,
                color = if (vm.partial.isNotEmpty()) ZCyan else ZTextHi,
                fontSize = 18.sp,
                lineHeight = 26.sp,
                textAlign = TextAlign.Center,
            )
        }
        QuickChips(vm)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun QuickChips(vm: ZenoViewModel) {
    val chips = listOf("What's the time?", "Open YouTube", "Torch on", "Tell me a joke", "Telugu lo oka fun fact cheppu")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
        items(chips) { label ->
            Text(
                label,
                color = ZTextHi,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(ZCard)
                    .border(BorderStroke(1.dp, ZCyan.copy(alpha = 0.25f)), RoundedCornerShape(20.dp))
                    .clickable { vm.send(label, viaVoice = true) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- Chat

@Composable
private fun ChatScreen(vm: ZenoViewModel) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val lastLen = vm.messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(vm.messages.size, lastLen) {
        if (vm.messages.isNotEmpty()) listState.scrollToItem(vm.messages.lastIndex)
    }

    Column(Modifier.fillMaxSize()) {
        if (vm.messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Zeno ni edaina adagandi…", color = ZTextLo)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(vm.messages, key = { it.id }) { Bubble(it) }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message Zeno") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                colors = fieldColors(),
            )
            Spacer(Modifier.size(8.dp))
            IconButton(
                onClick = {
                    vm.send(input)
                    input = ""
                },
                enabled = input.isNotBlank(),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (input.isNotBlank()) ZCyan else ZTextLo,
                )
            }
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage) {
    val shape = RoundedCornerShape(18.dp)
    Box(Modifier.fillMaxWidth(), contentAlignment = if (m.isUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(
                    if (m.isUser) Brush.horizontalGradient(listOf(ZCyan.copy(alpha = 0.28f), ZViolet.copy(alpha = 0.32f)))
                    else SolidColor(ZCard)
                )
                .border(1.dp, if (m.isUser) ZCyan.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.06f), shape)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            SelectionContainer {
                Text(if (m.text.isEmpty()) "…" else m.text, color = ZTextHi, fontSize = 15.sp, lineHeight = 22.sp)
            }
        }
    }
}

// ---------------------------------------------------------------- Settings

@Composable
private fun SettingsScreen(vm: ZenoViewModel) {
    val s = vm.settings
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionTitle("Gemini API key")
        OutlinedTextField(
            value = s.apiKey,
            onValueChange = { vm.updateSettings(s.copy(apiKey = it.trim())) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            placeholder = { Text("AIza…") },
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors(),
        )
        Text("Free key: aistudio.google.com/apikey. Ee phone lo mathrame save avthundi.", color = ZTextLo, fontSize = 12.sp)

        SectionTitle("Model")
        OutlinedTextField(
            value = s.model,
            onValueChange = { vm.updateSettings(s.copy(model = it.trim())) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors(),
        )

        SectionTitle("Voice language")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("te-IN" to "తెలుగు", "en-IN" to "English", "hi-IN" to "हिन्दी").forEach { (code, label) ->
                val sel = s.lang == code
                Text(
                    label,
                    color = if (sel) ZBg else ZTextHi,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (sel) ZCyan else ZCard)
                        .clickable { vm.updateSettings(s.copy(lang = code)) }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }

        SwitchRow("Reply voice lo chepali", "Voice tho adigina prathi answer ni Zeno matladutundi", s.speak) {
            vm.updateSettings(s.copy(speak = it))
        }
        SwitchRow("Continuous conversation", "Zeno matladaka malli automatic ga vintundi", s.continuous) {
            vm.updateSettings(s.copy(continuous = it))
        }

        OutlinedButton(
            onClick = { vm.clearHistory() },
            border = BorderStroke(1.dp, ZError.copy(alpha = 0.6f)),
        ) { Text("Chat history clear cheyyi", color = ZError) }

        Text("Zeno AI v2.0", color = ZTextLo, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = ZCyan, fontFamily = FontFamily.Monospace, fontSize = 13.sp, letterSpacing = 1.sp)
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ZCard)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = ZTextHi, fontWeight = FontWeight.Medium)
            Text(subtitle, color = ZTextLo, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = ZCyan, checkedThumbColor = ZBg),
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ZCyan,
    unfocusedBorderColor = ZCyan.copy(alpha = 0.25f),
    cursorColor = ZCyan,
    focusedTextColor = ZTextHi,
    unfocusedTextColor = ZTextHi,
    focusedContainerColor = ZCard,
    unfocusedContainerColor = ZCard,
    focusedPlaceholderColor = ZTextLo,
    unfocusedPlaceholderColor = ZTextLo,
)
