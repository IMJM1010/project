# 사진 기반 알고리즘 구현 매핑

## 1. 센서 데이터 수집

- AD8232 → ECG 원신호
- MPU6050 → 3축 가속도, 3축 자이로
- MAX30208 → 피부온도
- GY-NEO6MV2 → 위도, 경도, 속도, 시간

펌웨어: `../firmware/wearable_patch_v2.ino`

## 2. 데이터 전처리

`WorkerSafetyPipeline.preprocess()`

- ECG 대역 필터링: 약 0.5~35 Hz
- ADC 포화/비정상 신호 확인
- 온도 Median/MAD 이상값 처리
- IMU 이동평균
- 결측값은 센서 종류에 따라 유지/보간
- `timestamp_ms` 기반 시간 정렬

## 3. 특징값 추출

`WorkerSafetyPipeline.extract_features()`

### ECG
- 심박수 HR
- HR 변화량
- HRV: RMSSD
- RR 간격 불규칙 기반 ECG 이상 후보 점수

### MPU6050
- 활동량
- 자세 변화량
- 충격량
- 무동작 시간
- 낙상 후보

### MAX30208
- 현재 피부온도
- 개인 baseline 대비 변화량
- 시간에 따른 상승/하락 기울기

### GPS
- 현재 위도/경도
- 이동속도
- 긴급상황 발생 위치

## 4. 개별 위험도 계산

`WorkerSafetyPipeline.individual_risks()`

모든 위험도를 0~100으로 변환합니다.

- Heart Risk
- ECG Anomaly Risk
- Temperature Risk
- Fall/Impact Risk
- Activity Risk

## 5. 상황 보정 및 종합 판단

`WorkerSafetyPipeline.assess()`

### 활동량 기반 보정
활동량이 높은데 심박수가 상승한 경우 작업 강도의 영향을 일부 반영합니다.

### 개인 기준값

```python
PersonalBaseline(
    heart_rate_bpm=78,
    skin_temp_c=35.4,
    hrv_rmssd_ms=45,
)
```

### 긴급 규칙

예시:

```text
강한 충격 + 큰 자세 변화
          ↓
낙상 후보 기억
          ↓
8초 이상 무동작
          ↓
EMERGENCY 후보
```

### 종합점수 기본 가중치

```text
심박       25%
ECG 이상   30%
피부온도   15%
낙상/충격  20%
활동상태   10%
```

가중치는 `config/risk_config.json`에서 수정할 수 있습니다.

## 6. 최종 위험 단계

```text
NORMAL     정상
CAUTION    주의
WARNING    경고
EMERGENCY  긴급
```

단일 순간값만으로 단계가 바뀌지 않도록 지속시간 조건을 둡니다.

## 7. 앱 / 관리자 전송

`WorkerSafetyPipeline.to_payload()`

- 사용자 앱: 상태 표시, 진동/알림
- 관리자 대시보드: 전체 작업자 상태
- EMERGENCY: GPS가 유효하면 현재 위치 포함

## 중요한 한계

- ECG 이상 점수는 의료적 부정맥 진단이 아니라 RR 패턴의 이상 후보를 찾는 프로토타입 로직입니다.
- 피부온도는 심부체온이 아닙니다.
- GPS는 실내 물류센터에서 불안정할 수 있습니다.
- 실제 기준치는 사용자별 baseline과 실제 수집 데이터로 재학습/보정해야 합니다.
