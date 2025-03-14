package com.example.umechika.samples

import com.mapbox.geojson.Point

sealed class NavigationRoute {
    enum class Floor {
        second_floor, first_floor, ground_floor,
    }

    abstract val routeName: String
    abstract val routes: List<Point>
    abstract val floorChangeIndex: Map<Int, Floor>
    abstract val messages: Map<Int, String>
}

class FromHankyuToHanshin : NavigationRoute() {

    override val routeName = "阪神 大阪梅田駅"
    override val floorChangeIndex: Map<Int, Floor> =
        mapOf(2 to Floor.first_floor, 4 to Floor.second_floor)

    override val messages: Map<Int, String> = mapOf(
        1 to "東方向に直進"
        2 to "西方向に直進"
        3 to "南方向に直進"
        4 to "北方向に直進"
        5 to "左に曲がる"
        6 to "右に曲がる"
        7 to "2階から1階へ"
        8 to "1階から地下1階へ"
        9 to "地下1階から地下2階へ"
    )

    override val routes: List<Point> = listOf(
        Point.fromLngLat(135.49847, 34.70522),
        Point.fromLngLat(135.49848, 34.70516),
        Point.fromLngLat(135.49849, 34.70510),
        Point.fromLngLat(135.49850, 34.70504),
        Point.fromLngLat(135.49851, 34.70496),
        Point.fromLngLat(135.49851, 34.70487),
        Point.fromLngLat(135.49852, 34.70482),
        Point.fromLngLat(135.49852, 34.70477),
        Point.fromLngLat(135.49853, 34.70470),
        Point.fromLngLat(135.49854, 34.70462),
        Point.fromLngLat(135.49857, 34.70457),
        Point.fromLngLat(135.49860, 34.70451),
        Point.fromLngLat(135.49870, 34.70450),
        Point.fromLngLat(135.49879, 34.70450),
        Point.fromLngLat(135.49889, 34.70449),
        Point.fromLngLat(135.49899, 34.70448),
        Point.fromLngLat(135.49909, 34.70447),
        Point.fromLngLat(135.49918, 34.70447),
        Point.fromLngLat(135.49928, 34.70446),
        Point.fromLngLat(135.49931, 34.70439),
        Point.fromLngLat(135.49934, 34.70431),
        Point.fromLngLat(135.49938, 34.70424),
        Point.fromLngLat(135.49941, 34.70416),
        Point.fromLngLat(135.49944, 34.70408),
        Point.fromLngLat(135.49947, 34.70400),
        Point.fromLngLat(135.49949, 34.70393),
        Point.fromLngLat(135.49952, 34.70385),
        Point.fromLngLat(135.49953, 34.70377),
        Point.fromLngLat(135.49954, 34.70370),
        Point.fromLngLat(135.49955, 34.70362),
        Point.fromLngLat(135.49956, 34.70354),
        Point.fromLngLat(135.49956, 34.70346),
        Point.fromLngLat(135.49955, 34.70338),
        Point.fromLngLat(135.49955, 34.70330),
        Point.fromLngLat(135.49955, 34.70323),
        Point.fromLngLat(135.49955, 34.70315),
        Point.fromLngLat(135.49954, 34.70307),
        Point.fromLngLat(135.49954, 34.70299),
        Point.fromLngLat(135.49954, 34.70291),
        Point.fromLngLat(135.49953, 34.70283),
        Point.fromLngLat(135.49952, 34.70274),
        Point.fromLngLat(135.49951, 34.70266),
        Point.fromLngLat(135.49950, 34.70258),
        Point.fromLngLat(135.49949, 34.70250),
        Point.fromLngLat(135.49948, 34.70241),
        Point.fromLngLat(135.49947, 34.70233),
        Point.fromLngLat(135.49945, 34.70225),
        Point.fromLngLat(135.49943, 34.70218),
        Point.fromLngLat(135.49941, 34.70210),
        Point.fromLngLat(135.49939, 34.70202),
        Point.fromLngLat(135.49937, 34.70195),
        Point.fromLngLat(135.49927, 34.70193),
        Point.fromLngLat(135.49917, 34.70192),
        Point.fromLngLat(135.49907, 34.70190),
        Point.fromLngLat(135.49897, 34.70189),
        Point.fromLngLat(135.49887, 34.70187),
        Point.fromLngLat(135.49878, 34.70187),
        Point.fromLngLat(135.49869, 34.70187),
        Point.fromLngLat(135.49860, 34.70187),
        Point.fromLngLat(135.49850, 34.70187),
        Point.fromLngLat(135.49841, 34.70187),
        Point.fromLngLat(135.49832, 34.70187)
    )
}

class EmptyRoute : NavigationRoute() {
    override val messages: Map<Int, String>
        get() = TODO("Not yet implemented")
    override val routeName = "未選択"
    override val floorChangeIndex: Map<Int, Floor> = emptyMap()
    override val routes: List<Point> = emptyList()
}

