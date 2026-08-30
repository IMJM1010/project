enum WorkerRiskLevel { normal, caution, warning, emergency, sensorCheck }

class SensorFeatures {
  final int timestampMs;
  final double? bpm;
  final double? hrvRmssdMs;
  final double ecgAnomaly;
  final double signalQuality;
  final double activityG;
  final double impactG;
  final double postureChangeDps;
  final double inactivitySec;
  final bool fallCandidate;
  final double? skinTempC;
  final double? tempDeltaC;
  final double tempSlopeCPerMin;
  final double? latitude;
  final double? longitude;
  final double? speedMps;

  const SensorFeatures({
    required this.timestampMs,
    this.bpm,
    this.hrvRmssdMs,
    required this.ecgAnomaly,
    required this.signalQuality,
    required this.activityG,
    required this.impactG,
    required this.postureChangeDps,
    required this.inactivitySec,
    required this.fallCandidate,
    this.skinTempC,
    this.tempDeltaC,
    required this.tempSlopeCPerMin,
    this.latitude,
    this.longitude,
    this.speedMps,
  });
}

class RiskResult {
  final WorkerRiskLevel level;
  final double score;
  final Map<String, double> individual;
  final List<String> reasons;
  final String? emergencyRule;

  const RiskResult({
    required this.level,
    required this.score,
    required this.individual,
    required this.reasons,
    this.emergencyRule,
  });
}
