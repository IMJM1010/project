# 산업안전 웨어러블 + 앱 알고리즘 V2

사용자가 제공한 알고리즘 그림의 **1~7단계 구조**를 그대로 코드 구조에 반영했습니다.

## 하드웨어 기준

- AD8232 ECG + 3전극
- MPU6050 IMU
- MAX30208 피부온도
- GY-NEO6MV2 GPS
- Arduino Nano 33 BLE Rev2
- BLE 기반 Flutter 앱

> 산업안전 연구/캡스톤 시제품용입니다. 의료 진단용이 아닙니다.

## 7단계 구현

### 1. 센서 데이터 수집
`firmware/wearable_patch_v2.ino`

### 2. 데이터 전처리
`algorithm/pipeline.py`

- ECG 필터링
- Median/MAD 이상값 억제
- 결측값 처리
- 이동평균/스무딩
- 타임스탬프 동기화

### 3. 특징값 추출

- ECG: HR, HR 변화량, HRV(RMSSD), ECG 이상 후보 점수
- IMU: 활동량, 자세 변화, 충격량, 무동작 시간, 낙상 후보
- 온도: 현재 피부온도, 개인 baseline 대비 변화, 상승 추세
- GPS: 현재 위치, 이동속도, 긴급 위치

### 4. 개별 위험도

- 심박 위험도
- ECG 이상 위험도
- 피부온도 위험도
- 낙상/충격 위험도
- 활동상태 위험도

### 5. 상황 보정 및 종합 판단

- 활동량 기반 심박 위험도 보정
- 개인 기준값 적용
- 긴급 규칙
- 가중치 기반 종합점수

### 6. 최종 단계

- NORMAL
- CAUTION
- WARNING
- EMERGENCY

순간 잡음 때문에 위험 단계가 튀는 것을 줄이기 위해 지속시간 조건도 포함합니다.

### 7. 앱/대시보드

Flutter 예제는 `app/`에 있습니다.
긴급 상태에서는 GPS 위치를 함께 표시/전송하도록 설계합니다.

## 실행

### Python 알고리즘 테스트

```bash
cd algorithm
python simulator.py
```

### Flutter

```bash
cd app
flutter pub get
flutter run
```

## 주의할 점

- 피부온도는 심부체온과 동일하지 않습니다.
- GY-NEO6MV2는 물류센터 실내에서 GPS 수신이 불안정할 수 있습니다.
- Nano 33 BLE Rev2에는 자체 IMU가 있으므로 최종 제품에서는 MPU6050을 제거하는 선택도 가능합니다. 이번 버전은 제공된 그림과 동일하게 MPU6050을 포함했습니다.
- 위험도 임계값과 가중치는 실제 근로자 데이터로 보정해야 합니다.
