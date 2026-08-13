package dev.duynp.flutter_health_connect

import android.content.Context
import android.content.pm.PackageManager
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Device

internal class HealthConnectRecordMetadataMapper(context: Context) {
    private val packageManager = context.applicationContext.packageManager
    private val sourceNames = mutableMapOf<String, String>()

    fun enrich(record: Record, recordMap: MutableMap<String, Any?>) {
        val packageName = record.metadata.dataOrigin.packageName
        recordMap["source"] = packageName
        recordMap["sourceName"] = sourceName(packageName)
        recordMap["deviceName"] = deviceName(record.metadata.device)
    }

    private fun sourceName(packageName: String): String = sourceNames.getOrPut(packageName) {
        try {
            @Suppress("DEPRECATION")
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }
}

internal fun deviceName(device: Device?): String? {
    if (device == null) return null

    val manufacturer = device.manufacturer?.trim().orEmpty()
    val model = device.model?.trim().orEmpty()

    return when {
        manufacturer.isEmpty() && model.isEmpty() -> null
        manufacturer.isEmpty() -> model
        model.isEmpty() -> manufacturer
        model.startsWith(manufacturer, ignoreCase = true) -> model
        else -> "$manufacturer $model"
    }
}
