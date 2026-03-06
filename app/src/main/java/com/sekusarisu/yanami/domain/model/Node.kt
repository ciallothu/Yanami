package com.sekusarisu.yanami.domain.model

/**
 * 领域模型 — 服务器节点
 *
 * 合并节点基本信息（common:getNodes）+ 实时状态（common:getNodesLatestStatus）。 仅保留 UI 层需要的字段。
 */
data class Node(
        val uuid: String,
        val name: String,
        val region: String, // emoji 旗帜
        val group: String,
        val isOnline: Boolean,
        val cpuUsage: Double, // %
        val memUsed: Long, // bytes
        val memTotal: Long,
        val swapUsed: Long,
        val swapTotal: Long,
        val diskUsed: Long,
        val diskTotal: Long,
        val netIn: Long, // bytes/s
        val netOut: Long,
        val netTotalUp: Long, // total bytes
        val netTotalDown: Long,
        val uptime: Long, // seconds
        val os: String,
        val cpuName: String,
        val cpuCores: Int,
        val weight: Int,
        val load1: Double,
        val load5: Double,
        val load15: Double,
        val process: Int,
        val connectionsTcp: Int,
        val connectionsUdp: Int,
        // 详情页额外字段
        val kernelVersion: String = "",
        val virtualization: String = "",
        val arch: String = "",
        val gpuName: String = ""
)
