package com.zeno.ai.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.RecognizerIntent
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.zeno.ai.ChatMessage
import com.zeno.ai.ImageUtil
import com.zeno.ai.OrbState
import com.zeno.ai.PhoneControlService
import com.zeno.ai.WakeWordService
import com.zeno.ai.ZenoEngine
import com.zeno.ai.ui.theme.ZBg
import com.zeno.ai.ui.theme.ZBg2
import com.zeno.ai.ui.theme.ZCard
import com.zeno.ai.ui.theme.ZCyan
import com.zeno.ai.ui.theme.ZError
import com.zeno.ai.ui.theme.ZMagenta
import com.zeno.ai.ui.theme.ZTextHi
import com.zeno.ai.ui.theme.ZTextLo
import com.zeno.ai.ui.theme.ZViolet
import java.io.File

private const val DEFAULT_IMAGE_PROMPT = "Ee image lo em undo cheppu"

@Composable
fun ZenoApp(vm: ZenoEngine) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    LaunchedEffect(Unit) {
        val wanted = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.READ_CONTACTS)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = wanted.filter { ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permLauncher.launch(missing.toTypedArray())
    }
    LaunchedEffect(vm.openTab) {
        vm.openTab?.let {
            tab = it
            vm.openTab = null
        }
    }

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
private fun Header(vm: ZenoEngine) {
    val palette = OrbPalettes[vm.settings.theme.coerceIn(0, OrbPalettes.lastIndex)]
    val dot = when (vm.orb) {
        OrbState.Idle -> ZTextLo
        OrbState.Listening -> palette.listen
        OrbState.Thinking -> palette.think
        OrbState.Speaking -> palette.speak
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "ZENO",
            color = palette.listen,
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

/** A row of selectable pills; [options] are (value, label) pairs. */
@Composable
private fun Pills(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val sel = selected == value
            Text(
                label,
                color = if (sel) ZBg else ZTextHi,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (sel) ZCyan else ZCard)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- Voice

@Composable
private fun HomeScreen(vm: ZenoEngine) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val s = vm.settings
    val palette = OrbPalettes[s.theme.coerceIn(0, OrbPalettes.lastIndex)]

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) {
            vm.startListening()
        } else {
            vm.error = "Mic permission ivvandi. App settings lo Microphone ni Allow cheyandi."
            vm.logEvent("mic permission denied, opening app settings")
            try {
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                )
            } catch (e: Exception) {
            }
        }
    }
    val onTap: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (vm.orb == OrbState.Idle) {
            val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) vm.startListening() else launcher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            vm.stop()
        }
    }
    // Fallback: Google's own voice dialog, which works on phones where the in-app mic path fails.
    val dialog = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        vm.logEvent("google dialog result code=${res.resultCode} text=${text ?: "none"}")
        if (res.resultCode == Activity.RESULT_OK && !text.isNullOrBlank()) vm.send(text, viaVoice = true)
    }
    val openDialog: () -> Unit = {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, s.lang)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Matladandi")
        try {
            dialog.launch(i)
        } catch (e: Exception) {
            vm.error = "Ee phone lo voice input ledu. Play Store nunchi Google app install/update cheyandi."
        }
    }
    var handledFallback by remember { mutableIntStateOf(vm.fallbackRequest) }
    LaunchedEffect(vm.fallbackRequest) {
        if (vm.fallbackRequest != handledFallback) {
            handledFallback = vm.fallbackRequest
            openDialog()
        }
    }

    val translator = s.mode == "translator"
    val status = when (vm.orb) {
        OrbState.Idle -> when (s.mode) {
            "translator" -> "Translator: tap and speak"
            "agent" -> "Phone control: em cheyalo cheppandi"
            else -> "Tap the orb and speak"
        }
        OrbState.Listening -> "Listening…"
        OrbState.Thinking -> "Thinking…"
        OrbState.Speaking -> "Speaking… tap to stop"
    }
    val lastReply = vm.messages.lastOrNull { !it.isUser }?.text.orEmpty()

    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Pills(
            options = listOf("assistant" to "Assistant", "agent" to "Phone control", "translator" to "Translator"),
            selected = s.mode,
            onSelect = { vm.updateSettings(s.copy(mode = it)) },
        )
        Spacer(Modifier.height(8.dp))
        Pills(
            options = listOf("te-IN" to "తెలుగు", "en-IN" to "English", "hi-IN" to "हिन्दी"),
            selected = s.lang,
            onSelect = { vm.updateSettings(s.copy(lang = it)) },
        )
        Spacer(Modifier.height(10.dp))
        Text(status, color = ZTextLo, fontFamily = FontFamily.Monospace, fontSize = 14.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        Orb(
            state = vm.orb,
            level = vm.level,
            palette = palette,
            modifier = Modifier
                .size(250.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTap() },
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "🎙 Orb pani cheyakapothe ikkada nokkandi",
            color = ZTextLo,
            fontSize = 12.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable { openDialog() }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Spacer(Modifier.height(4.dp))
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val shown = if (vm.partial.isNotEmpty()) "“${vm.partial}”" else lastReply
            Text(
                shown,
                color = if (vm.partial.isNotEmpty()) palette.listen else ZTextHi,
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
private fun QuickChips(vm: ZenoEngine) {
    val chips = if (vm.settings.mode == "translator") {
        emptyList()
    } else if (vm.settings.mode == "agent") {
        listOf("Open WhatsApp", "Open Settings", "YouTube lo Telugu songs search chey", "Go home")
    } else {
        listOf("Good morning", "What's the time?", "Open YouTube", "Torch on", "Timer 5 minutes", "Tell me a joke")
    }
    if (chips.isEmpty()) return
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
private fun ChatScreen(vm: ZenoEngine) {
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val listState = rememberLazyListState()
    val lastLen = vm.messages.lastOrNull()?.text?.length ?: 0

    LaunchedEffect(vm.messages.size, lastLen) {
        if (vm.messages.isNotEmpty()) listState.scrollToItem(vm.messages.lastIndex)
    }

    fun sendImage(uri: Uri) {
        val b64 = ImageUtil.toBase64(ctx, uri)
        if (b64 == null) {
            vm.error = "Image load cheyalekapoyanu"
        } else {
            vm.send(input.ifBlank { DEFAULT_IMAGE_PROMPT }, image = b64)
            input = ""
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) sendImage(uri)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) sendImage(uri)
    }

    Column(Modifier.fillMaxSize()) {
        if (vm.messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Zeno ni edaina adagandi, leda photo pampandi…", color = ZTextLo)
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
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "📷",
                fontSize = 22.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        val dir = File(ctx.cacheDir, "images").apply { mkdirs() }
                        val file = File(dir, "shot_${System.currentTimeMillis()}.jpg")
                        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
                        cameraUri = uri
                        takePicture.launch(uri)
                    }
                    .padding(8.dp),
            )
            Text(
                "🖼️",
                fontSize = 22.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    .padding(8.dp),
            )
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message Zeno") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                colors = fieldColors(),
            )
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
    val bitmap = remember(m.id, m.image) {
        m.image?.let {
            try {
                val bytes = Base64.decode(it, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = if (m.isUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
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
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.height(8.dp))
            }
            SelectionContainer {
                Text(if (m.text.isEmpty()) "…" else m.text, color = ZTextHi, fontSize = 15.sp, lineHeight = 22.sp)
            }
        }
    }
}

// ---------------------------------------------------------------- Settings

@Composable
private fun SettingsScreen(vm: ZenoEngine) {
    val ctx = LocalContext.current
    val s = vm.settings
    val isOr = s.provider == "openrouter"

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionTitle("AI provider")
        Pills(
            options = listOf("openrouter" to "OpenRouter (free)", "gemini" to "Gemini"),
            selected = s.provider,
            onSelect = { vm.updateSettings(s.copy(provider = it)) },
        )

        SectionTitle(if (isOr) "OpenRouter API key" else "Gemini API key")
        OutlinedTextField(
            value = if (isOr) s.orKey else s.apiKey,
            onValueChange = {
                vm.updateSettings(if (isOr) s.copy(orKey = it.trim()) else s.copy(apiKey = it.trim()))
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            placeholder = { Text(if (isOr) "sk-or-…" else "AIza…") },
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors(),
        )
        Text(
            if (isOr) "Free key: openrouter.ai/keys. Ee phone lo mathrame save avthundi."
            else "Free key: aistudio.google.com/apikey. Ee phone lo mathrame save avthundi.",
            color = ZTextLo,
            fontSize = 12.sp,
        )

        SectionTitle("Model")
        OutlinedTextField(
            value = if (isOr) s.orModel else s.model,
            onValueChange = {
                vm.updateSettings(if (isOr) s.copy(orModel = it.trim()) else s.copy(model = it.trim()))
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors(),
        )
        if (isOr) {
            Text("openrouter/free = free models lo available ayyedi auto select chesthundi.", color = ZTextLo, fontSize = 12.sp)
        }

        SectionTitle("Voice language")
        Pills(
            options = listOf("te-IN" to "తెలుగు", "en-IN" to "English", "hi-IN" to "हिन्दी"),
            selected = s.lang,
            onSelect = { vm.updateSettings(s.copy(lang = it)) },
        )

        SectionTitle("Orb theme")
        Pills(
            options = OrbPalettes.mapIndexed { i, p -> i.toString() to p.name },
            selected = s.theme.toString(),
            onSelect = { vm.updateSettings(s.copy(theme = it.toInt())) },
        )

        SectionTitle("Wake word key (Picovoice, free)")
        OutlinedTextField(
            value = s.picoKey,
            onValueChange = { vm.updateSettings(s.copy(picoKey = it.trim())) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            placeholder = { Text("AccessKey paste cheyandi") },
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors(),
        )
        Text(
            "Free key: console.picovoice.ai. Key unte wake word \"Jarvis\" (offline, accurate). Key lekapothe \"Hey Zeno\" (takkuva accurate).",
            color = ZTextLo,
            fontSize = 12.sp,
        )
        SwitchRow("Wake word (Jarvis / Hey Zeno)", "App close chesina, screen off lo kuda pilavochu", s.wake) { on ->
            vm.updateSettings(s.copy(wake = on))
            val mic = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (on && mic) WakeWordService.start(ctx)
            else if (on) vm.error = "Mic permission ivvandi, tarvata malli on cheyandi"
            else WakeWordService.stop(ctx)
        }
        SectionTitle("Phone control (full control)")
        Text(
            if (PhoneControlService.instance != null) "Status: ON. Zeno screen chusi tap/type/scroll cheyagaladu."
            else "Status: OFF. Accessibility lo Zeno AI ni on cheyandi.",
            color = ZTextLo,
            fontSize = 12.sp,
        )
        OutlinedButton(
            onClick = { vm.openAccessibilitySettings() },
            border = BorderStroke(1.dp, ZCyan.copy(alpha = 0.6f)),
        ) { Text("Phone control on cheyyi (Accessibility)", color = ZCyan) }
        Text(
            "Voice tab lo Phone control mode select chesi cheppandi. Idi Gemini provider tho baaga pani chesthundi. " +
                "Payments, purchases, delete lanti steps Zeno cheyadu.",
            color = ZTextLo,
            fontSize = 12.sp,
        )
        OutlinedButton(
            onClick = { vm.runDiagnostics() },
            border = BorderStroke(1.dp, ZCyan.copy(alpha = 0.6f)),
        ) { Text("Voice test (Telugu/English check)", color = ZCyan) }
        SectionTitle("Debug log (problem vaste screenshot pampandi)")
        Text(
            if (vm.log.isEmpty()) "Inka emi ledu. Voice tab lo orb tap chesi, ikkadiki randi."
            else vm.log.take(16).joinToString("\n"),
            color = ZTextHi,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ZCard)
                .padding(12.dp),
        )
        if (vm.diag.isNotBlank()) {
            Text(
                vm.diag,
                color = ZTextHi,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ZCard)
                    .padding(12.dp),
            )
        }
        SwitchRow("Reply voice lo chepali", "Voice tho adigina prathi answer ni Zeno matladutundi", s.speak) {
            vm.updateSettings(s.copy(speak = it))
        }
        SwitchRow("Continuous conversation", "Zeno matladaka malli automatic ga vintundi", s.continuous) {
            vm.updateSettings(s.copy(continuous = it))
        }

        SectionTitle("City (Good morning briefing kosam)")
        OutlinedTextField(
            value = s.city,
            onValueChange = { vm.updateSettings(s.copy(city = it)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Hyderabad") },
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors(),
        )

        SectionTitle("Zeno ki gurtunna vishayalu")
        if (vm.memory.isEmpty()) {
            Text("Inka emi ledu. Voice lo \"remember that …\" ani cheppandi.", color = ZTextLo, fontSize = 13.sp)
        } else {
            vm.memory.toList().forEachIndexed { i, fact ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ZCard)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(fact, color = ZTextHi, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(
                        "✕",
                        color = ZError,
                        modifier = Modifier.clip(CircleShape).clickable { vm.forget(i) }.padding(8.dp),
                    )
                }
            }
        }

        OutlinedButton(
            onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, vm.exportChat())
                }
                ctx.startActivity(Intent.createChooser(send, "Chat export"))
            },
            border = BorderStroke(1.dp, ZCyan.copy(alpha = 0.6f)),
        ) { Text("Chat export / share", color = ZCyan) }

        OutlinedButton(
            onClick = { vm.clearHistory() },
            border = BorderStroke(1.dp, ZError.copy(alpha = 0.6f)),
        ) { Text("Chat history clear cheyyi", color = ZError) }

        Text("Zeno AI v3.0", color = ZTextLo, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
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
