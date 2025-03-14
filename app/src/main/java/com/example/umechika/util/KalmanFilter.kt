package com.example.umechika.util

class KalmanFilter(private val processNoise: Float, private val measurementNoise: Float) {
    private var estimate = 0f
    private var errorCovariance = 1f

    fun update(measurement: Float): Float {
        // Prediction
        val predictedErrorCovariance = errorCovariance + processNoise

        // Update
        val kalmanGain = predictedErrorCovariance / (predictedErrorCovariance + measurementNoise)
        estimate += kalmanGain * (measurement - estimate)
        errorCovariance = (1 - kalmanGain) * predictedErrorCovariance

        return estimate
    }
}