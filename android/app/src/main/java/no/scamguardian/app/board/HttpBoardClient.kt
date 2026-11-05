package no.scamguardian.app.board

import java.io.IOException
import java.net.Proxy
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * OkHttp client for the board's HTTP API (port 80) and WebSocket (port 81). URLs always use
 * [LocalDns.BOARD_HOST]; [LocalDns] maps it to [BoardEndpoint.host]. No logging interceptor: request
 * bodies can hold family words and every request carries the token.
 */
class HttpBoardClient(
    private val endpoint: BoardEndpoint,
    base: OkHttpClient,
) : BoardClient {

    private val http: OkHttpClient = base.newBuilder()
        .dns(LocalDns(endpoint.host))
        .proxy(Proxy.NO_PROXY)
        .build()

    private fun url(path: String, query: Map<String, String> = emptyMap()): HttpUrl =
        HttpUrl.Builder().scheme("http").host(LocalDns.BOARD_HOST).port(endpoint.port)
            .addPathSegments(path.trimStart('/'))
            .apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()

    private fun request(url: HttpUrl) = Request.Builder().url(url)
        .header("Authorization", "Bearer ${endpoint.token}")
        .header("Accept", "application/json")

    private suspend fun <T> call(req: Request, de: DeserializationStrategy<T>): T {
        val response = try {
            http.newCall(req).await()
        } catch (e: IOException) {
            throw BoardException.Unreachable(e)
        }
        response.use { r ->
            val body = r.body.string()
            if (!r.isSuccessful) throw errorFor(r.code, body)
            return try {
                BoardJson.decodeFromString(de, body)
            } catch (e: Exception) {
                throw BoardException.Protocol("Unexpected answer from the board for ${req.url.encodedPath}")
            }
        }
    }

    private suspend fun <T> get(path: String, de: DeserializationStrategy<T>, query: Map<String, String> = emptyMap()) =
        call(request(url(path, query)).get().build(), de)

    private suspend fun <T> post(path: String, body: JsonObject, de: DeserializationStrategy<T>) =
        call(request(url(path)).post(body.toString().toRequestBody(JSON)).build(), de)

    override suspend fun status() = get("/status", BoardStatus.serializer())

    override suspend fun setGuard(on: Boolean): Boolean =
        post("/guard", buildJsonObject { put("on", JsonPrimitive(on)) }, GuardResponse.serializer()).guarding

    override suspend fun events(since: Long, limit: Int) =
        get("/events", EventsPage.serializer(), mapOf("since" to since.toString(), "limit" to limit.toString()))

    override suspend fun config() = get("/config", BoardConfig.serializer())

    override suspend fun updateConfig(patch: JsonObject) = post("/config", patch, BoardConfig.serializer())

    override suspend fun members() = get("/words", WordsList.serializer())

    override suspend fun addMember(name: String, word: String?) = post(
        "/words",
        buildJsonObject {
            put("name", JsonPrimitive(name))
            if (!word.isNullOrEmpty()) put("word", JsonPrimitive(word))
        },
        Member.serializer(),
    )

    override suspend fun renameMember(id: Int, name: String) = post(
        "/words",
        buildJsonObject { put("id", JsonPrimitive(id)); put("name", JsonPrimitive(name)) },
        Member.serializer(),
    )

    override suspend fun setWord(id: Int, word: String) = post(
        "/words",
        buildJsonObject { put("id", JsonPrimitive(id)); put("word", JsonPrimitive(word)) },
        Member.serializer(),
    )

    override suspend fun clearWord(id: Int) = post(
        "/words",
        buildJsonObject { put("id", JsonPrimitive(id)); put("clear", JsonPrimitive(true)) },
        Member.serializer(),
    )

    override suspend fun deleteMember(id: Int) {
        call(request(url("/words", mapOf("id" to id.toString()))).delete().build(), JsonObject.serializer())
    }

    override suspend fun startSetup() {
        post("/setup", JsonObject(emptyMap()), JsonObject.serializer())
    }

    override fun live(): Flow<LiveMessage> = callbackFlow {
        val wsUrl = HttpUrl.Builder().scheme("http").host(LocalDns.BOARD_HOST).port(endpoint.wsPort).build()
        val req = Request.Builder().url(wsUrl.toString().replaceFirst("http:", "ws:"))
            .header("Authorization", "Bearer ${endpoint.token}")
            .build()
        var gotHello = false
        val ws = http.newWebSocket(req, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = BoardEvent.parseLive(text) ?: return
                if (msg is LiveMessage.Hello) gotHello = true
                trySendBlocking(msg)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                if (gotHello) close() else close(closedBeforeHello(code, reason))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (gotHello) close() else close(closedBeforeHello(code, reason))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val code = response?.code
                close(
                    when {
                        code == 401 -> BoardException.Unauthorized()
                        code != null -> errorFor(code, "")
                        else -> BoardException.Unreachable(t)
                    },
                )
            }
        })
        awaitClose { ws.cancel() }
    }.buffer(Channel.UNLIMITED)

    private fun closedBeforeHello(code: Int, reason: String): BoardException =
        // The protocol says a wrong token may be refused by closing the socket right after it opens,
        // without naming a close code. 1008 (policy violation) is the standard code for that.
        if (code == 1008 || code == 4401 || reason.contains("auth", true) || reason.contains("token", true)) {
            BoardException.Unauthorized()
        } else {
            BoardException.Refused(code, reason)
        }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun errorFor(code: Int, body: String): BoardException {
            val error = runCatching { BoardJson.decodeFromString(ErrorBody.serializer(), body).error }.getOrDefault("")
            return when (code) {
                401 -> BoardException.Unauthorized()
                429 -> BoardException.RateLimited()
                400 -> BoardException.BadRequest(error.ifEmpty { "bad request" })
                404 -> BoardException.NotFound(error.ifEmpty { "not found" })
                503 -> if (error == "low_memory") BoardException.LowMemory() else BoardException.Http(code)
                else -> BoardException.Http(code)
            }
        }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            cont.resume(response) { _, r, _ -> r.close() }
        }

        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
