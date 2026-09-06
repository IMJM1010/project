import '../models/worker_state.dart';

/// 이미지의 4~6단계(개별위험도 → 상황보정 → 최종분류)를 앱에서 수행하는 버전.
/// 프로젝트 초기 휴리스틱이며 의료 진단 기준이 아닙니다.
class RiskEngine {
  final double baselineBpm;
  final double baselineSkinTempC;

  int? cautionSinceMs;
  int? warningSinceMs;
  int? emergencySinceMs;

  RiskEngine({
    this.baselineBpm = 80,
    this.baselineSkinTempC = 35.5,
  });

  double clip(double v, double lo, double hi) =>
      v < lo ? lo : (v > hi ? hi : v);

  RiskResult evaluate(SensorFeatures f) {
    if (f.signalQuality < 0.35 || f.bpm == null) {
      cautionSinceMs = null;
      warningSinceMs = null;
      emergencySinceMs = null;
      return const RiskResult(
        level: WorkerRiskLevel.sensorCheck,
        score: 0,
        individual: {},
        reasons: ['ECG 전극 또는 신호 품질 확인 필요'],
      );
    }

    final bpm = f.bpm!;

    var heart = clip(((bpm - baselineBpm).abs() - 10) / 50 * 100, 0, 100);
    final ecg = clip(f.ecgAnomaly, 0, 100);

    var temperature = 0.0;
    if (f.tempDeltaC != null) {
      final deltaScore = clip((f.tempDeltaC! - 0.5) / 2.0 * 70, 0, 70);
      final slopeScore = clip((f.tempSlopeCPerMin - 0.05) / 0.20 * 30, 0, 30);
      temperature = clip(deltaScore + slopeScore, 0, 100);
    }

    var fallImpact = 0.0;
    if (f.impactG >= 1.5) {
      fallImpact += clip((f.impactG - 1.5) / 2.0 * 55, 0, 55);
    }
    if (f.postureChangeDps >= 80) {
      fallImpact += clip((f.postureChangeDps - 80) / 220 * 25, 0, 25);
    }
    if (f.inactivitySec >= 5) {
      fallImpact += clip((f.inactivitySec - 5) / 15 * 20, 0, 20);
    }
    if (f.fallCandidate && fallImpact < 85) fallImpact = 85;

    var activity = 0.0;
    if (f.inactivitySec > 60) {
      activity = clip((f.inactivitySec - 60) / 240 * 100, 0, 100);
    } else if (f.activityG > 0.8) {
      activity = clip((f.activityG - 0.8) / 1.2 * 100, 0, 100);
    }

    final reasons = <String>[];
    if (f.activityG >= 0.35) {
      heart *= 0.65;
      reasons.add('활동량 기반 심박 위험도 보정');
    }

    var contextBonus = 0.0;
    if (bpm > baselineBpm + 30 && f.activityG < 0.08) {
      contextBonus = 12;
      reasons.add('활동량이 낮은 상태에서 심박 상승');
    }

    String? emergencyRule;
    if (f.fallCandidate && f.inactivitySec >= 8) {
      emergencyRule = '낙상 후보 + 이후 무동작';
    } else if (ecg >= 85 && heart >= 70) {
      emergencyRule = '강한 ECG 이상 후보 + 높은 심박 위험';
    } else if (fallImpact >= 95) {
      emergencyRule = '매우 큰 충격/낙상 위험';
    }

    var score =
        heart * 0.30 +
        ecg * 0.30 +
        temperature * 0.10 +
        fallImpact * 0.30 +
        activity * 0.10 +
        contextBonus;
    score = clip(score, 0, 100);

    final now = f.timestampMs;
    if (emergencyRule != null) {
      if (score < 90) score = 90;
      emergencySinceMs ??= now;
    } else {
      emergencySinceMs = null;
    }

    if (score >= 50) {
      warningSinceMs ??= now;
      cautionSinceMs ??= now;
    } else if (score >= 30) {
      warningSinceMs = null;
      cautionSinceMs ??= now;
    } else {
      warningSinceMs = null;
      cautionSinceMs = null;
    }

    final emergencySec = emergencySinceMs == null ? 0.0 : (now - emergencySinceMs!) / 1000.0;
    final warningSec = warningSinceMs == null ? 0.0 : (now - warningSinceMs!) / 1000.0;
    final cautionSec = cautionSinceMs == null ? 0.0 : (now - cautionSinceMs!) / 1000.0;

    WorkerRiskLevel level;
    if (emergencyRule != null && emergencySec >= 3) {
      level = WorkerRiskLevel.emergency;
      reasons.add('긴급 규칙: $emergencyRule');
    } else if (score >= 50 && warningSec >= 5) {
      level = WorkerRiskLevel.warning;
    } else if (score >= 30 && cautionSec >= 5) {
      level = WorkerRiskLevel.caution;
    } else {
      level = WorkerRiskLevel.normal;
    }

    return RiskResult(
      level: level,
      score: score,
      individual: {
        '심박': heart,
        'ECG 이상': ecg,
        '피부온도': temperature,
        '낙상/충격': fallImpact,
        '활동상태': activity,
      },
      reasons: reasons.isEmpty ? ['특이사항 없음'] : reasons,
      emergencyRule: emergencyRule,
    );
  }
}
