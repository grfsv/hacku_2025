package com.example.umechika.utils

import android.animation.ValueAnimator
import com.mapbox.common.location.LocationError
import com.mapbox.geojson.Point
import com.mapbox.maps.plugin.locationcomponent.LocationConsumer
import kotlin.math.*

interface RouteCallback {
    fun onRouteLineUpdated(passedIndex: Int)
}

class NavigationManager(
    private val waypoints: List<Point>,
    private val callback: RouteCallback
) : LocationConsumer { // 位置情報のリスナーを実装
    private val thresholdDistance: Double = 10.0 // 10m
//    private val mutableWaypoints: MutableList<Point> = waypoints.toMutableList()

    // 位置情報が更新された時
    override fun onLocationUpdated(vararg location: Point, options: (ValueAnimator.() -> Unit)?) {
        println(waypoints.size)
        if (location.isEmpty()) return

        val userLocation = location[0]  // 引数から位置情報を取得

        val (closestIndex, closestDistance) = findClosestPoint(userLocation)

        if (closestIndex == null) return

        // ルート上で最も近いポイントより前のポイントを削除
        if (closestDistance < thresholdDistance) {
            if (closestIndex < waypoints.size - 1) {
                callback.onRouteLineUpdated(closestIndex - 1)
            }
        }
    }

    override fun onBearingUpdated(vararg bearing: Double, options: (ValueAnimator.() -> Unit)?) {}
    override fun onPuckAccuracyRadiusAnimatorDefaultOptionsUpdated(options: ValueAnimator.() -> Unit) {}
    override fun onPuckBearingAnimatorDefaultOptionsUpdated(options: ValueAnimator.() -> Unit) {}
    override fun onPuckLocationAnimatorDefaultOptionsUpdated(options: ValueAnimator.() -> Unit) {}
    override fun onHorizontalAccuracyRadiusUpdated(
        vararg radius: Double,
        options: (ValueAnimator.() -> Unit)?
    ) {
    }

    override fun onError(error: LocationError) {}

    private fun findClosestPoint(userLocation: Point): Pair<Int?, Double> {
        var minDistance = Double.MAX_VALUE
        var closestIndex: Int? = null

        waypoints.forEachIndexed { index, point ->
            val distance = haversineDistance(userLocation, point)
            if (distance < minDistance) {
                minDistance = distance
                closestIndex = index
            }
        }
        return Pair(closestIndex, minDistance)
    }

    private fun haversineDistance(p1: Point, p2: Point): Double {
        val r = 6371000.0
        val lat1 = Math.toRadians(p1.latitude())
        val lon1 = Math.toRadians(p1.longitude())
        val lat2 = Math.toRadians(p2.latitude())
        val lon2 = Math.toRadians(p2.longitude())

        val dLat = lat2 - lat1
        val dLon = lon2 - lon1

        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return r * c
    }
}
