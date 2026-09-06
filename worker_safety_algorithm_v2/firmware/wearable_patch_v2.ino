#include <ArduinoBLE.h>
#include <Wire.h>
#include <TinyGPSPlus.h>
#include <Adafruit_MPU6050.h>
#include <Adafruit_Sensor.h>

// =============================================================
// Wearable Patch V2
// AD8232 + MPU6050 + MAX30208 + GY-NEO6MV2
// Board: Arduino Nano 33 BLE Rev2
// Prototype / capstone only. Not a medical device.
// =============================================================

const int ECG_PIN = A0;
const int LEAD_OFF_PLUS = 10;
const int LEAD_OFF_MINUS = 11;

Adafruit_MPU6050 mpu;
TinyGPSPlus gps;

const uint8_t MAX30208_ADDR = 0x50;
const uint8_t REG_FIFO_COUNT = 0x07;
const uint8_t REG_FIFO_DATA  = 0x08;
const uint8_t REG_TEMP_SETUP = 0x14;

const uint32_t ECG_HZ = 300;
const uint32_t IMU_HZ = 100;
const uint32_t TEMP_HZ = 15;
const uint32_t TELEMETRY_HZ = 100;

const uint32_t ECG_PERIOD_US = 1000000UL / ECG_HZ;
const uint32_t IMU_PERIOD_US = 1000000UL / IMU_HZ;
const uint32_t TEMP_PERIOD_US = 1000000UL / TEMP_HZ;
const uint32_t TELEMETRY_PERIOD_US = 1000000UL / TELEMETRY_HZ;

uint32_t nextEcgUs = 0;
uint32_t lastImuUs = 0;
uint32_t lastTelemetryUs = 0;
uint32_t lastTempRequestUs = 0;
uint32_t tempStartedMs = 0;
bool tempPending = false;

float skinTempC = NAN;
float axG = NAN, ayG = NAN, azG = NAN;
float gxDps = NAN, gyDps = NAN, gzDps = NAN;

double latitude = 0;
double longitude = 0;
double speedMps = 0;
bool gpsValid = false;

BLEService wearableService("8a6f1001-41b8-4e73-9c27-4e8e1bb00001");
BLECharacteristic ecgCharacteristic(
  "8a6f1002-41b8-4e73-9c27-4e8e1bb00001",
  BLENotify,
  20
);
BLECharacteristic telemetryCharacteristic(
  "8a6f1003-41b8-4e73-9c27-4e8e1bb00001",
  BLENotify | BLERead,
  36
);

uint16_t ecgBuffer[10];
uint8_t ecgIndex = 0;

bool writeReg(uint8_t addr, uint8_t reg, uint8_t value) {
  Wire.beginTransmission(addr);
  Wire.write(reg);
  Wire.write(value);
  return Wire.endTransmission() == 0;
}

bool readRegBytes(uint8_t addr, uint8_t reg, uint8_t *buf, size_t len) {
  Wire.beginTransmission(addr);
  Wire.write(reg);
  if (Wire.endTransmission(false) != 0) return false;
  size_t n = Wire.requestFrom((int)addr, (int)len);
  if (n != len) return false;
  for (size_t i = 0; i < len; ++i) buf[i] = Wire.read();
  return true;
}

void putU16LE(uint8_t *b, int o, uint16_t v) {
  b[o] = v & 0xFF;
  b[o+1] = (v >> 8) & 0xFF;
}

void putI16LE(uint8_t *b, int o, int16_t v) {
  putU16LE(b, o, (uint16_t)v);
}

void putI32LE(uint8_t *b, int o, int32_t v) {
  uint32_t u = (uint32_t)v;
  b[o] = u & 0xFF;
  b[o+1] = (u >> 8) & 0xFF;
  b[o+2] = (u >> 16) & 0xFF;
  b[o+3] = (u >> 24) & 0xFF;
}

void requestTemperature() {
  if (writeReg(MAX30208_ADDR, REG_TEMP_SETUP, 0x01)) {
    tempPending = true;
    tempStartedMs = millis();
  }
}

void serviceTemperature() {
  uint32_t nowUs = micros();
  uint32_t nowMs = millis();
  if (!tempPending && nowUs - lastTempRequestUs >= TEMP_PERIOD_US) {
    requestTemperature();
    lastTempRequestUs = nowUs;
  }
  if (tempPending && nowMs - tempStartedMs >= 55) {
    uint8_t count = 0;
    if (readRegBytes(MAX30208_ADDR, REG_FIFO_COUNT, &count, 1)) {
      count &= 0x3F;
      if (count > 0) {
        uint8_t raw[2];
        if (readRegBytes(MAX30208_ADDR, REG_FIFO_DATA, raw, 2)) {
          int16_t value = (int16_t)((raw[0] << 8) | raw[1]);
          skinTempC = value * 0.005f;
        }
      }
    }
    tempPending = false;
  }
}

