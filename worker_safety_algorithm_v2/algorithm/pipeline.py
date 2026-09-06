from __future__ import annotations

from collections import deque
from dataclasses import dataclass, field
from enum import Enum
from math import sqrt
from statistics import median
from typing import Optional
import json
import math
from pathlib import Path


class RiskLevel(str, Enum):
    NORMAL = "NORMAL"
    CAUTION = "CAUTION"
    WARNING = "WARNING"
    EMERGENCY = "EMERGENCY"
    SENSOR_CHECK = "SENSOR_CHECK"


@dataclass
class RawSensorFrame:
    timestamp_ms: int
    ecg_raw: Optional[float] = None
    lead_off: bool = False
    ax_g: Optional[float] = None
    ay_g: Optional[float] = None
    az_g: Optional[float] = None
    gx_dps: Optional[float] = None
    gy_dps: Optional[float] = None
    gz_dps: Optional[float] = None
    skin_temp_c: Optional[float] = None
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    speed_mps: Optional[float] = None
    gps_valid: bool = False


@dataclass
class PreprocessedFrame:
    timestamp_ms: int
    ecg: Optional[float]
    signal_quality: float
    lead_off: bool
    ax_g: Optional[float]
    ay_g: Optional[float]
    az_g: Optional[float]
    gyro_mag_dps: Optional[float]
    skin_temp_c: Optional[float]
    latitude: Optional[float]
    longitude: Optional[float]
    speed_mps: Optional[float]
    gps_valid: bool


@dataclass
class ExtractedFeatures:
    timestamp_ms: int
    heart_rate_bpm: Optional[float] = None
    heart_rate_delta_bpm: Optional[float] = None
    hrv_rmssd_ms: Optional[float] = None
    ecg_anomaly_score: float = 0.0
    signal_quality: float = 0.0
    activity_g: float = 0.0
    posture_change_dps: float = 0.0
    posture_angle_deg: float = 0.0
    pitch_deg: float = 0.0
    roll_deg: float = 0.0
    posture_delta_deg: float = 0.0
    impact_g: float = 0.0
    inactivity_sec: float = 0.0
    free_fall_detected: bool = False
    impact_detected: bool = False
    posture_change_detected: bool = False
    fall_candidate: bool = False
    skin_temp_c: Optional[float] = None
    temp_delta_c: Optional[float] = None
    temp_slope_c_per_min: float = 0.0
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    speed_mps: Optional[float] = None
    gps_valid: bool = False


@dataclass
class IndividualRisks:
    heart: float = 0.0
    ecg_anomaly: float = 0.0
    temperature: float = 0.0
    fall_impact: float = 0.0
    activity: float = 0.0


@dataclass
class Assessment:
    timestamp_ms: int
    level: RiskLevel
    total_score: float
    individual: IndividualRisks
    reasons: list[str] = field(default_factory=list)
    emergency_rule: Optional[str] = None
    latitude: Optional[float] = None
    longitude: Optional[float] = None


@dataclass
class PersonalBaseline:
    heart_rate_bpm: float = 80.0
    skin_temp_c: float = 35.5
    hrv_rmssd_ms: float = 45.0


def clip(v: float, lo: float, hi: float) -> float:
    return max(lo, min(hi, v))


class MovingAverage:
    def __init__(self, size: int):
        self.values = deque(maxlen=size)

    def update(self, value: Optional[float]) -> Optional[float]:
        if value is None:
            return None if not self.values else sum(self.values) / len(self.values)
        self.values.append(float(value))
        return sum(self.values) / len(self.values)


class RobustOutlierFilter:
    """Median/MAD 기반 이상값 억제."""
    def __init__(self, size: int = 21, z_limit: float = 5.0):
        self.values = deque(maxlen=size)
        self.z_limit = z_limit

    def update(self, value: Optional[float]) -> Optional[float]:
        if value is None:
            return None
        value = float(value)
        if len(self.values) >= 7:
            m = median(self.values)
            mad = median([abs(v - m) for v in self.values]) or 1e-6
            z = 0.6745 * (value - m) / mad
            if abs(z) > self.z_limit:
                value = m
        self.values.append(value)
        return value


