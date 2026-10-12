package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.ConnectionState
import com.example.protocol.FileOfferMessage
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CardBorder
import com.example.ui.theme.Charcoal800
import com.example.ui.theme.Charcoal900
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseDanger
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = CardBorder,
    backgroundColor: Color = Charcoal900,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
fun ConnectionStatusBar(
    state: ConnectionState,
    onPairClick: () -> Unit,
    onReconnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier,
        borderColor = when (state) {
            is ConnectionState.Connected -> EmeraldSuccess.copy(alpha = 0.5f)
            is ConnectionState.Connecting, is ConnectionState.Handshaking -> ElectricCyan.copy(alpha = 0.5f)
            is ConnectionState.Error -> RoseDanger.copy(alpha = 0.5f)
            is ConnectionState.Disconnected -> CardBorder
        }
    ) {
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
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            when (state) {
                                is ConnectionState.Connected -> EmeraldSuccess.copy(alpha = 0.15f)
                                is ConnectionState.Connecting, is ConnectionState.Handshaking -> ElectricCyan.copy(alpha = 0.15f)
                                is ConnectionState.Error -> RoseDanger.copy(alpha = 0.15f)
                                is ConnectionState.Disconnected -> Charcoal800
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when (state) {
                        is ConnectionState.Connected -> Icon(
                            Icons.Default.Computer,
                            contentDescription = "Connected",
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(22.dp)
                        )
                        is ConnectionState.Connecting, is ConnectionState.Handshaking -> CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        is ConnectionState.Error -> Icon(
                            Icons.Default.Error,
                            contentDescription = "Error",
                            tint = RoseDanger,
                            modifier = Modifier.size(22.dp)
                        )
                        is ConnectionState.Disconnected -> Icon(
                            Icons.Default.WifiOff,
                            contentDescription = "Disconnected",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    val statusTitle = when (state) {
                        is ConnectionState.Connected -> state.pcName
                        is ConnectionState.Connecting -> "Connecting to PC..."
                        is ConnectionState.Handshaking -> "Verifying pairing..."
                        is ConnectionState.Error -> "Connection failed"
                        is ConnectionState.Disconnected -> "Not connected"
                    }
                    val statusSubtitle = when (state) {
                        is ConnectionState.Connected -> "Local Wi-Fi TLS encrypted • ${state.host}:${state.port}"
                        is ConnectionState.Connecting -> "${state.host}:${state.port}"
                        is ConnectionState.Handshaking -> "Check verification code"
                        is ConnectionState.Error -> state.message
                        is ConnectionState.Disconnected -> "Companion PC not linked"
                    }

                    Text(
                        text = statusTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = statusSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            when (state) {
                is ConnectionState.Connected -> {
                    OutlinedButton(
                        onClick = onDisconnectClick,
                        border = BorderStroke(1.dp, Charcoal800),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("disconnect_button")
                    ) {
                        Text("Disconnect", color = TextSecondary, fontSize = 12.sp)
                    }
                }
                is ConnectionState.Disconnected, is ConnectionState.Error -> {
                    Button(
                        onClick = onReconnectClick,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("connect_button")
                    ) {
                        Text("Connect", color = Color(0xFF090D14), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
fun VerificationCodeDialog(
    code: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Charcoal900,
        shape = RoundedCornerShape(20.dp),
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Default.Sync,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Confirm Pairing Code",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Text(
                    text = "Verify this code matches the code displayed on your Windows 11 desktop application:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Charcoal800)
                        .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = code.ifBlank { "------" },
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        letterSpacing = 4.sp,
                        color = ElectricCyan
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Do not approve if the codes do not match.",
                    style = MaterialTheme.typography.labelMedium,
                    color = AmberWarning,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                modifier = Modifier.testTag("confirm_pair_code_button")
            ) {
                Text("Confirm Match", color = Color(0xFF090D14), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onCancel,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Text("Reject", color = TextSecondary)
            }
        }
    )
}

@Composable
fun IncomingFileOfferDialog(
    offer: FileOfferMessage,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onReject,
        containerColor = Charcoal900,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Incoming File Offer",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    text = "Your paired Windows PC wants to send a file:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Charcoal800)
                        .padding(14.dp)
                ) {
                    Column {
                        Text(
                            text = offer.fileName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val formattedSize = formatFileSize(offer.fileSize)
                        Text(
                            text = "Size: $formattedSize",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ElectricCyan
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "SHA-256: ${offer.sha256.take(16)}...",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "The file will be saved to your device Downloads upon checksum verification.",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                modifier = Modifier.testTag("accept_file_offer_button")
            ) {
                Text("Accept Transfer", color = Color(0xFF090D14), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onReject,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Text("Decline", color = TextSecondary)
            }
        }
    )
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 4)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return "%.1f %s".format(value, units[digitGroups])
}
