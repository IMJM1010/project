import argparse
import csv
import math
import random
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

from pipeline import WorkerSafetyPipeline, RawSensorFrame, PersonalBaseline, RiskLevel


FS = 250
BASELINE = PersonalBaseline(
    heart_rate_bpm=78,
    skin_temp_c=35.4,
    hrv_rmssd_ms=45,
)

LEVEL_RANK = {
    RiskLevel.SENSOR_CHECK: 0,
    RiskLevel.NORMAL: 1,
    RiskLevel.CAUTION: 2,
    RiskLevel.WARNING: 3,
    RiskLevel.EMERGENCY: 4,
}


@dataclass
class SensorState:
    bpm: float = 78.0
    ax: float = 0.02
    ay: float = 0.01
    az: float = 1.00
    gx: float = 5.0
    gy: float = 0.0
    gz: float = 0.0
    temp: float = 35.4
    lead_off: bool = False
    speed_mps: float = 0.0
    irregular_ecg: bool = False


@dataclass
class Scenario:
    name: str
    description: str
    duration_s: float
    generator: Callable[[float, random.Random], SensorState]


def synthetic_ecg(
    sample_no: int,
    fs: int,
    bpm: float,
    rng: random.Random,
    irregular: bool = False,
) -> float:
    """
    프로토타입 테스트용 합성 ECG.
    irregular=True일 때는 RR 간격 변동을 크게 만들기 위해 심박 주기를
    시간 구간별로 바꾼다. 의료적 부정맥 파형을 재현하는 용도가 아니다.
    """
    t = sample_no / fs

    if irregular:
        # 0.8초마다 서로 다른 심박 주기를 사용해 RR 불규칙성 스트레스 테스트.
        pattern = [58.0, 128.0, 72.0, 145.0, 64.0, 118.0]
        bpm = pattern[int(t / 0.8) % len(pattern)]

    period = 60.0 / max(bpm, 30.0)
    phase = (t % period) / period

    baseline = 2048 + 15 * math.sin(2 * math.pi * 0.3 * t)
    p = 60 * math.exp(-((phase - 0.03) / 0.025) ** 2)
    q = -100 * math.exp(-((phase - 0.09) / 0.015) ** 2)
    r = 700 * math.exp(-((phase - 0.12) / 0.018) ** 2)
    s = -180 * math.exp(-((phase - 0.15) / 0.020) ** 2)
    twave = 120 * math.exp(-((phase - 0.38) / 0.060) ** 2)
    return baseline + p + q + r + s + twave + rng.uniform(-12, 12)


def normal_rest(sec: float, rng: random.Random) -> SensorState:
    return SensorState(
        bpm=78,
        ax=0.02,
        ay=0.01,
        az=1.00,
        gx=5,
        temp=35.4,
    )


def light_work(sec: float, rng: random.Random) -> SensorState:
    return SensorState(
        bpm=95,
        ax=rng.uniform(-0.12, 0.12),
        ay=rng.uniform(-0.12, 0.12),
        az=1.0 + rng.uniform(-0.12, 0.12),
        gx=25,
        temp=35.6,
        speed_mps=0.7,
    )


def heavy_work(sec: float, rng: random.Random) -> SensorState:
    return SensorState(
        bpm=130,
        ax=rng.uniform(-0.45, 0.45),
        ay=rng.uniform(-0.45, 0.45),
        az=1.0 + rng.uniform(-0.45, 0.45),
        gx=70,
        temp=36.0,
        speed_mps=1.3,
    )


def high_hr_at_rest(sec: float, rng: random.Random) -> SensorState:
    return SensorState(
        bpm=145,
        ax=0.02,
        ay=0.01,
        az=1.00,
        gx=4,
        temp=35.6,
    )


def irregular_ecg_at_rest(sec: float, rng: random.Random) -> SensorState:
    return SensorState(
        bpm=90,
        ax=0.02,
        ay=0.01,
        az=1.00,
        gx=4,
        temp=35.5,
        irregular_ecg=True,
    )


def rising_skin_temp(sec: float, rng: random.Random) -> SensorState:
    # 35.4 -> 약 38.0 C까지 점진 상승
    temp = min(38.0, 35.4 + sec * 0.09)
    return SensorState(
        bpm=98,
        ax=0.03,
        ay=0.01,
        az=1.00,
        gx=5,
        temp=temp,
    )


