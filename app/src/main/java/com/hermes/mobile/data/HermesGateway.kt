package com.hermes.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class HermesGateway {
 private val cookies=mutableMapOf<String,List<Cookie>>()
 private val client=OkHttpClient.Builder().cookieJar(object:CookieJar{
  override fun saveFromResponse(url:HttpUrl,c:List<Cookie>){cookies[url.host]=c}
  override fun loadForRequest(url:HttpUrl)=cookies[url.host].orEmpty().filter{it.matches(url)}
 }).connectTimeout(15,TimeUnit.SECONDS).readTimeout(0,TimeUnit.MILLISECONDS).build()
 private var base=""; private var socket:WebSocket?=null; private var nextId=1; private var sessionId:String?=null
 var onEvent:((JSONObject)->Unit)?=null; var onConnection:((Boolean,String?)->Unit)?=null
 suspend fun login(url:String,user:String,pass:String)=withContext(Dispatchers.IO){
  base=url.trimEnd('/'); val form=FormBody.Builder().add("username",user).add("password",pass).add("next","/").build()
  client.newCall(Request.Builder().url("$base/auth/password-login").post(form).build()).execute().use{if(!it.isSuccessful && it.code !in 300..399) error("Login failed: HTTP ${it.code}")}
 }
 suspend fun connect()=withContext(Dispatchers.IO){
  val ticket=client.newCall(Request.Builder().url("$base/api/auth/ws-ticket").post(FormBody.Builder().build()).build()).execute().use{r->if(!r.isSuccessful) error("WS ticket failed: HTTP ${r.code}"); JSONObject(r.body?.string().orEmpty()).getString("ticket")}
  val wsBase=when{base.startsWith("https://")->base.replaceFirst("https://","wss://");base.startsWith("http://")->base.replaceFirst("http://","ws://");else->base}
  socket=client.newWebSocket(Request.Builder().url(wsBase+"/api/ws?ticket="+URLEncoder.encode(ticket,"UTF-8")).build(),object:WebSocketListener(){
   override fun onOpen(w:WebSocket,r:Response){onConnection?.invoke(true,null)}
   override fun onMessage(w:WebSocket,t:String){parse(t)}
   override fun onMessage(w:WebSocket,b:ByteString){parse(b.utf8())}
   override fun onFailure(w:WebSocket,t:Throwable,r:Response?){onConnection?.invoke(false,t.message)}
   override fun onClosed(w:WebSocket,c:Int,r:String){onConnection?.invoke(false,r)}
  })
 }
 fun send(text:String){val id=sessionId?:run{request("session.create",emptyMap());return};request("prompt.submit",mapOf("session_id" to id,"text" to text,"keep_transport" to true))}
 fun stop(){sessionId?.let{request("session.interrupt",mapOf("session_id" to it))}}
 fun close(){socket?.close(1000,"client closed");socket=null}
 private fun request(method:String,params:Map<String,Any?>){val o=JSONObject().put("jsonrpc","2.0").put("id",nextId++).put("method",method);val p=JSONObject();params.forEach{(k,v)->p.put(k,v)};o.put("params",p);socket?.send(o.toString()+"\n")}
 private fun parse(raw:String){raw.lineSequence().filter{it.isNotBlank()}.forEach{line->val o=runCatching{JSONObject(line)}.getOrNull()?:return@forEach;o.optJSONObject("result")?.let{if(it.has("session_id"))sessionId=it.optString("session_id").takeIf{s->s.isNotBlank()}};onEvent?.invoke(o)}}
}
