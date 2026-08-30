import math
import random
from pipeline import WorkerSafetyPipeline, RawSensorFrame, PersonalBaseline


def synthetic_ecg(sample_no: int, fs: int = 250, bpm: float = 80) -> float:
    period = 60.0 / bpm
    t = sample_no / fs
    phase = (t % period) / period
    baseline = 2048 + 15 * math.sin(2 * math.pi * 0.3 * t)
    r = 700 * math.exp(-((phase - 0.12) / 0.018) ** 2)
    q = -100 * math.exp(-((phase - 0.09) / 0.015) ** 2)
    s = -180 * math.exp(-((phase - 0.15) / 0.020) ** 2)
    return baseline + q + r + s + random.uniform(-12, 12)


def main():
    fs = 250
    pipeline = WorkerSafetyPipeline(
        baseline=PersonalBaseline(heart_rate_bpm=78, skin_temp_c=35.4, hrv_rmssd_ms=45)
    )

    for n in range(45 * fs):
        sec = n / fs
        if sec < 15:
            bpm = 78
            ax, ay, az = 0.02, 0.01, 1.00
            gyro = 8
            temp = 35.4 + sec * 0.002
        elif sec < 30:
            bpm = 110
            ax = random.uniform(-0.35, 0.35)
            ay = random.uniform(-0.35, 0.35)
            az = 1.0 + random.uniform(-0.35, 0.35)
            gyro = 60
            temp = 35.7 + (sec - 15) * 0.02
        else:
            bpm = 118
            if sec < 30.5:
                ax, ay, az = 2.7, 0.6, 1.2
                gyro = 180
            else:
                ax, ay, az = 0.0, 0.0, 1.0
                gyro = 2
            temp = 36.1

        frame = RawSensorFrame(
            timestamp_ms=int(sec * 1000),
            ecg_raw=synthetic_ecg(n, fs, bpm),
            lead_off=False,
            ax_g=ax, ay_g=ay, az_g=az,
            gx_dps=gyro, gy_dps=0, gz_dps=0,
            skin_temp_c=temp if n % fs == 0 else None,
            latitude=36.3504 if n % fs == 0 else None,
            longitude=127.3845 if n % fs == 0 else None,
            speed_mps=1.1 if 15 <= sec < 30 else 0.0,
            gps_valid=(n % fs == 0),
        )
        features, assessment = pipeline.process(frame)
        if n % fs == 0:
            print(
                f"{sec:05.1f}s | HR={features.heart_rate_bpm!s:>7} | "
                f"motion={features.activity_g:.2f}g | impact={features.impact_g:.2f}g | "
                f"inactive={features.inactivity_sec:.1f}s | score={assessment.total_score:5.1f} | "
                f"{assessment.level.value}"
            )


if __name__ == "__main__":
    main()
