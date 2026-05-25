package com.example.boatapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

data class SensorData(
    val fuelConsumption: Float = 0f,
    val fuelLevel: Float = 85f,
    val trimPosition: Int = 2,
    val bilgeWaterLevel: String = "Normal",
    val speed: Float = 0f,
    val rpms: Int = 800,
    val oilPressure: Float = 15f
)

class BluetoothManager(private val context: Context) {
    
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    
    // State flows for UI updates
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    private val _availableDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val availableDevices: StateFlow<List<BluetoothDevice>> = _availableDevices.asStateFlow()
    
    private val _receivedData = MutableStateFlow("")
    val receivedData: StateFlow<String> = _receivedData.asStateFlow()
    
    private val _sensorData = MutableStateFlow(SensorData())
    val sensorData: StateFlow<SensorData> = _sensorData.asStateFlow()
    
    // ESP32 Bluetooth UUID (standard Serial Port Profile)
    private val ESP32_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    fun startDiscovery() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        
        _isScanning.value = true
        _availableDevices.value = emptyList()
        
        bluetoothAdapter?.startDiscovery()
        
        // Stop discovery after 12 seconds
        Thread {
            Thread.sleep(12000)
            stopDiscovery()
        }.start()
    }
    
    fun stopDiscovery() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        
        bluetoothAdapter?.cancelDiscovery()
        _isScanning.value = false
    }
    
    suspend fun connectToDevice(device: BluetoothDevice): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return@withContext false
                }
                
                bluetoothSocket = device.createRfcommSocketToServiceRecord(ESP32_UUID)
                bluetoothSocket?.connect()
                
                inputStream = bluetoothSocket?.inputStream
                outputStream = bluetoothSocket?.outputStream
                
                _isConnected.value = true
                startDataListener()
                true
            } catch (e: IOException) {
                e.printStackTrace()
                _isConnected.value = false
                false
            }
        }
    }
    
    private fun startDataListener() {
        Thread {
            val buffer = ByteArray(1024)
            while (_isConnected.value) {
                try {
                    val bytes = inputStream?.read(buffer)
                    if (bytes != null && bytes > 0) {
                        val data = String(buffer, 0, bytes)
                        _receivedData.value = data
                        parseSensorData(data)
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    _isConnected.value = false
                    break
                }
            }
        }.start()
    }
    
    private fun parseSensorData(data: String) {
        try {
            // Expected format from ESP32: "FUEL:12.5,LEVEL:75,TRIM:3,SPEED:25,RPM:3200,OIL:45,BILGE:NORMAL"
            val pairs = data.split(",")
            val newSensorData = _sensorData.value.copy()
            
            pairs.forEach { pair ->
                val keyValue = pair.split(":")
                if (keyValue.size == 2) {
                    val key = keyValue[0].trim()
                    val value = keyValue[1].trim()
                    
                    when (key.uppercase()) {
                        "FUEL" -> newSensorData.copy(fuelConsumption = value.toFloatOrNull() ?: 0f)
                        "LEVEL" -> newSensorData.copy(fuelLevel = value.toFloatOrNull() ?: 85f)
                        "TRIM" -> newSensorData.copy(trimPosition = value.toIntOrNull() ?: 2)
                        "SPEED" -> newSensorData.copy(speed = value.toFloatOrNull() ?: 0f)
                        "RPM" -> newSensorData.copy(rpms = value.toIntOrNull() ?: 800)
                        "OIL" -> newSensorData.copy(oilPressure = value.toFloatOrNull() ?: 15f)
                        "BILGE" -> newSensorData.copy(bilgeWaterLevel = value.uppercase())
                    }.let { _sensorData.value = it }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    suspend fun sendData(data: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                outputStream?.write(data.toByteArray())
                true
            } catch (e: IOException) {
                e.printStackTrace()
                false
            }
        }
    }
    
    fun disconnect() {
        try {
            inputStream?.close()
            outputStream?.close()
            bluetoothSocket?.close()
            _isConnected.value = false
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
    
    fun addDiscoveredDevice(device: BluetoothDevice) {
        val currentDevices = _availableDevices.value.toMutableList()
        if (!currentDevices.contains(device)) {
            currentDevices.add(device)
            _availableDevices.value = currentDevices
        }
    }
} 