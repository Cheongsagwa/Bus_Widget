package com.cheon.ccbuswidget.data.local

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import com.cheon.ccbuswidget.data.model.BusRoute
import com.cheon.ccbuswidget.data.model.BusStop
import com.cheon.ccbuswidget.data.model.FavoriteItem
import com.cheon.ccbuswidget.data.model.SearchHistoryItem
import com.cheon.ccbuswidget.data.model.WidgetConfig

/**
 * 위젯별 설정과 공용 API 키를 SharedPreferences 에 저장한다.
 * (위젯은 프로세스가 죽은 뒤에도 동작해야 하므로 동기 저장소를 사용)
 */
object WidgetStore {

    private const val PREF = "cc_bus_widget"
    private const val KEY_API = "api_key"
    private const val KEY_NAVER = "naver_key_id"
    private const val KEY_CITY = "city_code"

    /** 춘천시 도시코드 */
    const val DEFAULT_CITY_CODE = "32010"

    /** One UI Home 의 네이티브 위젯 블러를 기대할 수 있는 기기인지 */
    val isSamsung: Boolean
        get() = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // ---- 공용 설정 ------------------------------------------------------

    fun getApiKey(context: Context): String = prefs(context).getString(KEY_API, "").orEmpty()

    fun setApiKey(context: Context, key: String) {
        prefs(context).edit { putString(KEY_API, key.trim()) }
    }

    /** 네이버 클라우드 플랫폼 Maps 의 Key ID */
    fun getNaverKeyId(context: Context): String =
        prefs(context).getString(KEY_NAVER, "").orEmpty()

    fun setNaverKeyId(context: Context, key: String) {
        prefs(context).edit { putString(KEY_NAVER, key.trim()) }
    }

    fun getCityCode(context: Context): String =
        prefs(context).getString(KEY_CITY, DEFAULT_CITY_CODE) ?: DEFAULT_CITY_CODE

    fun setCityCode(context: Context, code: String) {
        prefs(context).edit { putString(KEY_CITY, code) }
    }

    // ---- 즐겨찾기 --------------------------------------------------------

    private const val KEY_FAVORITES = "favorite_stops"
    /**
     * 즐겨찾기 편집에서 정한 순서. 정류장·노선을 섞은 한 줄짜리 목록이다.
     * "stop:<nodeId>" / "route:<routeId>" 를 줄바꿈으로 이어 저장. 목록에 없는 것은 뒤에 붙는다.
     */
    private const val KEY_FAVORITE_ORDER = "favorite_order"

    private fun readOrder(context: Context): List<String> =
        prefs(context).getString(KEY_FAVORITE_ORDER, "").orEmpty().split("\n").filter { it.isNotBlank() }

    /** 정류장·노선을 섞은 즐겨찾기 순서를 저장한다 ([FavoriteItem.key] 목록) */
    fun setFavoriteOrder(context: Context, keys: List<String>) {
        prefs(context).edit { putString(KEY_FAVORITE_ORDER, keys.joinToString("\n")) }
    }

    private fun removeFromOrder(context: Context, key: String) {
        setFavoriteOrder(context, readOrder(context).filterNot { it == key })
    }

    /**
     * 즐겨찾기 전체(정류장 + 노선)를 저장된 순서대로.
     * 순서에 없는 것(새로 추가한 것)은 뒤에 정류장(이름순) → 노선(번호순)으로 붙는다.
     */
    fun getFavoriteItems(context: Context): List<FavoriteItem> {
        val rank = readOrder(context).withIndex().associate { (i, v) -> v to i }
        val unranked = Int.MAX_VALUE
        val items: List<FavoriteItem> =
            getFavorites(context).map { FavoriteItem.Stop(it) } +
                getFavoriteRoutes(context).map { FavoriteItem.Route(it) }
        // sortedBy 는 안정 정렬이라 순서에 없는 것끼리는 위의 정류장 → 노선 순서가 유지된다
        return items.sortedBy { rank[it.key] ?: unranked }
    }

    /** 저장된 순서를 먼저, 순서에 없는 것(새로 추가한 것 등)은 [fallback] 순으로 뒤에 붙인다 */
    private fun <T> List<T>.inOrder(order: List<String>, id: (T) -> String, fallback: (T) -> String): List<T> {
        val rank = order.withIndex().associate { (i, v) -> v to i }
        return sortedWith(compareBy<T>({ rank[id(it)] ?: Int.MAX_VALUE }, { fallback(it) }))
    }

