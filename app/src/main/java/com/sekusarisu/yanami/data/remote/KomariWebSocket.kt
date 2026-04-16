package com.sekusarisu.yanami.data.remote

import android.util.Log
import com.sekusarisu.yanami.domain.model.AuthType
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.ws
import io.ktor.client.plugins.websocket.wss
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.net.URI

internal data class KomariWebSocketEndpoint(
        val host: String,
        val port: Int,
        val path: String,
        val isSecure: Boolean,
        val origin: String
) {
    private val displayHost = host.toBracketedHost()
    val displayUrl: String
        get() = "${if (isSecure) "wss" else "ws"}://$displayHost:$port$path"
}

internal fun buildKomariWebSocketEndpoint(
        baseUrl: String,
        targetPath: String
): KomariWebSocketEndpoint {
    val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
    val parsed = URI(normalizedBaseUrl)
    val scheme = parsed.scheme?.lowercase()
            ?: throw IllegalArgumentException("Base URL missing scheme: $baseUrl")
    val isSecure =
            when (scheme) {
                "https" -> true
                "http" -> false
                else -> throw IllegalArgumentException("Unsupported URL scheme: $scheme")
            }
    val host = parsed.host ?: throw IllegalArgumentException("Base URL missing host: $baseUrl")
    val defaultPort = if (isSecure) 443 else 80
    val port = if (parsed.port == -1) defaultPort else parsed.port
    val normalizedTargetPath =
            if (targetPath.startsWith('/')) targetPath else "/$targetPath"
    val basePath = parsed.rawPath.orEmpty().trimEnd('/')
    val path = "$basePath$normalizedTargetPath"
    val originPort =
            if (parsed.port == -1) {
                ""
            } else {
                ":$port"
            }
    val originScheme = if (isSecure) "https" else "http"
    val origin = "$originScheme://${host.toBracketedHost()}$originPort"

    return KomariWebSocketEndpoint(
            host = host,
            port = port,
            path = path,
            isSecure = isSecure,
            origin = origin
    )
}

internal suspend fun HttpClient.connectKomariWebSocket(
        endpoint: KomariWebSocketEndpoint,
        sessionToken: String,
        authType: AuthType,
        block: suspend DefaultClientWebSocketSession.() -> Unit
) {
    val requestBuilder: HttpRequestBuilder.() -> Unit = {
        applyAuth(sessionToken, authType)
        header("Origin", endpoint.origin)
    }

    if (endpoint.isSecure) {
        wss(
                host = endpoint.host,
                port = endpoint.port,
                path = endpoint.path,
                request = requestBuilder,
                block = block
        )
    } else {
        ws(
                host = endpoint.host,
                port = endpoint.port,
                path = endpoint.path,
                request = requestBuilder,
                block = block
        )
    }
}

internal suspend fun HttpClient.runKomariWebSocketLifecycle(
        endpoint: KomariWebSocketEndpoint,
        sessionToken: String,
        authType: AuthType,
        loggerTag: String,
        reconnectDelayMs: Long? = null,
        reconnectOnNormalClose: Boolean = false,
        block: suspend DefaultClientWebSocketSession.() -> Unit
) {
    while (currentCoroutineContext().isActive) {
        var shouldReconnect = false
        try {
            Log.d(loggerTag, "WebSocket connecting to ${endpoint.displayUrl}")
            connectKomariWebSocket(endpoint, sessionToken, authType, block)
            shouldReconnect = reconnectOnNormalClose && reconnectDelayMs != null
            if (!shouldReconnect) {
                return
            }
            Log.w(
                    loggerTag,
                    "WebSocket closed, reconnecting in ${reconnectDelayMs}ms..."
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isKomariWebSocketAuthException(e)) {
                Log.w(loggerTag, "WebSocket auth error: ${e.message}, propagating")
                throw e
            }
            if (reconnectDelayMs == null) {
                throw e
            }
            shouldReconnect = true
            Log.w(loggerTag, "WebSocket error: ${e.message}, reconnecting in ${reconnectDelayMs}ms...")
        }

        if (!shouldReconnect) {
            return
        }
        delay(reconnectDelayMs ?: return)
    }
}

internal fun isKomariWebSocketAuthException(e: Exception): Boolean {
    if (e is ClientRequestException) {
        val status = e.response.status.value
        return status == 401 || status == 403
    }
    val msg = (e.message ?: "").lowercase()
    return msg.contains("403") || msg.contains("401") ||
            msg.contains("forbidden") || msg.contains("unauthorized")
}

private fun String.toBracketedHost(): String =
        if (contains(':') && !startsWith("[")) {
            "[$this]"
        } else {
            this
        }
