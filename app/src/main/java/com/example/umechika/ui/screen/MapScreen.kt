package com.example.umechika.ui.screen

import com.example.umechika.utils.NavigationManager
import com.example.umechika.utils.RouteCallback
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults.buttonColors
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.umechika.R
import com.example.umechika.samples.EmptyRoute
import com.example.umechika.samples.NavigationRoute
import com.example.umechika.ui.component.NavigationButton
import com.example.umechika.ui.theme.*
import com.example.umechika.utils.CustomLocationProvider
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.android.gestures.StandardScaleGestureDetector
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.extension.style.layers.addLayerAt
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.OnScaleListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateOptions


@Composable
fun ShowMap() {
    // 位置情報を追尾するフラグ
    val isTracking = remember { mutableStateOf(true) }
    // 地図の立体表示切り替え
    val isTilt = remember { mutableStateOf(true) }

    // 地図の表示サイズ
    val scale = remember { mutableDoubleStateOf(18.5) }
    val mapViewportState = rememberMapViewportState()
    // 目的地までのルート
    val routeLine = remember { mutableStateOf<NavigationRoute>(EmptyRoute()) }
    val currentMapView = remember { mutableStateOf<com.mapbox.maps.MapView?>(null) }

    // 表示に使うデフォルトのスタイル
    val defaultStyle = "mapbox://styles/umetika/cm85zer6f007f01rg1mrn8ggd"

    // テキストの表示
    val NavigationMessage = remember { mutableStateOf("梅チカナビ") }

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.systemBars.asPaddingValues())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        ) {
            // Box 内にコンテンツを追加してみる
            Text(
                text = NavigationMessage.value,
                modifier = Modifier.align(Alignment.Center),
                fontSize = 24.sp,
                fontWeight = FontWeight.W400
            )
        }
        Box {
            MapboxMap(
                modifier = Modifier.fillMaxSize(),
                style = { MapStyle(defaultStyle) },
                mapViewportState = mapViewportState,
            ) {
                MapEffect(Unit) { mapView ->
                    currentMapView.value = mapView
                    mapView.visibility = View.GONE
                    mapView.location.apply {
                        enabled = true
                        // 地図上に自信の座標を表示する
                        updateSettings {
                            locationPuck = createDefault2DPuck(withBearing = true)
                            enabled = true
                            puckBearing = PuckBearing.HEADING
                            puckBearingEnabled = true
                        }
                    }


                    val japanCenter = Point.fromLngLat(138.2529, 36.2048) // 日本の中心位置
                    mapView.mapboxMap.setCamera(
                        CameraOptions.Builder()
                            .center(japanCenter)
                            .zoom(4.0)
                            .pitch(0.0)
                            .build()
                    )

                    // 自身の位置情報にカメラを移動させる
                    mapViewportState.transitionToFollowPuckState(
                        followPuckViewportStateOptions = FollowPuckViewportStateOptions.Builder()
                            .zoom(scale.doubleValue).pitch(if (isTilt.value) 45.0 else 0.0)
                            .build()
                    )

                    mapView.gestures.apply {
                        // 地図をズームした時にボタン押下時の倍率も同一になるように取得
                        addOnScaleListener(object : OnScaleListener {
                            override fun onScaleBegin(detector: StandardScaleGestureDetector) {}
                            override fun onScale(detector: StandardScaleGestureDetector) {}
                            override fun onScaleEnd(detector: StandardScaleGestureDetector) {
                                scale.doubleValue = mapView.mapboxMap.cameraState.zoom
                            }
                        })
                        // 地図を動かした時にトラッキング状態でないことを通知させる
                        addOnMoveListener(object : OnMoveListener {
                            override fun onMoveBegin(detector: MoveGestureDetector) {
                                isTracking.value = false
                            }

                            override fun onMove(detector: MoveGestureDetector): Boolean {
                                return false
                            }

                            override fun onMoveEnd(detector: MoveGestureDetector) {}
                        })
                    }
                    mapView.visibility = View.VISIBLE
                }

                // 案内先を決定した時の処理
                MapEffect(routeLine.value) { mapView ->
                    // 経路が更新されたならスタイルを更新する
                    mapView.mapboxMap.loadStyle(defaultStyle) {
                        routeLine.value.routes.windowed(2, 1).forEachIndexed { index, segment ->
                            val segmentSourceId = "line-source-$index" // 各線のIDをユニークにする
                            val segmentLayerId = "line-layer-$index"
                            mapView.mapboxMap.getStyle { style ->
                                // GeoJSON ソースを追加
                                val source = geoJsonSource(segmentSourceId) {
                                    geometry(LineString.fromLngLats(segment))
                                }

                                if (!style.styleSourceExists(segmentSourceId)) {
                                    style.addSource(source)
                                } else {
                                    style.getSourceAs<GeoJsonSource>(segmentSourceId)
                                        ?.geometry(LineString.fromLngLats(segment))
                                }

                                style.addLayerAt(
                                    lineLayer(segmentLayerId, segmentSourceId) {
                                        lineCap(LineCap.ROUND)
                                        lineJoin(LineJoin.ROUND)
                                        lineOpacity(0.7)
                                        lineWidth(8.0)
                                        lineColor("#0F3")
                                    }, style.styleLayers.lastIndex
                                )
                            }
                        }
                    }

                    // 決定したルートをもとにルートマネージャーを生成する
                    val routeManager =
                        NavigationManager(routeLine.value.routes, object : RouteCallback {
                            // 案内ルートが更新された時に新しいルートに描画し直す
                            override fun onRouteLineUpdated(passedIndex: Int) {
                                mapView.mapboxMap.style?.let { style ->
                                    for (i in passedIndex downTo 0) {
                                        val segmentSourceId = "line-source-$i"
                                        val segmentLayerId = "line-layer-$i"
                                        if (!style.styleSourceExists(segmentSourceId)) break
                                        style.removeStyleSource(segmentSourceId)
                                        style.removeStyleLayer(segmentLayerId)
                                    }
//                                routeLine.value.floorChangeIndex.toSortedMap(comparator = compareByDescending { it })
//                                    .forEach { (index, floor) ->
//                                        if (passedIndex >= index) {
//                                            mapView.mapboxMap.loadStyle(Style.DARK)
//                                            return@forEach
//                                        }
//                                    }
                                }
                            }
                        })
                    // プロバイダーに位置情報を要求していることを伝える
                    mapView.location.getLocationProvider()
                        ?.registerLocationConsumer(routeManager)
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 56.dp)
            ) {
                ElevatedButton(
                    onClick = {
                        isTilt.value = !isTilt.value

                        if (isTracking.value) {
                            mapViewportState.transitionToFollowPuckState(
                                followPuckViewportStateOptions = FollowPuckViewportStateOptions.Builder()
                                    .zoom(scale.doubleValue)
                                    .pitch(if (isTilt.value) 45.0 else 0.0)
                                    .build()
                            )
                        } else {
                            mapViewportState.setCameraOptions {
                                center(mapViewportState.cameraState?.center)
                                zoom(scale.doubleValue)
                                pitch(if (isTilt.value) 45.0 else 0.0)
                                build()
                            }
                        }
                    },

                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    colors = if (isTilt.value) buttonColors(
                        containerColor = InactiveButtonColor,
                        contentColor = Color.Gray
                    ) else buttonColors(
                        containerColor = InactiveButtonColor,
                        contentColor = Color.Gray
                    )
                ) {
                    // Boxで囲んでアイコンを中央に配置
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = if (isTilt.value) painterResource(R.drawable.ic_three_dimensional)
                            else painterResource(R.drawable.ic_plane),
                            contentDescription = "カップの表示角度の切り替え",
                            modifier = Modifier.requiredSize(width = 24.dp, height = 24.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                NavigationButton(onConfirm = {
                    routeLine.value = it
                    if (it !is EmptyRoute) {
                        currentMapView.value?.location?.setLocationProvider(
                            locationProvider = CustomLocationProvider(context = context)
                        )
                    }
                })

                ElevatedButton(
                    onClick = {
                        isTracking.value = true // Resume tracking
                        mapViewportState.transitionToFollowPuckState(
                            followPuckViewportStateOptions = FollowPuckViewportStateOptions.Builder()
                                .zoom(scale.doubleValue).pitch(if (isTilt.value) 45.0 else 0.0)
                                .build()
                        )
                    }, modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    colors = if (isTracking.value) buttonColors(
                        containerColor = ActiveButtonColor,
                        contentColor = Color.White
                    ) else buttonColors(
                        containerColor = InactiveButtonColor,
                        contentColor = Color.Gray
                    )
                ) {
                    Icon(
                        painter = if (isTracking.value) painterResource(id = R.drawable.ic_my_location) else painterResource(
                            id = R.drawable.ic_disable_location
                        ),
                        contentDescription = "タイルを変更する",
                        modifier = Modifier.requiredSize(48.dp)
                    )
                }
            }
        }
    }
}
