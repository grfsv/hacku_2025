package com.example.umechika.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Handler
import com.mapbox.maps.plugin.locationcomponent.LocationConsumer
import com.mapbox.maps.plugin.locationcomponent.LocationProvider

class CustomLocationProvider(context: Context) :
    LocationProvider, SensorEventListener {
   private val sampleCoordinates = arrayListOf(
//        Pair(35.6762, 139.6503),    // 東京
//        Pair(35.4437, 139.6380),    // 横浜
        Pair(34.6937, 135.5023),    // 大阪
//        Pair(35.0116, 135.7681),    // 京都
//        Pair(36.2048, 138.2529)     // 長野
    )

    private val consumers: MutableList<LocationConsumer> = ArrayList()
    private var index = 0
    private val UPDATE_INTERVAL: Long = 5000 // 5秒（ミリ秒）
    private val handler = Handler()

    // センサー関連
    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    init {
        startUpdating()
        // センサーリスナーを登録
        sensorManager.registerListener(
            this,
            rotationVectorSensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )
    }

    override fun registerLocationConsumer(locationConsumer: LocationConsumer) {
        consumers.add(locationConsumer)
    }

    override fun unRegisterLocationConsumer(locationConsumer: LocationConsumer) {
        consumers.remove(locationConsumer)
    }

    override fun onSensorChanged(p0: SensorEvent?) {
        if (p0?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, p0.values)
            val orientationAngles = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            // orientationAngles[0] が azimuth（ラジアン）です
            val azimuth = Math.toDegrees(orientationAngles[0].toDouble())
            // 0～360度に正規化
            val normalizedAzimuth = (azimuth + 360) % 360

            // センサー更新時に各consumerに対してonBearingUpdated()を呼び出す
            for (consumer in consumers) {
                consumer.onBearingUpdated(normalizedAzimuth)
            }
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
    }

    private fun startUpdating() {
        handler.post(UpdateTask())
    }

    private inner class UpdateTask : Runnable {
        override fun run() {
            sendLocationUpdate()
            handler.postDelayed(this, UPDATE_INTERVAL)
        }
    }

    private fun sendLocationUpdate() {
        val currentIndex = index
        index = (index + 1) % sampleCoordinates.size
        val newLocation = Location("sample").apply {
            latitude = sampleCoordinates[currentIndex].first
            longitude = sampleCoordinates[currentIndex].second
            time = System.currentTimeMillis()
        }
        // Location -> Pointに変換する（Point.fromLngLat(longitude, latitude)）
        val point = com.mapbox.geojson.Point.fromLngLat(newLocation.longitude, newLocation.latitude)
        for (consumer in consumers) {
            consumer.onLocationUpdated(point)
        }
    }

    fun stopUpdating() {
        handler.removeCallbacksAndMessages(null)
        sensorManager.unregisterListener(this)
    }
}