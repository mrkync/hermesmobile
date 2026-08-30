package com.hermes.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hermes.mobile.data.HermesGateway
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HermesApp() }
    }
}

data class ChatMessage(val role: String, val text: String)

@Composable
fun HermesApp() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        var connected by remember { mutableStateOf(false) }
        var baseUrl by remember { mutableStateOf("http://100.82.236.41:9119") }
        var user by remember { mutableStateOf("") }
        var pass by remember { mutableStateOf("") }
        var input by remember { mutableStateOf("") }
        var status by remember { mutableStateOf("Bağlantı bekleniyor") }
        val messages = remember { mutableStateListOf<ChatMessage>() }
        val scope = rememberCoroutineScope()
        val gateway = remember { HermesGateway() }

        DisposableEffect(Unit) {
            gateway.onConnection = { ok, err -> connected = ok; status = if (ok) "Bağlı" else (err ?: "Bağlantı kapandı") }
            gateway.onEvent = { obj ->
                val params = obj.optJSONObject("params") ?: return@onEvent
                when (params.optString("type")) {
                    "message.delta" -> {
                        val text = params.optString("text")
                        if (text.isNotEmpty()) {
                            val last = messages.lastOrNull()
                            if (last?.role == "assistant") messages[messages.lastIndex] = last.copy(text = last.text + text)
                            else messages.add(ChatMessage("assistant", text))
                        }
                    }
                    "message.complete" -> Unit
                    "gateway.ready" -> status = "Gateway hazır"
                    "error" -> status = params.optString("message", "Hermes hatası")
                }
            }
            onDispose { gateway.close() }
        }

        if (!connected) {
            LoginScreen(baseUrl, { baseUrl = it }, user, { user = it }, pass, { pass = it }, status) {
                scope.launch {
                    try {
                        status = "Giriş yapılıyor…"
                        gateway.login(baseUrl, user, pass)
                        gateway.connect()
                    } catch (e: Exception) { status = e.message ?: "Bağlantı hatası" }
                }
            }
        } else {
            ChatScreen(messages, input, { input = it }, status, onSend = {
                val text = input.trim(); if (text.isEmpty()) return@ChatScreen
                messages.add(ChatMessage("user", text)); input = ""; gateway.send(text)
            }, onStop = { gateway.stop() })
        }
    }
}

@Composable
fun LoginScreen(url: String, setUrl: (String) -> Unit, user: String, setUser: (String) -> Unit, pass: String, setPass: (String) -> Unit, status: String, login: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Hermes", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text("Mobile", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(url, setUrl, Modifier.fillMaxWidth(), label = { Text("Gateway URL") }, singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(user, setUser, Modifier.fillMaxWidth(), label = { Text("Kullanıcı adı") }, singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(pass, setPass, Modifier.fillMaxWidth(), label = { Text("Şifre") }, singleLine = true)
        Spacer(Modifier.height(20.dp))
        Button(login, Modifier.fillMaxWidth()) { Text("Hermes'e bağlan") }
        Spacer(Modifier.height(12.dp))
        Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ChatScreen(messages: List<ChatMessage>, input: String, setInput: (String) -> Unit, status: String, onSend: () -> Unit, onStop: () -> Unit) {
    val list = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.lastIndex) }
    Scaffold(topBar = { TopAppBar(title = { Text("Hermes") }, actions = { Text(status, Modifier.padding(end = 12.dp), style = MaterialTheme.typography.labelSmall) }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), state = list, verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
                items(messages) { m -> MessageBubble(m) }
            }
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(input, setInput, Modifier.weight(1f), placeholder = { Text("Hermes'e mesaj yaz…") }, maxLines = 6, shape = RoundedCornerShape(24.dp))
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = onSend) { Text("➤") }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val mine = message.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp), modifier = Modifier.widthIn(max = 340.dp)) {
            Text(message.text, Modifier.padding(horizontal = 16.dp, vertical = 11.dp))
        }
    }
}
