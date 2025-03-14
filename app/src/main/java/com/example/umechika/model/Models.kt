package com.example.umechika.model

// WiFiデータモデル
data class WifiData(
    val bssid: String,
    val frequency: Int,
    val level: Int,
    val ssid: String
)

// APIリクエストモデル
data class LocationRequest(
    val wifiDataList: List<WifiData>
)

// 境界位置情報
data class BoundaryLocation(
    val lat: Double,
    val lon: Double
)

// 隣接エリア情報
data class AdjacentArea(
    val direction: Int,
    val distance: Int,
    val boundary: BoundaryLocation? = null
)

// エリア詳細情報
data class AreaInfo(
    val id: Int,
    val lat: Double? = null,
    val lon: Double? = null,
    val adjacent: Map<String, AdjacentArea>,
    val timestamp: Long,
    val wifi_data_id: String
)

// マッチング情報
data class MatchInfo(
    val docId: String,
    val score: Double
)

// デバッグ情報
data class DebugInfo(
    val bestMatch: MatchInfo,
    val allMatches: List<MatchInfo>,
    val matchingArea: MatchingArea
)

// マッチングエリア情報
data class MatchingArea(
    val documentId: String,
    val id: Int,
    val wifi_data_id: String
)

// APIレスポンスモデル
data class LocationResponse(
    val areaId: String,
    val areaName: String,
    val confidence: Double,
    val areaInfo: AreaInfo,
    val timestamp: Long,
    val debug: DebugInfo
)