def lead_off_test(sec: float, rng: random.Random) -> SensorState:
    return SensorState(
        bpm=78,
        ax=0.02,
        ay=0.01,
        az=1.00,
        gx=5,
        temp=35.4,
        lead_off=(sec >= 6.0),
    )


def free_fall_only(sec: float, rng: random.Random) -> SensorState:
    if 6.0 <= sec < 6.25:
        return SensorState(bpm=90, ax=0.10, ay=0.10, az=0.10, gx=80, temp=35.5)
    return SensorState(bpm=90, ax=0.02, ay=0.01, az=1.00, gx=5, temp=35.5)


def impact_only(sec: float, rng: random.Random) -> SensorState:
    if 6.0 <= sec < 6.30:
        return SensorState(bpm=95, ax=2.8, ay=0.4, az=1.1, gx=170, temp=35.5)
    return SensorState(bpm=95, ax=0.02, ay=0.01, az=1.00, gx=5, temp=35.5)


def fall_then_recover(sec: float, rng: random.Random) -> SensorState:
    if 6.0 <= sec < 6.20:
        return SensorState(bpm=105, ax=0.10, ay=0.10, az=0.10, gx=100, temp=35.7)
    if 6.20 <= sec < 6.50:
        return SensorState(bpm=110, ax=2.7, ay=0.6, az=1.2, gx=180, temp=35.7)
    if 6.50 <= sec < 11.0:
        return SensorState(bpm=110, ax=1.0, ay=0.0, az=0.0, gx=2, temp=35.7)
    return SensorState(
        bpm=100,
        ax=rng.uniform(-0.18, 0.18),
        ay=rng.uniform(-0.18, 0.18),
        az=1.0 + rng.uniform(-0.18, 0.18),
        gx=30,
        temp=35.7,
    )


def fall_and_inactive(sec: float, rng: random.Random) -> SensorState:
    if 6.0 <= sec < 6.20:
        return SensorState(bpm=110, ax=0.10, ay=0.10, az=0.10, gx=100, temp=35.8)
    if 6.20 <= sec < 6.50:
        return SensorState(bpm=118, ax=2.7, ay=0.6, az=1.2, gx=180, temp=35.8)
    if sec >= 6.50:
        return SensorState(bpm=118, ax=1.0, ay=0.0, az=0.0, gx=2, temp=35.8)
    return SensorState(bpm=90, ax=0.02, ay=0.01, az=1.00, gx=5, temp=35.6)


def combined_risk(sec: float, rng: random.Random) -> SensorState:
    if sec < 8.0:
        return SensorState(bpm=145, ax=0.02, ay=0.01, az=1.0, gx=5, temp=36.5)
    if 8.0 <= sec < 8.20:
        return SensorState(bpm=145, ax=0.10, ay=0.10, az=0.10, gx=110, temp=36.8)
    if 8.20 <= sec < 8.50:
        return SensorState(bpm=150, ax=3.0, ay=0.6, az=1.1, gx=210, temp=36.8)
    return SensorState(
        bpm=150,
        ax=1.0,
        ay=0.0,
        az=0.0,
        gx=2,
        temp=min(38.0, 36.8 + max(0.0, sec - 8.5) * 0.03),
        irregular_ecg=True,
    )


SCENARIOS = [
    Scenario("normal_rest", "정상 안정 상태", 15.0, normal_rest),
    Scenario("light_work", "가벼운 작업", 15.0, light_work),
    Scenario("heavy_work", "높은 활동량 + 높은 심박", 15.0, heavy_work),
    Scenario("high_hr_rest", "무동작 상태의 높은 심박", 18.0, high_hr_at_rest),
    Scenario("irregular_ecg", "RR 불규칙성 스트레스 테스트", 25.0, irregular_ecg_at_rest),
    Scenario("temp_rise", "피부온도 지속 상승", 30.0, rising_skin_temp),
    Scenario("lead_off", "ECG 전극 이탈", 12.0, lead_off_test),
    Scenario("free_fall_only", "자유낙하만 발생, 충격 없음", 15.0, free_fall_only),
    Scenario("impact_only", "충격만 발생, 선행 자유낙하 없음", 15.0, impact_only),
    Scenario("fall_recovery", "낙상 시퀀스 후 10초 전에 움직임 회복", 20.0, fall_then_recover),
    Scenario("fall_inactive", "낙상 시퀀스 후 10초 이상 무동작", 24.0, fall_and_inactive),
    Scenario("combined_risk", "고심박 + ECG 불규칙 + 온도상승 + 낙상", 25.0, combined_risk),
]


