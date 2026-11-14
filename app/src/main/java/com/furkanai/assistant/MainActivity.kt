package com.furkanai.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale

private val Cyan = Color(0xFF48DDF5)
private val Background = Color(0xFF070B14)
private val Panel = Color(0xFF111C2D)
private val Muted = Color(0xFF9EACC1)

class MainActivity : ComponentActivity() {
    private var speechOutput: TextToSpeech? = null
    private var speechReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        speechOutput = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                speechReady = true
                speechOutput?.language = Locale.getDefault()
            }
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Cyan,
                    background = Background,
                    surface = Panel
                )
            ) {
                AssistantApp(
                    onSpeak = { text, robotic ->
                        if (speechReady) {
                            speechOutput?.setPitch(if (robotic) 0.78f else 1.0f)
                            speechOutput?.setSpeechRate(if (robotic) 0.92f else 1.0f)
                            speechOutput?.speak(
                                text,
                                TextToSpeech.QUEUE_FLUSH,
                                null,
                                "furkan-ai-reply"
                            )
                        }
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        speechOutput?.stop()
        speechOutput?.shutdown()
        speechOutput = null
        super.onDestroy()
    }
}

@Composable
private fun AssistantApp(onSpeak: (String, Boolean) -> Unit) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("furkan_ai_settings", android.content.Context.MODE_PRIVATE)
    }
    val scope = rememberCoroutineScope()

    var currentScreen by remember { mutableStateOf("home") }
    var provider by remember { mutableStateOf(AiProvider.GEMINI) }
    var model by remember { mutableStateOf(AiProvider.GEMINI.defaultModel) }
    var keyInput by remember { mutableStateOf("") }
    var keySaved by remember { mutableStateOf(false) }
    var voiceStyle by remember { mutableStateOf("natural") }
    var testStatus by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        provider = AiProvider.fromId(prefs.getString("provider", AiProvider.GEMINI.id))
        model = prefs.getString("model", provider.defaultModel) ?: provider.defaultModel
        voiceStyle = prefs.getString("voice_style", "natural") ?: "natural"
        keyInput = ApiKeyVault.read(context, provider).orEmpty()
        keySaved = keyInput.isNotBlank()
    }

    fun changeProvider(next: AiProvider) {
        provider = next
        model = next.defaultModel
        keyInput = ApiKeyVault.read(context, next).orEmpty()
        keySaved = keyInput.isNotBlank()
        testStatus = ""
        prefs.edit().putString("provider", next.id).putString("model", next.defaultModel).apply()
    }

    fun saveSettings() {
        ApiKeyVault.save(context, provider, keyInput)
        prefs.edit()
            .putString("provider", provider.id)
            .putString("model", model.trim())
            .putString("voice_style", voiceStyle)
            .apply()
        keyInput = ApiKeyVault.read(context, provider).orEmpty()
        keySaved = true
        testStatus = "API anahtarı bu telefonda şifreli olarak kaydedildi."
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Background) {
        when (currentScreen) {
            "chat" -> ChatScreen(
                provider = provider,
                model = model,
                voiceStyle = voiceStyle,
                onBack = { currentScreen = "home" },
                onSettings = { currentScreen = "settings" },
                onSpeak = onSpeak
            )
            "settings" -> SettingsScreen(
                provider = provider,
                model = model,
                keyInput = keyInput,
                keySaved = keySaved,
                voiceStyle = voiceStyle,
                testStatus = testStatus,
                testing = testing,
                onProviderChange = ::changeProvider,
                onModelChange = { model = it },
                onKeyChange = { keyInput = it },
                onVoiceChange = { voiceStyle = it },
                onBack = { currentScreen = "home" },
                onSave = {
                    try {
                        saveSettings()
                    } catch (error: Exception) {
                        testStatus = error.message ?: "Anahtar kaydedilemedi."
                    }
                },
                onDelete = {
                    ApiKeyVault.delete(context, provider)
                    keyInput = ""
                    keySaved = false
                    testStatus = "Bu sağlayıcının kayıtlı anahtarı silindi."
                },
                onTest = {
                    if (keyInput.isBlank()) {
                        testStatus = "Önce API anahtarını gir ve kaydet."
                    } else {
                        testing = true
                        testStatus = "Bağlantı kontrol ediliyor. Bu test sağlayıcının API kullanımına ücret yazabilir."
                        scope.launch {
                            try {
                                val answer = AiApiClient.reply(
                                    provider,
                                    model,
                                    keyInput,
                                    listOf(ChatMessage("user", "Sadece 'Bağlantı başarılı' yaz."))
                                )
                                testStatus = "Bağlantı başarılı: " + answer.take(100)
                            } catch (error: Exception) {
                                testStatus = error.message ?: "Bağlantı testi başarısız."
                            } finally {
                                testing = false
                            }
                        }
                    }
                }
            )
            else -> HomeScreen(
                provider = provider,
                keySaved = keySaved,
                onChat = { currentScreen = "chat" },
                onSettings = { currentScreen = "settings" }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    provider: AiProvider,
    keySaved: Boolean,
    onChat: () -> Unit,
    onSettings: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "assistant-pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF101D31), Background, Color(0xFF05070D))
                )
            )
            .padding(22.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))
            Text(
                "FURKAN AI",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
            Text(
                "YOUR PERSONAL AI ASSISTANT",
                color = Cyan.copy(alpha = 0.75f),
                fontSize = 10.sp,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(218.dp)
                    .scale(pulse)
                    .background(
                        Brush.radialGradient(
                            listOf(Cyan.copy(alpha = 0.22f), Color(0xFF183B62), Color.Transparent)
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(142.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF1A5475), Color(0xFF101B35))
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("AI", color = Cyan, fontSize = 42.sp, fontWeight = FontWeight.Light)
                }
            }

            Spacer(Modifier.height(28.dp))
            Text(
                "Merhaba, ben FURKAN AI.",
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text("Konuşmaya hazır mısın?", color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("AI sağlayıcısı", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(provider.title, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        if (keySaved) "Kişisel API anahtarı kayıtlı" else "API anahtarı henüz eklenmedi",
                        color = if (keySaved) Cyan else Color(0xFFFFC16E),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onChat,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Cyan,
                    contentColor = Color(0xFF06111B)
                )
            ) {
                Text("Sohbete başla", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onSettings,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("API ve ses ayarları", color = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Sesli sohbet • Çoklu AI • Kişisel anahtar",
                color = Color(0xFF75849B),
                fontSize = 11.sp
            )
            Spacer(Modifier.height(12.dp))
            Text("FURKAN AI  •  v0.2.0", color = Color(0xFF536176), fontSize = 10.sp)
        }
    }
}

@Composable
private fun SettingsScreen(
    provider: AiProvider,
    model: String,
    keyInput: String,
    keySaved: Boolean,
    voiceStyle: String,
    testStatus: String,
    testing: Boolean,
    onProviderChange: (AiProvider) -> Unit,
    onModelChange: (String) -> Unit,
    onKeyChange: (String) -> Unit,
    onVoiceChange: (String) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Geri", color = Cyan) }
            Text("API AYARLARI", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("AI sağlayıcını seç", color = Color.White, fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(AiProvider.GEMINI, AiProvider.OPENAI).forEach { item ->
                        FilterChip(
                            selected = provider == item,
                            onClick = { onProviderChange(item) },
                            label = { Text(item.title) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(AiProvider.GROK, AiProvider.OPENROUTER).forEach { item ->
                        FilterChip(
                            selected = provider == item,
                            onClick = { onProviderChange(item) },
                            label = { Text(item.title) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = model,
                onValueChange = onModelChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Model adı") },
                singleLine = true
            )

            OutlinedTextField(
                value = keyInput,
                onValueChange = onKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(provider.title + " API Key") },
                placeholder = { Text("Kendi API anahtarını yapıştır") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )

            Text(
                if (keySaved) "Bu sağlayıcı için cihazda kayıtlı bir anahtar var." else "Henüz kayıtlı bir anahtar yok.",
                color = if (keySaved) Cyan else Muted,
                fontSize = 12.sp
            )

            Text("Ses biçimi", color = Color.White, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = voiceStyle == "natural",
                    onClick = { onVoiceChange("natural") },
                    label = { Text("Doğal ses") }
                )
                FilterChip(
                    selected = voiceStyle == "robotic",
                    onClick = { onVoiceChange("robotic") },
                    label = { Text("Jarvis / Robotik") }
                )
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    "Anahtar AES-GCM ile Android Keystore kullanılarak bu telefonda şifrelenir. " +
                        "FURKAN AI sunucusuna gönderilmez; AI isteği doğrudan seçtiğin sağlayıcıya gider. " +
                        "API kullanımı sağlayıcının ücretlerine tabi olabilir.",
                    modifier = Modifier.padding(14.dp),
                    color = Muted,
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                enabled = keyInput.isNotBlank()
            ) {
                Text("Anahtarı şifreli kaydet")
            }
            OutlinedButton(
                onClick = onTest,
                modifier = Modifier.fillMaxWidth(),
                enabled = !testing && keyInput.isNotBlank()
            ) {
                Text(if (testing) "Kontrol ediliyor..." else "API bağlantısını test et")
            }
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Text("Kayıtlı anahtarı sil", color = Color(0xFFFF8D8D))
            }
            if (testStatus.isNotBlank()) {
                Text(testStatus, color = Cyan, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ChatScreen(
    provider: AiProvider,
    model: String,
    voiceStyle: String,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onSpeak: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var messages by remember { mutableStateOf(emptyList<ChatMessage>()) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                input = if (input.isBlank()) spoken else input.trimEnd() + " " + spoken
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "FURKAN AI'ye konuş")
            }
            try {
                speechLauncher.launch(intent)
            } catch (_: Exception) {
                notice = "Bu telefonda konuşma tanıma hizmeti bulunamadı."
            }
        } else {
            notice = "Sesle yazdırmak için mikrofon izni gerekiyor."
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size)
    }

    fun sendMessage() {
        val text = input.trim()
        if (text.isEmpty() || sending) return
        val key = ApiKeyVault.read(context, provider)
        if (key.isNullOrBlank()) {
            notice = "Önce API ayarlarından " + provider.title + " anahtarını kaydet."
            onSettings()
            return
        }

        val updated = messages + ChatMessage("user", text)
        messages = updated
        input = ""
        notice = ""
        sending = true

        scope.launch {
            try {
                val answer = AiApiClient.reply(provider, model, key, updated)
                messages = messages + ChatMessage("assistant", answer)
            } catch (error: Exception) {
                messages = messages + ChatMessage(
                    "assistant",
                    "Bir hata oluştu: " + (error.message ?: "Bilinmeyen bağlantı hatası")
                )
            } finally {
                sending = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) { Text("← Geri", color = Cyan) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("FURKAN AI", color = Color.White, fontWeight = FontWeight.Bold)
                Text(provider.title + " • " + model, color = Muted, fontSize = 10.sp)
            }
            TextButton(onClick = onSettings) { Text("Ayarlar", color = Cyan) }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                        Column(Modifier.padding(18.dp)) {
                            Text("Merhaba! Ben FURKAN AI.", color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Sorunu yazabilir veya mikrofonla söyleyebilirsin. Yanıtları sesli de dinleyebilirsin.",
                                color = Muted
                            )
                        }
                    }
                }
            }
            items(messages) { message ->
                ChatBubble(
                    message = message,
                    robotic = voiceStyle == "robotic",
                    onSpeak = onSpeak
                )
            }
            if (sending) {
                item {
                    Text("FURKAN AI düşünüyor...", color = Cyan, fontSize = 13.sp)
                }
            }
        }

        if (notice.isNotBlank()) {
            Text(notice, color = Color(0xFFFFC16E), fontSize = 12.sp, modifier = Modifier.padding(4.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("FURKAN AI'ye yaz...") },
                maxLines = 4
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton(
                    onClick = {
                        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                )
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "FURKAN AI'ye konuş")
                            }
                            try {
                                speechLauncher.launch(intent)
                            } catch (_: Exception) {
                                notice = "Bu telefonda konuşma tanıma hizmeti bulunamadı."
                            }
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                ) { Text("🎙", color = Cyan, fontSize = 20.sp) }
                Button(
                    onClick = { sendMessage() },
                    enabled = !sending && input.isNotBlank(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 8.dp
                    )
                ) {
                    Text("Gönder")
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    robotic: Boolean,
    onSpeak: (String, Boolean) -> Unit
) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 310.dp),
            color = if (isUser) Color(0xFF16455E) else Panel,
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    if (isUser) "SEN" else "FURKAN AI",
                    color = if (isUser) Cyan else Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(message.text, color = Color.White, fontSize = 14.sp)
                if (!isUser) {
                    TextButton(onClick = { onSpeak(message.text, robotic) }) {
                        Text("▶ Sesli oku", color = Cyan, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
