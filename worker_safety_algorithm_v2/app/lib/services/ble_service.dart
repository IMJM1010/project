import 'dart:async';
import 'package:flutter_blue_plus/flutter_blue_plus.dart';

class TelemetryPacket {
  final bool leadOff, gpsValid, tempValid, imuValid;
  final double? skinTempC;
  final double axG, ayG, azG, gxDps, gyDps, gzDps;
  final double? latitude, longitude, speedMps;
  final int timestampMs;

  const TelemetryPacket({required this.leadOff, required this.gpsValid,
    required this.tempValid, required this.imuValid, required this.skinTempC,
    required this.axG, required this.ayG, required this.azG,
    required this.gxDps, required this.gyDps, required this.gzDps,
    required this.latitude, required this.longitude, required this.speedMps,
    required this.timestampMs});

  factory TelemetryPacket.fromBytes(List<int> b) {
    if (b.length < 29) throw ArgumentError('telemetry packet too short');
    int u16(int o) => b[o] | (b[o+1] << 8);
    int i16(int o) { final v=u16(o); return v>=0x8000 ? v-0x10000 : v; }
    int u32(int o) => b[o] | (b[o+1]<<8) | (b[o+2]<<16) | (b[o+3]<<24);
    int i32(int o) { final v=u32(o); return v>=0x80000000 ? v-0x100000000 : v; }
    final flags=b[0];
    final gps=(flags & 0x02)!=0, temp=(flags & 0x04)!=0;
    return TelemetryPacket(
      leadOff:(flags & 0x01)!=0, gpsValid:gps, tempValid:temp, imuValid:(flags & 0x08)!=0,
      skinTempC: temp ? i16(1)/100.0 : null,
      axG:i16(3)/1000.0, ayG:i16(5)/1000.0, azG:i16(7)/1000.0,
      gxDps:i16(9)/10.0, gyDps:i16(11)/10.0, gzDps:i16(13)/10.0,
      latitude:gps ? i32(15)/1e7 : null,
      longitude:gps ? i32(19)/1e7 : null,
      speedMps:gps ? u16(23)/100.0 : null,
      timestampMs:u32(25),
    );
  }
}

class WearableBleService {
  static final serviceUuid=Guid('8a6f1001-41b8-4e73-9c27-4e8e1bb00001');
  static final ecgUuid=Guid('8a6f1002-41b8-4e73-9c27-4e8e1bb00001');
  static final telemetryUuid=Guid('8a6f1003-41b8-4e73-9c27-4e8e1bb00001');

  final ecg = StreamController<List<int>>.broadcast();
  final telemetry = StreamController<TelemetryPacket>.broadcast();
  BluetoothDevice? device;

  Future<void> connect() async {
    await FlutterBluePlus.startScan(withServices:[serviceUuid], timeout:const Duration(seconds:8));
    BluetoothDevice? found;
    await for (final results in FlutterBluePlus.scanResults) {
      for (final r in results) {
        if (r.device.platformName=='WorkerSafetyPatchV2') { found=r.device; break; }
      }
      if (found != null) break;
    }
    await FlutterBluePlus.stopScan();
    if (found == null) throw Exception('웨어러블을 찾지 못했습니다.');
    device=found; await found.connect();
    final services=await found.discoverServices();
    final svc=services.firstWhere((s)=>s.uuid==serviceUuid);
    final ecgChar=svc.characteristics.firstWhere((c)=>c.uuid==ecgUuid);
    final teleChar=svc.characteristics.firstWhere((c)=>c.uuid==telemetryUuid);
    await ecgChar.setNotifyValue(true); await teleChar.setNotifyValue(true);
    ecgChar.onValueReceived.listen((bytes){
      final samples=<int>[];
      for(int i=0;i+1<bytes.length;i+=2) samples.add(bytes[i] | (bytes[i+1]<<8));
      ecg.add(samples);
    });
    teleChar.onValueReceived.listen((b)=>telemetry.add(TelemetryPacket.fromBytes(b)));
  }
}
