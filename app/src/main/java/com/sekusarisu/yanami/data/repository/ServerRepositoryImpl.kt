package com.sekusarisu.yanami.data.repository

import android.util.Log
import com.sekusarisu.yanami.data.local.crypto.CryptoManager
import com.sekusarisu.yanami.data.local.dao.ServerInstanceDao
import com.sekusarisu.yanami.data.local.entity.ServerInstanceEntity
import com.sekusarisu.yanami.data.remote.KomariAuthService
import com.sekusarisu.yanami.data.remote.KomariRpcService
import com.sekusarisu.yanami.data.remote.LoginResult
import com.sekusarisu.yanami.data.remote.SessionManager
import com.sekusarisu.yanami.domain.model.ServerInstance
import com.sekusarisu.yanami.domain.repository.Requires2FAException
import com.sekusarisu.yanami.domain.repository.ServerRepository
import com.sekusarisu.yanami.domain.repository.SessionExpiredException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 服务端实例仓库实现
 *
 * 负责：
 * - Entity ↔ Domain Model 转换
 * - 用户名/密码加解密
 * - session_token 持久化与恢复
 * - 通过 KomariAuthService 登录获取 session_token
 * - 通过 KomariRpcService 验证连接
 */
class ServerRepositoryImpl(
        private val dao: ServerInstanceDao,
        private val cryptoManager: CryptoManager,
        private val authService: KomariAuthService,
        private val rpcService: KomariRpcService,
        private val sessionManager: SessionManager
) : ServerRepository {

    companion object {
        private const val TAG = "ServerRepo"
    }

    override fun getAllFlow(): Flow<List<ServerInstance>> {
        return dao.getAllFlow().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getActive(): ServerInstance? {
        return dao.getActive()?.toDomain()
    }

    override fun getActiveFlow(): Flow<ServerInstance?> {
        return dao.getActiveFlow().map { it?.toDomain() }
    }

    override suspend fun getById(id: Long): ServerInstance? {
        return dao.getById(id)?.toDomain()
    }

    override suspend fun add(instance: ServerInstance): Long {
        val entity = instance.toEntity()
        return dao.insert(entity)
    }

    override suspend fun update(instance: ServerInstance) {
        dao.update(instance.toEntity())
    }

    override suspend fun remove(id: Long) {
        val entity = dao.getById(id) ?: return
        dao.delete(entity)
    }

    override suspend fun setActive(id: Long) {
        dao.deactivateAll()
        dao.activateById(id)
    }

    override suspend fun testConnection(
            baseUrl: String,
            username: String,
            password: String,
            twoFaCode: String?
    ): String {
        // 1. 登录获取 session_token
        val loginResult = authService.login(baseUrl, username, password, twoFaCode)
        val sessionToken =
                when (loginResult) {
                    is LoginResult.Success -> loginResult.sessionToken
                    is LoginResult.Requires2FA -> throw Requires2FAException(loginResult.message)
                    is LoginResult.Error -> throw Exception(loginResult.message)
                }

        // 2. 用 session_token 调用 RPC 获取版本号验证
        val version = rpcService.getVersion(baseUrl, sessionToken)
        Log.d(TAG, "Test connection ok, version=$version")
        return version
    }

    override suspend fun login(instance: ServerInstance, twoFaCode: String?): Boolean {
        val loginResult =
                authService.login(instance.baseUrl, instance.username, instance.password, twoFaCode)

        return when (loginResult) {
            is LoginResult.Success -> {
                // 更新内存 session
                sessionManager.setSession(instance.id, instance.baseUrl, loginResult.sessionToken)

                // 持久化 session_token（加密）
                val encryptedToken = cryptoManager.encrypt(loginResult.sessionToken)
                dao.updateSessionToken(instance.id, encryptedToken)

                Log.d(TAG, "Login successful for server ${instance.name}")
                true
            }
            is LoginResult.Requires2FA -> {
                throw Requires2FAException(loginResult.message)
            }
            is LoginResult.Error -> {
                throw Exception(loginResult.message)
            }
        }
    }

    override suspend fun ensureSessionToken(instance: ServerInstance): String {
        val restored = restoreSession(instance)
        if (!restored) {
            login(instance) // 成功时内部已更新 SessionManager；失败时抛出 Requires2FAException 或其他异常
        }
        return sessionManager.getSessionToken()
                ?: throw SessionExpiredException()
    }

    override suspend fun restoreSession(instance: ServerInstance): Boolean {
        val cachedToken = instance.sessionToken
        if (cachedToken.isNullOrBlank()) {
            Log.d(TAG, "No cached session_token for server ${instance.name}")
            sessionManager.clearSession()
            return false
        }

        // 验证 token 有效性
        val isValid = authService.validateSession(instance.baseUrl, cachedToken)
        if (isValid) {
            sessionManager.setSession(instance.id, instance.baseUrl, cachedToken)
            Log.d(TAG, "Session restored for server ${instance.name}")
            return true
        }

        // Token 已失效，清除缓存
        Log.d(TAG, "Cached session_token expired for server ${instance.name}")
        sessionManager.clearSession()
        dao.updateSessionToken(instance.id, null)
        return false
    }

    override suspend fun updateRequires2fa(id: Long, requires2fa: Boolean) {
        val entity = dao.getById(id) ?: return
        dao.update(entity.copy(requires2fa = requires2fa))
    }

    override suspend fun updateAuthInfo(
            id: Long,
            username: String,
            password: String,
            requires2fa: Boolean
    ) {
        val entity = dao.getById(id) ?: return
        dao.update(
                entity.copy(
                        encryptedUsername = cryptoManager.encrypt(username),
                        encryptedPassword = cryptoManager.encrypt(password),
                        requires2fa = requires2fa
                )
        )
    }

    // ─── Entity ↔ Domain 转换 ───

    private fun ServerInstanceEntity.toDomain(): ServerInstance {
        return ServerInstance(
                id = id,
                name = name,
                baseUrl = baseUrl,
                username =
                        try {
                            cryptoManager.decrypt(encryptedUsername)
                        } catch (e: Exception) {
                            ""
                        },
                password =
                        try {
                            cryptoManager.decrypt(encryptedPassword)
                        } catch (e: Exception) {
                            ""
                        },
                sessionToken =
                        encryptedSessionToken?.let {
                            try {
                                cryptoManager.decrypt(it)
                            } catch (e: Exception) {
                                null
                            }
                        },
                requires2fa = requires2fa,
                isActive = isActive,
                createdAt = createdAt
        )
    }

    private fun ServerInstance.toEntity(): ServerInstanceEntity {
        return ServerInstanceEntity(
                id = if (id == 0L) 0 else id,
                name = name,
                baseUrl = baseUrl,
                encryptedUsername = cryptoManager.encrypt(username),
                encryptedPassword = cryptoManager.encrypt(password),
                encryptedSessionToken =
                        sessionToken?.let {
                            try {
                                cryptoManager.encrypt(it)
                            } catch (e: Exception) {
                                null
                            }
                        },
                requires2fa = requires2fa,
                isActive = isActive,
                createdAt = createdAt
        )
    }
}
