import 'dart:math';

class EcgOutput {
  final double filtered;
  final double? bpm;
  final double? rrMs;
  final double anomalyScore;
  const EcgOutput(this.filtered, this.bpm, this.rrMs, this.anomalyScore);
}

class EcgProcessor {
  final double fs;
  double xPrev = 0, hpPrev = 0, lpPrev = 0, prev = 0;
  bool initialized = false;
  double e2 = 0, e1 = 0, eBase = 1;
  int sampleIndex = 0;
  int? lastPeak;
  final List<double> rr = [];

  EcgProcessor({this.fs = 250});

  EcgOutput process(int raw) {
    final x = raw.toDouble();
    if (!initialized) {
      initialized = true;
      xPrev = x;
      sampleIndex++;
      return const EcgOutput(0, null, null, 0);
    }

    final dt = 1 / fs;
    final hpRc = 1 / (2 * pi * 0.5);
    final hpA = hpRc / (hpRc + dt);
    final hp = hpA * (hpPrev + x - xPrev);
    xPrev = x; hpPrev = hp;

    final lpRc = 1 / (2 * pi * 35);
    final lpA = dt / (lpRc + dt);
    final filtered = lpPrev + lpA * (hp - lpPrev);
    lpPrev = filtered;

    final d = filtered - prev;
    prev = filtered;
    final e = d * d;
    eBase = 0.997 * eBase + 0.003 * e;
    final threshold = max(20.0, eBase * 4.5);
    final candidate = e1 > e2 && e1 >= e && e1 > threshold;

    double? rrMs;
    double? bpm;
    final refractory = (fs * 0.25).round();
    if (candidate) {
      final peak = sampleIndex - 1;
      if (lastPeak == null || peak - lastPeak! >= refractory) {
        if (lastPeak != null) {
          rrMs = (peak - lastPeak!) * 1000 / fs;
          if (rrMs >= 300 && rrMs <= 2000) {
            rr.add(rrMs);
            if (rr.length > 20) rr.removeAt(0);
            final recent = rr.length > 7 ? rr.sublist(rr.length - 7) : [...rr];
            recent.sort();
            bpm = 60000 / recent[recent.length ~/ 2];
          }
        }
        lastPeak = peak;
      }
    }
    e2 = e1; e1 = e; sampleIndex++;

    double anomaly = 0;
    if (rr.length >= 5) {
      final sorted = [...rr]..sort();
      final med = sorted[sorted.length ~/ 2];
      final dev = rr.map((v) => (v - med).abs()).toList()..sort();
      final mad = dev[dev.length ~/ 2];
      anomaly = (((mad / max(med, 1)) - 0.05) / 0.20 * 100).clamp(0, 100).toDouble();
    }

    return EcgOutput(filtered, bpm, rrMs, anomaly);
  }
}
