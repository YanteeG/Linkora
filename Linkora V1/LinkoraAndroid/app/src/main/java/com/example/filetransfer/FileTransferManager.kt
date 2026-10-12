package com.example.filetransfer

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import com.example.data.history.TransferHistoryDao
import com.example.data.history.TransferHistoryEntity
import com.example.network.LinkoraClient
import com.example.protocol.BinaryFrameCodec
import com.example.protocol.ChecksumUtils
import com.example.protocol.FileAcceptMessage
import com.example.protocol.FileCancelMessage
import com.example.protocol.FileCompleteMessage
import com.example.protocol.FileOfferMessage
import com.example.protocol.FileProgressMessage
import com.example.protocol.FileRejectMessage
import com.example.protocol.FilenameSanitizer
import com.example.protocol.JsonCodec
import com.example.protocol.MAX_CHUNK_SIZE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.UUID

class FileTransferManager(
    private val context: Context,
    private val client: LinkoraClient,
    private val historyDao: TransferHistoryDao
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _activeTransfers = MutableStateFlow<Map<String, ActiveTransfer>>(emptyMap())
    val activeTransfers: StateFlow<Map<String, ActiveTransfer>> = _activeTransfers.asStateFlow()

    private val _pendingIncomingOffer = MutableStateFlow<FileOfferMessage?>(null)
    val pendingIncomingOffer: StateFlow<FileOfferMessage?> = _pendingIncomingOffer.asStateFlow()

    private val activeJobs = mutableMapOf<String, Job>()

    init {
        // Collect incoming offers
        scope.launch {
            client.incomingOffers.collect { offer ->
                _pendingIncomingOffer.value = offer
            }
        }

        // Collect incoming binary chunks
        scope.launch {
            client.incomingChunks.collect { chunk ->
                handleIncomingChunk(chunk.transferId.toString(), chunk.chunkIndex, chunk.payload)
            }
        }

        // Collect file control events (accept, reject, progress, complete, cancel)
        scope.launch {
            client.fileControlEvents.collect { (type, event) ->
                handleControlEvent(type, event)
            }
        }
    }

    private fun handleControlEvent(type: String, event: Any) {
        when (type) {
            "file_accept" -> {
                val msg = event as FileAcceptMessage
                startOutgoingChunkStream(msg.transferId)
            }
            "file_reject" -> {
                val msg = event as FileRejectMessage
                updateTransferStatus(msg.transferId, TransferStatus.FAILED, msg.reason ?: "Offer rejected by peer")
                recordHistory(msg.transferId, "FAILED")
            }
            "file_cancel" -> {
                val msg = event as FileCancelMessage
                cancelTransfer(msg.transferId, msg.reason ?: "Cancelled by peer", notifyPeer = false)
            }
            "file_complete" -> {
                val msg = event as FileCompleteMessage
                val current = _activeTransfers.value[msg.transferId]
                if (current != null && current.direction == "OUTGOING") {
                    updateTransferStatus(msg.transferId, TransferStatus.COMPLETED)
                    recordHistory(msg.transferId, "COMPLETED")
                }
            }
        }
    }

    fun acceptIncomingOffer(offer: FileOfferMessage) {
        _pendingIncomingOffer.value = null
        val safeName = FilenameSanitizer.sanitize(offer.fileName)
        val tempFile = File(context.cacheDir, "incoming_${offer.transferId}.tmp")
        if (tempFile.exists()) tempFile.delete()

        val transfer = ActiveTransfer(
            transferId = offer.transferId,
            direction = "INCOMING",
            fileName = safeName,
            totalBytes = offer.fileSize,
            transferredBytes = 0L,
            sha256 = offer.sha256,
            status = TransferStatus.TRANSFERRING,
            tempFile = tempFile
        )
        val map = _activeTransfers.value.toMutableMap()
        map[offer.transferId] = transfer
        _activeTransfers.value = map

        val acceptMsg = FileAcceptMessage(transferId = offer.transferId)
        val json = JsonCodec.fileAcceptAdapter.toJson(acceptMsg)
        client.sendTextMessage(json)
        client.addDiagnosticLog("Accepted file offer: $safeName (${offer.fileSize} bytes)")
    }

    fun rejectIncomingOffer(offer: FileOfferMessage, reason: String = "Declined by user") {
        _pendingIncomingOffer.value = null
        val rejectMsg = FileRejectMessage(transferId = offer.transferId, reason = reason)
        val json = JsonCodec.fileRejectAdapter.toJson(rejectMsg)
        client.sendTextMessage(json)
        client.addDiagnosticLog("Rejected file offer: ${offer.fileName}")
    }

    private fun handleIncomingChunk(transferId: String, chunkIndex: Long, payload: ByteArray) {
        val transfer = _activeTransfers.value[transferId] ?: return
        if (transfer.status != TransferStatus.TRANSFERRING) return

        val tempFile = transfer.tempFile ?: return
        try {
            RandomAccessFile(tempFile, "rw").use { raf ->
                val offset = chunkIndex * MAX_CHUNK_SIZE
                raf.seek(offset)
                raf.write(payload)
            }

            val newTransferred = (transfer.transferredBytes + payload.size).coerceAtMost(transfer.totalBytes)
            val updated = transfer.copy(transferredBytes = newTransferred)
            val map = _activeTransfers.value.toMutableMap()
            map[transferId] = updated
            _activeTransfers.value = map

            if (newTransferred >= transfer.totalBytes) {
                finalizeIncomingFile(updated)
            }
        } catch (e: Exception) {
            updateTransferStatus(transferId, TransferStatus.FAILED, "Error writing chunk: ${e.message}")
            cancelTransfer(transferId, "File write error", notifyPeer = true)
        }
    }

    private fun finalizeIncomingFile(transfer: ActiveTransfer) {
        scope.launch {
            updateTransferStatus(transfer.transferId, TransferStatus.VERIFYING)
            val tempFile = transfer.tempFile ?: return@launch

            val computedSha256 = withContext(Dispatchers.IO) {
                tempFile.inputStream().use { ChecksumUtils.sha256Hex(it) }
            }

            if (computedSha256.equals(transfer.sha256, ignoreCase = true)) {
                // Verify successful - move to downloads
                val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                var destFile = File(downloadsDir, transfer.fileName)
                if (destFile.exists()) {
                    val nameWithoutExt = transfer.fileName.substringBeforeLast(".")
                    val ext = transfer.fileName.substringAfterLast(".", "")
                    val extSuffix = if (ext.isNotEmpty()) ".$ext" else ""
                    destFile = File(downloadsDir, "${nameWithoutExt}_${System.currentTimeMillis()}$extSuffix")
                }
                tempFile.copyTo(destFile, overwrite = true)
                tempFile.delete()

                updateTransferStatus(transfer.transferId, TransferStatus.COMPLETED)
                recordHistory(transfer.transferId, "COMPLETED")

                val completeMsg = FileCompleteMessage(
                    transferId = transfer.transferId,
                    sha256 = computedSha256
                )
                val json = JsonCodec.fileCompleteAdapter.toJson(completeMsg)
                client.sendTextMessage(json)
                client.addDiagnosticLog("Incoming transfer verified and saved: ${destFile.name}")
            } else {
                tempFile.delete()
                updateTransferStatus(
                    transfer.transferId,
                    TransferStatus.FAILED,
                    "SHA-256 mismatch! Integrity verification failed"
                )
                recordHistory(transfer.transferId, "FAILED")
                cancelTransfer(transfer.transferId, "Checksum verification failed", notifyPeer = true)
                client.addDiagnosticLog("SHA-256 mismatch on incoming file! Discarded.")
            }
        }
    }

    fun sendFile(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            try {
                var fileName = "unnamed_file"
                var fileSize = 0L

                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                        if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                    }
                }

                fileName = FilenameSanitizer.sanitize(fileName)

                // Compute SHA256 of file
                val sha256 = context.contentResolver.openInputStream(uri)?.use { stream ->
                    ChecksumUtils.sha256Hex(stream)
                } ?: throw IllegalStateException("Could not read file stream")

                val transferId = UUID.randomUUID().toString()

                val transfer = ActiveTransfer(
                    transferId = transferId,
                    direction = "OUTGOING",
                    fileName = fileName,
                    totalBytes = fileSize,
                    transferredBytes = 0L,
                    sha256 = sha256,
                    status = TransferStatus.PENDING_APPROVAL
                )
                val map = _activeTransfers.value.toMutableMap()
                map[transferId] = transfer
                _activeTransfers.value = map

                val offer = FileOfferMessage(
                    transferId = transferId,
                    fileName = fileName,
                    fileSize = fileSize,
                    sha256 = sha256,
                    chunkSize = MAX_CHUNK_SIZE
                )
                val json = JsonCodec.fileOfferAdapter.toJson(offer)
                client.sendTextMessage(json)
                client.addDiagnosticLog("Sent file offer for $fileName ($fileSize bytes)")

                // Store URI mapping for when peer accepts
                pendingOutgoingUris[transferId] = uri
            } catch (e: Exception) {
                client.addDiagnosticLog("Error preparing file send: ${e.message}")
            }
        }
    }

    private val pendingOutgoingUris = mutableMapOf<String, Uri>()

    private fun startOutgoingChunkStream(transferId: String) {
        val uri = pendingOutgoingUris[transferId] ?: return
        val currentTransfer = _activeTransfers.value[transferId] ?: return
        updateTransferStatus(transferId, TransferStatus.TRANSFERRING)

        val job = scope.launch(Dispatchers.IO) {
            try {
                val uuid = UUID.fromString(transferId)
                var chunkIndex = 0L
                var totalSent = 0L
                val buffer = ByteArray(MAX_CHUNK_SIZE)

                context.contentResolver.openInputStream(uri)?.use { stream ->
                    var bytesRead = stream.read(buffer)
                    while (isActive && bytesRead != -1) {
                        val payload = if (bytesRead == MAX_CHUNK_SIZE) buffer else buffer.copyOf(bytesRead)
                        val frameBytes = BinaryFrameCodec.encode(uuid, chunkIndex, payload)
                        client.sendBinaryFrame(frameBytes)

                        totalSent += bytesRead
                        chunkIndex++

                        val updated = currentTransfer.copy(transferredBytes = totalSent)
                        val map = _activeTransfers.value.toMutableMap()
                        map[transferId] = updated
                        _activeTransfers.value = map

                        // Progress event
                        val prog = FileProgressMessage(
                            transferId = transferId,
                            bytesTransferred = totalSent,
                            totalBytes = currentTransfer.totalBytes
                        )
                        client.sendTextMessage(JsonCodec.fileProgressAdapter.toJson(prog))

                        bytesRead = stream.read(buffer)
                    }
                }

                if (isActive) {
                    client.addDiagnosticLog("All chunks sent for $transferId, awaiting peer confirmation")
                }
            } catch (e: Exception) {
                updateTransferStatus(transferId, TransferStatus.FAILED, "Transfer error: ${e.message}")
                cancelTransfer(transferId, "Error during streaming", notifyPeer = true)
            } finally {
                pendingOutgoingUris.remove(transferId)
            }
        }
        activeJobs[transferId] = job
    }

    fun cancelTransfer(transferId: String, reason: String = "Cancelled by user", notifyPeer: Boolean = true) {
        activeJobs[transferId]?.cancel()
        activeJobs.remove(transferId)
        pendingOutgoingUris.remove(transferId)

        val transfer = _activeTransfers.value[transferId]
        transfer?.tempFile?.delete()

        updateTransferStatus(transferId, TransferStatus.CANCELLED, reason)
        recordHistory(transferId, "CANCELLED")

        if (notifyPeer) {
            val cancelMsg = FileCancelMessage(transferId = transferId, reason = reason)
            val json = JsonCodec.fileCancelAdapter.toJson(cancelMsg)
            client.sendTextMessage(json)
        }
        client.addDiagnosticLog("Cancelled transfer $transferId: $reason")
    }

    private fun updateTransferStatus(transferId: String, status: TransferStatus, error: String? = null) {
        val current = _activeTransfers.value[transferId] ?: return
        val map = _activeTransfers.value.toMutableMap()
        map[transferId] = current.copy(status = status, errorMessage = error)
        _activeTransfers.value = map
    }

    private fun recordHistory(transferId: String, status: String) {
        val transfer = _activeTransfers.value[transferId] ?: return
        scope.launch {
            historyDao.insertTransfer(
                TransferHistoryEntity(
                    id = transfer.transferId,
                    direction = transfer.direction,
                    fileName = transfer.fileName,
                    fileSize = transfer.totalBytes,
                    timestamp = System.currentTimeMillis(),
                    status = status,
                    sha256 = transfer.sha256
                )
            )
        }
    }
}
