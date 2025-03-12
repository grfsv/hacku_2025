package com.example.umechika.ui.screen

import NavigationManager
import RouteCallback
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults.buttonColors
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.umechika.R

import com.example.umechika.samples.toHansinFromHankyu
import com.example.umechika.ui.theme.*
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.android.gestures.StandardScaleGestureDetector
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.Style
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.style.layers.addLayerAt
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.getLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.gestures.GesturesPlugin
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
    val isTilt = remember { mutableStateOf(false) }
    // 地図の表示サイズ
    val scale = remember { mutableDoubleStateOf(15.0) }

    val mapViewportState = rememberMapViewportState()
    val currentStyle = remember { mutableStateOf(Style.STANDARD) }

    // 目的地までのルート
    val routeLine = remember { mutableStateOf<List<Point>>(emptyList()) }
    val context = LocalContext.current




    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.systemBars.asPaddingValues())
    ) {
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState,
        ) {
            // ルートが設定されていた場合にルートの進行状況に応じて視覚的に管理する関数
            // 1. 設定されているルートをコピー
            /// ->ループ
            // 2. 自分が経過したところまでを削除する
            // 3. 自分の位置を追加
            // 4. 描画


            MapEffect(Unit) { mapView ->
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

//                mapView.location.setLocationProvider(
//                    locationProvider = CustomLocationProvider(context = context)
//                )

                mapViewportState.transitionToFollowPuckState(
                    followPuckViewportStateOptions = FollowPuckViewportStateOptions.Builder()
                        .zoom(scale.doubleValue).pitch(0.0).build()
                )

                // ジェスチャーを管理
                val gesturesPlugin: GesturesPlugin = mapView.gestures

                // 地図をズームした時の動きを定義
                val onScaleListener = object : OnScaleListener {
                    override fun onScaleBegin(detector: StandardScaleGestureDetector) {}
                    override fun onScale(detector: StandardScaleGestureDetector) {}
                    override fun onScaleEnd(detector: StandardScaleGestureDetector) {
                        scale.doubleValue = mapView.mapboxMap.cameraState.zoom
                    }
                }
                val onMoveListener = object : OnMoveListener {
                    override fun onMoveBegin(detector: MoveGestureDetector) {
                        isTracking.value = false
                    }

                    override fun onMove(detector: MoveGestureDetector): Boolean {
                        return false
                    }

                    override fun onMoveEnd(detector: MoveGestureDetector) {}
                }

                gesturesPlugin.apply {
                    addOnScaleListener(onScaleListener)
                    addOnMoveListener(onMoveListener)
                }
            }

            // マップのタイル更新、ルート案内設定時に再描画を行う
//            MapEffect(currentStyle.value) { mapView ->
//                mapView.mapboxMap.loadStyle(currentStyle.value) { style ->
//                    style.addSource(
//                        geoJsonSource("line-source").geometry(
//                            LineString.fromLngLats(
//                                routeLine.value
//                            )
//                        )
//                    )
//
//                    if (style.getLayer("line-layer") != null) {
//                        style.removeStyleLayer("line-layer")
//                    }
//
//                    style.addLayerAt(
//                        // LineLayerを追加
//                        lineLayer("line-layer", "line-source") {
//                            lineCap(LineCap.SQUARE)
//                            lineJoin(LineJoin.NONE)
//                            lineOpacity(0.7)
//                            lineWidth(8.0)
//                            lineColor("#0F3")
//                        },
//                        style.styleLayers.lastIndex
//                    )
//                }
//            }
            MapEffect(routeLine.value) { mapView ->
                // 決定したルートをもとにルートマネージャーを生成する
               val routeManager = NavigationManager(toHansinFromHankyu, object : RouteCallback {
                    override fun onRouteLineUpdated(routeLine: List<Point>) {
                        println("位置情報が更新された")
                        mapView.mapboxMap.loadStyle(currentStyle.value) { style ->
                            style.addSource(
                                geoJsonSource("line-source").geometry(
                                    LineString.fromLngLats(
                                        routeLine
                                    )
                                )
                            )

                            if (style.getLayer("line-layer") != null) {
                                style.removeStyleLayer("line-layer")
                            }

                            style.addLayerAt(
                                // LineLayerを追加
                                lineLayer("line-layer", "line-source") {
                                    lineCap(LineCap.SQUARE)
                                    lineJoin(LineJoin.NONE)
                                    lineOpacity(0.7)
                                    lineWidth(8.0)
                                    lineColor("#0F3")
                                },
                                style.styleLayers.lastIndex
                            )
                        }
                    }
                })
                mapView.location.getLocationProvider()?.registerLocationConsumer(routeManager)
            }

        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ElevatedButton(
                onClick = {
                    println("おした")


                    routeLine.value =
                        if (routeLine.value.isEmpty()) toHansinFromHankyu else emptyList()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "ルート案内",
                    modifier = Modifier.size(20.dp)
                )
            }
            ElevatedButton(
                onClick = {
                    isTilt.value = !isTilt.value
                    mapViewportState.setCameraOptions {
                        center(mapViewportState.cameraState?.center)
                        zoom(scale.doubleValue)
                        pitch(if (isTilt.value) 45.0 else 0.0)
                        build()
                    }
                },
                modifier = Modifier.size(80.dp),
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
                    painter = if (isTilt.value) painterResource(R.drawable.ic_plane) else painterResource(
                        R.drawable.ic_three_dimensional
                    ),
                    contentDescription = "現在地に戻る",
                    modifier = Modifier.requiredSize(48.dp)
                )
            }

            Button(
                onClick = {
                    isTracking.value = true // Resume tracking
                    mapViewportState.transitionToFollowPuckState(
                        followPuckViewportStateOptions = FollowPuckViewportStateOptions.Builder()
                            .zoom(scale.doubleValue).pitch(0.0).build()
                    )
                    if (currentStyle.value == Style.STANDARD) {
                        currentStyle.value = "mapbox://styles/umetika/cm82qo3t700b001soh2ylhmx7"
                    } else {
                        currentStyle.value = Style.STANDARD
                    }
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

