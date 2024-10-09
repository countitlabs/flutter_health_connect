package dev.duynp.flutter_health_connect

import android.content.Context
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.fasterxml.jackson.databind.ObjectMapper
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.ComponentActivity
import io.flutter.plugin.common.PluginRegistry.Registrar
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import kotlinx.coroutines.*
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.ArrayList
import java.util.HashMap
import java.util.concurrent.TimeUnit
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import android.os.Handler

/** FlutterHealthConnectPlugin */
public class FlutterHealthConnectPlugin(private var channel: MethodChannel? = null) : FlutterPlugin, MethodCallHandler, ActivityAware, PluginRegistry.ActivityResultListener, Result {
    private var job: Job = Job()
    var replyMapper: ObjectMapper = ObjectMapper()
    private var permissionResult: Result? = null
    private lateinit var client: HealthConnectClient
    private var healthConnectRequestPermissionsLauncher:  ActivityResultLauncher<Set<String>>? = null
    private var currentActivity: Activity? = null
    lateinit var scope: CoroutineScope
    private var handler: Handler? = null
    private lateinit var context: Context


    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        Log.e("FLUTTER_HEALTH_CONNECT","onAttachedToEngine")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flutter_health_connect")
        channel?.setMethodCallHandler(this)
        context = flutterPluginBinding.applicationContext
        client = HealthConnectClient.getOrCreate(flutterPluginBinding.applicationContext)
        checkAvailability()
    }

     companion object {
        @Suppress("unused")
        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val channel = MethodChannel(registrar.messenger(), "flutter_health_connect")
            val plugin = FlutterHealthConnectPlugin(channel)
            registrar.addActivityResultListener(plugin)
            channel.setMethodCallHandler(plugin)
        }
    }

    override fun success(p0: Any?) {
        handler?.post { permissionResult?.success(p0) }
    }

    override fun notImplemented() {
        handler?.post { permissionResult?.notImplemented() }
    }

    override fun error(
        errorCode: String,
        errorMessage: String?,
        errorDetails: Any?,
    ) {
        handler?.post { permissionResult?.error(errorCode, errorMessage, errorDetails) }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        scope.cancel()
        currentActivity = null
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        Log.e("FLUTTER_HEALTH_CONNECT","onAttachedToActivity")

        if (channel == null) {
            return
        }

        binding.addActivityResultListener(this)
        currentActivity = binding.activity
        
        val requestPermissionActivityContract = PermissionController.createRequestPermissionResultContract()

        healthConnectRequestPermissionsLauncher =(currentActivity as ComponentActivity).registerForActivityResult(requestPermissionActivityContract) { granted ->
            onHealthConnectPermissionCallback(granted);
        }
    }

   
    private  fun onHealthConnectPermissionCallback(permissionGranted: Set<String>)
    {
        if(permissionGranted.isEmpty()) {
            permissionResult?.success(false);
            Log.i("FLUTTER_HEALTH_CONNECT", "Access Denied (to Health Connect)!")
        }else {
            permissionResult?.success(true);
            Log.i("FLUTTER_HEALTH_CONNECT", "Access Granted (to Health Connect)!")
        }
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        Log.e("FLUTTER_HEALTH_CONNECT","onReattachedToActivityForConfigChanges")
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity();
    }

    override fun onDetachedFromActivity() {
        currentActivity = null;
        healthConnectRequestPermissionsLauncher = null;
    }

    var healthConnectStatus = HealthConnectClient.SDK_UNAVAILABLE

    var healthConnectAvailable = false
    var healthConnectApiSupported = false

    fun checkAvailability() {
        healthConnectStatus = HealthConnectClient.getSdkStatus(context!!)
        healthConnectAvailable = healthConnectStatus == HealthConnectClient.SDK_AVAILABLE
        healthConnectApiSupported = healthConnectStatus != HealthConnectClient.SDK_UNAVAILABLE
    }

    override fun onMethodCall(call: MethodCall, result: Result) {


        val activityContext = currentActivity
        val args = call.arguments?.let { it as? HashMap<*, *> } ?: hashMapOf<String, Any>()
        val requestedTypes = (args["types"] as? ArrayList<*>)?.filterIsInstance<String>()
        when (call.method) {

            "isApiSupported" -> {
                result.success(healthConnectApiSupported)
            }

            "isAvailable" -> {
                result.success(healthConnectAvailable)
            }

            "installHealthConnect" -> {
                try {
                    if(activityContext == null){
                        result.error("NO_ACTIVITY", "No activity available", null)
                        return
                    }
                    activityContext.startActivity(
                        Intent(Intent.ACTION_VIEW).apply {
                            setPackage("com.android.vending")
                            data =
                                Uri.parse("market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding")
                            putExtra("overlay", true)
                            putExtra("callerId", activityContext.packageName)
                        })
                    result.success(true)
                } catch (e: Throwable) {
                    Log.e("FLUTTER_HEALTH_CONNECT", "Error starting Health Connect", e)
                    result.error("UNABLE_TO_START_ACTIVITY", e.message, e)
                }
            }

            "hasPermissions" -> {
                scope.launch {
                    val isReadOnly = call.argument<Boolean>("readOnly") ?: false
                    val granted = client.permissionController.getGrantedPermissions()
                    val status =
                        granted.containsAll(mapTypesToPermissions(requestedTypes, isReadOnly))
                    result.success(status)
                }
            }

            "requestPermissions" -> {
                try {
                    permissionResult = result
                    val isReadOnly = call.argument<Boolean>("readOnly") ?: false
                    val allPermissions = mapTypesToPermissions(
                        requestedTypes,
                        isReadOnly
                    )

                    if(healthConnectRequestPermissionsLauncher == null) {
                        result.success(false)
                        Log.e("FLUTTER_HEALTH_CONNECT", "Permission launcher not found")
                        return;
                    }
                    healthConnectRequestPermissionsLauncher!!.launch(allPermissions.toSet());
                } catch (e: Throwable) {
                    Log.e("FLUTTER_HEALTH_CONNECT", "Error requesting permissions", e)
                    result.error("UNABLE_TO_START_ACTIVITY", e.message, e)
                }
            }
            "getChanges" -> {
                val token = call.argument<String>("token") ?: ""
                scope.launch {
                    try {
                        val changes = client.getChanges(token)
                        val reply = replyMapper.convertValue(
                            changes,
                            hashMapOf<String, Any>()::class.java
                        )
                        val typedChanges = changes.changes.mapIndexed { _, change ->
                            when (change) {
                                is UpsertionChange -> hashMapOf(
                                    change::class.simpleName to
                                            hashMapOf(
                                                change.record::class.simpleName to
                                                        replyMapper.convertValue(
                                                            change.record,
                                                            hashMapOf<String, Any>()::class.java
                                                        )
                                            )
                                )
                                else -> hashMapOf(
                                    change::class.simpleName to
                                            replyMapper.convertValue(
                                                change,
                                                hashMapOf<String, Any>()::class.java
                                            )
                                )
                            }
                        }
                        reply["changes"] = typedChanges
                        result.success(reply)
                    } catch (e: Throwable) {
                        Log.e("FLUTTER_HEALTH_CONNECT", "Error getting changes", e)
                        result.error("GET_CHANGES_FAIL", e.localizedMessage, e)
                    }
                }

            }
            "getChangesToken" -> {
                val recordTypes = requestedTypes?.mapNotNull {
                    HealthConnectRecordTypeMap[it]
                }?.toSet() ?: emptySet()
                scope.launch {
                    try {
                        result.success(
                            client.getChangesToken(
                                ChangesTokenRequest(
                                    recordTypes,
                                    setOf()
                                )
                            )
                        )
                    } catch (e: Throwable) {
                        Log.e("FLUTTER_HEALTH_CONNECT", "Error getting changes token", e)
                        result.error("GET_CHANGES_TOKEN_FAIL", e.localizedMessage, e)
                    }
                }
            }
            "getRecord" -> {
                scope.launch {
                    val type = call.argument<String>("type") ?: ""
                    val startTime = call.argument<String>("startTime")
                    val endTime = call.argument<String>("endTime")
                    val pageSize = call.argument<Int>("pageSize") ?: MAX_LENGTH
                    val pageToken = call.argument<String?>("pageToken")
                    val ascendingOrder = call.argument<Boolean?>("ascendingOrder") ?: true
                    try {
                        val start =
                            startTime?.let { LocalDateTime.parse(it) } ?: LocalDateTime.now()
                                .minus(1, ChronoUnit.DAYS)
                        val end = endTime?.let { LocalDateTime.parse(it) } ?: LocalDateTime.now()
                        HealthConnectRecordTypeMap[type]?.let { classType ->
                            val reply = client.readRecords(
                                ReadRecordsRequest(
                                    recordType = classType,
                                    timeRangeFilter = TimeRangeFilter.between(start, end),
                                    pageSize = pageSize,
                                    pageToken = pageToken,
                                    ascendingOrder = ascendingOrder,
                                )
                            )
                            result.success(
                                replyMapper.convertValue(
                                    reply,
                                    hashMapOf<String, Any>()::class.java
                                )
                            )
                        } ?: throw Throwable("Unsupported type $type")
                    } catch (e: Throwable) {
                        Log.e("FLUTTER_HEALTH_CONNECT", "Error getting record", e)
                        result.error("GET_RECORD_FAIL", e.localizedMessage, e)
                    }
                }
            }
            "openHealthConnectSettings" -> {
                try {
                    if(activityContext == null){
                        result.error("NO_ACTIVITY", "No activity available", null)
                        return
                    }
                    val intent = Intent()
                    intent.action = HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS
                    activityContext.startActivity(intent)
                    result.success(true)
                } catch (e: Throwable) {
                    Log.e("FLUTTER_HEALTH_CONNECT", "Error opening Health Connect settings", e)
                    result.error("UNABLE_TO_START_ACTIVITY", e.message, e)
                }
            }
            "aggregate" -> aggregate(call, result)
            "disconnect" -> {
                try {
                    scope.launch {
                        client.permissionController.revokeAllPermissions()
                        result.success(true)
                    }
                }catch (e: Throwable) {
                    Log.e("FLUTTER_HEALTH_CONNECT", "Error disconnecting", e)
                    result.success(false)
                }
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode == HEALTH_CONNECT_RESULT_CODE) {
            val result = permissionResult
            permissionResult = null
            if (resultCode == Activity.RESULT_OK) {
                if (data != null && result != null) {
                    scope.launch {
                        result.success(true)
                    }
                    return true
                }
            }
            scope.launch {
                result?.success(false)
            }
        }
        return false
    }
    
    private fun aggregate(call: MethodCall, result: Result) {
        scope.launch {
            try {
                val aggregationKeys =
                    (call.argument<ArrayList<*>>("aggregationKeys")?.filterIsInstance<String>() as ArrayList<String>?)?.toList()
                if(aggregationKeys.isNullOrEmpty()) {
                    result.success(LinkedHashMap<String, Any?>())
                } else {
                    val startTime = call.argument<String>("startTime")
                    val endTime = call.argument<String>("endTime")
                    val start = startTime?.let { Instant.parse(it) } ?: Instant.now()
                        .minus(1, ChronoUnit.DAYS)
                    val end = endTime?.let { Instant.parse(it) } ?: Instant.now()
                    val metrics =
                        aggregationKeys.mapNotNull { HealthConnectAggregateMetricTypeMap[it] }
                    val response =
                        client.aggregate(
                            AggregateRequest(
                                metrics.toSet(),
                                timeRangeFilter = TimeRangeFilter.between(start, end)
                            )
                        )
                    val resultData = aggregationKeys.associateBy(
                        {it},
                        {
                            replyMapper.convertValue(
                                response[HealthConnectAggregateMetricTypeMap[it]!!],
                                Double::class.java
                            )
                        }
                    )
                    result.success(resultData)
                }
            } catch (e: Exception) {
                result.error("AGGREGATE_FAIL", e.localizedMessage, e)
            }
        }
    }
}