    /** "nodeId|이름|번호|lat|lng" 한 줄씩 저장 */
    fun getFavorites(context: Context): List<BusStop> =
        prefs(context).getStringSet(KEY_FAVORITES, emptySet()).orEmpty()
            .mapNotNull { line ->
                val p = line.split("|")
                if (p.isEmpty() || p[0].isBlank()) null
                else BusStop(
                    nodeId = p[0],
                    nodeName = p.getOrNull(1).orEmpty(),
                    nodeNo = p.getOrNull(2)?.takeIf { it.isNotBlank() },
                    gpsLat = p.getOrNull(3)?.toDoubleOrNull(),
                    gpsLng = p.getOrNull(4)?.toDoubleOrNull()
                )
            }
            .inOrder(readOrder(context), { FavoriteItem.STOP_PREFIX + it.nodeId }, { it.nodeName })

    /** 즐겨찾기 정류장 하나를 지운다 */
    fun removeFavorite(context: Context, nodeId: String) {
        val p = prefs(context)
        val current = p.getStringSet(KEY_FAVORITES, emptySet()).orEmpty()
            .filterNot { it.substringBefore("|") == nodeId }.toSet()
        p.edit { putStringSet(KEY_FAVORITES, current) }
        removeFromOrder(context, FavoriteItem.STOP_PREFIX + nodeId)
    }

    fun isFavorite(context: Context, nodeId: String): Boolean =
        prefs(context).getStringSet(KEY_FAVORITES, emptySet()).orEmpty()
            .any { it.substringBefore("|") == nodeId }

    /** 즐겨찾기를 켜고 끈다. 결과(켜졌는지)를 돌려준다. */
    fun toggleFavorite(context: Context, stop: BusStop): Boolean {
        val p = prefs(context)
        val current = p.getStringSet(KEY_FAVORITES, emptySet()).orEmpty().toMutableSet()
        val existing = current.firstOrNull { it.substringBefore("|") == stop.nodeId }
        val nowOn: Boolean
        if (existing != null) {
            current.remove(existing)
            nowOn = false
        } else {
            current.add(
                listOf(
                    stop.nodeId,
                    stop.nodeName,
                    stop.nodeNo.orEmpty(),
                    stop.gpsLat?.toString().orEmpty(),
                    stop.gpsLng?.toString().orEmpty()
                ).joinToString("|")
            )
            nowOn = true
        }
        p.edit { putStringSet(KEY_FAVORITES, current) }
        // 해제했다가 다시 추가하면 예전 자리 대신 맨 뒤에 붙도록 순서에서도 뺀다
        if (!nowOn) removeFromOrder(context, FavoriteItem.STOP_PREFIX + stop.nodeId)
        return nowOn
    }

    // ---- 노선 즐겨찾기 ----------------------------------------------------

    private const val KEY_FAVORITE_ROUTES = "favorite_routes"

    /** "routeId|노선번호|유형" 한 줄씩 저장 */
    fun getFavoriteRoutes(context: Context): List<BusRoute> =
        prefs(context).getStringSet(KEY_FAVORITE_ROUTES, emptySet()).orEmpty()
            .mapNotNull { line ->
                val p = line.split("|")
                if (p.isEmpty() || p[0].isBlank()) null
                else BusRoute(
                    routeId = p[0],
                    routeNo = p.getOrNull(1).orEmpty(),
                    routeType = p.getOrNull(2)?.takeIf { it.isNotBlank() }
                )
            }
            .inOrder(readOrder(context), { FavoriteItem.ROUTE_PREFIX + it.routeId }, { it.routeNo })

    /** 즐겨찾기 노선 하나를 지운다 */
    fun removeFavoriteRoute(context: Context, routeId: String) {
        val p = prefs(context)
        val current = p.getStringSet(KEY_FAVORITE_ROUTES, emptySet()).orEmpty()
            .filterNot { it.substringBefore("|") == routeId }.toSet()
        p.edit { putStringSet(KEY_FAVORITE_ROUTES, current) }
        removeFromOrder(context, FavoriteItem.ROUTE_PREFIX + routeId)
    }

    fun isFavoriteRoute(context: Context, routeId: String): Boolean =
        prefs(context).getStringSet(KEY_FAVORITE_ROUTES, emptySet()).orEmpty()
            .any { it.substringBefore("|") == routeId }

