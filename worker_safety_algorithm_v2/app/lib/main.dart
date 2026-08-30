import 'package:flutter/material.dart';
import 'models/worker_state.dart';
import 'services/risk_engine.dart';

void main() => runApp(const WorkerSafetyApp());

class WorkerSafetyApp extends StatelessWidget {
  const WorkerSafetyApp({super.key});
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: ThemeData(useMaterial3: true, colorSchemeSeed: Colors.blue),
      home: const DashboardPage(),
    );
  }
}

class DashboardPage extends StatefulWidget {
  const DashboardPage({super.key});
  @override
  State<DashboardPage> createState() => _DashboardPageState();
}

class _DashboardPageState extends State<DashboardPage> {
  final engine = RiskEngine(baselineBpm: 78, baselineSkinTempC: 35.4);
  final features = const SensorFeatures(
    timestampMs: 10000,
    bpm: 82, hrvRmssdMs: 46, ecgAnomaly: 8, signalQuality: 0.94,
    activityG: 0.12, impactG: 1.03, postureChangeDps: 12,
    inactivitySec: 0, fallCandidate: false,
    skinTempC: 35.8, tempDeltaC: 0.4, tempSlopeCPerMin: 0.02,
    latitude: 36.3504, longitude: 127.3845, speedMps: 1.1,
  );

  Color levelColor(WorkerRiskLevel level) {
    switch (level) {
      case WorkerRiskLevel.normal: return Colors.green;
      case WorkerRiskLevel.caution: return Colors.amber;
      case WorkerRiskLevel.warning: return Colors.orange;
      case WorkerRiskLevel.emergency: return Colors.red;
      case WorkerRiskLevel.sensorCheck: return Colors.blueGrey;
    }
  }

  String levelText(WorkerRiskLevel level) => level.name.toUpperCase();

  Widget metric(String title, String value) => SizedBox(
    width: 165,
    child: Card(child: Padding(
      padding: const EdgeInsets.all(14),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(title, style: const TextStyle(color: Colors.grey)),
        const SizedBox(height: 6),
        Text(value, style: const TextStyle(fontSize: 19, fontWeight: FontWeight.w600)),
      ]),
    )),
  );

  @override
  Widget build(BuildContext context) {
    final result = engine.evaluate(features);
    return Scaffold(
      appBar: AppBar(title: const Text('산업안전 실시간 모니터링'), actions: const [
        Padding(padding: EdgeInsets.only(right: 16), child: Icon(Icons.bluetooth_connected))
      ]),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        Card(child: ListTile(
          leading: CircleAvatar(backgroundColor: levelColor(result.level), child: const Icon(Icons.shield, color: Colors.white)),
          title: Text('A-001 · ${levelText(result.level)}'),
          subtitle: Text('종합 위험점수 ${result.score.toStringAsFixed(0)} / 100'),
        )),
        Wrap(spacing: 8, runSpacing: 8, children: [
          metric('심박수', '${features.bpm?.round()} BPM'),
          metric('HRV', '${features.hrvRmssdMs?.round()} ms'),
          metric('피부온도', '${features.skinTempC?.toStringAsFixed(1)} °C'),
          metric('활동량', '${features.activityG.toStringAsFixed(2)} g'),
          metric('충격량', '${features.impactG.toStringAsFixed(2)} g'),
          metric('ECG 신호품질', '${(features.signalQuality * 100).round()} %'),
        ]),
        const SizedBox(height: 16),
        const Text('실시간 ECG', style: TextStyle(fontWeight: FontWeight.bold)),
        const Card(child: SizedBox(height: 180, child: Center(child: Text('ECG 그래프 영역')))),
        Card(child: ListTile(
          leading: const Icon(Icons.location_on),
          title: const Text('현재 위치'),
          subtitle: Text('${features.latitude}, ${features.longitude}'),
          trailing: Text('${((features.speedMps ?? 0) * 3.6).toStringAsFixed(1)} km/h'),
        )),
        Card(child: Padding(padding: const EdgeInsets.all(16), child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('개별 위험도', style: TextStyle(fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            for (final e in result.individual.entries)
              Text('${e.key}: ${e.value.toStringAsFixed(0)} / 100'),
            const Divider(),
            const Text('상황 판단 근거', style: TextStyle(fontWeight: FontWeight.bold)),
            for (final reason in result.reasons) Text('• $reason'),
          ],
        ))),
        if (result.level == WorkerRiskLevel.emergency)
          FilledButton.icon(
            style: FilledButton.styleFrom(backgroundColor: Colors.red, minimumSize: const Size.fromHeight(52)),
            onPressed: () {},
            icon: const Icon(Icons.emergency),
            label: const Text('GPS 위치 포함 긴급 알림 전송'),
          ),
      ]),
      bottomNavigationBar: const NavigationBar(selectedIndex: 0, destinations: [
        NavigationDestination(icon: Icon(Icons.monitor_heart), label: '실시간'),
        NavigationDestination(icon: Icon(Icons.people), label: '작업자'),
        NavigationDestination(icon: Icon(Icons.map), label: '위치'),
        NavigationDestination(icon: Icon(Icons.notifications), label: '알림'),
        NavigationDestination(icon: Icon(Icons.settings), label: '설정'),
      ]),
    );
  }
}
