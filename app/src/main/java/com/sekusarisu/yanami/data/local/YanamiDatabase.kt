package com.sekusarisu.yanami.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sekusarisu.yanami.data.local.dao.ServerInstanceDao
import com.sekusarisu.yanami.data.local.entity.ServerInstanceEntity

/** Yanami Room 数据库 — v2：从 API Key 改为账户密码 + session_token 认证 */
@Database(entities = [ServerInstanceEntity::class], version = 2, exportSchema = false)
abstract class YanamiDatabase : RoomDatabase() {
    abstract fun serverInstanceDao(): ServerInstanceDao
}
