package com.example.data.history

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferHistoryDao {

    @Query("SELECT * FROM transfer_history ORDER BY timestamp DESC")
    fun getAllTransfers(): Flow<List<TransferHistoryEntity>>

    @Query("SELECT * FROM transfer_history ORDER BY timestamp DESC LIMIT 5")
    fun getRecentTransfers(): Flow<List<TransferHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransfer(entity: TransferHistoryEntity)

    @Delete
    suspend fun deleteTransfer(entity: TransferHistoryEntity)

    @Query("DELETE FROM transfer_history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM transfer_history")
    suspend fun clearAll()
}
