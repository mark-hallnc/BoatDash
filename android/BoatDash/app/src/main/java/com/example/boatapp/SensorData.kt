package com.example.boatapp

data class SensorData(
    val fuelConsumption: Float = 0f,
    val fuelLevel: Float = 85f,
    val trimPosition: Int = 2,
    val bilgeWaterLevel: String = "Normal",
    val speed: Float = 0f,
    val rpms: Int = 800,
    val oilPressure: Float = 15f,
    val battery1Voltage: Float = 0f,
    val battery2Voltage: Float = 0f
)
