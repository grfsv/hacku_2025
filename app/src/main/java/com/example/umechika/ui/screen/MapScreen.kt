package com.example.umechika.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner

import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import com.mapbox.android.gestures.StandardScaleGestureDetector
import com.mapbox.api.directions.v5.models.DirectionsRoute

import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
//import com.mapbox.maps.extension.compose.MapEffect
//import com.mapbox.maps.extension.compose.MapboxMap
//import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.plugin.gestures.GesturesPlugin
import com.mapbox.maps.plugin.gestures.OnScaleListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.core.MapboxNavigationProvider
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineOptions

import org.json.JSONObject


@Composable
fun ShowMap() {
    // 位置情報を追尾するフラグ
    val isTracking = remember { mutableStateOf(true) }
    // 地図の表示サイズ
    val scale = remember { mutableDoubleStateOf(15.0) }

    val mapViewportState = rememberMapViewportState()
    val currentStyle = remember { mutableStateOf(Style.SATELLITE) }

    // setupが既に完了したかどうかを記録する状態を保持
    val isSetup = remember { mutableStateOf(false) }
    // LocalContext.current を使って Context を取得
    val context = LocalContext.current.applicationContext

    // MapboxNavigation のインスタンスを取得
    val loadedStyle = remember { mutableStateOf<Style?>(null) } // ロード済みのスタイルを保持

    val routeLineOptions = MapboxRouteLineOptions.Builder(context).build()

    // ルート描画用のAPIとView
    val routeLineApi = remember {
        MapboxRouteLineApi(routeLineOptions)
    }

    val routeLineView = remember {
        MapboxRouteLineView(routeLineOptions)
    }

    val routeData = remember { mutableStateOf<List<NavigationRoute>>(emptyList()) }

    // 出発地点と目的地を設定（例: 東京駅から大阪駅）
    val destination = Point.fromLngLat(135.4995621018494, 34.700881643904395) // 東京駅
    val origin = Point.fromLngLat(135.494972, 34.693725) // 大阪駅
    // ルート描画を管理
//    val routesObserver = RoutesObserver { result ->
//        routeData.value = result.navigationRoutes
//    }

    fun extractCoordinates(geometry: JSONObject): List<Point> {
        val coordinates = mutableListOf<Point>()

        // geometryの"type"が"LineString"の場合
        if (geometry.getString("type") == "LineString") {
            val coordsArray = geometry.getJSONArray("coordinates")

            // 座標をPointオブジェクトとしてリストに追加
            for (i in 0 until coordsArray.length()) {
                val coord = coordsArray.getJSONArray(i)
                val longitude = coord.getDouble(0)
                val latitude = coord.getDouble(1)

                coordinates.add(Point.fromLngLat(longitude, latitude))
            }
        }

        return coordinates
    }
    // レスポンスのパースとDirectionsRouteの生成
    fun parseDirectionsResponse(responseBody: String): DirectionsRoute? {
        try {
            val responseJson = JSONObject(responseBody)

            // "routes"配列が存在するか確認
            val routes = responseJson.getJSONArray("routes")
            if (routes.length() > 0) {
                // 最初のルートを取得
                val routeJson = routes.getJSONObject(0)

                // DirectionsRouteを生成するためのMapbox APIが提供する方法を使用する
                val route = DirectionsRoute.fromJson(routeJson.toString())

                // 生成されたDirectionsRouteを返す
                return route
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
    // MapboxNavigationにルートを設定
    fun setCustomRouteToNavigation(route: DirectionsRoute) {
//        route.toNavigationRoute(route)
//        MapboxNavigationProvider.retrieve().setNavigationRoutes(listOf(route))
    }

//    fun requestNavigationRoute(
//        mapboxNavigation: MapboxNavigation
//    ) {
//
//        // MapMatchingOptionsを設定
//        @OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
//        val mapMatchingOptions =
//            MapMatchingOptions.Builder().coordinates(listOf(origin, destination)).build()
//        @OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
//
//
//        mapboxNavigation.requestMapMatching(mapMatchingOptions, object : MapMatchingAPICallback {
//            override fun success(result: MapMatchingSuccessfulResult) {
//                routeData.value = result.navigationRoutes
//            }
//
//            override fun onCancel() {
//                println("できなかった")
//            }
//
//            override fun failure(failure: MapMatchingFailure) {
//                println("failure")
//            }
//        }
//        )
//    }

    // 初回描画時にsetupが実行される
    LaunchedEffect(Unit) {
        if (!isSetup.value) {
            println("初回セットアップします")
                println(MapboxNavigationApp.current())
//            if (!MapboxNavigationProvider.isCreated()) {
//                println("MapboxNavigationAppが起動していない")
//                MapboxNavigationProvider.create(NavigationOptions.Builder(context).build())
//            }
//            val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner

//            MapboxNavigationApp.attach(lifecycleOwner)

            isSetup.value = true
        }

    }

    // routeDataが更新されたら、既にロードされた style を使ってルート描画を実施
    LaunchedEffect(routeData.value, loadedStyle.value) {
        loadedStyle.value?.let { style ->
            // ラインの座標点を作成
            val points = listOf(
                origin, destination
            )
            // LineStringを作成
            val lineString = LineString.fromLngLats(points)

            // スタイルを読み込む
            style.addSource(
                geoJsonSource("line-source") {
                    geometry(lineString)
                }
            )

            style.addLayer(
                // LineLayerを追加
                lineLayer("line-layer", "line-source") {
                    lineCap(LineCap.ROUND)
                    lineJoin(LineJoin.ROUND)
                    lineOpacity(0.7)
                    lineWidth(8.0)
                    lineColor("#888")
                }
            )

//            if (routeData.value.isNotEmpty()) {
//                routeLineApi.setNavigationRoutes(routeData.value) { routeDrawData ->
//                    routeLineView.renderRouteDrawData(style, routeDrawData)
//                }
//            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            routeLineApi.cancel()
            routeLineView.cancel()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
            MapView(context).apply {
                location
            }

        }
            )
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState,
        ) {
            MapEffect(Unit) { mapView ->


                mapView.location.apply {
                    enabled = true
                    // 地図上に自信の座標を表示する
                    updateSettings {
                        locationPuck = createDefault2DPuck(context,withBearing = true)
                        enabled = true

//                        puckBearing = PuckBearing.HEADING
//                        puckBearingEnabled = true
                    }
                }

                mapViewportState.setCameraOptions {
                    center(origin)
                    zoom(15.0)
                    bearing(0.0)
                    pitch(0.0)
                    build()
                }

                // ジェスチャーを管理
                val gesturesPlugin: GesturesPlugin = mapView.gestures

                // 地図をズームした時の動きを定義
                val onScaleListener = object : OnScaleListener {
                    override fun onScaleBegin(detector: StandardScaleGestureDetector) {}
                    override fun onScale(detector: StandardScaleGestureDetector) {}
                    override fun onScaleEnd(detector: StandardScaleGestureDetector) {
                        scale.doubleValue = mapView.getMapboxMap().cameraState.zoom
                    }
                }

                gesturesPlugin.apply {
                    addOnScaleListener(onScaleListener)
                }
            }

            // マップのスタイルが変更された時に適用する
            MapEffect(currentStyle.value) { mapView ->
                mapView.getMapboxMap().loadStyleUri(currentStyle.value) { style ->
                    loadedStyle.value = style // ロード済みの style を保持
                }
            }

        }

        FloatingActionButton(
            onClick = {
                isTracking.value = true // Resume tracking
                mapViewportState.transitionToFollowPuckState()
            }, modifier = Modifier
                .align(Alignment.BottomEnd) // Position at bottom-right
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn, contentDescription = "現在地に戻る"
            )
        }

        FloatingActionButton(
            onClick = {
                if (currentStyle.value == Style.MAPBOX_STREETS) {
                    currentStyle.value = "mapbox://styles/umetika/cm82qo3t700b001soh2ylhmx7"
                } else {
                    currentStyle.value = Style.MAPBOX_STREETS
                }
            }, modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search, contentDescription = "タイルを変更する"
            )
        }
        FloatingActionButton(
            onClick = {
                println("ボタンを押した")
                if (MapboxNavigationProvider.isCreated()) {
                    println("ルートをリクエスト")
//                    requestNavigationRoute(MapboxNavigationProvider.retrieve())
                }
            }, modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)

        ) {
            Icon(
                imageVector = Icons.Default.Add, contentDescription = "ナビ開始"
            )
        }
    }
}
