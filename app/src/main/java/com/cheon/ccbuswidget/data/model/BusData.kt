package com.cheon.ccbuswidget.data.model

/** 정류소 (TAGO nodeid / nodenm / nodeno) */
data class BusStop(
    val nodeId: String,
    val nodeName: String,
    val nodeNo: String? = null,
    val gpsLat: Double? = null,
    val gpsLng: Double? = null
)

/** 정류소를 경유하는 노선 */
data class BusRoute(
    val routeId: String,
    val routeNo: String,
    val routeType: String? = null,
    val startNode: String? = null,
    val endNode: String? = null
)

/** 노선이 지나는 정류소 (노선별 경유 정류소 목록) */
data class RouteStop(
    val nodeId: String,
    val nodeName: String,
    val nodeNo: String?,
    /** 노선 내 순번 */
    val order: Int,
    val lat: Double?,
    val lng: Double?,
    /** 0 = 상행/기점 방향, 1 = 하행/종점 방향 */
    val upDown: String?
)

/** 노선 위를 달리고 있는 버스 한 대 */
data class BusLocation(
    val vehicleNo: String,
    val nodeId: String?,
    val nodeName: String?,
    val order: Int,
    val lat: Double?,
    val lng: Double?
) {
    /**
     * 화면에 보여줄 차량번호.
     * API 는 "강원70자1077" 처럼 주는데 실제로 구분에 쓰이는 건 뒤 4자리라
     * 숫자 끝 4자리만 잘라서 쓴다. (4자리가 안 되면 있는 그대로)
     */
    val shortVehicleNo: String
        get() {
            val digits = vehicleNo.filter { it.isDigit() }
            return if (digits.length >= 4) digits.takeLast(4) else vehicleNo
        }
}

/** 노선 기본 정보 (기점/종점, 첫차/막차) */
data class RouteDetail(
    val routeId: String,
    val routeNo: String,
    val routeType: String?,
    val startNode: String?,
    val endNode: String?,
    val firstBus: String?,
    val lastBus: String?,
    val intervalMin: String?
)

/** 도착 예정 정보 */
data class BusArrival(
    val routeId: String,
    val routeNo: String,
    val routeType: String?,
    /** 남은 시간(초) */
    val arrTimeSec: Int,
    /** 남은 정류장 수 */
    val prevStationCount: Int,
    /** 차량 유형(일반/저상 등) */
    val vehicleType: String?
) {
    /** "3분" / "곧 도착" 형태의 표시용 문자열 */
    val displayTime: String
        get() = when {
            arrTimeSec <= 60 -> "곧 도착"
            else -> "${arrTimeSec / 60}분"
        }

    val displayStations: String
        get() = if (prevStationCount <= 0) "전 정류장" else "${prevStationCount}번째 전"

    /** 좁은 칸(2열 그리드)에서 쓰는 짧은 표기: "13전" */
    val displayStationsShort: String
        get() = if (prevStationCount <= 0) "곧" else "${prevStationCount}전"
}

/** 위젯 하나에 저장되는 설정 */
data class WidgetConfig(
    val appWidgetId: Int,
    val nodeId: String,
    val nodeName: String,
    /** 정류장 좌표 (지도 표시용, 예전 설정에는 없을 수 있음) */
    val gpsLat: Double? = null,
    val gpsLng: Double? = null,
    /** 사용자가 고른 노선 번호들 (표시 순서 유지) */
    val routeNumbers: List<String>,
    /** 0(완전 투명) ~ 100(불투명) */
    val backgroundAlpha: Int = 55,
    val darkStyle: Boolean = true,
    val maxRows: Int = 4,
    /**
     * true  : 루트 배경색만 칠하고 One UI Home(7.0+) 런처가 뒤를 블러 처리하게 맡긴다.
     * false : 앱이 직접 반투명 패널을 그린다 (삼성이 아닌 런처용).
     */
    val oneUiBlur: Boolean = true
) {
    val isValid: Boolean get() = nodeId.isNotBlank()
}

/**
 * 검색 기록 한 줄.
 * 검색어가 아니라 '그 검색에서 무엇을 골랐는지'를 남긴다.
 */
sealed interface SearchHistoryItem {
    /** 같은 항목인지 가리는 값 (저장 줄의 앞부분) */
    val key: String

    data class Stop(val stop: BusStop) : SearchHistoryItem {
        override val key: String get() = "S|" + stop.nodeId
    }

    data class Route(val route: BusRoute) : SearchHistoryItem {
        override val key: String get() = "R|" + route.routeId
    }
}