class EcgFilter:
    """0.5Hz high-pass + 35Hz low-pass 1차 IIR."""
    def __init__(self, fs: float = 250.0):
        self.fs = fs
        self.x_prev = 0.0
        self.hp_prev = 0.0
        self.lp_prev = 0.0
        self.initialized = False

    def update(self, x: Optional[float]) -> Optional[float]:
        if x is None:
            return None
        if not self.initialized:
            self.x_prev = float(x)
            self.initialized = True
            return 0.0
        dt = 1.0 / self.fs
        hp_rc = 1.0 / (2.0 * math.pi * 0.5)
        hp_alpha = hp_rc / (hp_rc + dt)
        hp = hp_alpha * (self.hp_prev + x - self.x_prev)
        self.x_prev, self.hp_prev = x, hp

        lp_rc = 1.0 / (2.0 * math.pi * 35.0)
        lp_alpha = dt / (lp_rc + dt)
        lp = self.lp_prev + lp_alpha * (hp - self.lp_prev)
        self.lp_prev = lp
        return lp


class RPeakDetector:
    """프로토타입용 적응형 R-peak 검출기. 의료 진단용 아님."""
    def __init__(self, fs: int = 250):
        self.fs = fs
        self.prev = 0.0
        self.energy_prev2 = 0.0
        self.energy_prev1 = 0.0
        self.energy_baseline = 1.0
        self.sample_index = 0
        self.last_peak: Optional[int] = None
        self.rr_ms = deque(maxlen=20)

    def update(self, value: Optional[float]) -> tuple[Optional[float], Optional[float], float]:
        if value is None:
            self.sample_index += 1
            return None, None, 0.0

        d = value - self.prev
        self.prev = value
        energy = d * d
        self.energy_baseline = 0.997 * self.energy_baseline + 0.003 * energy
        threshold = max(20.0, self.energy_baseline * 4.5)

        candidate = (
            self.energy_prev1 > self.energy_prev2
            and self.energy_prev1 >= energy
            and self.energy_prev1 > threshold
        )

        bpm = None
        rr = None
        refractory = int(self.fs * 0.25)
        if candidate:
            peak = self.sample_index - 1
            if self.last_peak is None or peak - self.last_peak >= refractory:
                if self.last_peak is not None:
                    rr = (peak - self.last_peak) * 1000.0 / self.fs
                    if 300 <= rr <= 2000:
                        self.rr_ms.append(rr)
                        recent = list(self.rr_ms)[-7:]
                        bpm = 60000.0 / median(recent)
                self.last_peak = peak

        self.energy_prev2 = self.energy_prev1
        self.energy_prev1 = energy
        self.sample_index += 1

        anomaly = 0.0
        if len(self.rr_ms) >= 5:
            med = median(self.rr_ms)
            mad = median([abs(v - med) for v in self.rr_ms])
            variability = mad / max(med, 1.0)
            anomaly = clip((variability - 0.05) / 0.20 * 100, 0, 100)
        return bpm, rr, anomaly

    def rmssd(self) -> Optional[float]:
        vals = list(self.rr_ms)
        if len(vals) < 3:
            return None
        diffs = [vals[i] - vals[i-1] for i in range(1, len(vals))]
        return sqrt(sum(d*d for d in diffs) / len(diffs))


