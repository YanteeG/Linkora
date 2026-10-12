package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.network.ConnectionState
import com.example.protocol.JsonCodec
import com.example.protocol.PairingPayload
import com.example.ui.MainViewModel
import com.example.ui.components.GlassCard
import com.example.ui.components.VerificationCodeDialog
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CardBorder
import com.example.ui.theme.Charcoal800
import com.example.ui.theme.Charcoal900
import com.example.ui.theme.Charcoal950
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseDanger
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val connectionState by viewModel.connectionState.collectAsState()
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: QR Scan, 1: Manual

    // Staged payload
    var scannedPayload by remember { mutableStateOf<PairingPayload?>(null) }
    var payloadValidationError by remember { mutableStateOf<String?>(null) }

    // Manual form state
    var manualJsonText by remember { mutableStateOf("") }
    var manualHost by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("43127") }
    var manualCertFingerprint by remember { mutableStateOf("") }
    var manualToken by remember { mutableStateOf("") }

    // If successfully connected, automatically pop back to dashboard
    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Connected) {
            onNavigateBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Charcoal950)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Pair Windows PC",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_button")) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Charcoal950)
        )

        // Security notice banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Charcoal900)
                .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Local Wi-Fi TLS handshake. Linkora never uses cloud relays or unencrypted endpoints.",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Charcoal950,
            contentColor = ElectricCyan,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = ElectricCyan
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("QR Scanner", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Manual Entry", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Active handshake modal
        if (connectionState is ConnectionState.Handshaking) {
            val handshaking = connectionState as ConnectionState.Handshaking
            VerificationCodeDialog(
                code = handshaking.verificationCode ?: "------",
                onConfirm = { viewModel.confirmPairingCode() },
                onCancel = { viewModel.disconnect() }
            )
        }

        // Error banner if any
        if (connectionState is ConnectionState.Error) {
            val error = (connectionState as ConnectionState.Error).message
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(RoseDanger.copy(alpha = 0.15f))
                    .border(1.dp, RoseDanger, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = RoseDanger, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = error, style = MaterialTheme.typography.bodyMedium, color = RoseDanger)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (selectedTab == 0) {
                // QR Scanner View
                QrScannerSection(
                    onPayloadScanned = { rawJson ->
                        val payload = JsonCodec.parsePairingPayload(rawJson)
                        if (payload != null && payload.isValid()) {
                            scannedPayload = payload
                            payloadValidationError = null
                        } else {
                            payloadValidationError = "Invalid QR code format or unsupported protocol version."
                        }
                    }
                )

                if (payloadValidationError != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = payloadValidationError ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RoseDanger,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (scannedPayload != null) {
                    val p = scannedPayload!!
                    Spacer(modifier = Modifier.height(16.dp))
                    TargetPcPreviewCard(
                        payload = p,
                        isConnecting = connectionState is ConnectionState.Connecting || connectionState is ConnectionState.Handshaking,
                        onConnectClick = {
                            viewModel.pairWithPayload(p)
                        }
                    )
                }
            } else {
                // Manual Entry View
                ManualEntrySection(
                    manualJsonText = manualJsonText,
                    onManualJsonChange = { manualJsonText = it },
                    manualHost = manualHost,
                    onHostChange = { manualHost = it },
                    manualPort = manualPort,
                    onPortChange = { manualPort = it },
                    manualCertFingerprint = manualCertFingerprint,
                    onCertChange = { manualCertFingerprint = it },
                    manualToken = manualToken,
                    onTokenChange = { manualToken = it },
                    isConnecting = connectionState is ConnectionState.Connecting || connectionState is ConnectionState.Handshaking,
                    onSubmitJson = { json ->
                        val payload = JsonCodec.parsePairingPayload(json)
                        if (payload != null && payload.isValid()) {
                            viewModel.pairWithPayload(payload)
                        } else {
                            viewModel.emitToast("Invalid pairing payload JSON")
                        }
                    },
                    onSubmitFields = { host, port, cert, token ->
                        val payload = PairingPayload(
                            protocolVersion = 1,
                            host = host.trim(),
                            port = port.toIntOrNull() ?: 43127,
                            certificateSha256 = cert.trim(),
                            pairingToken = token.trim(),
                            expiresAt = "N/A"
                        )
                        if (payload.isValid()) {
                            viewModel.pairWithPayload(payload)
                        } else {
                            viewModel.emitToast("Invalid host, certificate fingerprint, or token")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun QrScannerSection(
    onPayloadScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    if (!hasCameraPermission) {
        GlassCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Camera Permission Required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Grant camera access to scan the pairing QR code displayed in Linkora on Windows 11.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { launcher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("grant_camera_permission_button")
                ) {
                    Text("Grant Camera Access", color = Color(0xFF090D14), fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        // Camera preview box with scanner guide overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .border(2.dp, ElectricCyan.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
        ) {
            val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
            val analyzer = remember {
                QrCodeAnalyzer { result ->
                    onPayloadScanned(result)
                }
            }

            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                it.setAnalyzer(cameraExecutor, analyzer)
                            }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            // Camera bind error
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Scanning reticle overlay
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .align(Alignment.Center)
                    .border(2.dp, ElectricCyan, RoundedCornerShape(16.dp))
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Point camera at the QR code in Linkora Windows 11",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun TargetPcPreviewCard(
    payload: PairingPayload,
    isConnecting: Boolean,
    onConnectClick: () -> Unit
) {
    GlassCard(borderColor = ElectricCyan) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(ElectricCyan.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Computer, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Windows 11 Companion",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = "${payload.host}:${payload.port}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ElectricCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Charcoal800)
                .padding(8.dp)
        ) {
            Column {
                Text(
                    text = "TLS Certificate SHA-256:",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Text(
                    text = payload.certificateSha256,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TextPrimary,
                    maxLines = 2
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onConnectClick,
            enabled = !isConnecting,
            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("connect_scanned_pc_button")
        ) {
            if (isConnecting) {
                CircularProgressIndicator(strokeWidth = 2.dp, color = Color(0xFF090D14), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connecting...", color = Color(0xFF090D14), fontWeight = FontWeight.Bold)
            } else {
                Text("Connect & Authenticate", color = Color(0xFF090D14), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ManualEntrySection(
    manualJsonText: String,
    onManualJsonChange: (String) -> Unit,
    manualHost: String,
    onHostChange: (String) -> Unit,
    manualPort: String,
    onPortChange: (String) -> Unit,
    manualCertFingerprint: String,
    onCertChange: (String) -> Unit,
    manualToken: String,
    onTokenChange: (String) -> Unit,
    isConnecting: Boolean,
    onSubmitJson: (String) -> Unit,
    onSubmitFields: (String, String, String, String) -> Unit
) {
    var entryMode by remember { mutableIntStateOf(0) } // 0: Raw JSON, 1: Form fields

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { entryMode = 0 },
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (entryMode == 0) Charcoal800 else Color.Transparent
                ),
                border = BorderStroke(1.dp, if (entryMode == 0) ElectricCyan else CardBorder),
                modifier = Modifier.weight(1f)
            ) {
                Text("Paste JSON Payload", color = if (entryMode == 0) ElectricCyan else TextSecondary, fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = { entryMode = 1 },
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (entryMode == 1) Charcoal800 else Color.Transparent
                ),
                border = BorderStroke(1.dp, if (entryMode == 1) ElectricCyan else CardBorder),
                modifier = Modifier.weight(1f)
            ) {
                Text("Manual Host Form", color = if (entryMode == 1) ElectricCyan else TextSecondary, fontSize = 12.sp)
            }
        }

        if (entryMode == 0) {
            GlassCard {
                Text(
                    text = "Paste Pairing Payload JSON:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Copy the payload from the Windows 11 companion app:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = manualJsonText,
                    onValueChange = onManualJsonChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .testTag("manual_json_input"),
                    placeholder = {
                        Text(
                            "{\n  \"protocolVersion\": 1,\n  \"host\": \"192.168.1.100\",\n  \"port\": 43127,\n  \"certificateSha256\": \"...\",\n  \"pairingToken\": \"...\"\n}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TextSecondary.copy(alpha = 0.5f)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { onSubmitJson(manualJsonText) },
                    enabled = !isConnecting && manualJsonText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("submit_manual_json_button")
                ) {
                    Text("Parse & Connect", color = Color(0xFF090D14), fontWeight = FontWeight.Bold)
                }
            }
        } else {
            GlassCard {
                Text(
                    text = "LAN Connection Parameters",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = manualHost,
                    onValueChange = onHostChange,
                    label = { Text("Windows PC LAN IP (e.g. 192.168.1.50)") },
                    modifier = Modifier.fillMaxWidth().testTag("host_input"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = manualPort,
                    onValueChange = onPortChange,
                    label = { Text("Port (default 43127)") },
                    modifier = Modifier.fillMaxWidth().testTag("port_input"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = manualCertFingerprint,
                    onValueChange = onCertChange,
                    label = { Text("Certificate SHA-256 Fingerprint (64 hex)") },
                    modifier = Modifier.fillMaxWidth().testTag("cert_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = manualToken,
                    onValueChange = onTokenChange,
                    label = { Text("One-time Pairing Token") },
                    modifier = Modifier.fillMaxWidth().testTag("token_input"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        onSubmitFields(manualHost, manualPort, manualCertFingerprint, manualToken)
                    },
                    enabled = !isConnecting && manualHost.isNotBlank() && manualCertFingerprint.isNotBlank() && manualToken.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("submit_manual_form_button")
                ) {
                    Text("Initiate TLS Handshake", color = Color(0xFF090D14), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
