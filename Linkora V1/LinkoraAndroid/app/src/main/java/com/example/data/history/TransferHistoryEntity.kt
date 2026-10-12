package com.example.data.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfer_history")
data class TransferHistoryEntity(
    @PrimaryKey val id: String,
    val direction: String, // "INCOMING" or "OUTGOING"
    val fileName: String,
    val fileSize: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String, // "COMPLETED", "FAILED", "CANCELLED"
    val sha256: String? = null
)
