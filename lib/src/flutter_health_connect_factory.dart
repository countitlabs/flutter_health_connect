part of flutter_health_connect;

class HealthConnectAggregateResult {
  final double value;
  final String? unit;

  const HealthConnectAggregateResult({required this.value, this.unit});
}

class HealthConnectFactory {
  static const MethodChannel _channel = MethodChannel('flutter_health_connect');

  static Future<bool> isApiSupported() async {
    return await _channel.invokeMethod('isApiSupported');
  }

  static Future<bool> isAvailable() async {
    return await _channel.invokeMethod('isAvailable');
  }

  static installHealthConnect() async {
    _channel.invokeMethod('installHealthConnect');
  }

  static Future<bool> hasPermissions(
    List<HealthConnectDataType> types, {
    bool readOnly = false,
    bool backgroundRead = false,
  }) async {
    return await _channel.invokeMethod('hasPermissions', {
      'types': types.map((e) => e.name).toList(),
      'readOnly': readOnly,
      'backgroundRead': backgroundRead,
    });
  }

  static Future<bool> hasBackgroundPermission() async {
    return await _channel.invokeMethod('hasBackgroundPermission');
  }

  static Future<bool> requestPermissions(
    List<HealthConnectDataType> types, {
    bool readOnly = false,
    bool backgroundRead = false,
  }) async {
    return await _channel.invokeMethod('requestPermissions', {
      'types': types.map((e) => e.name).toList(),
      'readOnly': readOnly,
      'backgroundRead': backgroundRead,
    });
  }

  static Future<Map<String, dynamic>> getChanges(String token) async {
    return await _channel.invokeMethod('getChanges', {
      'token': token,
    }).then((value) => Map<String, Object>.from(value));
  }

  static Future<String> getChangesToken(
      List<HealthConnectDataType> types) async {
    return await _channel.invokeMethod('getChangesToken', {
      'types': types.map((e) => e.name).toList(),
    });
  }

  static Future<Map<String, dynamic>> getRecord({
    required DateTime startTime,
    required DateTime endTime,
    required HealthConnectDataType type,
    int? pageSize,
    String? pageToken,
    bool ascendingOrder = true,
  }) async {
    final start = startTime.toLocal().toIso8601String();
    final end = endTime.toLocal().toIso8601String();
    final args = <String, dynamic>{
      'type': type.name,
      'startTime': start,
      'endTime': end,
      'pageSize': pageSize,
      'pageToken': pageToken,
      'ascendingOrder': ascendingOrder,
    };
    return await _channel
        .invokeMethod('getRecord', args)
        .then((value) => Map<String, Object?>.from(value));
  }

  static Future<bool> openHealthConnectSettings() async {
    return await _channel.invokeMethod('openHealthConnectSettings');
  }

  static Future<Map<String, HealthConnectAggregateResult>> aggregate({
    required List<String> aggregationKeys,
    required DateTime startTime,
    required DateTime endTime,
  }) async {
    if (aggregationKeys.isEmpty) {
      return {};
    }
    final start = startTime.toUtc().toIso8601String();
    final end = endTime.toUtc().toIso8601String();
    final args = <String, dynamic>{
      'aggregationKeys': aggregationKeys,
      'startTime': start,
      'endTime': end,
    };
    return await _channel.invokeMethod('aggregate', args).then((value) {
      final raw = Map<String, Object?>.from(value);
      return raw.map((key, entry) {
        final entryMap = Map<String, Object?>.from(entry as Map);
        return MapEntry(
          key,
          HealthConnectAggregateResult(value: entryMap['value'] as double, unit: entryMap['unit'] as String?),
        );
      });
    });
  }
  
  static Future<bool> disconnect() async {
   return await _channel.invokeMethod('disconnect');
  }
}
