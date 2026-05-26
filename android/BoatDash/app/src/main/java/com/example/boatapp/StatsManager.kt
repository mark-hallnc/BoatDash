package com.example.boatapp

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

class StatsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("boat_stats", Context.MODE_PRIVATE)
    
    private val _engineHours = MutableStateFlow(prefs.getFloat("engine_hours", 0f))
    val engineHours: StateFlow<Float> = _engineHours.asStateFlow()
    
    private val _odometerMiles = MutableStateFlow(prefs.getFloat("odometer_miles", 0f))
    val odometerMiles: StateFlow<Float> = _odometerMiles.asStateFlow()

    private var lastLocation: Location? = null
    private var lastRunningTimestamp: Long = 0

    fun updateEngineHours(isEngineRunning: Boolean) {
        val now = System.currentTimeMillis()
        if (isEngineRunning) {
            if (lastRunningTimestamp != 0L) {
                val elapsedMillis = now - lastRunningTimestamp
                val elapsedHours = elapsedMillis.toFloat() / TimeUnit.HOURS.toMillis(1).toFloat()
                if (elapsedHours > 0) {
                    val newHours = _engineHours.value + elapsedHours
                    _engineHours.value = newHours
                    // Save periodically (e.g., every 0.01 hours ~ 36 seconds)
                    if (newHours - prefs.getFloat("engine_hours", 0f) >= 0.01f) {
                        prefs.edit().putFloat("engine_hours", newHours).apply()
                    }
                }
            }
            lastRunningTimestamp = now
        } else {
            if (lastRunningTimestamp != 0L) {
                // Final save when engine stops
                prefs.edit().putFloat("engine_hours", _engineHours.value).apply()
            }
            lastRunningTimestamp = 0
        }
    }

    fun updateOdometer(newLocation: Location) {
        if (!newLocation.hasAccuracy() || newLocation.accuracy > 50) return
        
        val lastLoc = lastLocation
        if (lastLoc != null) {
            val distanceMeters = lastLoc.distanceTo(newLocation)
            val distanceMiles = distanceMeters * 0.000621371f
            
            // Reliability filters
            val speedMps = if (newLocation.hasSpeed()) newLocation.speed else distanceMeters / ((newLocation.time - lastLoc.time) / 1000f)
            val speedMph = speedMps * 2.23694f
            
            // 1. Ignore jumps that imply unrealistic speed (> 80 MPH)
            // 2. Ignore tiny jitter when speed is basically 0
            if (speedMph in 0.5f..80f && distanceMiles > 0.0001f) {
                val newMiles = _odometerMiles.value + distanceMiles
                _odometerMiles.value = newMiles
                
                // Save periodically (every 0.1 miles)
                if (newMiles - prefs.getFloat("odometer_miles", 0f) >= 0.1f) {
                    prefs.edit().putFloat("odometer_miles", newMiles).apply()
                }
            }
        }
        lastLocation = newLocation
    }

    fun resetEngineHours() {
        prefs.edit().putFloat("engine_hours", 0f).apply()
        _engineHours.value = 0f
        lastRunningTimestamp = 0
    }

    fun resetOdometer() {
        prefs.edit().putFloat("odometer_miles", 0f).apply()
        _odometerMiles.value = 0f
        lastLocation = null
    }

    fun saveAll() {
        prefs.edit()
            .putFloat("engine_hours", _engineHours.value)
            .putFloat("odometer_miles", _odometerMiles.value)
            .apply()
    }
}
