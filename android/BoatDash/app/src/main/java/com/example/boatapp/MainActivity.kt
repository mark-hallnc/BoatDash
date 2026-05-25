package com.example.boatapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.example.boatapp.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
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

class MainActivity : ComponentActivity(), LocationListener {
    private lateinit var bluetoothManager: BluetoothManager
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var locationManager: LocationManager? = null
    
    private val _locationState = mutableStateOf<Location?>(null)
    private val _locationPermissionGranted = mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val btGranted = permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
        val locGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        
        _locationPermissionGranted.value = locGranted
        
        if (btGranted) {
            bluetoothManager.startDiscovery()
        }
        
        if (locGranted) {
            startLocationUpdates()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize Managers
        bluetoothManager = BluetoothManager(this)
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        // Request permissions
        requestPermissions()
        
        setContent {
            BoatAppTheme {
                BoatDashboard(
                    bluetoothManager = bluetoothManager,
                    location = _locationState.value,
                    locationPermissionGranted = _locationPermissionGranted.value
                )
            }
        }
    }
    
    private fun requestPermissions() {
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

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000L,
                5f,
                this
            )
            // Also try to get last known location
            val lastKnown = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (lastKnown != null) {
                _locationState.value = lastKnown
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        _locationState.value = location
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    override fun onDestroy() {
        super.onDestroy()
        locationManager?.removeUpdates(this)
    }
}

@Composable
fun BoatDashboard(
    bluetoothManager: BluetoothManager,
    location: Location? = null,
    locationPermissionGranted: Boolean = false
) {
    val sensorData by bluetoothManager.sensorData.collectAsState()
    val isConnected by bluetoothManager.isConnected.collectAsState()
    val isScanning by bluetoothManager.isScanning.collectAsState()

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            DashboardHeader(
                isConnected = isConnected,
                isScanning = isScanning,
                location = location,
                locationPermissionGranted = locationPermissionGranted
            )
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = if (isTablet) GridCells.Fixed(3) else GridCells.Fixed(1),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Prominent Speed Card
            item(span = { GridItemSpan(if (isTablet) 2 else 1) }) {
                ProminentStatCard(
                    title = "SPEED",
                    value = String.format("%.1f", sensorData.speed),
                    unit = "MPH",
                    icon = Icons.Default.Speed,
                    accentColor = MaterialTheme.colorScheme.primary,
                    progress = (sensorData.speed / 50f).coerceIn(0f, 1f)
                )
            }

            // Prominent RPM Card
            item(span = { GridItemSpan(1) }) {
                ProminentStatCard(
                    title = "RPM",
                    value = "${sensorData.rpms}",
                    unit = "",
                    icon = Icons.Default.Cyclone,
                    accentColor = MaterialTheme.colorScheme.secondary,
                    progress = (sensorData.rpms / 5000f).coerceIn(0f, 1f)
                )
            }

            // Fuel Level Card
            item {
                StatCard(
                    title = "FUEL LEVEL",
                    value = "${sensorData.fuelLevel.toInt()}",
                    unit = "%",
                    icon = Icons.Default.LocalGasStation,
                    accentColor = if (sensorData.fuelLevel < 20f) WarningOrange else MaterialTheme.colorScheme.tertiary,
                    showProgress = true,
                    progress = sensorData.fuelLevel / 100f
                )
            }

            // Fuel Consumption Card
            item {
                StatCard(
                    title = "FUEL RATE",
                    value = String.format("%.1f", sensorData.fuelConsumption),
                    unit = "GPH",
                    icon = Icons.Default.PropaneTank,
                    accentColor = MaterialTheme.colorScheme.secondary
                )
            }

            // Oil Pressure Card
            item {
                StatCard(
                    title = "OIL PRESSURE",
                    value = "${sensorData.oilPressure.toInt()}",
                    unit = "PSI",
                    icon = Icons.Default.OilBarrel,
                    accentColor = if (sensorData.oilPressure < 20f) WarningRed else SuccessGreen
                )
            }

            // Trim Position Card
            item {
                TrimCard(
                    trimPosition = sensorData.trimPosition,
                    accentColor = MaterialTheme.colorScheme.primary
                )
            }

            // Bilge Status Card
            item {
                BilgeCard(
                    status = sensorData.bilgeWaterLevel,
                    isHigh = sensorData.bilgeWaterLevel == "High"
                )
            }
        }
    }
}

