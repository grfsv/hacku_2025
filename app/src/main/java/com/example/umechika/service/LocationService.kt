package com.example.umechika.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.umechika.model.*
import com.example.umechika.util.KalmanFilter
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocationService(private val context: Context) : SensorEventListener {
    private val TAG = "LocationService"

    private val _debugInfo = MutableLiveData<String>()
    val debugInfo: LiveData<String> = _debugInfo

    private val apiUrl = "https://umeda-location-api.kazu3jp-purin.workers.dev/api/locate"

    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val client = OkHttpClient()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val _currentLocation = MutableLiveData<LocationResponse>()
    val currentLocation: LiveData<LocationResponse> = _currentLocation

    private val _wifiBasedAreaId = MutableLiveData<String>()
    val wifiBasedAreaId: LiveData<String> = _wifiBasedAreaId

    private val _sensorBasedAreaId = MutableLiveData<String>()
    val sensorBasedAreaId: LiveData<String> = _sensorBasedAreaId

    private val _predictedAreaId = MutableLiveData<String>()
    val predictedAreaId: LiveData<String> = _predictedAreaId

    private val _currentDirection = MutableLiveData<Pair<Float, String>>()
    val currentDirection: LiveData<Pair<Float, String>> = _currentDirection

    private val _movementDistance = MutableLiveData(0.0f)
    val movementDistance: LiveData<Float> = _movementDistance

    private val _currentAltitude = MutableLiveData(0.0f)
    val currentAltitude: LiveData<Float> = _currentAltitude

    private val _predictedArea = MutableLiveData<String>()
    val predictedArea: LiveData<String> = _predictedArea

    private val _scanStatus = MutableLiveData<String>()
    val scanStatus: LiveData<String> = _scanStatus

    private val _currentAreaInfo = MutableLiveData<AreaInfo>()
    val currentAreaInfo: LiveData<AreaInfo> = _currentAreaInfo

    private val _areaApiDebugInfo = MutableLiveData<String>()
    val areaApiDebugInfo: LiveData<String> = _areaApiDebugInfo

    private var stepCount: Int = 0
    private var lastStepTime: Long = 0
    private var strideLength: Float = 0.7f  // デフォルトの歩幅（メートル）

    private var lastDirection: Float = 0f
    private var baselinePressure = 0.0f
    private var baselineAltitude = 0.0f
    private val pressureReadings = ArrayDeque<Float>()
    private val PRESSURE_WINDOW_SIZE = 10
    private val PRESSURE_THRESHOLD = 0.02f // hPa (さらに小さくして感度を上げる)
    private val ALTITUDE_CHANGE_THRESHOLD = 0.1f // メートル (さらに小さくして感度を上げる)

    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private val remappedRotationMatrix = FloatArray(9)

    private var gravity = FloatArray(3)
    private var geomagnetic = FloatArray(3)

    // 歩行検出のための変数
    private val STEP_THRESHOLD = 1f
    private val STEP_DELAY = 100 // ミリ秒
    private var lastMagnitude = 0f
    private val magnitudeWindow = ArrayDeque<Float>()
    private val WINDOW_SIZE = 5
    private var magnitudePeak = 0f
    private var magnitudeValley = Float.MAX_VALUE
    private var lastPeakTime = 0L
    private var lastValleyTime = 0L

    // WiFiスキャン制限に対応するための変数
    private var lastScanTime = 0L
    private var scanCount = 0
    private val SCAN_INTERVAL = 30000L // 30秒ごとにスキャン
    private var lastMovementTime = 0L
    private val MOVEMENT_THRESHOLD = 0.5f // 動きを検出する閾値（m/s^2）
    private var isMoving = false
    private val SCAN_LIMIT_PERIOD = 120000L // 2分間
    private val SCAN_LIMIT_COUNT = 4 // 2分間に4回まで

    // 以下のプロパティを追加
    private var lastPredictedAreaId: String? = null
    private var sameAreaPredictionCount = 0
    private val AREA_PREDICTION_THRESHOLD = 3 // 同じエリアが3回連続で予測された場合に更新
    private val AREA_PREDICTION_INTERVAL = 5000L // 5秒間隔でエリア予測を行う
    private var lastAreaPredictionTime = 0L

    // または、予測エリアの安定性情報を直接提供するLiveDataを追加
    private val _predictionStabilityInfo = MutableLiveData<String>()
    val predictionStabilityInfo: LiveData<String> = _predictionStabilityInfo

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                } else {
                    true
                }

                if (success) {
                    _scanStatus.postValue("WiFiスキャン成功")
                    processWifiScanResults()
                } else {
                    _scanStatus.postValue("WiFiスキャン失敗 - センサーベースの予測を使用")
                    Log.e(TAG, "WiFiスキャンに失敗しました - センサーベースの予測を使用します")
                    // 現在位置情報があれば、センサーベースの予測のみを使用
                    _currentLocation.value?.let {
                        predictLocationBasedOnMovement(it)
                    }
                }
            }
        }
    }

    private val MAX_ACCELERATION_CHANGE = 30f // m/s^2 (軽く走る程度の動きを許容)
    private val MAX_SPEED = 5f // m/s (約18 km/h、軽いジョギング程度)
    private var lastAcceleration = 0f

    private val _directionalDistances = MutableLiveData<Map<String, Float>>()
    val directionalDistances: LiveData<Map<String, Float>> = _directionalDistances

    private val SIGNIFICANT_MOVEMENT_THRESHOLD = 5.0f // 5メートル以上移動したら更新
    private var lastUpdatePosition: Pair<Float, Float>? = null // 最後の更新位置（x, y）

    private val _apiDebugInfo = MutableLiveData<String>()
    val apiDebugInfo: LiveData<String> = _apiDebugInfo

    private var lastAltitude: Float = 0f

    private val _altitudeChangeDirection = MutableLiveData<String>()
    val altitudeChangeDirection: LiveData<String> = _altitudeChangeDirection

    private val STAIR_STEP_HEIGHT = 0.1f // 平均的な階段の高さ（メートル）(さらに小さくする)
    private val STAIR_DETECTION_THRESHOLD = 2 // 階段と判断するためのステップ数
    private val STAIR_TIME_THRESHOLD = 3000L // ミリ秒 (少し長くする)

    private var lastPressureReading = 0f
    private var pressureChangeAccumulator = 0f
    private val PRESSURE_CHANGE_THRESHOLD = 0.05f // hPa

    private val _stairMovement = MutableLiveData<String>()
    val stairMovement: LiveData<String> = _stairMovement

    private var lastStairDirection: String = "水平移動中"
    private var lastStairTime: Long = 0
    private var stairStepCount: Int = 0

    // 現在の推定位置（緯度・経度）
    private var currentLat: Double? = null
    private var currentLon: Double? = null

    // 地球の半径（メートル）
    private val EARTH_RADIUS = 6371000.0

    init {
        val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(wifiScanReceiver, intentFilter)
    }

    // クラス内に以下のメソッドを追加
    fun getAreaPredictionCount(): Int {
        return sameAreaPredictionCount
    }

    fun getAreaPredictionThreshold(): Int {
        return AREA_PREDICTION_THRESHOLD
    }

    // 現在の緯度・経度を取得するメソッドを追加
    fun getCurrentLat(): Double? {
        return currentLat
    }

    fun getCurrentLon(): Double? {
        return currentLon
    }

    // クラス内に新しいメソッドを追加

    // 加速度センサーの位置情報を定期的に更新するためのメソッド
    private fun updateSensorPositionDebugInfo() {
        coroutineScope.launch {
            while (isActive) {
                if (currentLat != null && currentLon != null) {
                    val sensorPositionDebug = "センサー推定位置: (${String.format("%.6f", currentLat)}, ${String.format("%.6f", currentLon)})"
                    Log.d(TAG, sensorPositionDebug)
                }
                delay(5000) // 5秒ごとに更新
            }
        }
    }

    fun startMonitoring() {
        val accelerometerRegistered = accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        } ?: false

        Log.d(TAG, "加速度センサー登録状態: $accelerometerRegistered")

        pressureSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        rotationVector?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        startWifiScan()
        // startMonitoring メソッド内の最後（startWifiScan()の後）に以下を追加
        updateSensorPositionDebugInfo()
    }

    fun stopMonitoring() {
        sensorManager.unregisterListener(this)
        try {
            context.unregisterReceiver(wifiScanReceiver)
        } catch (e: IllegalArgumentException) {
            // レシーバーが登録されていない場合
        }
        coroutineScope.cancel()
    }

    fun requestSingleScan() {
        coroutineScope.launch {
            val currentTime = System.currentTimeMillis()
            val shouldScan = _currentLocation.value == null || currentTime - lastScanTime >= SCAN_INTERVAL

            if (shouldScan) {
                if (canScan()) {
                    if (wifiManager.startScan()) {
                        Log.d(TAG, "手動WiFiスキャンを開始しました")
                        _scanStatus.postValue("WiFiスキャン開始...")
                        updateScanCounter()
                    } else {
                        Log.e(TAG, "WiFiスキャンの開始に失敗しました")
                        _scanStatus.postValue("WiFiスキャン開始失敗")
                    }
                } else {
                    val timeToNextScan = calculateTimeToNextScan()
                    _scanStatus.postValue("スキャン制限に達しました。${timeToNextScan / 1000}秒後に再試行できます")
                    Log.d(TAG, "スキャン制限に達しました。${timeToNextScan / 1000}秒後に再試行できます")
                }
            } else {
                val timeToNextScan = (lastScanTime + SCAN_INTERVAL) - currentTime
                _scanStatus.postValue("スキャン間隔制限中: あと${timeToNextScan / 1000}秒")
                Log.d(TAG, "スキャン間隔制限中: あと${timeToNextScan / 1000}秒")
            }
        }
    }

    // 移動情報をリセットするメソッドを拡張
    fun resetMovementInfo() {
        stepCount = 0
        _movementDistance.postValue(0f)
        _currentAltitude.postValue(0f)
        baselinePressure = 0f

        // 加速度センサーの基準点もリセット
        resetSensorBaseline()
    }

    private fun startWifiScan() {
        coroutineScope.launch {
            while (isActive) {
                val currentTime = System.currentTimeMillis()
                val shouldScan = _currentLocation.value == null ||
                        (currentTime - lastScanTime >= SCAN_INTERVAL && (isMoving || _currentLocation.value == null))

                if (shouldScan && canScan()) {
                    if (wifiManager.startScan()) {
                        Log.d(TAG, "WiFiスキャンを開始しました")
                        _scanStatus.postValue("WiFiスキャン開始...")
                        updateScanCounter()
                    } else {
                        Log.e(TAG, "WiFiスキャンの開始に失敗しました")
                        _scanStatus.postValue("WiFiスキャン開始失敗")
                    }
                } else {
                    if (!canScan()) {
                        Log.d(TAG, "スキャン制限に達しました。センサーベースの予測のみを使用します")
                        _scanStatus.postValue("スキャン制限中 - センサーベースの予測を使用")
                    } else if (!isMoving && _currentLocation.value != null) {
                        Log.d(TAG, "デバイスが静止中かつ位置情報あり。スキャンをスキップします")
                        _scanStatus.postValue("スキャンスキップ - デバイス静止中")
                    }
                }

                // センサーベースの予測を継続的に実行
                _currentLocation.value?.let {
                    predictLocationBasedOnMovement(it)
                }

                delay(SCAN_INTERVAL)
            }
        }
    }

    private fun canScan(): Boolean {
        val currentTime = System.currentTimeMillis()

        // 2分間のウィンドウ内のスキャン回数をチェック
        if (currentTime - lastScanTime > SCAN_LIMIT_PERIOD) {
            // 2分以上経過している場合はカウンターをリセット
            scanCount = 0
            return true
        }

        return scanCount < SCAN_LIMIT_COUNT
    }

    private fun updateScanCounter() {
        val currentTime = System.currentTimeMillis()

        // 2分以上経過している場合はカウンターをリセット
        if (currentTime - lastScanTime > SCAN_LIMIT_PERIOD) {
            scanCount = 1
        } else {
            scanCount++
        }

        lastScanTime = currentTime
    }

    private fun calculateTimeToNextScan(): Long {
        val currentTime = System.currentTimeMillis()
        return (lastScanTime + SCAN_LIMIT_PERIOD) - currentTime
    }

    // WiFiスキャンで位置が更新された際に、加速度センサーの情報も更新するように修正
    private fun sendLocationRequest(wifiDataList: List<WifiData>) {
        val jsonObject = JSONObject()
        val wifiDataJsonArray = JSONArray()

        wifiDataList.forEach { wifiData ->
            val wifiDataJson = JSONObject().apply {
                put("bssid", wifiData.bssid)
                put("frequency", wifiData.frequency)
                put("level", wifiData.level)
                put("ssid", wifiData.ssid)
            }
            wifiDataJsonArray.put(wifiDataJson)
        }

        jsonObject.put("wifiDataList", wifiDataJsonArray)

        val requestBodyString = jsonObject.toString()
        val requestBody = requestBodyString.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(apiUrl)
            .post(requestBody)
            .build()

        Log.d(TAG, "APIリクエスト送信: $apiUrl")
        Log.d(TAG, "リクエストボディ: $requestBodyString")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "API呼び出しに失敗しました: ${e.message}")
                _scanStatus.postValue("API呼び出し失敗: ${e.message}")
                _apiDebugInfo.postValue("API呼び出し失敗: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d(TAG, "APIレスポンス: ${response.code}")
                val responseBody = response.body?.string()
                Log.d(TAG, "レスポンスボディ: $responseBody")

                val formattedJson = try {
                    JSONObject(responseBody ?: "{}").toString(2)
                } catch (e: Exception) {
                    "JSONの整形に失敗しました: $responseBody"
                }

                _apiDebugInfo.postValue("APIレスポンス: ${response.code}\n\nレスポンスボディ:\n$formattedJson")

                if (response.isSuccessful) {
                    try {
                        responseBody?.let {
                            val json = JSONObject(it)

                            // 隣接エリア情報の取得
                            val adjacentMap = mutableMapOf<String, AdjacentArea>()
                            val adjacentJson = json.getJSONObject("areaInfo").getJSONObject("adjacent")
                            val keys = adjacentJson.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                val areaObj = adjacentJson.getJSONObject(key)

                                // 境界情報の取得（新しいフィールド）
                                val boundaryObj = if (areaObj.has("boundary")) {
                                    val boundaryJson = areaObj.getJSONObject("boundary")
                                    BoundaryLocation(
                                        lat = boundaryJson.getDouble("lat"),
                                        lon = boundaryJson.getDouble("lon")
                                    )
                                } else {
                                    null
                                }

                                adjacentMap[key] = AdjacentArea(
                                    direction = areaObj.getInt("direction"),
                                    distance = areaObj.getInt("distance"),
                                    boundary = boundaryObj
                                )
                            }

                            // デバッグ情報の取得
                            val debugJson = json.getJSONObject("debug")
                            val bestMatchJson = debugJson.getJSONObject("bestMatch")
                            val bestMatch = MatchInfo(
                                docId = bestMatchJson.getString("docId"),
                                score = bestMatchJson.getDouble("score")
                            )

                            val allMatchesJson = debugJson.getJSONArray("allMatches")
                            val allMatches = mutableListOf<MatchInfo>()
                            for (i in 0 until allMatchesJson.length()) {
                                val matchJson = allMatchesJson.getJSONObject(i)
                                allMatches.add(
                                    MatchInfo(
                                        docId = matchJson.getString("docId"),
                                        score = matchJson.getDouble("score")
                                    )
                                )
                            }

                            val matchingAreaJson = debugJson.getJSONObject("matchingArea")
                            val matchingArea = MatchingArea(
                                documentId = matchingAreaJson.getString("documentId"),
                                id = matchingAreaJson.getInt("id"),
                                wifi_data_id = matchingAreaJson.getString("wifi_data_id")
                            )

                            val areaInfoJson = json.getJSONObject("areaInfo")

                            // 緯度・経度情報の取得（新しいフィールド）
                            val lat = if (areaInfoJson.has("lat")) areaInfoJson.getDouble("lat") else null
                            val lon = if (areaInfoJson.has("lon")) areaInfoJson.getDouble("lon") else null

                            val areaInfo = AreaInfo(
                                id = areaInfoJson.getInt("id"),
                                lat = lat,
                                lon = lon,
                                adjacent = adjacentMap,
                                timestamp = areaInfoJson.getLong("timestamp"),
                                wifi_data_id = areaInfoJson.getString("wifi_data_id")
                            )

                            val debugInfo = DebugInfo(
                                bestMatch = bestMatch,
                                allMatches = allMatches,
                                matchingArea = matchingArea
                            )

                            val locationResponse = LocationResponse(
                                areaId = json.getString("areaId"),
                                areaName = json.getString("areaName"),
                                confidence = json.getDouble("confidence"),
                                areaInfo = areaInfo,
                                timestamp = json.getLong("timestamp"),
                                debug = debugInfo
                            )

                            _wifiBasedAreaId.postValue(locationResponse.areaId)

                            // 現在位置の緯度・経度を更新
                            val previousLat = currentLat
                            val previousLon = currentLon
                            currentLat = lat
                            currentLon = lon

                            // UIスレッドで更新
                            MainScope().launch {
                                _currentLocation.value = locationResponse
                                _wifiBasedAreaId.value = locationResponse.areaId
                                _predictedAreaId.value = locationResponse.areaId
                                _scanStatus.value = "位置情報更新完了"

                                // 前回の位置情報がある場合は、移動距離を計算して更新
                                if (previousLat != null && previousLon != null && currentLat != null && currentLon != null) {
                                    val distance = calculateDistance(previousLat, previousLon, currentLat!!, currentLon!!)
                                    _movementDistance.value = distance.toFloat()
                                    Log.d(TAG, "WiFiスキャンによる位置更新: 移動距離 = $distance m")
                                } else {
                                    // 移動距離をリセット（新しい基準点）
                                    resetMovementInfo()
                                }

                                // 加速度センサーの基準点を更新
                                resetSensorBaseline()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "レスポンスの解析に失敗しました: ${e.message}")
                        _scanStatus.postValue("レスポンス解析失敗: ${e.message}")
                    }
                } else {
                    try {
                        responseBody?.let {
                            val errorJson = JSONObject(it)
                            val errorMessage = errorJson.optString("error", "不明なエラー")
                            val detailMessage = errorJson.optString("message", "詳細不明")
                            Log.e(TAG, "APIエラー: $errorMessage, 詳細: $detailMessage")
                            _scanStatus.postValue("APIエラー: $errorMessage")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "エラーレスポンスの解析に失敗しました: ${e.message}")
                        _scanStatus.postValue("APIエラー: ${response.code}")
                    }
                }
            }
        })
    }

    // WiFiスキャン結果を処理するメソッド
    private fun processWifiScanResults() {
        val scanResults = wifiManager.scanResults
        val wifiDataList = scanResults.map { result ->
            WifiData(
                bssid = result.BSSID,
                frequency = result.frequency,
                level = result.level,
                ssid = result.SSID
            )
        }

        if (wifiDataList.isNotEmpty()) {
            sendLocationRequest(wifiDataList)
        } else {
            Log.e(TAG, "WiFiスキャン結果が空です")
            _scanStatus.postValue("WiFiスキャン結果が空です")
        }
    }

    // 加速度センサーの基準点をリセットするメソッド
    private fun resetSensorBaseline() {
        // 現在の方向を維持
        lastUpdatePosition = Pair(0f, 0f)

        // 気圧センサーの基準値をリセット
        baselinePressure = 0.0f
        baselineAltitude = 0.0f

        // 歩行検出のリセット
        magnitudePeak = 0f
        magnitudeValley = Float.MAX_VALUE
        lastPeakTime = 0L
        lastValleyTime = 0L

        Log.d(TAG, "加速度センサーの基準点をリセットしました")
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
                updateOrientationAngles()
                processAccelerometerData(event)
                Log.d(TAG, "加速度センサーイベント受信")
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
                updateOrientationAngles()
            }
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                updateOrientationAngles()
            }
            Sensor.TYPE_PRESSURE -> processPressureData(event)
        }
    }

    private fun lowPassFilter(input: FloatArray, output: FloatArray): FloatArray {
        val alpha = 0.1f
        if (output == null) return input.clone()
        for (i in input.indices) {
            output[i] = output[i] + alpha * (input[i] - output[i])
        }
        return output
    }

    private fun updateDirectionalDistances(currentDirection: Float, totalDistance: Float) {
        val directions = listOf("北", "北東", "東", "南東", "南", "南西", "西", "北西")
        val distances = mutableMapOf<String, Float>()

        for ((index, direction) in directions.withIndex()) {
            val angle = (index * 45 - currentDirection + 360) % 360
            val radians = angle * PI / 180
            val distance = totalDistance * cos(radians.toFloat()).coerceIn(-1f, 1f)
            distances[direction] = abs(distance)
        }

        _directionalDistances.postValue(distances)
    }

    private fun isValidMovement(currentTime: Long, accelerationChange: Float): Boolean {
        val timeDiff = currentTime - lastStepTime
        if (timeDiff == 0L) return false

        val speed = calculateSpeed(currentTime)

        val isValid = accelerationChange < MAX_ACCELERATION_CHANGE && speed < MAX_SPEED

        if (isValid) {
            lastStepTime = currentTime
        } else {
            Log.d(TAG, "無効な動きを検出: 加速度変化 = $accelerationChange, 速度 = $speed")
        }

        return isValid
    }

    private fun calculateSpeed(currentTime: Long): Float {
        val timeDiff = currentTime - lastStepTime
        return if (timeDiff > 0) (strideLength / (timeDiff / 1000f)) else 0f
    }

    private fun detectStep(currentTime: Long, averageMagnitude: Float): Boolean {
        val timeSinceLastPeak = currentTime - lastPeakTime
        val timeSinceLastValley = currentTime - lastValleyTime
        val peakValleyDifference = magnitudePeak - magnitudeValley

        if (peakValleyDifference > STEP_THRESHOLD &&
            timeSinceLastPeak > STEP_DELAY &&
            timeSinceLastValley > STEP_DELAY) {

            // ピークと谷をリセット
            magnitudePeak = max(averageMagnitude, magnitudeValley + STEP_THRESHOLD)
            magnitudeValley = min(averageMagnitude, magnitudePeak - STEP_THRESHOLD)
            lastPeakTime = currentTime
            lastValleyTime = currentTime

            return true
        }

        return false
    }

    // calculateBoundaryLocation メソッドを追加
    private fun calculateBoundaryLocation(startLat: Double, startLon: Double, direction: Int, distance: Int): BoundaryLocation {
        // 方向をラジアンに変換
        val directionRad = Math.toRadians(direction.toDouble())

        // 緯度1度あたりの距離（メートル）
        val metersPerLatDegree = 111320.0

        // 経度1度あたりの距離（メートル）- 緯度によって変わる
        val metersPerLonDegree = 111320.0 * cos(Math.toRadians(startLat))

        // 緯度・経度の変化量を計算
        val latChange = (distance * cos(directionRad)) / metersPerLatDegree
        val lonChange = (distance * sin(directionRad)) / metersPerLonDegree

        // 新しい緯度・経度を計算
        val newLat = startLat + latChange
        val newLon = startLon + lonChange

        return BoundaryLocation(lat = newLat, lon = newLon)
    }

    private fun predictCurrentArea(currentLocation: LocationResponse, currentDirection: Float, movementDistance: Float) {
        val currentTime = System.currentTimeMillis()

        // 前回の予測から一定時間経過していない場合はスキップ
        if (currentTime - lastAreaPredictionTime < AREA_PREDICTION_INTERVAL) {
            return
        }

        lastAreaPredictionTime = currentTime

        val adjacentAreas = currentLocation.areaInfo.adjacent
        var bestMatchArea: String? = null
        var smallestScore = Double.MAX_VALUE

        // 現在の緯度・経度が利用可能な場合
        if (currentLat != null && currentLon != null) {
            for ((areaId, areaInfo) in adjacentAreas) {
                // 境界点を方角と距離から計算
                val boundary = areaInfo.boundary ?: calculateBoundaryLocation(
                    currentLat!!,
                    currentLon!!,
                    areaInfo.direction,
                    areaInfo.distance
                )

                // 現在位置から境界点までの距離を計算
                val distance = calculateDistance(currentLat!!, currentLon!!, boundary.lat, boundary.lon)

                // 方向の差を計算
                val bearingToBoundary = calculateBearing(currentLat!!, currentLon!!, boundary.lat, boundary.lon)
                val directionDiff = abs(angleDifference(currentDirection, bearingToBoundary.toFloat()))

                // スコアを計算（距離と方向の差の重み付け合計）
                val score = distance * 0.7 + directionDiff * 0.3

                if (score < smallestScore) {
                    smallestScore = score
                    bestMatchArea = areaId
                }
            }
        } else {
            // 緯度・経度情報がない場合は従来の方法で計算
            for ((areaId, areaInfo) in adjacentAreas) {
                val directionDifference = abs(angleDifference(currentDirection, areaInfo.direction.toFloat()))
                val distanceDifference = abs(movementDistance - areaInfo.distance)

                val score = directionDifference * 0.7 + distanceDifference * 0.3

                if (score < smallestScore) {
                    smallestScore = score.toDouble()
                    bestMatchArea = areaId
                }
            }
        }

        bestMatchArea?.let { predictedAreaId ->
            if (smallestScore < 45) {
                // 予測エリアの変更頻度を下げるための処理
                if (predictedAreaId == lastPredictedAreaId) {
                    sameAreaPredictionCount++
                } else {
                    sameAreaPredictionCount = 1
                    lastPredictedAreaId = predictedAreaId
                }

                // 同じエリアが連続で予測された場合のみ更新
                if (sameAreaPredictionCount >= AREA_PREDICTION_THRESHOLD) {
                    _sensorBasedAreaId.postValue(predictedAreaId)
                    _predictedArea.postValue(predictedAreaId)

                    // 新しいエリアに到達したと予測された場合、エリア情報を取得
                    // 現在のエリアと異なる場合のみAPIリクエストを送信
                    if (predictedAreaId != currentLocation.areaId) {
                        fetchAreaInfo(predictedAreaId)
                    }
                }
            } else {
                _sensorBasedAreaId.postValue(currentLocation.areaId)
                _predictedArea.postValue(currentLocation.areaId)
                lastPredictedAreaId = currentLocation.areaId
                sameAreaPredictionCount = AREA_PREDICTION_THRESHOLD // 現在のエリアを維持
            }
        } ?: run {
            _sensorBasedAreaId.postValue(currentLocation.areaId)
            _predictedArea.postValue(currentLocation.areaId)
            lastPredictedAreaId = currentLocation.areaId
            sameAreaPredictionCount = AREA_PREDICTION_THRESHOLD // 現在のエリアを維持
        }
        _predictionStabilityInfo.postValue("$sameAreaPredictionCount/$AREA_PREDICTION_THRESHOLD")
    }

    // 2点間の距離を計算（ハーバーサイン公式）
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS * c
    }

    // 2点間の方位角を計算（北を0度として時計回り）
    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)

        val y = sin(dLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)

        var bearing = Math.toDegrees(atan2(y, x))
        if (bearing < 0) bearing += 360

        return bearing
    }

    // 最後にAPIリクエストを送信したエリアIDと時間を記録
    private var lastFetchedAreaId: String? = null
    private var lastFetchTime = 0L
    private val FETCH_INTERVAL = 30000L // 30秒間隔でAPIリクエストを送信

    private fun fetchAreaInfo(areaId: String) {
        val currentTime = System.currentTimeMillis()

        // 同じエリアに対して一定時間内に再度リクエストを送信しないようにする
        if (areaId == lastFetchedAreaId && currentTime - lastFetchTime < FETCH_INTERVAL) {
            Log.d(TAG, "エリア情報の取得をスキップ: $areaId (前回の取得から${(currentTime - lastFetchTime) / 1000}秒)")
            return
        }

        lastFetchedAreaId = areaId
        lastFetchTime = currentTime

        coroutineScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("https://umeda-location-api.kazu3jp-purin.workers.dev/api/area/$areaId")
                        .build()
                    client.newCall(request).execute()
                }

                val responseBody = response.body?.string()
                val formattedJson = try {
                    JSONObject(responseBody ?: "{}").toString(2)
                } catch (e: Exception) {
                    "JSONの整形に失敗しました: $responseBody"
                }

                _areaApiDebugInfo.postValue("エリアAPI レスポンス (エリアID: $areaId):\n$formattedJson")

                if (response.isSuccessful) {
                    responseBody?.let {
                        val json = JSONObject(it)
                        val areaInfo = parseAreaInfo(json)
                        _currentAreaInfo.postValue(areaInfo)
                        updateLocationWithNewAreaInfo(areaInfo)
                    }
                } else {
                    val errorBody = responseBody
                    errorBody?.let {
                        val errorJson = JSONObject(it)
                        val errorMessage = errorJson.optString("error", "不明なエラー")
                        Log.e(TAG, "エリア情報の取得に失敗: $errorMessage")
                        _areaApiDebugInfo.postValue("エリアAPI エラー (エリアID: $areaId):\n$errorMessage")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "エリア情報の取得中にエラーが発生: ${e.message}")
                _areaApiDebugInfo.postValue("エリアAPI 例外 (エリアID: $areaId):\n${e.message}")
            }
        }
    }

    private fun parseAreaInfo(json: JSONObject): AreaInfo {
        val areaId = json.getString("areaId")
        val areaName = json.getString("areaName")
        val areaInfoJson = json.getJSONObject("areaInfo")

        val id = areaInfoJson.getInt("id")

        // 緯度・経度情報の取得（新しいフィールド）
        val lat = if (areaInfoJson.has("lat")) areaInfoJson.getDouble("lat") else null
        val lon = if (areaInfoJson.has("lon")) areaInfoJson.getDouble("lon") else null

        val adjacent = mutableMapOf<String, AdjacentArea>()
        val adjacentJson = areaInfoJson.getJSONObject("adjacent")
        for (key in adjacentJson.keys()) {
            val adjacentAreaJson = adjacentJson.getJSONObject(key)

            // 境界情報の取得（新しいフィールド）
            val boundaryObj = if (adjacentAreaJson.has("boundary")) {
                val boundaryJson = adjacentAreaJson.getJSONObject("boundary")
                BoundaryLocation(
                    lat = boundaryJson.getDouble("lat"),
                    lon = boundaryJson.getDouble("lon")
                )
            } else {
                null
            }

            adjacent[key] = AdjacentArea(
                direction = adjacentAreaJson.getInt("direction"),
                distance = adjacentAreaJson.getInt("distance"),
                boundary = boundaryObj
            )
        }
        val timestamp = areaInfoJson.getLong("timestamp")
        val wifiDataId = areaInfoJson.getString("wifi_data_id")

        return AreaInfo(
            id = id,
            lat = lat,
            lon = lon,
            adjacent = adjacent,
            timestamp = timestamp,
            wifi_data_id = wifiDataId
        )
    }

    private fun updateLocationWithNewAreaInfo(areaInfo: AreaInfo) {
        val currentLocation = _currentLocation.value
        currentLocation?.let {
            val updatedLocation = it.copy(
                areaId = areaInfo.id.toString(),
                areaInfo = areaInfo
            )
            _currentLocation.postValue(updatedLocation)

            // 現在位置の緯度・経度を更新
            currentLat = areaInfo.lat
            currentLon = areaInfo.lon

            // 移動距離をリセット
            resetMovementInfo()
        }
    }

    private fun angleDifference(angle1: Float, angle2: Float): Float {
        var diff = (angle2 - angle1 + 360) % 360
        if (diff > 180) diff -= 360
        return abs(diff)
    }

    private fun updateOrientationAngles() {
        SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)

        // デバイスの向きに応じて回転行列を調整
        val deviceRotation = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        val axisX: Int
        val axisY: Int

        when (deviceRotation) {
            Surface.ROTATION_0 -> {
                axisX = SensorManager.AXIS_X
                axisY = SensorManager.AXIS_Y
            }
            Surface.ROTATION_90 -> {
                axisX = SensorManager.AXIS_Y
                axisY = SensorManager.AXIS_MINUS_X
            }
            Surface.ROTATION_180 -> {
                axisX = SensorManager.AXIS_MINUS_X
                axisY = SensorManager.AXIS_MINUS_Y
            }
            Surface.ROTATION_270 -> {
                axisX = SensorManager.AXIS_MINUS_Y
                axisY = SensorManager.AXIS_X
            }
            else -> {
                axisX = SensorManager.AXIS_X
                axisY = SensorManager.AXIS_Y
            }
        }

        SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remappedRotationMatrix)
        SensorManager.getOrientation(remappedRotationMatrix, orientationAngles)

        val azimuthInRadians = orientationAngles[0]
        var azimuthInDegrees = Math.toDegrees(azimuthInRadians.toDouble()).toFloat()

        // 0-360度の範囲に正規化
        azimuthInDegrees = (azimuthInDegrees + 360) % 360

        lastDirection = azimuthInDegrees
        // 方位を取得
        val cardinalDirection = getCardinalDirection(azimuthInDegrees)
        _currentDirection.postValue(Pair(azimuthInDegrees, cardinalDirection))
    }

    private fun processPressureData(event: SensorEvent) {
        val currentPressure = event.values[0]

        // 移動平均フィルタを適用
        pressureReadings.addLast(currentPressure)
        if (pressureReadings.size > PRESSURE_WINDOW_SIZE) {
            pressureReadings.removeFirst()
        }
        val averagePressure = pressureReadings.average().toFloat()

        if (baselinePressure == 0.0f) {
            baselinePressure = averagePressure
            baselineAltitude = calculateAltitude(baselinePressure)
            lastAltitude = baselineAltitude
            lastPressureReading = averagePressure
        }

        val pressureChange = averagePressure - lastPressureReading
        pressureChangeAccumulator += pressureChange

        val currentAltitude = calculateAltitude(averagePressure)
        val altitudeChange = currentAltitude - lastAltitude

        // 気圧の変化が閾値を超えた場合のみ高度を更新
        if (abs(pressureChangeAccumulator) > PRESSURE_CHANGE_THRESHOLD) {
            _currentAltitude.postValue(currentAltitude - baselineAltitude)

            // 高度変化の方向を検知
            val altitudeChangeDirection = when {
                altitudeChange > ALTITUDE_CHANGE_THRESHOLD -> "上昇中"
                altitudeChange < -ALTITUDE_CHANGE_THRESHOLD -> "下降中"
                else -> "水平移動中"
            }
            _altitudeChangeDirection.postValue(altitudeChangeDirection)

            // 階段の検出
            detectStairs(altitudeChange, altitudeChangeDirection)

            // デバッグ情報を更新
            val debugText = "気圧: $averagePressure hPa, " +
                    "気圧変化: $pressureChange hPa, " +
                    "累積気圧変化: $pressureChangeAccumulator hPa, " +
                    "高度: $currentAltitude m, " +
                    "高度変化: $altitudeChange m, " +
                    "移動方向: $altitudeChangeDirection, " +
                    "階段: ${_stairMovement.value}"
            _debugInfo.postValue(_debugInfo.value + "\n" + debugText)
            Log.d(TAG, debugText)

            lastAltitude = currentAltitude
            pressureChangeAccumulator = 0f
        }

        lastPressureReading = averagePressure
    }

    private fun detectStairs(altitudeChange: Float, direction: String) {
        val currentTime = System.currentTimeMillis()
        if (abs(altitudeChange) >= STAIR_STEP_HEIGHT) {
            if (direction == lastStairDirection && currentTime - lastStairTime < STAIR_TIME_THRESHOLD) {
                stairStepCount++
            } else {
                stairStepCount = 1
                lastStairDirection = direction
            }
            lastStairTime = currentTime

            if (stairStepCount >= STAIR_DETECTION_THRESHOLD) {
                val stairMovement = when (direction) {
                    "上昇中" -> "階段を上っています"
                    "下降中" -> "階段を下っています"
                    else -> "水平移動中"
                }
                _stairMovement.postValue(stairMovement)
                Log.d(TAG, "階段検出: $stairMovement (高度変化: $altitudeChange, ステップ数: $stairStepCount)")
            }
        } else {
            if (currentTime - lastStairTime > STAIR_TIME_THRESHOLD) {
                stairStepCount = 0
                _stairMovement.postValue("水平移動中")
            }
        }
    }

    private fun calculateAltitude(pressure: Float): Float {
        // 国際標準大気（ISA）モデルを使用した高度計算
        val P0 = 1013.25f  // 海面標準気圧（hPa）
        val T0 = 288.15f   // 海面標準温度（K）
        val L = 0.0065f    // 気温減率（K/m）
        val g = 9.80665f   // 重力加速度（m/s^2）
        val R = 287.05f    // 乾燥空気の気体定数（J/(kg·K)）

        return T0 / L * (1 - (pressure / P0).pow(R * L / g))
    }

    private fun predictLocationBasedOnMovement(currentLocation: LocationResponse) {
        val currentTime = System.currentTimeMillis()

        // 前回の予測から一定時間経過していない場合はスキップ
        if (currentTime - lastAreaPredictionTime < AREA_PREDICTION_INTERVAL) {
            return
        }

        lastAreaPredictionTime = currentTime

        val currentAreaId = currentLocation.areaId
        val adjacentAreas = currentLocation.areaInfo.adjacent
        val distance = _movementDistance.value ?: 0.0f

        // 現在の緯度・経度が利用可能な場合
        if (currentLat != null && currentLon != null) {
            var closestAreaId = currentAreaId
            var minDistance = Double.MAX_VALUE

            for ((areaId, areaInfo) in adjacentAreas) {
                // 境界点を方角と距離から計算
                val boundary = areaInfo.boundary ?: calculateBoundaryLocation(
                    currentLat!!,
                    currentLon!!,
                    areaInfo.direction,
                    areaInfo.distance
                )

                // 現在位置から境界点までの距離を計算
                val distanceToBoundary = calculateDistance(currentLat!!, currentLon!!, boundary.lat, boundary.lon)

                if (distanceToBoundary < minDistance) {
                    minDistance = distanceToBoundary
                    closestAreaId = areaId
                }
            }

            // 十分に近い場合、予測エリアを更新
            if ((minDistance < 20 && currentLat != null) || (minDistance < 45 && currentLat == null)) {
                // 予測エリアの変更頻度を下げるための処理
                if (closestAreaId == lastPredictedAreaId) {
                    sameAreaPredictionCount++
                } else {
                    sameAreaPredictionCount = 1
                    lastPredictedAreaId = closestAreaId
                }

                // 同じエリアが連続で予測された場合のみ更新
                if (sameAreaPredictionCount >= AREA_PREDICTION_THRESHOLD) {
                    _sensorBasedAreaId.postValue(closestAreaId)
                    _predictedAreaId.postValue(closestAreaId)

                    // 新しいエリアに到達したと予測された場合、エリア情報を取得
                    // 現在のエリアと異なる場合のみAPIリクエストを送信
                    if (closestAreaId != currentAreaId) {
                        fetchAreaInfo(closestAreaId)
                    }
                }
            } else {
                _sensorBasedAreaId.postValue(currentAreaId)
                _predictedAreaId.postValue(currentAreaId)
                lastPredictedAreaId = currentAreaId
                sameAreaPredictionCount = AREA_PREDICTION_THRESHOLD // 現在のエリアを維持
            }

            Log.d(TAG, "予測エリア: $closestAreaId, 最小距離: $minDistance, 移動距離: $distance, 方向: $lastDirection, カウント: $sameAreaPredictionCount")
        } else {
            // 緯度・経度情報がない場合は従来の方法で計算
            var closestAreaId = currentAreaId
            var minDirectionDiff = Float.MAX_VALUE
            var minDistanceDiff = Float.MAX_VALUE

            for ((areaId, areaInfo) in adjacentAreas) {
                val areaDistance = areaInfo.distance.toFloat()
                val areaDirection = areaInfo.direction.toFloat()

                val distanceDiff = abs(distance - areaDistance)
                val directionDiff = abs(angleDifference(lastDirection, areaDirection))

                // 方向と距離の両方を考慮して最も近いエリアを見つける
                if (directionDiff < minDirectionDiff && distanceDiff < minDistanceDiff) {
                    minDirectionDiff = directionDiff
                    minDistanceDiff = distanceDiff
                    closestAreaId = areaId
                }
            }

            // 移動距離と方向が十分であれば、予測エリアを更新
            if (distance > 5.0f && minDirectionDiff < 45f && minDistanceDiff < distance * 0.5f) {
                // 予測エリアの変更頻度を下げるための処理
                if (closestAreaId == lastPredictedAreaId) {
                    sameAreaPredictionCount++
                } else {
                    sameAreaPredictionCount = 1
                    lastPredictedAreaId = closestAreaId
                }

                // 同じエリアが連続で予測された場合のみ更新
                if (sameAreaPredictionCount >= AREA_PREDICTION_THRESHOLD) {
                    _sensorBasedAreaId.postValue(closestAreaId)
                    _predictedAreaId.postValue(closestAreaId)

                    // 新しいエリアに到達したと予測された場合、エリア情報を取得
                    // 現在のエリアと異なる場合のみAPIリクエストを送信
                    if (closestAreaId != currentAreaId) {
                        fetchAreaInfo(closestAreaId)
                    }
                }
            } else {
                _sensorBasedAreaId.postValue(currentAreaId)
                _predictedAreaId.postValue(currentAreaId)
                lastPredictedAreaId = currentAreaId
                sameAreaPredictionCount = AREA_PREDICTION_THRESHOLD // 現在のエリアを維持
            }

            Log.d(TAG, "予測エリア: $closestAreaId, 移動距離: $distance, 方向: $lastDirection, カウント: $sameAreaPredictionCount")
        }

        // 方向ごとの距離を更新
        updateDirectionalDistances(lastDirection, distance)
        _predictionStabilityInfo.postValue("$sameAreaPredictionCount/$AREA_PREDICTION_THRESHOLD")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 精度変更の処理は必要に応じて実装
    }

    private fun getCardinalDirection(azimuth: Float): String {
        val directions = arrayOf("北", "北東", "東", "南東", "南", "南西", "西", "北西")
        val index = ((azimuth + 22.5) / 45).toInt() % 8
        return directions[index]
    }

    // 加速度センサーによる位置更新時に、APIの座標情報を使用して移動距離を計算
    private fun updateCurrentPosition(direction: Float, stepDistance: Float) {
        // 現在の緯度・経度が利用可能な場合のみ更新
        if (currentLat != null && currentLon != null) {
            // 前回の位置を保存
            val previousLat = currentLat
            val previousLon = currentLon

            // 方向をラジアンに変換
            val directionRad = Math.toRadians(direction.toDouble())

            // 緯度1度あたりの距離（メートル）
            val metersPerLatDegree = 111320.0

            // 経度1度あたりの距離（メートル）- 緯度によって変わる
            val metersPerLonDegree = 111320.0 * cos(Math.toRadians(currentLat!!))

            // 緯度・経度の変化量を計算
            val latChange = (stepDistance * cos(directionRad)) / metersPerLatDegree
            val lonChange = (stepDistance * sin(directionRad)) / metersPerLonDegree

            // 新しい緯度・経度を計算
            currentLat = currentLat!! + latChange
            currentLon = currentLon!! + lonChange

            // APIの座標情報を使用して実際の移動距離を計算
            val actualDistance = calculateDistance(previousLat!!, previousLon!!, currentLat!!, currentLon!!)

            // 累積移動距離を更新
            val currentDistance = _movementDistance.value ?: 0f
            val newDistance = currentDistance + actualDistance.toFloat()
            _movementDistance.postValue(newDistance)

            Log.d(TAG, "位置更新: 緯度=$currentLat, 経度=$currentLon, 方向=$direction°, 距離=$actualDistance m, 累積距離=$newDistance m")
        }
    }

    // 加速度センサーによる位置変化を検出した際に、APIからの座標情報を使用して移動距離を更新
    private fun processAccelerometerData(event: SensorEvent) {
        val currentTime = System.currentTimeMillis()

        // 重力加速度を除去
        val x = event.values[0] - gravity[0]
        val y = event.values[1] - gravity[1]
        val z = event.values[2] - gravity[2]

        // 加速度の大きさを計算
        val magnitude = sqrt(x * x + y * y + z * z)

        // 動きの検出
        if (magnitude > MOVEMENT_THRESHOLD) {
            lastMovementTime = currentTime
            isMoving = true
        } else if (currentTime - lastMovementTime > 5000) { // 5秒間動きがない場合
            isMoving = false
        }

        // 加速度の変化率を計算
        val accelerationChange = abs(magnitude - lastAcceleration)

        // 移動平均のウィンドウを更新
        magnitudeWindow.addLast(magnitude)
        if (magnitudeWindow.size > WINDOW_SIZE) {
            magnitudeWindow.removeFirst()
        }

        // 移動平均を計算
        val averageMagnitude = magnitudeWindow.average().toFloat()

        // ピークと谷を更新
        if (averageMagnitude > magnitudePeak) {
            magnitudePeak = averageMagnitude
            lastPeakTime = currentTime
        }
        if (averageMagnitude < magnitudeValley) {
            magnitudeValley = averageMagnitude
            lastValleyTime = currentTime
        }

        // デバッグ情報を更新
        val debugText = "加速度: x=$x, y=$y, z=$z, magnitude=$magnitude, avg=$averageMagnitude, peak=$magnitudePeak, valley=$magnitudeValley, change=$accelerationChange"
        _debugInfo.postValue(debugText)
        Log.d(TAG, debugText)

        // 歩行または軽い走りを検出し、極端な動きをフィルタリング
        if (detectStep(currentTime, averageMagnitude) && isValidMovement(currentTime, accelerationChange)) {
            stepCount++

            // 移動距離を更新（走っている場合はより長いストライドを想定）
            val currentSpeed = calculateSpeed(currentTime)
            val adjustedStrideLength = if (currentSpeed > 2.5f) strideLength * 1.5f else strideLength

            // 現在位置と方向が利用可能な場合、エリア予測を更新
            _currentLocation.value?.let { location ->
                _currentDirection.value?.let { (direction, _) ->
                    // APIの座標情報を使用して位置を更新
                    updateCurrentPosition(direction, adjustedStrideLength)

                    // 加速度センサーによる位置情報をデバッグ情報に追加
                    if (currentLat != null && currentLon != null) {
                        val sensorPositionDebug = "センサー推定位置: (${String.format("%.6f", currentLat)}, ${String.format("%.6f", currentLon)})"
                        val currentDebugInfo = _debugInfo.value ?: ""
                        _debugInfo.postValue("$currentDebugInfo\n$sensorPositionDebug")
                        Log.d(TAG, sensorPositionDebug)
                    }

                    // エリア予測と方向ごとの距離を更新
                    predictCurrentArea(location, direction, _movementDistance.value ?: 0f)
                    updateDirectionalDistances(direction, _movementDistance.value ?: 0f)
                    checkAndUpdatePosition(direction, _movementDistance.value ?: 0f)
                }
            }
        }

        lastAcceleration = magnitude
    }

    // 位置の更新をチェックし、必要に応じて位置情報を更新するメソッド
    private fun checkAndUpdatePosition(direction: Float, distance: Float) {
        val radians = Math.toRadians(direction.toDouble())
        val dx = distance * sin(radians).toFloat()
        val dy = distance * cos(radians).toFloat()

        val lastPos = lastUpdatePosition
        if (lastPos == null) {
            lastUpdatePosition = Pair(dx, dy)
        } else {
            val newX = lastPos.first + dx
            val newY = lastPos.second + dy
            val totalMovement = sqrt(newX * newX + newY * newY)

            if (totalMovement >= SIGNIFICANT_MOVEMENT_THRESHOLD || _currentLocation.value == null) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastScanTime >= SCAN_INTERVAL || _currentLocation.value == null) {
                    requestSingleScan()
                } else {
                    val timeToNextScan = (lastScanTime + SCAN_INTERVAL) - currentTime
                    _scanStatus.postValue("移動検知: スキャン間隔制限中（あと${timeToNextScan / 1000}秒）")
                    Log.d(TAG, "移動検知: スキャン間隔制限中（あと${timeToNextScan / 1000}秒）")
                }
                lastUpdatePosition = Pair(0f, 0f) // リセット
            } else {
                lastUpdatePosition = Pair(newX, newY)
            }
        }
    }
}

