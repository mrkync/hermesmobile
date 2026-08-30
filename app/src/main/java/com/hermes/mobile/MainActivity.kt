package com.hermes.mobile
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hermes.mobile.data.HermesGateway
import kotlinx.coroutines.launch

data class ChatMessage(val role:String,val text:String)
class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{HermesApp()}}}
@Composable fun HermesApp(){MaterialTheme(colorScheme=darkColorScheme()){var connected by remember{mutableStateOf(false)};var url by remember{mutableStateOf("http://100.82.236.41:9119")};var user by remember{mutableStateOf("")};var pass by remember{mutableStateOf("")};var input by remember{mutableStateOf("")};var status by remember{mutableStateOf("Bağlantı bekleniyor")};val messages=remember{mutableStateListOf<ChatMessage>()};val scope=rememberCoroutineScope();val gateway=remember{HermesGateway()};DisposableEffect(Unit){gateway.onConnection={ok,e->connected=ok;status=if(ok)"Bağlı" else e?:"Bağlantı kapandı"};gateway.onEvent={o->val p=o.optJSONObject("params")?:return@onEvent;when(p.optString("type")){"message.delta"->{val t=p.optString("text");if(t.isNotEmpty()){val l=messages.lastOrNull();if(l?.role=="assistant")messages[messages.lastIndex]=l.copy(text=l.text+t) else messages.add(ChatMessage("assistant",t))}};"gateway.ready"->status="Gateway hazır";"error"->status=p.optString("message","Hermes hatası")}};onDispose{gateway.close()}}
if(!connected) Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center){Text("Hermes",style=MaterialTheme.typography.displaySmall,fontWeight=FontWeight.Bold);Text("Mobile");Spacer(Modifier.height(32.dp));OutlinedTextField(url,{url=it},Modifier.fillMaxWidth(),label={Text("Gateway URL")});Spacer(Modifier.height(8.dp));OutlinedTextField(user,{user=it},Modifier.fillMaxWidth(),label={Text("Kullanıcı adı")});Spacer(Modifier.height(8.dp));OutlinedTextField(pass,{pass=it},Modifier.fillMaxWidth(),label={Text("Şifre")});Spacer(Modifier.height(16.dp));Button({scope.launch{try{status="Giriş yapılıyor…";gateway.login(url,user,pass);gateway.connect()}catch(e:Exception){status=e.message?:"Bağlantı hatası"}}},Modifier.fillMaxWidth()){Text("Hermes'e bağlan")};Text(status)} else Scaffold(topBar={TopAppBar(title={Text("Hermes")})}){pad->Column(Modifier.fillMaxSize().padding(pad)){LazyColumn(Modifier.weight(1f).padding(12.dp)){items(messages){m->Text((if(m.role=="user")"Sen: " else "Hermes: ")+m.text,Modifier.padding(vertical=8.dp))}};Row(Modifier.padding(10.dp),verticalAlignment=Alignment.Bottom){OutlinedTextField(input,{input=it},Modifier.weight(1f),placeholder={Text("Hermes'e mesaj yaz…")});Button({val t=input.trim();if(t.isNotEmpty()){messages.add(ChatMessage("user",t));input="";gateway.send(t)}},Modifier.padding(start=8.dp)){Text("Gönder")}}}}}}