def level_name(level: RiskLevel) -> str:
    return level.value


def run_scenario(
    scenario: Scenario,
    seed: int,
    detailed: bool,
    csv_writer: csv.DictWriter | None,
) -> dict:
    rng = random.Random(seed)
    pipeline = WorkerSafetyPipeline(baseline=BASELINE)

    max_score = 0.0
    max_hr = 0.0
    max_motion = 0.0
    max_impact = 0.0
    max_inactive = 0.0
    max_ecg_risk = 0.0
    max_temp_risk = 0.0
    max_fall_risk = 0.0
    highest_level = RiskLevel.SENSOR_CHECK
    emergency_seen = False
    last_level = RiskLevel.SENSOR_CHECK

    if detailed:
        print(f"\n=== {scenario.name}: {scenario.description} ===")
        print(
            " sec |    HR | motion |   SVM | inactive | ECG_R | TEMP_R | FALL_R | "
            "score | level       | FF IMP POST FALL"
        )
        print("-" * 112)

    total_samples = int(scenario.duration_s * FS)
    for n in range(total_samples):
        sec = n / FS
        s = scenario.generator(sec, rng)
        ecg_raw = None if s.lead_off else synthetic_ecg(
            n, FS, s.bpm, rng, irregular=s.irregular_ecg
        )

        frame = RawSensorFrame(
            timestamp_ms=int(sec * 1000),
            ecg_raw=ecg_raw,
            lead_off=s.lead_off,
            ax_g=s.ax,
            ay_g=s.ay,
            az_g=s.az,
            gx_dps=s.gx,
            gy_dps=s.gy,
            gz_dps=s.gz,
            skin_temp_c=s.temp if n % FS == 0 else None,
            latitude=36.3504 if n % FS == 0 else None,
            longitude=127.3845 if n % FS == 0 else None,
            speed_mps=s.speed_mps,
            gps_valid=(n % FS == 0),
        )

        features, assessment = pipeline.process(frame)

        max_score = max(max_score, assessment.total_score)
        if features.heart_rate_bpm is not None:
            max_hr = max(max_hr, features.heart_rate_bpm)
        max_motion = max(max_motion, features.activity_g)
        max_impact = max(max_impact, features.impact_g)
        max_inactive = max(max_inactive, features.inactivity_sec)
        max_ecg_risk = max(max_ecg_risk, assessment.individual.ecg_anomaly)
        max_temp_risk = max(max_temp_risk, assessment.individual.temperature)
        max_fall_risk = max(max_fall_risk, assessment.individual.fall_impact)

        if LEVEL_RANK[assessment.level] > LEVEL_RANK[highest_level]:
            highest_level = assessment.level
        emergency_seen = emergency_seen or assessment.level == RiskLevel.EMERGENCY
        last_level = assessment.level

        # 1초마다 상세 기록/CSV 저장
        if n % FS == 0:
            row = {
                "scenario": scenario.name,
                "sec": round(sec, 1),
                "heart_rate_bpm": (
                    "" if features.heart_rate_bpm is None
                    else round(features.heart_rate_bpm, 1)
                ),
                "motion_g": round(features.activity_g, 3),
                "svm_impact_g": round(features.impact_g, 3),
                "pitch_deg": round(features.pitch_deg, 1),
                "roll_deg": round(features.roll_deg, 1),
                "posture_delta_deg": round(features.posture_delta_deg, 1),
                "inactive_s": round(features.inactivity_sec, 1),
                "free_fall": features.free_fall_detected,
                "impact_detected": features.impact_detected,
                "posture_changed": features.posture_change_detected,
                "fall_candidate": features.fall_candidate,
                "heart_risk": round(assessment.individual.heart, 1),
                "ecg_risk": round(assessment.individual.ecg_anomaly, 1),
                "temperature_risk": round(assessment.individual.temperature, 1),
                "fall_risk": round(assessment.individual.fall_impact, 1),
                "activity_risk": round(assessment.individual.activity, 1),
                "score": round(assessment.total_score, 1),
                "level": assessment.level.value,
                "emergency_rule": assessment.emergency_rule or "",
            }
            if csv_writer is not None:
                csv_writer.writerow(row)

            if detailed:
                hr_text = (
                    "   None"
                    if features.heart_rate_bpm is None
                    else f"{features.heart_rate_bpm:7.1f}"
                )
                print(
                    f"{sec:4.0f} | {hr_text} | {features.activity_g:6.2f} | "
                    f"{features.impact_g:5.2f} | {features.inactivity_sec:8.1f} | "
                    f"{assessment.individual.ecg_anomaly:5.1f} | "
                    f"{assessment.individual.temperature:6.1f} | "
                    f"{assessment.individual.fall_impact:6.1f} | "
                    f"{assessment.total_score:5.1f} | "
                    f"{assessment.level.value:11s} | "
                    f"{int(features.free_fall_detected)}  "
                    f"{int(features.impact_detected)}   "
                    f"{int(features.posture_change_detected)}    "
                    f"{int(features.fall_candidate)}"
                )

    return {
        "scenario": scenario.name,
        "description": scenario.description,
        "highest_level": highest_level.value,
        "final_level": last_level.value,
        "max_score": round(max_score, 1),
        "max_hr": round(max_hr, 1),
        "max_motion": round(max_motion, 2),
        "max_impact": round(max_impact, 2),
        "max_inactive": round(max_inactive, 1),
        "max_ecg_risk": round(max_ecg_risk, 1),
        "max_temp_risk": round(max_temp_risk, 1),
        "max_fall_risk": round(max_fall_risk, 1),
        "emergency_seen": emergency_seen,
    }


