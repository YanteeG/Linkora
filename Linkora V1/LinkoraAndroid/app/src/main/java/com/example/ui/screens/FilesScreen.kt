package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.filetransfer.ActiveTransfer
import com.example.filetransfer.TransferStatus
import com.example.network.ConnectionState
import com.example.ui.MainViewModel
import com.example.ui.components.GlassCard
import com.example.ui.components.IncomingFileOfferDialog
import com.example.ui.components.formatFileSize
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.Charcoal800
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseDanger
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun FilesScreen(
    viewModel: MainViewModel,
    onNavigateToHistory: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val activeTransfers by viewModel.activeTransfers.collectAsState()
    val pendingIncomingOffer by viewModel.pendingIncomingOffer.collectAsState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.sendFile(uri)
        }
    }

    // Modal dialog for incoming file offer approval
    pendingIncomingOffer?.let { offer ->
        IncomingFileOfferDialog(
            offer = offer,
            onAccept = { viewModel.acceptIncomingOffer(offer) },
            onReject = { viewModel.rejectIncomingOffer(offer) }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "File Transfer",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "End-to-end binary streaming over local Wi-Fi",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }

                IconButton(onClick = onNavigateToHistory) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = "Transfer History",
                        tint = ElectricCyan
                    )
                }
            }
        }

        // Action banner: Send Files
        item {
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(ElectricCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.UploadFile,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Send to Windows PC",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Storage Access Framework (SAF)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }

                    Button(
                        onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                        enabled = connectionState is ConnectionState.Connected,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("select_file_button")
                    ) {
                        Text(
                            text = "Select File",
                            color = Color(0xFF090D14),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (connectionState !is ConnectionState.Connected) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Connect to your Windows companion to send files.",
                        style = MaterialTheme.typography.labelMedium,
                        color = AmberWarning
                    )
                }
            }
        }

        // Active transfers section
        item {
            Text(
                text = "Active & Recent Queue (${activeTransfers.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }

        if (activeTransfers.isEmpty()) {
            item {
                GlassCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No active transfers",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Incoming files from Windows or outgoing files selected via SAF will show live chunk progress here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 24.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(activeTransfers.values.toList()) { transfer ->
                ActiveTransferCard(
                    transfer = transfer,
                    onCancel = { viewModel.cancelTransfer(transfer.transferId) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun ActiveTransferCard(
    transfer: ActiveTransfer,
    onCancel: () -> Unit
) {
    val isOutgoing = transfer.direction == "OUTGOING"
    val isFinished = transfer.status == TransferStatus.COMPLETED ||
            transfer.status == TransferStatus.FAILED ||
            transfer.status == TransferStatus.CANCELLED

    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isOutgoing) ElectricCyan.copy(alpha = 0.15f) else EmeraldSuccess.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isOutgoing) Icons.Default.Upload else Icons.Default.Download,
                        contentDescription = null,
                        tint = if (isOutgoing) ElectricCyan else EmeraldSuccess,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = transfer.fileName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = "${formatFileSize(transfer.transferredBytes)} of ${formatFileSize(transfer.totalBytes)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
            }

            if (!isFinished) {
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cancel Transfer",
                        tint = RoseDanger,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                val statusColor = when (transfer.status) {
                    TransferStatus.COMPLETED -> EmeraldSuccess
                    TransferStatus.FAILED -> RoseDanger
                    TransferStatus.CANCELLED -> AmberWarning
                    else -> TextSecondary
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = transfer.status.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Progress bar
        LinearProgressIndicator(
            progress = { transfer.progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = if (isOutgoing) ElectricCyan else EmeraldSuccess,
            trackColor = Charcoal800
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = when (transfer.status) {
                    TransferStatus.PENDING_APPROVAL -> "Awaiting peer acceptance..."
                    TransferStatus.TRANSFERRING -> "Streaming binary chunks..."
                    TransferStatus.VERIFYING -> "Verifying SHA-256 digest..."
                    TransferStatus.COMPLETED -> "Saved successfully"
                    TransferStatus.FAILED -> transfer.errorMessage ?: "Transfer failed"
                    TransferStatus.CANCELLED -> "Cancelled"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (transfer.status == TransferStatus.FAILED) RoseDanger else TextSecondary
            )

            Text(
                text = "${(transfer.progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
    }
}
