package com.ishireader.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ExactPercentDao {

    @Query("SELECT * FROM exact_percents WHERE manifestUrl = :manifestUrl")
    suspend fun get(manifestUrl: String): ExactPercentEntity?

    @Upsert
    suspend fun upsert(entity: ExactPercentEntity)

    @Query("DELETE FROM exact_percents WHERE manifestUrl = :manifestUrl")
    suspend fun delete(manifestUrl: String)
}