@Composable
fun DashboardHeader(
    isConnected: Boolean,
    isScanning: Boolean,
    location: Location?,
    locationPermissionGranted: Boolean
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.shadow(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) SuccessGreen else WarningRed)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "BoatDash",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "Live boat telemetry",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                BluetoothStatusPill(isConnected = isConnected, isScanning = isScanning)
                Spacer(modifier = Modifier.height(8.dp))
                LocationDisplay(location = location, permissionGranted = locationPermissionGranted)
            }
        }
    }
}

@Composable
fun BluetoothStatusPill(isConnected: Boolean, isScanning: Boolean) {
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isConnected -> SuccessGreen.copy(alpha = 0.1f)
            isScanning -> WarningOrange.copy(alpha = 0.1f)
            else -> WarningRed.copy(alpha = 0.1f)
        }, label = "bg"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            isConnected -> SuccessGreen
            isScanning -> WarningOrange
            else -> WarningRed
        }, label = "content"
    )

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.BluetoothDisabled,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = when {
                    isConnected -> "Connected"
                    isScanning -> "Scanning..."
                    else -> "Disconnected"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

@Composable
fun LocationDisplay(location: Location?, permissionGranted: Boolean) {
    val text = when {
        !permissionGranted -> "Location permission needed"
        location == null -> "GPS searching..."
        else -> String.format("Location: %.4f, %.4f", location.latitude, location.longitude)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
fun ProminentStatCard(
    title: String,
    value: String,
    unit: String,
    icon: ImageVector,
    accentColor: Color,
    progress: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor.copy(alpha = 0.6f)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Black,
                    color = accentColor,
                    fontSize = 64.sp
                )
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = accentColor,
                trackColor = accentColor.copy(alpha = 0.1f)
            )
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    unit: String,
    icon: ImageVector,
    accentColor: Color,
    showProgress: Boolean = false,
    progress: Float = 0f
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                    )
                }
            }
            
            if (showProgress) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = accentColor,
                    trackColor = accentColor.copy(alpha = 0.1f)
                )
            }
        }
    }
}

@Composable
fun TrimCard(trimPosition: Int, accentColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "TRIM POSITION",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Position $trimPosition",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            // Simple visual representation of trim
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (i in 1..5) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (i <= trimPosition) accentColor else accentColor.copy(alpha = 0.1f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun BilgeCard(status: String, isHigh: Boolean) {
    val backgroundColor = if (isHigh) WarningRed.copy(alpha = 0.1f) else SuccessGreen.copy(alpha = 0.05f)
    val contentColor = if (isHigh) WarningRed else SuccessGreen

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
        border = if (isHigh) BorderStroke(1.dp, WarningRed.copy(alpha = 0.5f)) else null
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "BILGE WATER",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(12.dp),
                    shape = CircleShape,
                    color = contentColor
                ) {}
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = status.uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = contentColor
                )
            }
            if (isHigh) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "DANGER: HIGH WATER",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = WarningRed
                )
            }
        }
    }
}

@Preview(
    showBackground = true,
    widthDp = 1280,
    heightDp = 800,
    name = "Landscape Tablet",
    device = "spec:width=1280,height=800,unit=dp,orientation=landscape"
)
@Composable
fun BoatDashboardPreview() {
    BoatAppTheme {
        BoatDashboard(bluetoothManager = BluetoothManager(LocalContext.current))
    }
}