void serviceImu() {
  uint32_t nowUs = micros();
  if (nowUs - lastImuUs < IMU_PERIOD_US) return;
  lastImuUs = nowUs;
  sensors_event_t a, g, temp;
  mpu.getEvent(&a, &g, &temp);
  const float G = 9.80665f;
  const float RAD_TO_DEG = 57.2957795f;
  axG = a.acceleration.x / G;
  ayG = a.acceleration.y / G;
  azG = a.acceleration.z / G;
  gxDps = g.gyro.x * RAD_TO_DEG;
  gyDps = g.gyro.y * RAD_TO_DEG;
  gzDps = g.gyro.z * RAD_TO_DEG;
}

void serviceGps() {
  while (Serial1.available()) gps.encode(Serial1.read());
  gpsValid = gps.location.isValid() && gps.location.age() < 5000;
  if (gpsValid) {
    latitude = gps.location.lat();
    longitude = gps.location.lng();
  }
  if (gps.speed.isValid()) speedMps = gps.speed.mps();
}

void serviceEcg() {
  uint32_t now = micros();
  while ((int32_t)(now - nextEcgUs) >= 0) {
    bool leadOff = digitalRead(LEAD_OFF_PLUS) == HIGH || digitalRead(LEAD_OFF_MINUS) == HIGH;
    uint16_t raw = leadOff ? 0 : (uint16_t)analogRead(ECG_PIN);
    ecgBuffer[ecgIndex++] = raw;
    if (ecgIndex == 10) {
      uint8_t bytes[20];
      for (int i = 0; i < 10; ++i) putU16LE(bytes, i * 2, ecgBuffer[i]);
      if (BLE.connected()) ecgCharacteristic.writeValue(bytes, sizeof(bytes));
      ecgIndex = 0;
    }
    nextEcgUs += ECG_PERIOD_US;
    now = micros();
  }
}

void sendTelemetry() {
  uint8_t b[36] = {0};
  bool leadOff = digitalRead(LEAD_OFF_PLUS) == HIGH || digitalRead(LEAD_OFF_MINUS) == HIGH;
  bool tempValid = !isnan(skinTempC);
  bool imuValid = !isnan(axG) && !isnan(ayG) && !isnan(azG);
  if (leadOff) b[0] |= 0x01;
  if (gpsValid) b[0] |= 0x02;
  if (tempValid) b[0] |= 0x04;
  if (imuValid) b[0] |= 0x08;
  putI16LE(b, 1, tempValid ? (int16_t)(skinTempC * 100) : 0);
  putI16LE(b, 3, imuValid ? (int16_t)(axG * 1000) : 0);
  putI16LE(b, 5, imuValid ? (int16_t)(ayG * 1000) : 0);
  putI16LE(b, 7, imuValid ? (int16_t)(azG * 1000) : 0);
  putI16LE(b, 9, imuValid ? (int16_t)(gxDps * 10) : 0);
  putI16LE(b, 11, imuValid ? (int16_t)(gyDps * 10) : 0);
  putI16LE(b, 13, imuValid ? (int16_t)(gzDps * 10) : 0);
  putI32LE(b, 15, gpsValid ? (int32_t)(latitude * 10000000.0) : 0);
  putI32LE(b, 19, gpsValid ? (int32_t)(longitude * 10000000.0) : 0);
  putU16LE(b, 23, gpsValid ? (uint16_t)min(65535.0, speedMps * 100.0) : 0);
  uint32_t ms = millis();
  b[25] = ms & 0xFF;
  b[26] = (ms >> 8) & 0xFF;
  b[27] = (ms >> 16) & 0xFF;
  b[28] = (ms >> 24) & 0xFF;
  if (BLE.connected()) telemetryCharacteristic.writeValue(b, sizeof(b));
}

void setup() {
  Serial.begin(115200);
  Serial1.begin(9600);
  pinMode(LEAD_OFF_PLUS, INPUT);
  pinMode(LEAD_OFF_MINUS, INPUT);
  analogReadResolution(12);
  Wire.begin();
  if (!mpu.begin()) {
    Serial.println("MPU6050 not found");
    while (1);
  }
  mpu.setAccelerometerRange(MPU6050_RANGE_8_G);
  mpu.setGyroRange(MPU6050_RANGE_500_DEG);
  mpu.setFilterBandwidth(MPU6050_BAND_44_HZ);
  if (!BLE.begin()) {
    Serial.println("BLE init failed");
    while (1);
  }
  BLE.setLocalName("WorkerSafetyPatchV2");
  BLE.setDeviceName("WorkerSafetyPatchV2");
  BLE.setAdvertisedService(wearableService);
  wearableService.addCharacteristic(ecgCharacteristic);
  wearableService.addCharacteristic(telemetryCharacteristic);
  BLE.addService(wearableService);
  BLE.advertise();
  nextEcgUs = micros();
  Serial.println("WorkerSafetyPatchV2 ready");
}

void loop() {
  BLE.poll();
  serviceEcg();
  serviceImu();
  serviceTemperature();
  serviceGps();
  uint32_t nowUs = micros();
  if (nowUs - lastTelemetryUs >= TELEMETRY_PERIOD_US) {
    sendTelemetry();
    lastTelemetryUs = nowUs;
  }
}
