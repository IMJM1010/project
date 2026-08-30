# 하드웨어 배선도 V2

## AD8232 ECG

| AD8232 | Nano 33 BLE Rev2 | 역할 |
|---|---|---|
| OUTPUT | A0 | ECG 아날로그 신호 |
| LO+ | D10 | 전극 이탈 감지 |
| LO- | D11 | 전극 이탈 감지 |
| 3.3V | 3.3V | 전원 |
| GND | GND | 공통 접지 |

전극은 RA / LA / RL 3개를 사용합니다.

## MPU6050

| MPU6050 | Nano | 역할 |
|---|---|---|
| SDA | SDA / A3 | I2C 데이터 |
| SCL | SCL / A4 | I2C 클럭 |
| GND | GND | 공통 접지 |
| VCC | 모듈 사양 확인 | 전원 |

MAX30208과 같은 I2C 버스를 공유할 수 있습니다.

## MAX30208

| MAX30208 | Nano |
|---|---|
| SDA | SDA / A3 |
| SCL | SCL / A4 |
| GND | GND |
| VCC | 3.3V |

피부 접촉부 근처에 두고 MCU/배터리 열원과 떨어뜨립니다.

## GY-NEO6MV2 GPS

| GPS | Nano |
|---|---|
| TX | D1 / RX |
| RX | D0 / TX |
| GND | GND |
| VCC | 실제 모듈 사양 확인 |

Nano 33 BLE Rev2의 GPIO는 3.3V 기준이므로 GPS UART 레벨을 확인해야 합니다.

## 전체 구조

```text
바깥쪽

┌─────────────────────────────────────┐
│  GPS 안테나          Li-Po Battery │
│                                     │
│  GY-NEO6MV2    Arduino Nano 33 BLE │
│                    │                │
│  AD8232          MPU6050             │
│                    │                │
│                 MAX30208            │
└──────────────────┬──────────────────┘
                   │ 피부 방향
       LA ●────────┼────────● RA
                   │
                 ● RL
```

초기 프로토타입에서는 모듈들을 중앙 케이스에 넣고 전극과 온도센서 접촉부만 피부 방향으로 배치하는 것이 현실적입니다.