class WorkerSafetyPipeline:
    """사용자 제공 이미지의 1~7단계 구조를 구현한 핵심 알고리즘."""
    def __init__(self, baseline: PersonalBaseline | None = None,
                 config_path: str | Path | None = None, ecg_fs: int = 250):
        self.baseline = baseline or PersonalBaseline()
        if config_path is None:
            config_path = Path(__file__).resolve().parents[1] / "config" / "risk_config.json"
        self.config = json.loads(Path(config_path).read_text(encoding="utf-8"))

        self.ecg_filter = EcgFilter(ecg_fs)
        self.ecg_outlier = RobustOutlierFilter()
        self.temp_outlier = RobustOutlierFilter(size=15, z_limit=4.0)
        self.ax_smooth = MovingAverage(5)
        self.ay_smooth = MovingAverage(5)
        self.az_smooth = MovingAverage(5)
        self.temp_smooth = MovingAverage(5)
        self.rpeak = RPeakDetector(ecg_fs)

        self.current_bpm: Optional[float] = None
        self.current_anomaly = 0.0
        self.last_motion_timestamp_ms: Optional[int] = None
        self.last_free_fall_event_ms: Optional[int] = None
        self.last_impact_event_ms: Optional[int] = None
        self.last_fall_event_ms: Optional[int] = None
        self.last_stable_pitch_deg: Optional[float] = None
        self.last_stable_roll_deg: Optional[float] = None
        self.prefall_pitch_deg: Optional[float] = None
        self.prefall_roll_deg: Optional[float] = None
        self.last_temp_values = deque(maxlen=120)
        self._caution_since_ms: Optional[int] = None
        self._warning_since_ms: Optional[int] = None
        self._emergency_since_ms: Optional[int] = None

    def preprocess(self, raw: RawSensorFrame) -> PreprocessedFrame:
        ecg = None
        quality = 0.0
        if not raw.lead_off and raw.ecg_raw is not None:
            sample = float(raw.ecg_raw)
            if 1.0 < sample < 4094.0:
                ecg = self.ecg_filter.update(sample)
                quality = 1.0
            else:
                quality = 0.2

        ax = self.ax_smooth.update(raw.ax_g)
        ay = self.ay_smooth.update(raw.ay_g)
        az = self.az_smooth.update(raw.az_g)
        gyro_mag = None
        if None not in (raw.gx_dps, raw.gy_dps, raw.gz_dps):
            gyro_mag = sqrt(raw.gx_dps**2 + raw.gy_dps**2 + raw.gz_dps**2)

        temp = self.temp_smooth.update(self.temp_outlier.update(raw.skin_temp_c))
        return PreprocessedFrame(
            timestamp_ms=raw.timestamp_ms,
            ecg=ecg, signal_quality=quality, lead_off=raw.lead_off,
            ax_g=ax, ay_g=ay, az_g=az, gyro_mag_dps=gyro_mag,
            skin_temp_c=temp,
            latitude=raw.latitude if raw.gps_valid else None,
            longitude=raw.longitude if raw.gps_valid else None,
            speed_mps=raw.speed_mps if raw.gps_valid else None,
            gps_valid=raw.gps_valid,
        )

    def extract_features(self, p: PreprocessedFrame) -> ExtractedFeatures:
        bpm, _, anomaly = self.rpeak.update(p.ecg)
        if bpm is not None:
            self.current_bpm = bpm
        self.current_anomaly = anomaly
        hr_delta = None if self.current_bpm is None else self.current_bpm - self.baseline.heart_rate_bpm

        activity_g = impact_g = 0.0
        posture_angle_deg = 0.0
        pitch_deg = 0.0
        roll_deg = 0.0
        if None not in (p.ax_g, p.ay_g, p.az_g):
            total_g = sqrt(p.ax_g**2 + p.ay_g**2 + p.az_g**2)
            activity_g = abs(total_g - 1.0)
            impact_g = total_g

            # MPU6050 가속도 기반 자세 추정.
            # 착용 기준: Z축이 서 있을 때 수직 방향이라고 가정.
            if total_g > 1e-6:
                vertical_ratio = clip(abs(p.az_g) / total_g, 0.0, 1.0)
                posture_angle_deg = math.degrees(math.acos(vertical_ratio))
                pitch_deg = math.degrees(
                    math.atan2(-p.ax_g, sqrt(p.ay_g**2 + p.az_g**2))
                )
                roll_deg = math.degrees(math.atan2(p.ay_g, p.az_g))

        posture = p.gyro_mag_dps or 0.0

        thresholds = self.config["prototype_thresholds"]
        low_activity = thresholds["low_activity_g"]

        # 실제 움직임이 한 번 이상 감지된 뒤부터 무동작 시간을 누적한다.
        if activity_g > low_activity:
            self.last_motion_timestamp_ms = p.timestamp_ms
        inactivity = 0.0 if self.last_motion_timestamp_ms is None else max(
            0.0, (p.timestamp_ms - self.last_motion_timestamp_ms) / 1000.0
        )

        free_fall_threshold = thresholds["free_fall_g"]
        impact_threshold = thresholds["impact_g"]
        posture_delta_threshold = thresholds["posture_change_deg"]
        sequence_window_ms = int(thresholds["fall_sequence_window_s"] * 1000)
        posture_confirmation_ms = int(thresholds["posture_confirmation_s"] * 1000)
        fall_candidate_window_ms = int(thresholds["fall_candidate_window_s"] * 1000)

        # 안정 자세(약 1 g)일 때 직전 Pitch/Roll을 저장한다.
        if 0.85 <= impact_g <= 1.15 and activity_g <= thresholds["low_activity_g"]:
            self.last_stable_pitch_deg = pitch_deg
            self.last_stable_roll_deg = roll_deg

        # [1] 자유낙하: SVM < 0.8 g (설정값 변경 가능)
        free_fall_detected = impact_g < free_fall_threshold
        if free_fall_detected:
            # 낙상 직전 안정 자세를 기준 자세로 고정한다.
            if self.last_stable_pitch_deg is not None:
                self.prefall_pitch_deg = self.last_stable_pitch_deg
                self.prefall_roll_deg = self.last_stable_roll_deg
            self.last_free_fall_event_ms = p.timestamp_ms

        # [2] 충격: 자유낙하 후 지정 시간 내 SVM >= 2.5 g
        recent_free_fall = (
            self.last_free_fall_event_ms is not None
            and 0 <= p.timestamp_ms - self.last_free_fall_event_ms <= sequence_window_ms
        )
        impact_detected = impact_g >= impact_threshold and recent_free_fall
        if impact_detected:
            self.last_impact_event_ms = p.timestamp_ms

        # [3] 자세 변화: 낙상 직전 Pitch/Roll 대비 큰 변화 확인
        pitch_delta = 0.0 if self.prefall_pitch_deg is None else abs(
            pitch_deg - self.prefall_pitch_deg
        )
        roll_delta = 0.0 if self.prefall_roll_deg is None else abs(
            roll_deg - self.prefall_roll_deg
        )
        posture_delta_deg = max(pitch_delta, roll_delta)

        recent_impact = (
            self.last_impact_event_ms is not None
            and 0 <= p.timestamp_ms - self.last_impact_event_ms <= posture_confirmation_ms
        )
        posture_change_detected = (
            recent_impact and posture_delta_deg >= posture_delta_threshold
        )

        # [1]→[2]→[3] 순서를 모두 만족하면 낙상 후보로 기억한다.
        if posture_change_detected:
            self.last_fall_event_ms = p.timestamp_ms

        # [4] 낙상 후 5~10초 상태 확인을 위해 후보 상태를 유지한다.
        fall_candidate = (
            self.last_fall_event_ms is not None
            and 0 <= p.timestamp_ms - self.last_fall_event_ms <= fall_candidate_window_ms
        )

        motion_penalty = clip(activity_g / 1.2, 0, 0.75)
        quality = p.signal_quality * (1.0 - motion_penalty)
        if p.lead_off:
            quality = 0.0

        temp_delta = None
        temp_slope = 0.0
        if p.skin_temp_c is not None:
            temp_delta = p.skin_temp_c - self.baseline.skin_temp_c
            self.last_temp_values.append((p.timestamp_ms, p.skin_temp_c))
            if len(self.last_temp_values) >= 2:
                t0, v0 = self.last_temp_values[0]
                t1, v1 = self.last_temp_values[-1]
                mins = (t1 - t0) / 60000.0
                if mins > 0:
                    temp_slope = (v1 - v0) / mins

        return ExtractedFeatures(
            timestamp_ms=p.timestamp_ms,
            heart_rate_bpm=self.current_bpm,
            heart_rate_delta_bpm=hr_delta,
            hrv_rmssd_ms=self.rpeak.rmssd(),
            ecg_anomaly_score=self.current_anomaly,
            signal_quality=quality,
            activity_g=activity_g,
            posture_change_dps=posture,
            posture_angle_deg=posture_angle_deg,
            pitch_deg=pitch_deg,
            roll_deg=roll_deg,
            posture_delta_deg=posture_delta_deg,
            impact_g=impact_g,
            inactivity_sec=inactivity,
            free_fall_detected=free_fall_detected,
            impact_detected=impact_detected,
            posture_change_detected=posture_change_detected,
            fall_candidate=fall_candidate,
            skin_temp_c=p.skin_temp_c,
            temp_delta_c=temp_delta,
            temp_slope_c_per_min=temp_slope,
            latitude=p.latitude, longitude=p.longitude,
            speed_mps=p.speed_mps, gps_valid=p.gps_valid,
        )

    def individual_risks(self, f: ExtractedFeatures) -> IndividualRisks:
        heart = 0.0
        if f.heart_rate_bpm is not None:
            delta = abs(f.heart_rate_bpm - self.baseline.heart_rate_bpm)
            heart = clip((delta - 10) / 50 * 100, 0, 100)

        ecg = clip(f.ecg_anomaly_score, 0, 100)

        temp = 0.0
        if f.temp_delta_c is not None:
            delta_score = clip((f.temp_delta_c - 0.5) / 2.0 * 70, 0, 70)
            slope_score = clip((f.temp_slope_c_per_min - 0.05) / 0.20 * 30, 0, 30)
            temp = clip(delta_score + slope_score, 0, 100)

        thresholds = self.config["prototype_thresholds"]
        fall = 0.0
        if f.free_fall_detected:
            fall += 10
        if f.impact_g >= thresholds["impact_g"]:
            fall += clip((f.impact_g - thresholds["impact_g"]) / 2.0 * 45 + 30, 0, 55)
        if f.posture_delta_deg >= thresholds["posture_change_deg"]:
            fall += clip(
                (f.posture_delta_deg - thresholds["posture_change_deg"]) / 30 * 25 + 10,
                0, 25
            )
        if f.inactivity_sec >= 5:
            fall += clip((f.inactivity_sec - 5) / 15 * 20, 0, 20)
        if f.fall_candidate:
            fall = max(fall, 85)

        activity = 0.0
        if f.inactivity_sec > 60:
            activity = clip((f.inactivity_sec - 60) / 240 * 100, 0, 100)
        elif f.activity_g > 0.8:
            activity = clip((f.activity_g - 0.8) / 1.2 * 100, 0, 100)

        return IndividualRisks(heart, ecg, temp, fall, activity)

    def assess(self, f: ExtractedFeatures, r: IndividualRisks) -> Assessment:
        thresholds = self.config["prototype_thresholds"]
        min_quality = thresholds["signal_quality_min"]
        fall_emergency = (
            f.fall_candidate
            and f.inactivity_sec >= thresholds["post_fall_inactivity_s"]
        )

        # 낙상 시퀀스 + 10초 무동작은 ECG 상태와 무관하게 EMERGENCY 우선.
        if not fall_emergency and (f.signal_quality < min_quality or f.heart_rate_bpm is None):
            return Assessment(f.timestamp_ms, RiskLevel.SENSOR_CHECK, 0.0, r,
                              ["ECG 전극 또는 신호 품질 확인 필요"],
                              latitude=f.latitude, longitude=f.longitude)

        reasons: list[str] = []
        corrected_heart = r.heart
        if f.activity_g >= self.config["prototype_thresholds"]["high_activity_g"]:
            corrected_heart *= 0.65
            if r.heart >= 40:
                reasons.append("높은 활동량을 고려해 심박 위험도 보정")

        context_bonus = 0.0
        if (f.heart_rate_bpm is not None
                and f.heart_rate_bpm > self.baseline.heart_rate_bpm + 30
                and f.activity_g < thresholds["low_activity_g"]):
            context_bonus += 12
            reasons.append("활동량이 낮은 상태에서 심박 상승")

        if corrected_heart >= 40: reasons.append("심박 위험도 상승")
        if r.ecg_anomaly >= 40: reasons.append("ECG RR 패턴 이상 후보")
        if r.temperature >= 40: reasons.append("피부온도 상승 추세")
        if r.fall_impact >= 50: reasons.append("충격/자세 변화/무동작 위험")
        if r.activity >= 50: reasons.append("비정상 활동 상태")

        emergency_rule = None
        if fall_emergency:
            emergency_rule = "자유낙하 + 충격 + 자세 변화 + 10초 무동작"
        elif r.ecg_anomaly >= 85 and corrected_heart >= 70:
            emergency_rule = "강한 ECG 이상 후보 + 높은 심박 위험"

        w = self.config["weights"]
        score = (
            corrected_heart * w["heart"] +
            r.ecg_anomaly * w["ecg_anomaly"] +
            r.temperature * w["temperature"] +
            r.fall_impact * w["fall_impact"] +
            r.activity * w["activity"] + context_bonus
        )
        score = clip(score, 0, 100)

        now_ms = f.timestamp_ms
        if emergency_rule:
            score = max(score, 90)
            if self._emergency_since_ms is None:
                self._emergency_since_ms = now_ms
        else:
            self._emergency_since_ms = None

        if score >= 50:
            if self._warning_since_ms is None:
                self._warning_since_ms = now_ms
            if self._caution_since_ms is None:
                self._caution_since_ms = now_ms
        elif score >= 30:
            self._warning_since_ms = None
            if self._caution_since_ms is None:
                self._caution_since_ms = now_ms
        else:
            self._warning_since_ms = None
            self._caution_since_ms = None

        p = self.config["persistence_seconds"]
        emergency_elapsed = 0 if self._emergency_since_ms is None else (now_ms - self._emergency_since_ms) / 1000.0
        warning_elapsed = 0 if self._warning_since_ms is None else (now_ms - self._warning_since_ms) / 1000.0
        caution_elapsed = 0 if self._caution_since_ms is None else (now_ms - self._caution_since_ms) / 1000.0

        if emergency_rule and (fall_emergency or emergency_elapsed >= p["emergency"]):
            level = RiskLevel.EMERGENCY
            reasons.append(f"긴급 규칙: {emergency_rule}")
        elif score >= 50 and warning_elapsed >= p["warning"]:
            level = RiskLevel.WARNING
        elif score >= 30 and caution_elapsed >= p["caution"]:
            level = RiskLevel.CAUTION
        else:
            level = RiskLevel.NORMAL

        corrected = IndividualRisks(corrected_heart, r.ecg_anomaly, r.temperature, r.fall_impact, r.activity)
        return Assessment(f.timestamp_ms, level, score, corrected, reasons, emergency_rule, f.latitude, f.longitude)

    def process(self, raw: RawSensorFrame) -> tuple[ExtractedFeatures, Assessment]:
        p = self.preprocess(raw)
        f = self.extract_features(p)
        r = self.individual_risks(f)
        return f, self.assess(f, r)

    @staticmethod
    def to_payload(f: ExtractedFeatures, a: Assessment) -> dict:
        return {
            "timestamp_ms": a.timestamp_ms,
            "risk": {"level": a.level.value, "score": round(a.total_score, 1),
                     "reasons": a.reasons, "emergency_rule": a.emergency_rule},
            "ecg": {"heart_rate_bpm": f.heart_rate_bpm,
                    "hr_change_bpm": f.heart_rate_delta_bpm,
                    "hrv_rmssd_ms": f.hrv_rmssd_ms,
                    "anomaly_score": round(f.ecg_anomaly_score, 1),
                    "signal_quality": round(f.signal_quality, 2)},
            "imu": {"activity_g": round(f.activity_g, 3),
                    "posture_change_dps": round(f.posture_change_dps, 1),
                    "posture_angle_deg": round(f.posture_angle_deg, 1),
                    "pitch_deg": round(f.pitch_deg, 1),
                    "roll_deg": round(f.roll_deg, 1),
                    "posture_delta_deg": round(f.posture_delta_deg, 1),
                    "impact_g": round(f.impact_g, 2),
                    "inactivity_sec": round(f.inactivity_sec, 1),
                    "free_fall_detected": f.free_fall_detected,
                    "impact_detected": f.impact_detected,
                    "posture_change_detected": f.posture_change_detected,
                    "fall_candidate": f.fall_candidate},
            "temperature": {"skin_temp_c": f.skin_temp_c,
                            "delta_c": f.temp_delta_c,
                            "slope_c_per_min": round(f.temp_slope_c_per_min, 3)},
            "gps": {"valid": f.gps_valid, "latitude": f.latitude,
                    "longitude": f.longitude, "speed_mps": f.speed_mps},
            "individual_risk": {
                "heart": round(a.individual.heart, 1),
                "ecg_anomaly": round(a.individual.ecg_anomaly, 1),
                "temperature": round(a.individual.temperature, 1),
                "fall_impact": round(a.individual.fall_impact, 1),
                "activity": round(a.individual.activity, 1),
            }
        }
