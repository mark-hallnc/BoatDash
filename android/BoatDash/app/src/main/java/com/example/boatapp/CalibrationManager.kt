package com.example.boatapp

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CalibrationSettings(
    val fuelConsumptionOffset: Float = 0f,
    val fuelLevelOffset: Float = 0f,
    val trimPositionOffset: Int = 0,
    val rpmsOffset: Int = 0,
    val oilPressureOffset: Float = 0f
)

class CalibrationManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("calibration_prefs", Context.MODE_PRIVATE)
    
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<CalibrationSettings> = _settings.asStateFlow()

    private fun loadSettings(): CalibrationSettings {
        return CalibrationSettings(
            fuelConsumptionOffset = prefs.getFloat("fuel_consumption_offset", 0f),
            fuelLevelOffset = prefs.getFloat("fuel_level_offset", 0f),
            trimPositionOffset = prefs.getInt("trim_position_offset", 0),
            rpmsOffset = prefs.getInt("rpm_offset", 0),
            oilPressureOffset = prefs.getFloat("oil_pressure_offset", 0f)
        )
    }

    fun updateFuelConsumptionOffset(offset: Float) {
        prefs.edit().putFloat("fuel_consumption_offset", offset).apply()
        _settings.value = _settings.value.copy(fuelConsumptionOffset = offset)
    }

    fun updateFuelLevelOffset(offset: Float) {
        prefs.edit().putFloat("fuel_level_offset", offset).apply()
        _settings.value = _settings.value.copy(fuelLevelOffset = offset)
    }

    fun updateTrimPositionOffset(offset: Int) {
        prefs.edit().putInt("trim_position_offset", offset).apply()
        _settings.value = _settings.value.copy(trimPositionOffset = offset)
    }

    fun updateRpmsOffset(offset: Int) {
        prefs.edit().putInt("rpm_offset", offset).apply()
        _settings.value = _settings.value.copy(rpmsOffset = offset)
    }

    fun updateOilPressureOffset(offset: Float) {
        prefs.edit().putFloat("oil_pressure_offset", offset).apply()
        _settings.value = _settings.value.copy(oilPressureOffset = offset)
    }

    fun resetAll() {
        prefs.edit().clear().apply()
        _settings.value = CalibrationSettings()
    }
    
    fun applyCalibration(raw: SensorData): SensorData {
        val s = _settings.value
        return raw.copy(
            fuelConsumption = (raw.fuelConsumption + s.fuelConsumptionOffset).coerceAtLeast(0f),
            fuelLevel = (raw.fuelLevel + s.fuelLevelOffset).coerceIn(0f, 100f),
            trimPosition = (raw.trimPosition + s.trimPositionOffset).coerceIn(0, 5), // Assuming 0-5 based on previous code
            rpms = (raw.rpms + s.rpmsOffset).coerceAtLeast(0),
            oilPressure = (raw.oilPressure + s.oilPressureOffset).coerceAtLeast(0f)
        )
    }
}
