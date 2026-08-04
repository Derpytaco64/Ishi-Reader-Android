package com.ishireader.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PositionDao {

    @Query("SELECT * FROM positions WHERE manifestUrl = :manifestUrl")
    suspend fun get(manifestUrl: String): PositionEntity?

    @Query("SELECT * FROM positions WHERE manifestUrl = :manifestUrl")
    fun observe(manifestUrl: String): Flow<PositionEntity?>

    @Query("SELECT * FROM positions WHERE pendingSync = 1")
    suspend fun getPending(): List<PositionEntity>

    @Upsert
    suspend fun upsert(entity: PositionEntity)
}
