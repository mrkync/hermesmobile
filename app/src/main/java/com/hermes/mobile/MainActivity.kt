package com.hermes.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hermes.mobile.data.HermesGateway
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            HermesApp()
        }
    }
}

data class ChatMessage(
    val role: String,
    val text: String
)

@Composable
fun HermesApp() {

    MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme()
    ) {

        var connected by remember {
            mutableStateOf(false)
        }

        var baseUrl by remember {
            mutableStateOf("http://100.82.236.41:9119")
        }

        var user by remember {
            mutableStateOf("")
        }

        var pass by remember {
            mutableStateOf("")
        }

        var input by remember {
            mutableStateOf("")
        }

        var status by remember {
            mutableStateOf("Bağlantı bekleniyor")
        }

        val messages = remember {
            mutableStateListOf<ChatMessage>()
        }

        val scope = rememberCoroutineScope()

        val gateway = remember {
            HermesGateway()
        }

        DisposableEffect(Unit) {

            gateway.onConnection = { ok, error ->

                connected = ok

                status = if (ok) {
                    "Bağlı"
                } else {
                    error ?: "Bağlantı kapandı"
                }
            }

            gateway.onEvent = { obj ->

                val params = obj.optJSONObject("params")

                if (params != null) {

                    when (params.optString("type")) {

                        "message.delta" -> {

                            val text =
                                params.optString("text")

                            if (text.isNotEmpty()) {

                                val last =
                                    messages.lastOrNull()

                                if (
                                    last != null &&
                                    last.role == "assistant"
                                ) {

                                    messages[
                                        messages.lastIndex
                                    ] = last.copy(
                                        text =
                                            last.text + text
                                    )

                                } else {

                                    messages.add(
                                        ChatMessage(
                                            "assistant",
                                            text
                                        )
                                    )
                                }
                            }
                        }

                        "gateway.ready" -> {

                            status = "Gateway hazır"
                        }

                        "error" -> {

                            status =
                                params.optString(
                                    "message",
                                    "Hermes hatası"
                                )
                        }
                    }
                }
            }

            onDispose {

                gateway.close()
            }
        }

        if (!connected) {

            LoginScreen(
                url = baseUrl,
                setUrl = {
                    baseUrl = it
                },
                user = user,
                setUser = {
                    user = it
                },
                pass = pass,
                setPass = {
                    pass = it
                },
                status = status,
                login = {

                    scope.launch {

                        try {

                            status =
                                "Giriş yapılıyor…"

                            gateway.login(
                                baseUrl,
                                user,
                                pass
                            )

                            gateway.connect()

                        } catch (e: Exception) {

                            status =
                                e.message
                                    ?: "Bağlantı hatası"
                        }
                    }
                }
            )

        } else {

            ChatScreen(
                messages = messages,
                input = input,
                setInput = {
                    input = it
                },
                status = status,
                onSend = {

                    val text =
                        input.trim()

                    if (text.isNotEmpty()) {

                        messages.add(
                            ChatMessage(
                                "user",
                                text
                            )
                        )

                        input = ""

                        gateway.send(text)
                    }
                },
                onStop = {

                    gateway.stop()
                }
            )
        }
    }
}

@Composable
fun LoginScreen(
    url: String,
    setUrl: (String) -> Unit,
    user: String,
    setUser: (String) -> Unit,
    pass: String,
    setPass: (String) -> Unit,
    status: String,
    login: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement =
            Arrangement.Center
    ) {

        Text(
            text = "Hermes",
            style =
                MaterialTheme.typography.displaySmall,
            fontWeight =
                FontWeight.Bold
        )

        Text(
            text = "Mobile",
            style =
                MaterialTheme.typography.titleLarge
        )

        Spacer(
            modifier =
                Modifier.height(32.dp)
        )

        OutlinedTextField(
            value = url,
            onValueChange = setUrl,
            modifier =
                Modifier.fillMaxWidth(),
            label = {
                Text("Gateway URL")
            },
            singleLine = true
        )

        Spacer(
            modifier =
                Modifier.height(12.dp)
        )

        OutlinedTextField(
            value = user,
            onValueChange = setUser,
            modifier =
                Modifier.fillMaxWidth(),
            label = {
                Text("Kullanıcı adı")
            },
            singleLine = true
        )

        Spacer(
            modifier =
                Modifier.height(12.dp)
        )

        OutlinedTextField(
            value = pass,
            onValueChange = setPass,
            modifier =
                Modifier.fillMaxWidth(),
            label = {
                Text("Şifre")
            },
            singleLine = true
        )

        Spacer(
            modifier =
                Modifier.height(20.dp)
        )

        Button(
            onClick = login,
            modifier =
                Modifier.fillMaxWidth()
        ) {

            Text(
                "Hermes'e bağlan"
            )
        }

        Spacer(
            modifier =
                Modifier.height(12.dp)
        )

        Text(
            text = status,
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    input: String,
    setInput: (String) -> Unit,
    status: String,
    onSend: () -> Unit,
    onStop: () -> Unit
) {

    val list =
        rememberLazyListState()

    LaunchedEffect(messages.size) {

        if (messages.isNotEmpty()) {

            list.animateScrollToItem(
                messages.lastIndex
            )
        }
    }

    Scaffold(
        topBar = {

            TopAppBar(
                title = {
                    Text("Hermes")
                },

                actions = {

                    Text(
                        text = status,
                        modifier =
                            Modifier.padding(
                                end = 12.dp
                            ),
                        style =
                            MaterialTheme
                                .typography
                                .labelSmall
                    )
                }
            )
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(
                        horizontal = 12.dp
                    ),
                state = list,
                verticalArrangement =
                    Arrangement.spacedBy(
                        10.dp
                    ),
                contentPadding =
                    PaddingValues(
                        vertical = 16.dp
                    )
            ) {

                items(messages) {
                    message ->

                    MessageBubble(
                        message
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment =
                    Alignment.Bottom
            ) {

                OutlinedTextField(
                    value = input,
                    onValueChange =
                        setInput,
                    modifier =
                        Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "Hermes'e mesaj yaz…"
                        )
                    },
                    maxLines = 6,
                    shape =
                        RoundedCornerShape(
                            24.dp
                        )
                )

                Spacer(
                    modifier =
                        Modifier.width(8.dp)
                )

                FilledIconButton(
                    onClick = onSend
                ) {

                    Text("➤")
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: ChatMessage
) {

    val mine =
        message.role == "user"

    Row(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalArrangement =
            if (mine) {
                Arrangement.End
            } else {
                Arrangement.Start
            }
    ) {

        Surface(
            color =
                if (mine) {
                    MaterialTheme
                        .colorScheme
                        .primaryContainer
                } else {
                    MaterialTheme
                        .colorScheme
                        .surfaceVariant
                },
            shape =
                RoundedCornerShape(18.dp),
            modifier =
                Modifier.widthIn(
                    max = 340.dp
                )
        ) {

            Text(
                text =
                    message.text,
                modifier =
                    Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 11.dp
                    )
            )
        }
    }
}
