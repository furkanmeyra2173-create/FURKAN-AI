package com.furkanai.assistant

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Cyan = Color(0xFF48DDF5)
private val Background = Color(0xFF070B14)
private val Panel = Color(0xFF111C2D)
private val Muted = Color(0xFF9EACC1)

data class TtsVoiceOption(val name: String, val label: String)

enum class VoicePreset(val title: String, val pitch: Float, val rate: Float) {
    NATURAL("Doğal", 1.0f, 1.0f),
    JARVIS("Jarvis", 0.78f, 0.92f),
    HACKER("Hacker / Derin", 0.58f, 0.82f),
    ROBOT("Robotik", 0.70f, 1.08f),
    ENERGETIC("Enerjik", 1.12f, 1.08f),
    CALM("Sakin", 0.92f, 0.84f);

    companion object {
        fun fromId(id: String?): VoicePreset = values().firstOrNull { it.name == id } ?: JARVIS
    }
}

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null
    private var speechReady = false
    private var installedVoices by mutableStateOf<List<TtsVoiceOption>>(emptyList())
    private var assistLaunch by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        assistLaunch = intent?.action == Intent.ACTION_ASSIST
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                speechReady = true
                tts?.language = Locale.getDefault()
                installedVoices = tts?.voices
                    ?.sortedWith(compareBy<Voice>({ it.locale.displayName }, { it.name }))
                    ?.map { voice -> TtsVoiceOption(voice.name, voice.locale.displayName + " • " + voice.name) }
                    .orEmpty()
            }
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, background = Background, surface = Panel)) {
                AssistantApp(
                    assistLaunch = assistLaunch,
                    installedVoices = installedVoices,
                    onSpeak = { text, presetId, voiceName ->
                        if (speechReady) {
                            val engine = tts ?: return@AssistantApp
                            val preset = VoicePreset.fromId(presetId)
                            engine.voices?.firstOrNull { it.name == voiceName }?.let { engine.voice = it }
                            engine.setPitch(preset.pitch)
                            engine.setSpeechRate(preset.rate)
                            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "furkan-ai-voice")
                        }
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        assistLaunch = intent.action == Intent.ACTION_ASSIST
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}

@Composable
private fun AssistantApp(
    assistLaunch: Boolean,
    installedVoices: List<TtsVoiceOption>,
    onSpeak: (String, String, String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val prefs = remember { context.getSharedPreferences("furkan_ai_settings", android.content.Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var screen by remember(assistLaunch) { mutableStateOf(if (assistLaunch) "chat" else "home") }
    var provider by remember { mutableStateOf(AiProvider.GEMINI) }
    var autoMode by remember { mutableStateOf(false) }
    var model by remember { mutableStateOf(AiProvider.GEMINI.defaultModel) }
    var keyInput by remember { mutableStateOf("") }
    var keySaved by remember { mutableStateOf(false) }
    var voicePreset by remember { mutableStateOf(VoicePreset.JARVIS.name) }
    var voiceName by remember { mutableStateOf("") }
    var webSearchDefault by remember { mutableStateOf(false) }
    var testStatus by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var roleStatus by remember { mutableStateOf("") }
    var profileName by remember { mutableStateOf("Jarvis") }
    var profilePrompt by remember { mutableStateOf("") }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        roleStatus = if (result.resultCode == Activity.RESULT_OK) {
            "FURKAN AI varsayılan asistan olarak seçildi."
        } else {
            "Varsayılan asistan seçimi tamamlanmadı."
        }
    }

    LaunchedEffect(Unit) {
        provider = AiProvider.fromId(prefs.getString("provider", AiProvider.GEMINI.id))
        if (provider == AiProvider.AUTO) provider = AiProvider.GEMINI
        model = prefs.getString("model_" + provider.id, provider.defaultModel) ?: provider.defaultModel
        autoMode = prefs.getBoolean("auto_mode", false)
        voicePreset = prefs.getString("voice_preset", VoicePreset.JARVIS.name) ?: VoicePreset.JARVIS.name
        voiceName = prefs.getString("voice_name", "") ?: ""
        webSearchDefault = prefs.getBoolean("web_search", false)
        keyInput = ApiKeyVault.read(context, provider).orEmpty()
        keySaved = keyInput.isNotBlank()
        profileName = LocalData.profileName(context)
        profilePrompt = LocalData.profilePrompt(context)
    }

    fun chooseProvider(next: AiProvider) {
        if (next == AiProvider.AUTO) {
            autoMode = true
            prefs.edit().putBoolean("auto_mode", true).apply()
            return
        }
        provider = next
        keyInput = ApiKeyVault.read(context, next).orEmpty()
        keySaved = keyInput.isNotBlank()
        model = prefs.getString("model_" + next.id, next.defaultModel) ?: next.defaultModel
        testStatus = ""
        prefs.edit().putString("provider", next.id).apply()
    }

    fun saveSettings() {
        ApiKeyVault.save(context, provider, keyInput)
        prefs.edit()
            .putString("provider", provider.id)
            .putString("model_" + provider.id, model.trim())
            .putBoolean("auto_mode", autoMode)
            .putBoolean("web_search", webSearchDefault)
            .putString("voice_preset", voicePreset)
            .putString("voice_name", voiceName)
            .apply()
        keyInput = ApiKeyVault.read(context, provider).orEmpty()
        keySaved = true
        testStatus = "Anahtar ve ayarlar bu telefona kaydedildi."
    }

    fun requestAssistantRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val manager = context.getSystemService(RoleManager::class.java)
            if (!manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                roleStatus = "Bu Android sürümü varsayılan asistan rolünü sunmuyor."
            } else if (manager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
                roleStatus = "FURKAN AI zaten varsayılan asistan."
            } else {
                roleLauncher.launch(manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))
            }
        } else {
            try {
                context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                roleStatus = "Sesli giriş ayarları açıldı."
            } catch (_: Exception) {
                roleStatus = "Bu cihaz varsayılan asistan seçimini desteklemiyor."
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = Background) {
        when (screen) {
            "chat" -> ChatScreen(
                provider, model, autoMode, voicePreset, voiceName, webSearchDefault,
                profileName, profilePrompt,
                onBack = { screen = "home" },
                onSettings = { screen = "settings" },
                onSpeak = onSpeak
            )
            "settings" -> SettingsScreen(
                provider = provider,
                autoMode = autoMode,
                model = model,
                keyInput = keyInput,
                keySaved = keySaved,
                voicePreset = voicePreset,
                voiceName = voiceName,
                voices = installedVoices,
                webSearch = webSearchDefault,
                testStatus = testStatus,
                testing = testing,
                onProviderChange = { next -> chooseProvider(next) },
                onAutoChange = { autoMode = it; prefs.edit().putBoolean("auto_mode", it).apply() },
                onModelChange = { model = it },
                onKeyChange = { keyInput = it },
                onVoicePresetChange = { voicePreset = it },
                onVoiceNameChange = { voiceName = it },
                onWebSearchChange = { webSearchDefault = it },
                onPreview = { onSpeak("Merhaba. Ben FURKAN AI. Sistemlerin hazır.", voicePreset, voiceName) },
                onBack = { screen = "home" },
                onSave = {
                    try { saveSettings() } catch (error: Exception) {
                        testStatus = error.message ?: "Ayarlar kaydedilemedi."
                    }
                },
                onDelete = {
                    ApiKeyVault.delete(context, provider)
                    keyInput = ""
                    keySaved = false
                    testStatus = "Bu sağlayıcının API anahtarı silindi."
                },
                onTest = {
                    if (keyInput.isBlank()) testStatus = "Önce API anahtarını gir."
                    else {
                        testing = true
                        testStatus = "Bağlantı kontrol ediliyor; bu istek sağlayıcı tarafından ücretlendirilebilir."
                        scope.launch {
                            try {
                                val answer = AiApiClient.reply(
                                    provider, model, keyInput,
                                    listOf(ChatMessage("user", "Only say: connection successful."))
                                )
                                testStatus = "Bağlantı başarılı: " + answer.take(100)
                            } catch (error: Exception) {
                                testStatus = error.message ?: "Bağlantı başarısız."
                            } finally { testing = false }
                        }
                    }
                }
            )
            "tools" -> ToolsScreen(
                profileName = profileName,
                profilePrompt = profilePrompt,
                roleStatus = roleStatus,
                onBack = { screen = "home" },
                onRequestRole = ::requestAssistantRole,
                onProfileSave = { name, prompt ->
                    LocalData.saveProfile(context, name, prompt)
                    profileName = name.trim().ifBlank { "Jarvis" }
                    profilePrompt = prompt.trim()
                    roleStatus = "Asistan profili kaydedildi."
                }
            )
            else -> HomeScreen(
                provider, keySaved, autoMode, assistLaunch,
                onChat = { screen = "chat" },
                onSettings = { screen = "settings" },
                onTools = { screen = "tools" }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    provider: AiProvider,
    keySaved: Boolean,
    autoMode: Boolean,
    assistLaunch: Boolean,
    onChat: () -> Unit,
    onSettings: () -> Unit,
    onTools: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "assistant-pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.94f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF101D31), Background, Color(0xFF05070D))))
            .padding(22.dp)
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(26.dp))
            Text("FURKAN AI", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
            Text("PERSONAL AI ASSISTANT", color = Cyan.copy(alpha = 0.75f), fontSize = 10.sp, letterSpacing = 3.sp)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(210.dp).scale(pulse).background(
                    Brush.radialGradient(listOf(Cyan.copy(alpha = 0.22f), Color(0xFF183B62), Color.Transparent)),
                    CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.size(138.dp).background(
                        Brush.linearGradient(listOf(Color(0xFF1A5475), Color(0xFF101B35))), CircleShape
                    ),
                    contentAlignment = Alignment.Center
                ) { Text("AI", color = Cyan, fontSize = 42.sp, fontWeight = FontWeight.Light) }
            }
            Spacer(Modifier.height(28.dp))
            Text(if (assistLaunch) "Dinliyorum." else "Merhaba, ben FURKAN AI.", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("Jarvis • Hacker • Çoklu AI • Telefon asistanı", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(18.dp))
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.padding(14.dp)) {
                    Text(if (autoMode) "Otomatik AI seçimi açık" else provider.title, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(if (keySaved) "Kişisel API anahtarı kayıtlı" else "API anahtarı eklemen gerekiyor", color = if (keySaved) Cyan else Color(0xFFFFC16E), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(onClick = onChat, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) {
                Text("Sohbete başla", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onTools, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text("Jarvis özellikleri ve telefon araçları", color = Color.White)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text("API ve ses ayarları", color = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Text("Hafıza • Web araştırması • Ses profilleri • Hatırlatıcılar", color = Color(0xFF75849B), fontSize = 10.sp)
            Text("FURKAN AI • v0.3.0", color = Color(0xFF536176), fontSize = 10.sp)
        }
    }
}

@Composable
private fun SettingsScreen(
    provider: AiProvider,
    autoMode: Boolean,
    model: String,
    keyInput: String,
    keySaved: Boolean,
    voicePreset: String,
    voiceName: String,
    voices: List<TtsVoiceOption>,
    webSearch: Boolean,
    testStatus: String,
    testing: Boolean,
    onProviderChange: (AiProvider) -> Unit,
    onAutoChange: (Boolean) -> Unit,
    onModelChange: (String) -> Unit,
    onKeyChange: (String) -> Unit,
    onVoicePresetChange: (String) -> Unit,
    onVoiceNameChange: (String) -> Unit,
    onWebSearchChange: (Boolean) -> Unit,
    onPreview: () -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit
) {
    var voiceMenuOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Background).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Geri", color = Cyan) }
            Text("API VE SES AYARLARI", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("AI sağlayıcısı", color = Color.White, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = !autoMode, onClick = { onAutoChange(false) }, label = { Text("Manuel") })
                FilterChip(selected = autoMode, onClick = { onAutoChange(true) }, label = { Text("Otomatik") })
            }
            if (!autoMode) {
                listOf(AiProvider.GEMINI, AiProvider.OPENAI, AiProvider.GROK, AiProvider.OPENROUTER).chunked(2).forEach { group ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        group.forEach { item ->
                            FilterChip(selected = provider == item, onClick = { onProviderChange(item) }, label = { Text(item.title) })
                        }
                    }
                }
            } else {
                Text("Göreve göre anahtarı kayıtlı sağlayıcıyı seçer. Otomatik modun kullanacağı sağlayıcıların anahtarlarını ayrı ayrı kaydet.", color = Muted, fontSize = 12.sp)
            }
            OutlinedTextField(value = model, onValueChange = onModelChange, modifier = Modifier.fillMaxWidth(), label = { Text("Model kimliği") }, singleLine = true)
            OutlinedTextField(
                value = keyInput, onValueChange = onKeyChange, modifier = Modifier.fillMaxWidth(),
                label = { Text(provider.title + " API anahtarı") },
                placeholder = { Text("Kendi anahtarını yapıştır") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true
            )
            Text(if (keySaved) "Bu sağlayıcının anahtarı kayıtlı." else "Bu sağlayıcı için anahtar henüz kaydedilmedi.", color = if (keySaved) Cyan else Color(0xFFFFC16E), fontSize = 12.sp)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Web araması", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Güncel bilgi ararken sağlayıcının web aracını kullanır; ek ücret olabilir.", color = Muted, fontSize = 11.sp)
                }
                Switch(checked = webSearch, onCheckedChange = onWebSearchChange)
            }

            Text("Ses profili", color = Color.White, fontWeight = FontWeight.Bold)
            VoicePreset.values().toList().chunked(3).forEach { group ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    group.forEach { preset ->
                        FilterChip(selected = voicePreset == preset.name, onClick = { onVoicePresetChange(preset.name) }, label = { Text(preset.title) })
                    }
                }
            }
            Box {
                OutlinedButton(onClick = { voiceMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(voices.firstOrNull { it.name == voiceName }?.label ?: "Sistem varsayılan sesi", color = Color.White)
                }
                DropdownMenu(expanded = voiceMenuOpen, onDismissRequest = { voiceMenuOpen = false }) {
                    DropdownMenuItem(text = { Text("Sistem varsayılan sesi") }, onClick = { onVoiceNameChange(""); voiceMenuOpen = false })
                    voices.take(40).forEach { voice ->
                        DropdownMenuItem(text = { Text(voice.label) }, onClick = { onVoiceNameChange(voice.name); voiceMenuOpen = false })
                    }
                }
            }
            Text("Ses seçenekleri cihazındaki kurulu Android TTS motoruna göre değişir. Hacker / Derin profili seçili sesi daha pes ve yavaş yapar; gerçek bir ses paketi değildir.", color = Muted, fontSize = 11.sp)
            OutlinedButton(onClick = onPreview, modifier = Modifier.fillMaxWidth()) { Text("Seçili sesi dene") }

            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Text(
                    "API anahtarı Android Keystore ile şifrelenir ve doğrudan seçtiğin sağlayıcıya gönderilir. FURKAN AI sunucusuna gitmez. Root edilmiş veya ele geçirilmiş cihazlarda yerel anahtar yine risk altında olabilir.",
                    Modifier.padding(14.dp), color = Muted, fontSize = 12.sp
                )
            }
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), enabled = keyInput.isNotBlank()) { Text("Anahtarı ve ayarları kaydet") }
            OutlinedButton(onClick = onTest, modifier = Modifier.fillMaxWidth(), enabled = !testing && keyInput.isNotBlank()) {
                Text(if (testing) "Kontrol ediliyor..." else "API bağlantısını test et")
            }
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Kayıtlı API anahtarını sil", color = Color(0xFFFF8D8D)) }
            if (testStatus.isNotBlank()) Text(testStatus, color = Cyan, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ToolsScreen(
    profileName: String,
    profilePrompt: String,
    roleStatus: String,
    onBack: () -> Unit,
    onRequestRole: () -> Unit,
    onProfileSave: (String, String) -> Unit
) {
    val context = LocalContext.current
    var chosenProfile by remember(profileName) { mutableStateOf(profileName) }
    var customName by remember(profileName) { mutableStateOf(profileName) }
    var customPrompt by remember(profilePrompt) { mutableStateOf(profilePrompt) }
    var memoryInput by remember { mutableStateOf("") }
    var memories by remember { mutableStateOf(LocalData.memories(context)) }
    var reminderTitle by remember { mutableStateOf("") }
    var reminderDelay by remember { mutableStateOf("10") }
    var reminders by remember { mutableStateOf(LocalData.reminders(context)) }
    var toolStatus by remember { mutableStateOf("") }
    val apps = remember { DeviceTools.launchableApps(context).take(50) }
    val profiles = listOf(
        "Jarvis" to "Calm, polished, respectful and proactive personal assistant.",
        "Hacker" to "Cybersecurity-minded terminal operator. Keep a cool technical tone. Focus only on authorized security, defensive testing and privacy.",
        "Öğretmen" to "Patient multilingual teacher. Explain step by step with examples and short practice.",
        "Kodlama" to "Senior software engineer. Review code carefully, explain root causes and provide testable fixes."
    )
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(Modifier.fillMaxSize().background(Background).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Geri", color = Cyan) }
            Text("JARVIS MERKEZİ", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Varsayılan telefon asistanı", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Android'in sistem penceresinden FURKAN AI'yi seç. Kullanılabilir kısayollar telefon üreticisine göre değişebilir.", color = Muted, fontSize = 12.sp)
                    Button(onClick = onRequestRole, modifier = Modifier.fillMaxWidth()) { Text("Varsayılan asistan yap") }
                    if (roleStatus.isNotBlank()) Text(roleStatus, color = Cyan, fontSize = 12.sp)
                }
            }

            Text("Asistan kişiliği", color = Color.White, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                profiles.forEach { (name, prompt) ->
                    FilterChip(
                        selected = chosenProfile == name,
                        onClick = { chosenProfile = name; customName = name; customPrompt = prompt },
                        label = { Text(name) }
                    )
                }
            }
            OutlinedTextField(value = customName, onValueChange = { customName = it; chosenProfile = "Özel" }, modifier = Modifier.fillMaxWidth(), label = { Text("Asistan adı") }, singleLine = true)
            OutlinedTextField(value = customPrompt, onValueChange = { customPrompt = it; chosenProfile = "Özel" }, modifier = Modifier.fillMaxWidth(), label = { Text("Özel talimatlar") }, minLines = 3)
            Button(onClick = { onProfileSave(customName, customPrompt) }, modifier = Modifier.fillMaxWidth()) { Text("Profili kaydet") }

            Text("Kalıcı hafıza", color = Color.White, fontWeight = FontWeight.Bold)
            Text("Eklediğin bilgiler yalnızca bu telefonda saklanır ve sohbet bağlamına eklenir.", color = Muted, fontSize = 12.sp)
            OutlinedTextField(value = memoryInput, onValueChange = { memoryInput = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Hatırlanacak bilgi") })
            Button(
                onClick = { LocalData.addMemory(context, memoryInput); memories = LocalData.memories(context); memoryInput = "" },
                modifier = Modifier.fillMaxWidth(), enabled = memoryInput.isNotBlank()
            ) { Text("Hafızaya ekle") }
            memories.forEach { memory ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(memory, color = Muted, modifier = Modifier.weight(1f), fontSize = 12.sp)
                    TextButton(onClick = { LocalData.deleteMemory(context, memory); memories = LocalData.memories(context) }) { Text("Sil", color = Color(0xFFFF9999)) }
                }
            }

            Text("Hatırlatıcı oluştur", color = Color.White, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = reminderTitle, onValueChange = { reminderTitle = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Ne hatırlatayım?") }, singleLine = true)
            OutlinedTextField(
                value = reminderDelay, onValueChange = { reminderDelay = it.filter(Char::isDigit).take(5) },
                modifier = Modifier.fillMaxWidth(), label = { Text("Kaç dakika sonra? 1–10080") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true
            )
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= 33 &&
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    try {
                        ReminderScheduler.schedule(context, reminderTitle, reminderDelay.toInt())
                        reminders = LocalData.reminders(context)
                        toolStatus = "Hatırlatıcı kuruldu. Android pil ayarları bildirimi geciktirebilir."
                        reminderTitle = ""
                    } catch (error: Exception) {
                        toolStatus = error.message ?: "Hatırlatıcı oluşturulamadı."
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = reminderTitle.isNotBlank() && (reminderDelay.toIntOrNull() ?: 0) in 1..10080
            ) { Text("Hatırlatıcıyı kur") }
            reminders.forEach { reminder ->
                val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(reminder.atMillis))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(reminder.title, color = Color.White, fontSize = 13.sp)
                        Text(date, color = Muted, fontSize = 11.sp)
                    }
                    TextButton(onClick = { LocalData.deleteReminder(context, reminder.id); reminders = LocalData.reminders(context) }) {
                        Text("İptal", color = Color(0xFFFF9999))
                    }
                }
            }
            if (toolStatus.isNotBlank()) Text(toolStatus, color = Cyan, fontSize = 12.sp)

            Text("Cihaz durumu", color = Color.White, fontWeight = FontWeight.Bold)
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Text(DeviceTools.deviceSummary(context), Modifier.padding(14.dp), color = Muted, fontSize = 13.sp)
            }

            Text("Kurulu uygulamalar", color = Color.White, fontWeight = FontWeight.Bold)
            Text("Bir uygulamaya basarak aç. Sohbette 'Chrome'u aç' veya 'YouTube'u aç' gibi çevrimdışı komutları da deneyebilirsin.", color = Muted, fontSize = 12.sp)
            apps.forEach { app ->
                TextButton(onClick = {
                    val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                    if (intent != null) context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(app.label, color = Color.White)
                }
            }
            Text("Çevrimdışı komutlar", color = Color.White, fontWeight = FontWeight.Bold)
            Text("Pil, RAM ve depolama durumunu sorabilir; Wi-Fi ayarlarını açabilir ve bazı uygulamaları başlatabilirsin. Güvenli sistem ayarları için Android onayı gerekir.", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ChatScreen(
    provider: AiProvider,
    model: String,
    autoMode: Boolean,
    voicePreset: String,
    voiceName: String,
    webSearchDefault: Boolean,
    profileName: String,
    profilePrompt: String,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onSpeak: (String, String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var messages by remember { mutableStateOf(LocalData.loadMessages(context)) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var webSearch by remember { mutableStateOf(webSearchDefault) }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) input = if (input.isBlank()) spoken else input.trimEnd() + " " + spoken
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startSpeechRecognition(speechLauncher)
        else notice = "Sesle yazmak için mikrofon izni gerekiyor."
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun sendMessage() {
        val text = input.trim()
        if (text.isBlank() || sending) return
        var history = messages + ChatMessage("user", text)
        messages = history
        LocalData.saveMessages(context, history)
        input = ""
        notice = ""

        val localResponse = DeviceTools.handleOfflineCommand(context, text)
        if (localResponse != null) {
            messages = history + ChatMessage("assistant", localResponse)
            LocalData.saveMessages(context, messages)
            return
        }

        val actual = resolveProvider(context, provider, autoMode, text)
        if (actual == null) {
            notice = "API anahtarı bulunamadı. Ayarlardan en az bir sağlayıcının anahtarını kaydet."
            onSettings()
            return
        }
        val actualModel = if (autoMode) actual.defaultModel else model
        val key = ApiKeyVault.read(context, actual)
        if (key.isNullOrBlank()) {
            notice = actual.title + " anahtarı kayıtlı değil."
            onSettings()
            return
        }
        val memory = LocalData.memories(context)
        val prompt = makeSystemPrompt(profileName, profilePrompt, memory)
        val shouldSearch = webSearch || isResearchPrompt(text)
        if (shouldSearch) history = history + ChatMessage("user", "Yanıt verirken güncel web araması yap ve kaynakları belirt: " + text)
        sending = true
        scope.launch {
            try {
                val answer = AiApiClient.reply(actual, actualModel, key, history, prompt, shouldSearch)
                messages = messages + ChatMessage("assistant", answer)
                LocalData.saveMessages(context, messages)
                if (autoMode) notice = "Otomatik seçim: " + actual.title
            } catch (error: Exception) {
                messages = messages + ChatMessage("assistant", "İstek başarısız: " + (error.message ?: "Bilinmeyen hata"))
                LocalData.saveMessages(context, messages)
            } finally {
                sending = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Background).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("← Geri", color = Cyan) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(profileName.uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                Text(if (autoMode) "OTOMATİK AI" else provider.title, color = Muted, fontSize = 10.sp)
            }
            TextButton(onClick = onSettings) { Text("Ayarlar", color = Cyan) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Web araştırması", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Switch(checked = webSearch, onCheckedChange = { webSearch = it })
            TextButton(onClick = {
                messages = emptyList()
                LocalData.clearMessages(context)
                notice = "Sohbet geçmişi silindi."
            }) { Text("Temizle", color = Color(0xFFFF9999), fontSize = 11.sp) }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(9.dp)) {
            if (messages.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                        Column(Modifier.padding(18.dp)) {
                            Text("Merhaba! Ben $profileName.", color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text("Yazabilir veya mikrofonla konuşabilirsin. Sohbet geçmişi ve seçtiğin hafıza bu telefonda saklanır.", color = Muted)
                        }
                    }
                }
            }
            items(messages) { message -> ChatBubble(message, voicePreset, voiceName, onSpeak) }
            if (sending) item { Text("FURKAN AI düşünüyor...", color = Cyan, fontSize = 13.sp) }
        }
        if (notice.isNotBlank()) Text(notice, color = Color(0xFFFFC16E), fontSize = 11.sp, modifier = Modifier.padding(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), placeholder = { Text("Komutunu yaz...") }, maxLines = 4)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton(onClick = {
                    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        startSpeechRecognition(speechLauncher)
                    } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text("🎙", color = Cyan, fontSize = 20.sp) }
                Button(
                    onClick = { sendMessage() }, enabled = !sending && input.isNotBlank(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) { Text("Gönder") }
            }
        }
    }
}

private fun startSpeechRecognition(launcher: androidx.activity.result.ActivityResultLauncher<Intent>) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PROMPT, "FURKAN AI'ye konuş")
    }
    try { launcher.launch(intent) } catch (_: Exception) { }
}

private fun resolveProvider(
    context: android.content.Context,
    preferred: AiProvider,
    auto: Boolean,
    text: String
): AiProvider? {
    if (!auto) return preferred.takeIf { ApiKeyVault.read(context, it) != null }
    val query = text.lowercase(Locale.getDefault())
    val order = when {
        listOf("araştır", "güncel", "haber", "internette", "kaynak", "bugün").any(query::contains) ->
            listOf(AiProvider.GROK, AiProvider.GEMINI, AiProvider.OPENAI, AiProvider.OPENROUTER)
        listOf("kod", "program", "github", "script", "hata").any(query::contains) ->
            listOf(AiProvider.OPENAI, AiProvider.GROK, AiProvider.GEMINI, AiProvider.OPENROUTER)
        else -> listOf(preferred, AiProvider.GEMINI, AiProvider.OPENAI, AiProvider.GROK, AiProvider.OPENROUTER)
    }
    return order.distinct().firstOrNull { ApiKeyVault.read(context, it) != null }
}

private fun isResearchPrompt(text: String): Boolean {
    val query = text.lowercase(Locale.getDefault())
    return listOf("araştır", "güncel", "son haber", "bugün", "internette ara", "kaynak bul").any(query::contains)
}

private fun makeSystemPrompt(name: String, custom: String, memories: List<String>): String {
    val persona = when (name.lowercase(Locale.getDefault())) {
        "jarvis" -> "Calm, polished, respectful and proactive personal assistant."
        "hacker" -> "Cybersecurity-minded terminal operator. Use a cool technical tone. Focus on authorized security, defensive testing and privacy."
        "öğretmen" -> "Patient multilingual teacher. Use step-by-step explanations, examples and short practice."
        "kodlama" -> "Senior software engineer. Analyze code, explain root causes and provide testable fixes."
        else -> "Personal AI assistant named $name."
    }
    val memory = if (memories.isEmpty()) "" else "\nUser-approved memory (context only):\n" + memories.joinToString("\n") { "- $it" }
    return "You are FURKAN AI, assistant persona $name. $persona Reply in the user's language. " +
        "Be truthful, practical and careful. Never claim an action has succeeded unless confirmed. " +
        "Ask before sensitive or irreversible actions. Custom instructions: $custom $memory"
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    voicePreset: String,
    voiceName: String,
    onSpeak: (String, String, String) -> Unit
) {
    val user = message.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(
            Modifier.widthIn(max = 320.dp),
            color = if (user) Color(0xFF16455E) else Panel,
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(if (user) "SEN" else "FURKAN AI", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(message.text, color = Color.White, fontSize = 14.sp)
                if (!user) TextButton(onClick = { onSpeak(message.text, voicePreset, voiceName) }) {
                    Text("▶ Sesli oku", color = Cyan, fontSize = 11.sp)
                }
            }
        }
    }
}