def print_summary(results: list[dict]) -> None:
    print("\n\n==================== 시나리오 요약 ====================")
    print(
        "scenario         | 최고상태    | maxScore | HRmax | motion | impact | "
        "inactive | ECG_R | TEMP_R | FALL_R"
    )
    print("-" * 111)
    for r in results:
        print(
            f"{r['scenario'][:16]:16s} | {r['highest_level']:11s} | "
            f"{r['max_score']:8.1f} | {r['max_hr']:5.1f} | "
            f"{r['max_motion']:6.2f} | {r['max_impact']:6.2f} | "
            f"{r['max_inactive']:8.1f} | {r['max_ecg_risk']:5.1f} | "
            f"{r['max_temp_risk']:6.1f} | {r['max_fall_risk']:6.1f}"
        )


def make_constant_scenario(
    name: str,
    bpm: float,
    motion_g: float,
    temp_c: float,
    duration_s: float = 14.0,
) -> Scenario:
    def generator(sec: float, rng: random.Random) -> SensorState:
        # total_g ~= 1 + motion_g가 되도록 Z축에 값을 배치.
        return SensorState(
            bpm=bpm,
            ax=0.0,
            ay=0.0,
            az=1.0 + motion_g,
            gx=5,
            temp=temp_c,
        )

    return Scenario(
        name=name,
        description=f"BPM={bpm}, motion={motion_g}g, temp={temp_c}C",
        duration_s=duration_s,
        generator=generator,
    )


