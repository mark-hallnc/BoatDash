package com.example.boatapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.example.boatapp.ui.theme.BoatAppTheme
import kotlinx.coroutines.delay
import kotlin.random.Random
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.sin
import java.io.IOException
import java.util.UUID

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothManager: BluetoothManager
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission results
        if (permissions.all { it.value }) {
            // All permissions granted, start Bluetooth discovery
            bluetoothManager.startDiscovery()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize Bluetooth Manager
        bluetoothManager = BluetoothManager(this)
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        
        // Request permissions
        requestBluetoothPermissions()
        
        setContent {
            BoatAppTheme {
                BoatDashboard(
                    bluetoothManager = bluetoothManager,
                    onConnectBluetooth = { device ->
                        // Connect to selected device
                        // This will be handled in the composable
                    }
                )
            }
        }
    }
    
    private fun requestBluetoothPermissions() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        requestPermissionLauncher.launch(permissions)
    }
}

@Composable
fun BoatDashboard(
    bluetoothManager: BluetoothManager,
    onConnectBluetooth: (BluetoothDevice) -> Unit = {}
) {
    var fuelConsumption by remember { mutableStateOf(0f) }
    var fuelLevel by remember { mutableStateOf(85f) }
    var trimPosition by remember { mutableStateOf(2) }
    var bilgeWaterLevel by remember { mutableStateOf("Normal") }
    var speed by remember { mutableStateOf(0f) }
    var rpms by remember { mutableStateOf(800) }
    var oilPressure by remember { mutableStateOf(15f) }
    var isBluetoothConnected by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            
            rpms = when {
                Random.nextFloat() < 0.1f -> 800
                Random.nextFloat() < 0.3f -> Random.nextInt(1200, 2000)
                Random.nextFloat() < 0.6f -> Random.nextInt(2000, 3500)
                else -> Random.nextInt(3500, 4200)
            }
            
            speed = (rpms * 0.01f) + Random.nextFloat() * 5f - 2.5f
            speed = speed.coerceIn(0f, 45f)
            
            oilPressure = (rpms * 0.008f) + Random.nextFloat() * 8f - 4f
            oilPressure = oilPressure.coerceIn(10f, 60f)
            
            val fuelBurnRate = (rpms * 0.0001f) + Random.nextFloat() * 0.5f
            fuelConsumption += fuelBurnRate
            
            fuelLevel -= Random.nextFloat() * 0.02f
            fuelLevel = fuelLevel.coerceIn(5f, 100f)
            
            if (Random.nextFloat() < 0.05f) {
                trimPosition = Random.nextInt(1, 6)
            }
            
            if (Random.nextFloat() < 0.02f) {
                bilgeWaterLevel = "High"
            } else if (Random.nextFloat() < 0.1f) {
                bilgeWaterLevel = "Normal"
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a2e))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            // Header with Bluetooth indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BOAT SYSTEMS MONITOR",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                
                // Bluetooth connection indicator
                BluetoothConnectionIndicator(
                    isConnected = isBluetoothConnected,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Prominent Speed Display with weight
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.2f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF16213e)),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "SPEED",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "${speed.toInt()}",
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE91E63)
                        )
                        Text(
                            text = " MPH",
                            fontSize = 20.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = (speed / 50f).coerceIn(0f, 1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFFE91E63),
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(2f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DashboardGauge(
                        title = "FUEL CONSUMPTION",
                        value = "${fuelConsumption.toInt()}",
                        unit = "GPH",
                        color = Color(0xFF4CAF50),
                        maxValue = 20f,
                        currentValue = fuelConsumption,
                        modifier = Modifier.weight(1f)
                    )
                    DashboardGauge(
                        title = "FUEL LEVEL",
                        value = "${fuelLevel.toInt()}",
                        unit = "%",
                        color = Color(0xFFFF9800),
                        maxValue = 100f,
                        currentValue = fuelLevel,
                        modifier = Modifier.weight(1f)
                    )
                    TrimGauge(
                        title = "TRIM POSITION",
                        trimPosition = trimPosition,
                        modifier = Modifier.weight(1f)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DashboardGauge(
                        title = "RPM",
                        value = "$rpms",
                        unit = "",
                        color = Color(0xFF9C27B0),
                        maxValue = 5000f,
                        currentValue = rpms.toFloat(),
                        modifier = Modifier.weight(1f)
                    )
                    DashboardGauge(
                        title = "OIL PRESSURE",
                        value = "${oilPressure.toInt()}",
                        unit = "PSI",
                        color = Color(0xFFFF5722),
                        maxValue = 80f,
                        currentValue = oilPressure,
                        modifier = Modifier.weight(1f)
                    )
                    BilgeStatusCard(
                        title = "BILGE WATER LEVEL",
                        status = bilgeWaterLevel,
                        isHigh = bilgeWaterLevel == "High",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardGauge(
    title: String,
    value: String,
    unit: String,
    color: Color,
    maxValue: Float,
    currentValue: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16213e)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = value,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                if (unit.isNotEmpty()) {
                    Text(
                        text = " $unit",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = (currentValue / maxValue).coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = color,
                trackColor = Color.White.copy(alpha = 0.2f)
            )
        }
    }
}

@Composable
fun BilgeStatusCard(
    title: String,
    status: String,
    isHigh: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isHigh) Color(0xFFD32F2F) else Color(0xFF16213e)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = status,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = if (isHigh) Color.White else Color(0xFF4CAF50),
                textAlign = TextAlign.Center
            )
            if (isHigh) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "WARNING!",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun TrimGauge(
    title: String,
    trimPosition: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16213e)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Vertical gauge with needle
            Box(
                modifier = Modifier
                    .size(80.dp, 60.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val centerX = size.width / 2
                    val centerY = size.height / 2
                    val gaugeRadius = minOf(size.width, size.height) * 0.35f

                    // Draw gauge arc (vertical)
                    drawArc(
                        color = Color.White.copy(alpha = 0.3f),
                        startAngle = -90f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(centerX - gaugeRadius, centerY - gaugeRadius),
                        size = androidx.compose.ui.geometry.Size(gaugeRadius * 2, gaugeRadius * 2),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Draw trim position markers
                    for (i in 1..5) {
                        val angle = -90f + (i - 1) * 45f // Spread markers evenly
                        val markerRadius = gaugeRadius + 8.dp.toPx()
                        val markerX = centerX + cos(Math.toRadians(angle.toDouble())).toFloat() * markerRadius
                        val markerY = centerY + sin(Math.toRadians(angle.toDouble())).toFloat() * markerRadius

                        drawCircle(
                            color = if (i == trimPosition) Color(0xFF2196F3) else Color.White.copy(alpha = 0.5f),
                            radius = 3.dp.toPx(),
                            center = Offset(markerX, markerY)
                        )
                    }

                    // Draw needle
                    val needleAngle = -90f + (trimPosition - 1) * 45f
                    rotate(needleAngle, Offset(centerX, centerY)) {
                        // Needle shaft
                        drawLine(
                            color = Color(0xFF2196F3),
                            start = Offset(centerX, centerY - gaugeRadius + 8.dp.toPx()),
                            end = Offset(centerX, centerY + 8.dp.toPx()),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        // Needle tip
                        drawLine(
                            color = Color(0xFF2196F3),
                            start = Offset(centerX, centerY - gaugeRadius + 8.dp.toPx()),
                            end = Offset(centerX, centerY - gaugeRadius - 4.dp.toPx()),
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Trim position labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "DOWN",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "UP",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Current position indicator
            Text(
                text = "Position $trimPosition",
                fontSize = 12.sp,
                color = Color(0xFF2196F3),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun BluetoothConnectionIndicator(
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.size(48.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) Color(0xFF4CAF50) else Color(0xFF666666)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "BT",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Preview(
    showBackground = true,
    widthDp = 1280,
    heightDp = 800,
    name = "Landscape",
    device =
        "spec:width=1280,height=800,unit=dp,orientation=landscape"
)
@Composable
fun BoatDashboardPreview() {
    BoatAppTheme {
        BoatDashboard(bluetoothManager = BluetoothManager(LocalContext.current))
    }
}

