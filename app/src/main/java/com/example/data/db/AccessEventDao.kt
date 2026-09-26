package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.AccessEvent
import com.example.data.model.ResourceType
import kotlinx.coroutines.flow.Flow

@Dao
interface AccessEventDao {
    @Query("SELECT * FROM access_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<AccessEvent>>

    @Query("SELECT * FROM access_events WHERE resourceType = :resourceType ORDER BY timestamp DESC")
    fun getEventsByResource(resourceType: ResourceType): Flow<List<AccessEvent>>

    @Query("SELECT * FROM access_events WHERE riskLevel != 'NORMAL' ORDER BY timestamp DESC")
    fun getFlaggedEvents(): Flow<List<AccessEvent>>

    @Query("SELECT * FROM access_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentEvents(limit: Int): Flow<List<AccessEvent>>

    @Query("SELECT COUNT(*) FROM access_events WHERE resourceType = 'MICROPHONE'")
    fun getMicEventsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM access_events WHERE isBackground = 1")
    fun getBackgroundEventsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM access_events")
    fun getTotalEventsCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: AccessEvent): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<AccessEvent>)

    @Query("DELETE FROM access_events WHERE id = :id")
    suspend fun deleteEventById(id: Long)

    @Query("DELETE FROM access_events")
    suspend fun clearAll()
}