def run_value_sweeps(seed: int) -> None:
    print("\n\n==================== 값별 추가 테스트 ====================")

    print("\n[심박수 테스트 - 안정 상태]")
    print("BPM | 최고상태    | maxScore | HR_Risk")
    print("-" * 44)
    for bpm in [55, 65, 78, 90, 105, 120, 135, 150, 165]:
        sc = make_constant_scenario(f"bpm_{bpm}", bpm, 0.0, 35.4)
        result = run_scenario(sc, seed + int(bpm), False, None)

        # 마지막 위험도 값을 보기 위한 짧은 재실행
        rng = random.Random(seed + int(bpm))
        p = WorkerSafetyPipeline(baseline=BASELINE)
        last_a = None
        for n in range(int(sc.duration_s * FS)):
            s = sc.generator(n / FS, rng)
            f, last_a = p.process(
                RawSensorFrame(
                    timestamp_ms=int(n / FS * 1000),
                    ecg_raw=synthetic_ecg(n, FS, s.bpm, rng),
                    ax_g=s.ax, ay_g=s.ay, az_g=s.az,
                    gx_dps=s.gx, gy_dps=0, gz_dps=0,
                    skin_temp_c=s.temp if n % FS == 0 else None,
                )
            )
        hr_risk = 0.0 if last_a is None else last_a.individual.heart
        print(
            f"{bpm:3d} | {result['highest_level']:11s} | "
            f"{result['max_score']:8.1f} | {hr_risk:7.1f}"
        )

    print("\n[활동량 테스트 - BPM 130]")
    print("motion(g) | 최고상태    | maxScore")
    print("-" * 39)
    for motion in [0.00, 0.04, 0.08, 0.20, 0.35, 0.50, 0.80, 1.20]:
        sc = make_constant_scenario(
            f"motion_{motion:.2f}", 130, motion, 35.4
        )
        result = run_scenario(sc, seed + int(motion * 1000), False, None)
        print(
            f"{motion:9.2f} | {result['highest_level']:11s} | "
            f"{result['max_score']:8.1f}"
        )

    print("\n[피부온도 테스트 - BPM 78, 안정 상태]")
    print("temp(C) | 최고상태    | maxScore | TEMP_R(max)")
    print("-" * 49)
    for temp in [34.5, 35.0, 35.4, 36.0, 36.5, 37.0, 37.5, 38.0]:
        sc = make_constant_scenario(
            f"temp_{temp:.1f}", 78, 0.0, temp
        )
        result = run_scenario(sc, seed + int(temp * 10), False, None)
        print(
            f"{temp:7.1f} | {result['highest_level']:11s} | "
            f"{result['max_score']:8.1f} | {result['max_temp_risk']:11.1f}"
        )


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Worker Safety Algorithm multi-scenario simulator"
    )
    parser.add_argument(
        "--scenario",
        default="all",
        help="실행할 시나리오 이름. 기본값 all",
    )
    parser.add_argument(
        "--detailed",
        action="store_true",
        help="1초 단위 상세 로그 출력",
    )
    parser.add_argument(
        "--no-sweep",
        action="store_true",
        help="BPM/활동량/온도 값별 추가 테스트 생략",
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=42,
        help="재현 가능한 난수 seed",
    )
    args = parser.parse_args()

    if args.scenario == "all":
        selected = SCENARIOS
    else:
        selected = [s for s in SCENARIOS if s.name == args.scenario]
        if not selected:
            names = ", ".join(s.name for s in SCENARIOS)
            raise SystemExit(
                f"알 수 없는 시나리오: {args.scenario}\n사용 가능: {names}"
            )

    output_path = Path(__file__).with_name("simulation_results.csv")
    fieldnames = [
        "scenario", "sec", "heart_rate_bpm", "motion_g", "svm_impact_g",
        "pitch_deg", "roll_deg", "posture_delta_deg", "inactive_s",
        "free_fall", "impact_detected", "posture_changed", "fall_candidate",
        "heart_risk", "ecg_risk", "temperature_risk", "fall_risk",
        "activity_risk", "score", "level", "emergency_rule",
    ]

    results = []
    with output_path.open("w", newline="", encoding="utf-8-sig") as fp:
        writer = csv.DictWriter(fp, fieldnames=fieldnames)
        writer.writeheader()
        for i, scenario in enumerate(selected):
            result = run_scenario(
                scenario,
                seed=args.seed + i,
                detailed=args.detailed,
                csv_writer=writer,
            )
            results.append(result)

    print_summary(results)
    print(f"\n1초 단위 전체 결과 CSV 저장: {output_path}")

    if not args.no_sweep and args.scenario == "all":
        run_value_sweeps(args.seed)

    print("\n상세 낙상 테스트 예시:")
    print("  python simulator.py --scenario fall_inactive --detailed")
    print("\n전체 시나리오를 상세 출력:")
    print("  python simulator.py --detailed")
    print("\n값별 추가 테스트를 생략:")
    print("  python simulator.py --no-sweep")


if __name__ == "__main__":
    main()
