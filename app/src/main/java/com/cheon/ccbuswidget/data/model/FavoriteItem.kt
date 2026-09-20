package com.cheon.ccbuswidget.data.model

/**
 * 즐겨찾기 한 줄. 정류장과 노선을 한 목록에 섞어서 순서를 정할 수 있도록 묶는다.
 * [key] 는 "stop:<nodeId>" / "route:<routeId>" — 저장되는 순서 목록과 LazyColumn key 로 같이 쓴다.
 */
sealed interface FavoriteItem {
    val key: String

    data class Stop(val stop: BusStop) : FavoriteItem {
        override val key: String get() = STOP_PREFIX + stop.nodeId
    }

    data class Route(val route: BusRoute) : FavoriteItem {
        override val key: String get() = ROUTE_PREFIX + route.routeId
    }

    companion object {
        const val STOP_PREFIX = "stop:"
        const val ROUTE_PREFIX = "route:"
    }
}
