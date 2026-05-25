/*
 * Boat Systems Monitor - ESP32 Arduino Code
 * 
 * This code reads various boat sensors and sends the data via Bluetooth
 * to the Android dashboard app in the format:
 * FUEL:12.5,LEVEL:75,TRIM:3,SPEED:25,RPM:3200,OIL:45,BILGE:NORMAL
 * 
 * Hardware Setup:
 * - Arduino Nano ESP32
 * - Fuel flow sensor (pulse output)
 * - Fuel level sender (analog)
 * - Trim position sender (analog)
 * - GPS module (UART)
 * - Tachometer signal (pulse input)
 * - Oil pressure sender (analog)
 * - Bilge float switch (digital)
 * 
 * Pin Assignments:
 * - Fuel Flow: GPIO 2 (pulse input)
 * - Fuel Level: GPIO 34 (analog input)
 * - Trim Position: GPIO 35 (analog input)
 * - GPS TX: GPIO 16 (UART2 RX)
 * - GPS RX: GPIO 17 (UART2 TX)
 * - Tachometer: GPIO 4 (pulse input)
 * - Oil Pressure: GPIO 36 (analog input)
 * - Bilge Switch: GPIO 5 (digital input)
 */

#include <BluetoothSerial.h>
#include <TinyGPS++.h>
#include <HardwareSerial.h>

// Check if Bluetooth is available
#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
#error Bluetooth is not enabled! Please run `make menuconfig` to enable it
#endif

// Initialize Bluetooth
BluetoothSerial SerialBT;

// Initialize GPS
TinyGPSPlus gps;
HardwareSerial GPSSerial(2); // Use UART2

// Pin definitions
#define FUEL_FLOW_PIN 2
#define FUEL_LEVEL_PIN 34
#define TRIM_POSITION_PIN 35
#define GPS_RX_PIN 16
#define GPS_TX_PIN 17
#define TACHOMETER_PIN 4
#define OIL_PRESSURE_PIN 36
#define BILGE_SWITCH_PIN 5

// Variables for sensor readings
volatile unsigned long fuelFlowPulses = 0;
volatile unsigned long tachometerPulses = 0;
unsigned long lastFuelFlowTime = 0;
unsigned long lastTachometerTime = 0;
float fuelConsumption = 0.0; // GPH
float fuelLevel = 85.0; // Percentage
int trimPosition = 2; // 1-5
float speed = 0.0; // MPH
int rpms = 800; // RPM
float oilPressure = 15.0; // PSI
String bilgeStatus = "Normal";

// Calibration constants
#define FUEL_FLOW_CALIBRATION 7.5 // Pulses per gallon
#define TACHOMETER_CALIBRATION 2.0 // Pulses per revolution
#define FUEL_LEVEL_EMPTY 200 // ADC value when empty
#define FUEL_LEVEL_FULL 4095 // ADC value when full
#define TRIM_POSITIONS 5 // Number of trim positions
#define OIL_PRESSURE_OFFSET 0.0 // PSI offset
#define OIL_PRESSURE_SCALE 0.1 // PSI per ADC unit

// Timing
unsigned long lastDataSend = 0;
const unsigned long DATA_SEND_INTERVAL = 1000; // Send data every 1 second

void setup() {
  // Initialize serial communication
  Serial.begin(115200);
  Serial.println("Boat Systems Monitor - ESP32 Starting...");
  
  // Initialize Bluetooth
  SerialBT.begin("BoatESP32");
  Serial.println("Bluetooth device 'BoatESP32' is ready to pair");
  
  // Initialize GPS
  GPSSerial.begin(9600, SERIAL_8N1, GPS_RX_PIN, GPS_TX_PIN);
  Serial.println("GPS initialized");
  
  // Initialize pins
  pinMode(FUEL_FLOW_PIN, INPUT_PULLUP);
  pinMode(TACHOMETER_PIN, INPUT_PULLUP);
  pinMode(BILGE_SWITCH_PIN, INPUT_PULLUP);
  
  // Attach interrupt handlers
  attachInterrupt(digitalPinToInterrupt(FUEL_FLOW_PIN), fuelFlowISR, FALLING);
  attachInterrupt(digitalPinToInterrupt(TACHOMETER_PIN), tachometerISR, FALLING);
  
  Serial.println("Setup complete - ready to read sensors");
}

void loop() {
  // Read all sensors
  readFuelFlow();
  readFuelLevel();
  readTrimPosition();
  readGPS();
  readTachometer();
  readOilPressure();
  readBilgeStatus();
  
  // Send data to Android app
  if (millis() - lastDataSend >= DATA_SEND_INTERVAL) {
    sendSensorData();
    lastDataSend = millis();
  }
  
  // Handle GPS data
  while (GPSSerial.available() > 0) {
    if (gps.encode(GPSSerial.read())) {
      // GPS data is processed in readGPS()
    }
  }
  
  // Small delay to prevent overwhelming the system
  delay(10);
}