    /** 노선 즐겨찾기를 켜고 끈다. 결과(켜졌는지)를 돌려준다. */
    fun toggleFavoriteRoute(
        context: Context,
        routeId: String,
        routeNo: String,
        routeType: String?
    ): Boolean {
        val p = prefs(context)
        val current = p.getStringSet(KEY_FAVORITE_ROUTES, emptySet()).orEmpty().toMutableSet()
        val existing = current.firstOrNull { it.substringBefore("|") == routeId }
        val nowOn: Boolean
        if (existing != null) {
            current.remove(existing)
            nowOn = false
        } else {
            current.add(listOf(routeId, routeNo, routeType.orEmpty()).joinToString("|"))
            nowOn = true
        }
        p.edit { putStringSet(KEY_FAVORITE_ROUTES, current) }
        if (!nowOn) removeFromOrder(context, FavoriteItem.ROUTE_PREFIX + routeId)
        return nowOn
    }

    // ---- 계정 동기화 (네이버 로그인) ---------------------------------------

    /**
     * 서버와 주고받는 즐겨찾기 원본. 저장 형식 그대로(정류장 "nodeId|이름|번호|lat|lng",
     * 노선 "routeId|번호|유형", 순서 "stop:…/route:…") 옮겨서 앱 버전이 달라도 그대로 되살린다.
     */
    data class FavoritesSnapshot(
        val stops: Set<String>,
        val routes: Set<String>,
        val order: List<String>,
        /** 검색 기록 원본 줄 (최근 것이 앞) */
        val history: List<String> = emptyList()
    ) {
        val isEmpty: Boolean get() = stops.isEmpty() && routes.isEmpty()
    }

    fun favoritesSnapshot(context: Context): FavoritesSnapshot {
        val p = prefs(context)
        return FavoritesSnapshot(
            stops = p.getStringSet(KEY_FAVORITES, emptySet()).orEmpty().toSet(),
            routes = p.getStringSet(KEY_FAVORITE_ROUTES, emptySet()).orEmpty().toSet(),
            order = readOrder(context),
            history = rawHistory(context)
        )
    }

    /** 즐겨찾기를 통째로 바꾼다 (서버에서 받아 온 것으로 되살릴 때) */
    fun applyFavoritesSnapshot(context: Context, snapshot: FavoritesSnapshot) {
        prefs(context).edit {
            putStringSet(KEY_FAVORITES, snapshot.stops)
            putStringSet(KEY_FAVORITE_ROUTES, snapshot.routes)
            putString(KEY_FAVORITE_ORDER, snapshot.order.joinToString("\n"))
            putString(KEY_SEARCH_HISTORY, snapshot.history.take(SEARCH_HISTORY_MAX).joinToString("\n"))
        }
    }

    /** 동기화 대상(즐겨찾기 · 검색 기록) 값이 바뀌었는지 (동기화가 이 키들만 지켜본다) */
    fun isFavoritesKey(key: String?): Boolean =
        key == KEY_FAVORITES || key == KEY_FAVORITE_ROUTES || key == KEY_FAVORITE_ORDER ||
            key == KEY_SEARCH_HISTORY

    /** 즐겨찾기가 바뀔 때 알림을 받는다. [listener] 는 호출하는 쪽이 계속 들고 있어야 한다. */
    fun registerChangeListener(
        context: Context,
        listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener
    ) = prefs(context).registerOnSharedPreferenceChangeListener(listener)

    // ---- 검색 기록 --------------------------------------------------------

    private const val KEY_SEARCH_HISTORY = "search_history_v2"
    const val SEARCH_HISTORY_MAX = 20

