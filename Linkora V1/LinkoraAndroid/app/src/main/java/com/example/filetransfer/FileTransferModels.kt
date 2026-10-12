package com.example.filetransfer

import java.io.File

enum class TransferStatus {
    PENDING_APPROVAL,
    TRANSFERRING,
    VERIFYING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class ActiveTransfer(
    val transferId: String,
    val direction: String, // "INCOMING" or "OUTGOING"
    val fileName: String,
    val totalBytes: Long,
    val transferredBytes: Long,
    val sha256: String,
    val status: TransferStatus,
    val errorMessage: String? = null,
    val tempFile: File? = null
) {
    val progress: Float
        get() = if (totalBytes > 0) (transferredBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}