// Interrupt Service Routines
void fuelFlowISR() {
  fuelFlowPulses++;
}

void tachometerISR() {
  tachometerPulses++;
}

// Read fuel flow rate
void readFuelFlow() {
  unsigned long currentTime = millis();
  unsigned long timeDiff = currentTime - lastFuelFlowTime;
  
  if (timeDiff >= 1000) { // Calculate every second
    // Convert pulses to gallons per hour
    float gallons = (float)fuelFlowPulses / FUEL_FLOW_CALIBRATION;
    fuelConsumption = gallons * (3600000.0 / timeDiff); // Convert to GPH
    
    // Reset counters
    fuelFlowPulses = 0;
    lastFuelFlowTime = currentTime;
  }
}

// Read fuel level from analog sender
void readFuelLevel() {
  int adcValue = analogRead(FUEL_LEVEL_PIN);
  
  // Convert ADC value to percentage
  if (adcValue <= FUEL_LEVEL_EMPTY) {
    fuelLevel = 0.0;
  } else if (adcValue >= FUEL_LEVEL_FULL) {
    fuelLevel = 100.0;
  } else {
    fuelLevel = ((float)(adcValue - FUEL_LEVEL_EMPTY) / (FUEL_LEVEL_FULL - FUEL_LEVEL_EMPTY)) * 100.0;
  }
  
  // Apply some smoothing
  fuelLevel = constrain(fuelLevel, 0.0, 100.0);
}

// Read trim position from analog sender
void readTrimPosition() {
  int adcValue = analogRead(TRIM_POSITION_PIN);
  
  // Convert ADC value to trim position (1-5)
  // Assuming linear relationship across the ADC range
  float position = ((float)adcValue / 4095.0) * TRIM_POSITIONS;
  trimPosition = round(position);
  
  // Ensure position is within valid range
  trimPosition = constrain(trimPosition, 1, TRIM_POSITIONS);
}

// Read GPS speed
void readGPS() {
  if (gps.speed.isValid()) {
    // Convert from knots to MPH
    speed = gps.speed.mph();
  } else {
    // If GPS not available, estimate from RPM
    speed = rpms * 0.01; // Rough estimation
  }
  
  // Limit speed to reasonable range
  speed = constrain(speed, 0.0, 50.0);
}

// Read tachometer RPM
void readTachometer() {
  unsigned long currentTime = millis();
  unsigned long timeDiff = currentTime - lastTachometerTime;
  
  if (timeDiff >= 1000) { // Calculate every second
    // Convert pulses to RPM
    rpms = (tachometerPulses * 60) / (TACHOMETER_CALIBRATION * (timeDiff / 1000.0));
    
    // Reset counters
    tachometerPulses = 0;
    lastTachometerTime = currentTime;
    
    // Ensure RPM is within reasonable range
    rpms = constrain(rpms, 0, 5000);
  }
}

// Read oil pressure from analog sender
void readOilPressure() {
  int adcValue = analogRead(OIL_PRESSURE_PIN);
  
  // Convert ADC value to PSI
  oilPressure = (adcValue * OIL_PRESSURE_SCALE) + OIL_PRESSURE_OFFSET;
  
  // Ensure pressure is within reasonable range
  oilPressure = constrain(oilPressure, 0.0, 80.0);
}

// Read bilge water level
void readBilgeStatus() {
  int bilgeReading = digitalRead(BILGE_SWITCH_PIN);
  
  if (bilgeReading == HIGH) {
    bilgeStatus = "High";
  } else {
    bilgeStatus = "Normal";
  }
}

// Send sensor data to Android app
void sendSensorData() {
  // Format data string
  String data = "FUEL:" + String(fuelConsumption, 1) + 
                ",LEVEL:" + String(fuelLevel, 0) + 
                ",TRIM:" + String(trimPosition) + 
                ",SPEED:" + String(speed, 0) + 
                ",RPM:" + String(rpms) + 
                ",OIL:" + String(oilPressure, 0) + 
                ",BILGE:" + bilgeStatus;
  
  // Send via Bluetooth
  if (SerialBT.available()) {
    SerialBT.println(data);
  }
  
  // Also print to serial for debugging
  Serial.println("Sent: " + data);
}

// Debug function to print sensor values
void printSensorValues() {
  Serial.println("=== Sensor Readings ===");
  Serial.println("Fuel Consumption: " + String(fuelConsumption, 1) + " GPH");
  Serial.println("Fuel Level: " + String(fuelLevel, 0) + "%");
  Serial.println("Trim Position: " + String(trimPosition));
  Serial.println("Speed: " + String(speed, 0) + " MPH");
  Serial.println("RPM: " + String(rpms));
  Serial.println("Oil Pressure: " + String(oilPressure, 0) + " PSI");
  Serial.println("Bilge Status: " + bilgeStatus);
  Serial.println("======================");
} 