    /**
     * 검색에서 실제로 골랐던 항목들. 가장 최근 것이 앞에 온다.
     *   정류장 : "S|nodeId|이름|번호|lat|lng"
     *   노선   : "R|routeId|노선번호|유형"
     */
    fun getSearchHistory(context: Context): List<SearchHistoryItem> =
        prefs(context).getString(KEY_SEARCH_HISTORY, "").orEmpty()
            .split("\n")
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val p = line.split("|")
                when (p.getOrNull(0)) {
                    "S" -> p.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { id ->
                        SearchHistoryItem.Stop(
                            BusStop(
                                nodeId = id,
                                nodeName = p.getOrNull(2).orEmpty(),
                                nodeNo = p.getOrNull(3)?.takeIf { it.isNotBlank() },
                                gpsLat = p.getOrNull(4)?.toDoubleOrNull(),
                                gpsLng = p.getOrNull(5)?.toDoubleOrNull()
                            )
                        )
                    }

                    "R" -> p.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { id ->
                        SearchHistoryItem.Route(
                            BusRoute(
                                routeId = id,
                                routeNo = p.getOrNull(2).orEmpty(),
                                routeType = p.getOrNull(3)?.takeIf { it.isNotBlank() }
                            )
                        )
                    }

                    else -> null
                }
            }

    private fun writeHistory(context: Context, lines: List<String>) {
        prefs(context).edit {
            putString(KEY_SEARCH_HISTORY, lines.take(SEARCH_HISTORY_MAX).joinToString("\n"))
        }
    }

    private fun rawHistory(context: Context): List<String> =
        prefs(context).getString(KEY_SEARCH_HISTORY, "").orEmpty()
            .split("\n")
            .filter { it.isNotBlank() }

    /** 검색 결과에서 정류장을 골랐을 때 */
    fun addSearchHistory(context: Context, stop: BusStop) {
        if (stop.nodeId.isBlank()) return
        val line = listOf(
            "S", stop.nodeId, stop.nodeName, stop.nodeNo.orEmpty(),
            stop.gpsLat?.toString().orEmpty(), stop.gpsLng?.toString().orEmpty()
        ).joinToString("|")
        val key = "S|" + stop.nodeId
        writeHistory(context, listOf(line) + rawHistory(context).filterNot { it.startsWith("$key|") })
    }

    /** 검색 결과에서 노선을 골랐을 때 */
    fun addSearchHistory(context: Context, routeId: String, routeNo: String, routeType: String?) {
        if (routeId.isBlank()) return
        val line = listOf("R", routeId, routeNo, routeType.orEmpty()).joinToString("|")
        val key = "R|" + routeId
        writeHistory(context, listOf(line) + rawHistory(context).filterNot { it.startsWith("$key|") })
    }

    fun removeSearchHistory(context: Context, item: SearchHistoryItem) {
        writeHistory(context, rawHistory(context).filterNot { it.startsWith(item.key + "|") })
    }

    // ---- 위젯별 설정 ----------------------------------------------------

    fun save(context: Context, config: WidgetConfig) {
        val id = config.appWidgetId
        prefs(context).edit {
            putString("w_${id}_nodeId", config.nodeId)
            putString("w_${id}_nodeName", config.nodeName)
            putString("w_${id}_lat", config.gpsLat?.toString())
            putString("w_${id}_lng", config.gpsLng?.toString())
            putString("w_${id}_routes", config.routeNumbers.joinToString("|"))
            putInt("w_${id}_alpha", config.backgroundAlpha)
            putBoolean("w_${id}_dark", config.darkStyle)
            putInt("w_${id}_rows", config.maxRows)
            putBoolean("w_${id}_blur", config.oneUiBlur)
        }
    }

    fun load(context: Context, appWidgetId: Int): WidgetConfig? {
        val p = prefs(context)
        val nodeId = p.getString("w_${appWidgetId}_nodeId", null) ?: return null
        return WidgetConfig(
            appWidgetId = appWidgetId,
            nodeId = nodeId,
            nodeName = p.getString("w_${appWidgetId}_nodeName", "").orEmpty(),
            gpsLat = p.getString("w_${appWidgetId}_lat", null)?.toDoubleOrNull(),
            gpsLng = p.getString("w_${appWidgetId}_lng", null)?.toDoubleOrNull(),
            routeNumbers = p.getString("w_${appWidgetId}_routes", "")
                .orEmpty().split("|").filter { it.isNotBlank() },
            backgroundAlpha = p.getInt("w_${appWidgetId}_alpha", 55),
            darkStyle = p.getBoolean("w_${appWidgetId}_dark", true),
            maxRows = p.getInt("w_${appWidgetId}_rows", 4),
            oneUiBlur = p.getBoolean("w_${appWidgetId}_blur", isSamsung)
        )
    }

    fun delete(context: Context, appWidgetId: Int) {
        prefs(context).edit {
            remove("w_${appWidgetId}_nodeId")
            remove("w_${appWidgetId}_nodeName")
            remove("w_${appWidgetId}_lat")
            remove("w_${appWidgetId}_lng")
            remove("w_${appWidgetId}_routes")
            remove("w_${appWidgetId}_alpha")
            remove("w_${appWidgetId}_dark")
            remove("w_${appWidgetId}_rows")
            remove("w_${appWidgetId}_blur")
        }
    }

    // ---- 마지막 갱신 시각 ------------------------------------------------

    fun setLastUpdated(context: Context, appWidgetId: Int, millis: Long) {
        prefs(context).edit { putLong("w_${appWidgetId}_ts", millis) }
    }

    fun getLastUpdated(context: Context, appWidgetId: Int): Long =
        prefs(context).getLong("w_${appWidgetId}_ts", 0L)
}

