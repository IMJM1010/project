# 준비물(BOM)

| 분류 | 준비물 | 수량 | 역할 |
|---|---|---:|---|
| MCU/BLE | Arduino Nano 33 BLE Rev2 | 1 | 센서 통합 + BLE |
| ECG | AD8232 ECG 모듈 | 1 | 심전도 수집 |
| ECG | 3극 전극 케이블 | 1 | RA/LA/RL |
| ECG | Ag/AgCl 전극 | 다수 | 피부 접촉 |
| IMU | MPU6050 모듈 | 1 | 3축 가속도 + 3축 자이로 |
| 온도 | MAX30208 모듈/Breakout | 1 | 피부온도 |
| GPS | GY-NEO6MV2 | 1 | 위치/속도/시간 |
| 전원 | 3.7V Li-Po | 1 | 웨어러블 전원 |
| 전원 | 보호/충전 회로 | 1 | 배터리 관리 |
| 외형 | TPU/실리콘 커버 | 1 | 보호 |
| 외형 | 의료용 접착필름 | 다수 | 패치 부착 |
| 제작 | 브레드보드/점퍼선 | 다수 | 1차 테스트 |

## 개발 도구

- Arduino IDE
- ArduinoBLE
- Adafruit MPU6050
- Adafruit Unified Sensor
- TinyGPSPlus
- Python 3
- Flutter SDK

## 제작 단계

1. 브레드보드에서 센서 각각 검증
2. Arduino에서 통합 수집
3. BLE 전송
4. Python 알고리즘 검증
5. Flutter 앱 연동
6. 패치 케이스 제작
7. 맞춤 PCB/Flex PCB로 소형화
