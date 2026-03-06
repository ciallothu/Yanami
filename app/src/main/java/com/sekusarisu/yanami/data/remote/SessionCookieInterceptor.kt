package com.sekusarisu.yanami.data.remote

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp Interceptor — 自动注入 session_token Cookie
 *
 * 确保所有 HTTP 请求和 WebSocket 升级请求都携带 session_token Cookie。 这比在 Ktor 的 request builder 中手动设置更可靠， 因为
 * OkHttp WebSocket 升级握手不一定会转发 Ktor 设置的自定义头。
 */
class SessionCookieInterceptor(private val sessionManager: SessionManager) : Interceptor {

    companion object {
        private const val TAG = "SessionCookie"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val sessionToken = sessionManager.getSessionToken()

        return if (sessionToken != null) {
            // 构造 Cookie header，保留原有 Cookie（如果有的话）
            val existingCookie = originalRequest.header("Cookie")
            val sessionCookie = "session_token=$sessionToken"
            val fullCookie =
                    if (existingCookie.isNullOrBlank()) sessionCookie
                    else "$existingCookie; $sessionCookie"

            val newRequest = originalRequest.newBuilder().header("Cookie", fullCookie).build()

            Log.d(
                    TAG,
                    "Injecting session cookie for ${originalRequest.url.host}${originalRequest.url.encodedPath}"
            )
            chain.proceed(newRequest)
        } else {
            chain.proceed(originalRequest)
        }
    }
}
