package com.cheon.ccbuswidget.data.model

import com.cheon.ccbuswidget.R

/**
 * 노선 유형 (간선 / 지선 / 마을).
 *
 * 색은 Figma 변수 이름을 그대로 따른다.
 *   지선 route_badge2 #00B9BE
 *   간선 route_badge3 #F9A64A
 *   마을 route_badge4 #7250A0
 */
enum class RouteKind(
    val label: String,
    val colorRes: Int,
    /** 지도 위 경유 정류장 점 */
    val stopDotRes: Int,
    /** 지도 위 버스 위치 아이콘 */
    val busMarkerRes: Int
) {
    BRANCH("지선", R.color.route_badge2, R.drawable.map_route_stop_dot2, R.drawable.map_stop_marker2),
    TRUNK("간선", R.color.route_badge3, R.drawable.map_route_stop_dot3, R.drawable.map_stop_marker3),
    VILLAGE("마을", R.color.route_badge4, R.drawable.map_route_stop_dot4, R.drawable.map_stop_marker4);

    companion object {
        /**
         * 노선 유형을 정한다.
         *
         * 1) API(routetp)가 간선·지선·마을을 명시하면 그걸 따른다.
         * 2) 아니면 노선 번호로 분류한다.
         *    - 숫자로 시작하지 않으면 마을 (북산1, 서면5 …)
         *    - 100 이상이면 간선 (100-1, 200, 300 …)
         *    - 그 아래는 지선 (8-1, 15 …)
         */
        fun of(routeNo: String, routeType: String? = null): RouteKind {
            routeType?.let { t ->
                when {
                    t.contains("마을") || t.contains("농어촌") -> return VILLAGE
                    t.contains("간선") -> return TRUNK
                    t.contains("지선") -> return BRANCH
                }
            }

            val head = routeNo.substringBefore('(').trim()
            val lead = head.takeWhile { it.isDigit() }
            if (lead.isEmpty()) return VILLAGE
            val n = lead.toIntOrNull() ?: return BRANCH
            return if (n >= 100) TRUNK else BRANCH
        }
    }
}

/**
 * 뱃지에 넣을 노선 번호.
 * "서면5(서면100)" 처럼 괄호가 붙는 노선은 주번호를 크게, 괄호를 작게 두 줄로 쓴다.
 */
data class RouteLabel(val main: String, val sub: String?) {
    val isTwoLine: Boolean get() = sub != null

    companion object {
        fun of(routeNo: String): RouteLabel {
            val no = routeNo.trim()
            val i = no.indexOf('(')
            return if (i > 0 && no.endsWith(")")) {
                RouteLabel(no.substring(0, i).trim(), no.substring(i))
            } else {
                RouteLabel(no, null)
            }
        }
    }
}

