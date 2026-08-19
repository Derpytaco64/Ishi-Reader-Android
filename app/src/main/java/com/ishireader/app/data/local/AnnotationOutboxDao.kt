package com.ishireader.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface AnnotationOutboxDao {

    @Query("SELECT * FROM annotation_outbox WHERE kind = :kind AND manifestUrl = :manifestUrl")
    suspend fun getForBook(kind: String, manifestUrl: String): List<AnnotationOutboxEntity>

    @Query("SELECT * FROM annotation_outbox")
    suspend fun getAll(): List<AnnotationOutboxEntity>

    @Upsert
    suspend fun upsert(entity: AnnotationOutboxEntity)

    @Query("DELETE FROM annotation_outbox WHERE kind = :kind AND manifestUrl = :manifestUrl AND itemId = :itemId")
    suspend fun remove(kind: String, manifestUrl: String, itemId: String)
}
