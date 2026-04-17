package com.sekusarisu.yanami.ui.screen.nodedetail

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import cafe.adriel.voyager.core.model.screenModelScope
import com.sekusarisu.yanami.R
import com.sekusarisu.yanami.data.remote.SessionManager
import com.sekusarisu.yanami.domain.model.AuthType
import com.sekusarisu.yanami.domain.model.LoadRecord
import com.sekusarisu.yanami.domain.model.Node
import com.sekusarisu.yanami.data.remote.dto.NodeStatusDto
import com.sekusarisu.yanami.domain.model.ServerInstance
import com.sekusarisu.yanami.domain.repository.NodeRepository
import com.sekusarisu.yanami.domain.repository.Requires2FAException
import com.sekusarisu.yanami.domain.repository.ServerRepository
import com.sekusarisu.yanami.domain.repository.SessionExpiredException
import com.sekusarisu.yanami.mvi.MviViewModel
import com.sekusarisu.yanami.ui.screen.isSessionAuthError
import com.sekusarisu.yanami.ui.screen.isTwoFaHint
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 节点详情 ViewModel */
class NodeDetailViewModel(
        private val uuid: String,
        private val nodeRepository: NodeRepository,
        private val serverRepository: ServerRepository,
        private val sessionManager: SessionManager,
        private val context: Context
) :
        MviViewModel<NodeDetailContract.State, NodeDetailContract.Event, NodeDetailContract.Effect>(
                NodeDetailContract.State()
        ) {

        private var wsJob: Job? = null
        private var seedFetchJob: Job? = null
        private val realtimeLoadRecordBuffer = ArrayDeque<LoadRecord>(MAX_REALTIME_RECORDS)
        private val realtimeLoadRecordsState = mutableStateListOf<LoadRecord>()

        companion object {
                /** 实时模式最大数据点数（约 4 分钟，每 2 秒一个） */
                private const val MAX_REALTIME_RECORDS = 120
        }

        init {
                setState { copy(realtimeLoadRecords = realtimeLoadRecordsState) }
                loadNodeDetail()
        }

        override fun onEvent(event: NodeDetailContract.Event) {
                when (event) {
                        is NodeDetailContract.Event.Refresh -> loadNodeDetail()
                        is NodeDetailContract.Event.Retry -> loadNodeDetail()
                        is NodeDetailContract.Event.LoadHoursChanged -> {
                                val switchingToRealtime = event.hours == 0
                                seedFetchJob?.cancel()
                                if (switchingToRealtime) {
                                        replaceRealtimeRecords(emptyList())
                                }
                                setState {
                                        copy(
                                                selectedLoadHours = event.hours,
                                                isLoadRecordsLoading = event.hours > 0,
                                                realtimeLoadRecords = realtimeLoadRecordsState
                                        )
                                }
                                if (switchingToRealtime) {
                                        seedFetchJob = screenModelScope.launch {
                                                val recentRecords = fetchRecentAsSeed()
                                                replaceRealtimeRecords(recentRecords)
                                                setState {
                                                        copy(
                                                                realtimeLoadRecords =
                                                                        realtimeLoadRecordsState
                                                        )
                                                }
                                                startWebSocket()
                                        }
                                } else {
                                        startWebSocket()
                                }
                        }
                        is NodeDetailContract.Event.PingHoursChanged -> {
                                setState {
                                        copy(
                                                selectedPingHours = event.hours,
                                                isPingRecordsLoading = true
                                        )
                                }
                                startWebSocket()
                        }
                }
        }

        /** 加载节点详情：基本信息 + 负载记录 + Ping 记录 */
        private fun loadNodeDetail() {
                setState { copy(isLoading = true, error = null) }
                screenModelScope.launch {
                        var activeServerId: Long? = null
                        var activeRequires2fa = false
                        var activeAuthType = AuthType.PASSWORD
                        try {
                                val server =
                                        serverRepository.getActive()
                                                ?: throw Exception(
                                                        context.getString(
                                                                R.string.error_no_server_selected
                                                        )
                                                )
                                activeServerId = server.id
                                activeRequires2fa = server.requires2fa
                                activeAuthType = server.authType

                                val sessionToken = ensureSession(server)

                                // 获取节点基本信息（包含实时状态）
                                val allNodes =
                                        nodeRepository.getNodeInfos(server.baseUrl, sessionToken)
                                val node =
                                        allNodes.find { it.uuid == uuid }
                                                ?: throw Exception(
                                                        context.getString(
                                                                R.string.node_detail_not_found
                                                        )
                                                )

                                setState {
                                        copy(
                                                node = node,
                                                isLoading = false,
                                                error = null,
                                                isLoadRecordsLoading = selectedLoadHours > 0,
                                                isPingRecordsLoading = true,
                                                authType = server.authType
                                        )
                                }

                                // 实时模式（默认）：先拉取最近 1 分钟数据作为图表 seed
                                if (currentState.selectedLoadHours == 0) {
                                        val recentRecords = try {
                                                nodeRepository.getNodeRecentStatus(
                                                        server.baseUrl, sessionToken, uuid)
                                        } catch (_: Exception) {
                                                emptyList()
                                        }
                                        replaceRealtimeRecords(recentRecords)
                                        setState {
                                                copy(realtimeLoadRecords = realtimeLoadRecordsState)
                                        }
                                }

                                // 启动复用的 WebSocket 获取实时状态和历史记录
                                startWebSocket()
                        } catch (e: Exception) {
                                if (activeServerId != null &&
                                                handleSessionExpired(
                                                        activeServerId,
                                                        activeRequires2fa,
                                                        e,
                                                        activeAuthType
                                                )
                                ) {
                                        return@launch
                                }
                                setState {
                                        copy(
                                                isLoading = false,
                                                error =
                                                        context.getString(
                                                                R.string.node_detail_load_failed,
                                                                e.message
                                                        )
                                        )
                                }
                        }
                }
        }

        /** 调用 common:getNodeRecentStatus 获取最近 1 分钟未降采样数据，用作实时图表 seed */
        private suspend fun fetchRecentAsSeed(): List<LoadRecord> {
                val server = serverRepository.getActive() ?: return emptyList()
                val sessionToken = sessionManager.getSessionToken() ?: return emptyList()
                return try {
                        nodeRepository.getNodeRecentStatus(server.baseUrl, sessionToken, uuid)
                } catch (_: Exception) {
                        emptyList()
                }
        }

        private fun startWebSocket() {
                wsJob?.cancel()
                val currentStateSnapshot = currentState
                currentStateSnapshot.node ?: return
                val isRealtime = currentStateSnapshot.selectedLoadHours == 0

                wsJob =
                        screenModelScope.launch {
                                var activeServer: ServerInstance? =
                                        null
                                try {
                                        val server = serverRepository.getActive() ?: return@launch
                                        activeServer = server
                                        val sessionToken =
                                                sessionManager.getSessionToken() ?: return@launch

                                        nodeRepository.observeNodeDetailWs(
                                                        server.baseUrl,
                                                        sessionToken,
                                                        uuid,
                                                        // 实时模式不请求历史数据
                                                        loadHours =
                                                                if (isRealtime) null
                                                                else
                                                                        currentStateSnapshot
                                                                                .selectedLoadHours,
                                                        pingHours =
                                                                currentStateSnapshot
                                                                        .selectedPingHours
                                                )
                                                .collect { event ->
                                                        when (event) {
                                                                is NodeRepository.NodeDetailWsEvent.Status -> {
                                                                        val statusMap =
                                                                                event.statusMap
                                                                        val status = statusMap[uuid]
                                                                        if (status != null) {
                                                                                val currentNode =
                                                                                        currentState
                                                                                                .node
                                                                                                ?: return@collect
                                                                                val updatedNode =
                                                                                        mergeNodeStatus(
                                                                                                currentNode,
                                                                                                status
                                                                                        )
                                                                                if (currentState.selectedLoadHours ==
                                                                                                0
                                                                                ) {
                                                                                        appendRealtimeRecord(
                                                                                                buildRealtimeRecord(
                                                                                                        updatedNode,
                                                                                                        status
                                                                                                )
                                                                                        )
                                                                                }
                                                                                setState {
                                                                                        copy(
                                                                                                node =
                                                                                                        updatedNode,
                                                                                                realtimeLoadRecords =
                                                                                                        realtimeLoadRecordsState
                                                                                        )
                                                                                }
                                                                        }
                                                                }
                                                                is NodeRepository.NodeDetailWsEvent.LoadRecords -> {
                                                                        setState {
                                                                                copy(
                                                                                        loadRecords =
                                                                                                event.records,
                                                                                        isLoadRecordsLoading =
                                                                                                false
                                                                                )
                                                                        }
                                                                }
                                                                is NodeRepository.NodeDetailWsEvent.PingRecords -> {
                                                                        setState {
                                                                                copy(
                                                                                        pingTasks =
                                                                                                event.tasks,
                                                                                        pingRecords =
                                                                                                event.records,
                                                                                        isPingRecordsLoading =
                                                                                                false
                                                                                )
                                                                        }
                                                                }
                                                        }
                                                }
                                } catch (e: CancellationException) {
                                        throw e
                                } catch (e: Exception) {
                                        if (activeServer?.id != null &&
                                                        handleSessionExpired(
                                                                activeServer.id,
                                                                activeServer.requires2fa,
                                                                e,
                                                                activeServer.authType
                                                        )
                                        ) {
                                                return@launch
                                        }
                                        setState {
                                                copy(
                                                        isLoadRecordsLoading = false,
                                                        isPingRecordsLoading = false
                                                )
                                        }
                                        sendEffect(
                                                NodeDetailContract.Effect.ShowToast(
                                                        context.getString(
                                                                R.string.node_detail_records_failed,
                                                                e.message
                                                        )
                                                )
                                        )
                                }
                        }
        }

        private fun mergeNodeStatus(node: Node, status: NodeStatusDto): Node {
                return node.copy(
                        isOnline = status.online,
                        cpuUsage = status.cpu,
                        memUsed = status.ram,
                        memTotal = if (status.ramTotal > 0) status.ramTotal else node.memTotal,
                        swapUsed = status.swap,
                        swapTotal = if (status.swapTotal > 0) status.swapTotal else node.swapTotal,
                        diskUsed = status.disk,
                        diskTotal = if (status.diskTotal > 0) status.diskTotal else node.diskTotal,
                        netIn = status.netIn,
                        netOut = status.netOut,
                        netTotalUp = status.netTotalUp,
                        netTotalDown = status.netTotalDown,
                        uptime = status.uptime,
                        load1 = status.load,
                        load5 = status.load5,
                        load15 = status.load15,
                        process = status.process,
                        connectionsTcp = status.connections,
                        connectionsUdp = status.connectionsUdp
                )
        }

        private fun buildRealtimeRecord(node: Node, status: NodeStatusDto): LoadRecord {
                val ramPercent =
                        if (node.memTotal > 0) {
                                node.memUsed.toDouble() / node.memTotal * 100
                        } else {
                                0.0
                        }
                return LoadRecord(
                        time = Instant.now().toString(),
                        cpu = status.cpu,
                        ramPercent = ramPercent,
                        diskPercent = 0.0,
                        netIn = status.netIn,
                        netOut = status.netOut,
                        load = status.load,
                        process = status.process,
                        connections = status.connections,
                        connectionsUdp = status.connectionsUdp
                )
        }

        private fun replaceRealtimeRecords(records: List<LoadRecord>) {
                realtimeLoadRecordBuffer.clear()
                records.takeLast(MAX_REALTIME_RECORDS).forEach { record ->
                        realtimeLoadRecordBuffer.addLast(record)
                }
                realtimeLoadRecordsState.clear()
                realtimeLoadRecordsState.addAll(realtimeLoadRecordBuffer)
        }

        private fun appendRealtimeRecord(record: LoadRecord) {
                if (realtimeLoadRecordBuffer.size == MAX_REALTIME_RECORDS) {
                        realtimeLoadRecordBuffer.removeFirst()
                        if (realtimeLoadRecordsState.isNotEmpty()) {
                                realtimeLoadRecordsState.removeAt(0)
                        }
                }
                realtimeLoadRecordBuffer.addLast(record)
                realtimeLoadRecordsState.add(record)
        }

        private suspend fun ensureSession(
                server: ServerInstance
        ): String {
                return try {
                        serverRepository.ensureSessionToken(server)
                } catch (e: Requires2FAException) {
                        serverRepository.updateRequires2fa(server.id, true)
                        throw SessionExpiredException(
                                e.message ?: context.getString(R.string.error_no_session)
                        )
                } catch (e: Exception) {
                        if (e.isSessionAuthError()) {
                                throw SessionExpiredException(
                                        e.message ?: context.getString(R.string.error_no_session)
                                )
                        }
                        throw e
                }
        }

        private fun handleSessionExpired(
                serverId: Long,
                requires2fa: Boolean,
                error: Throwable,
                authType: AuthType = AuthType.PASSWORD
        ): Boolean {
                if (!error.isSessionAuthError()) return false
                if (authType == AuthType.GUEST) return false
                wsJob?.cancel()
                setState {
                        copy(
                                isLoading = false,
                                isLoadRecordsLoading = false,
                                isPingRecordsLoading = false,
                                error = null
                        )
                }
                sendEffect(
                        NodeDetailContract.Effect.ShowToast(
                                error.message ?: context.getString(R.string.error_no_session)
                        )
                )
                if (authType == AuthType.API_KEY) {
                        sendEffect(NodeDetailContract.Effect.NavigateToServerEdit(serverId))
                } else {
                        val forceTwoFa = requires2fa || error.isTwoFaHint()
                        sendEffect(
                                NodeDetailContract.Effect.NavigateToServerRelogin(
                                        serverId,
                                        forceTwoFa
                                )
                        )
                }
                return true
        }
}
