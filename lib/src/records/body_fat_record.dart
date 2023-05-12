import 'package:flutter_health_connect/src/records/instantaneous_record.dart';
import 'package:flutter_health_connect/src/units/percentage.dart';

import 'metadata/metadata.dart';

class BodyFatRecord extends InstantaneousRecord {
  @override
  DateTime time;
  @override
  Duration? zoneOffset;
  @override
  Metadata metadata;
  Percentage percentage;

  BodyFatRecord({
    required this.time,
    this.zoneOffset,
    required this.percentage,
    metadata,
  })  : assert(percentage.value >= _minBodyFatPercentage &&
            percentage.value <= _maxBodyFatPercentage),
        metadata = metadata ?? Metadata.empty();

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is BodyFatRecord &&
          time == other.time &&
          zoneOffset == other.zoneOffset &&
          percentage == other.percentage;

  @override
  int get hashCode => time.hashCode ^ zoneOffset.hashCode ^ percentage.hashCode;

  static const double _minBodyFatPercentage = 0;
  static const double _maxBodyFatPercentage = 100;
}
