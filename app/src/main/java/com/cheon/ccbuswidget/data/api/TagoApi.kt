package com.cheon.ccbuswidget.data.api

import android.content.Context
import com.cheon.ccbuswidget.data.api.TagoApi.cachedItems
import com.cheon.ccbuswidget.data.api.TagoApi.prefetch
import com.cheon.ccbuswidget.data.model.BusArrival
import com.cheon.ccbuswidget.data.model.BusLocation
import com.cheon.ccbuswidget.data.model.BusRoute
import com.cheon.ccbuswidget.data.model.BusStop
import com.cheon.ccbuswidget.data.model.RouteDetail
import com.cheon.ccbuswidget.data.model.RouteStop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 국토교통부(TAGO) 버스 관련 오픈 API 클라이언트.
 *
 *  - 버스정류소정보 : https://www.data.go.kr/data/15098534/openapi.do
 *  - 버스도착정보   : https://www.data.go.kr/data/15098530/openapi.do
 *
 * 공공데이터포털에서 위 두 서비스의 활용신청을 하고 발급받은 서비스키가 필요하다.
 *
 * ## 속도
 * TAGO 서버는 한 번 부를 때마다 수백 ms~수 초가 걸린다. 그래서
 *  - 잘 바뀌지 않는 정보(노선 목록 · 노선 경로 · 노선 정보 · 정류장 경유 노선 · 검색 결과)는
 *    메모리 + 디스크(cacheDir/tago)에 저장해 두고 다시 부르지 않는다. ([cachedItems])
 *  - 도시 전체 정류장 · 노선 목록을 한 번 받아 두고([prefetch]) 검색 · 주변 정류장은 폰 안에서 바로 찾는다.
 *  - 실시간 정보(도착 예정 · 버스 위치)만 매번 서버에 묻는다.
 */
object TagoApi {

    private const val BASE = "https://apis.data.go.kr/1613000"
    private const val STOP_SERVICE = "$BASE/BusSttnInfoInqireService"
    private const val ARRIVAL_SERVICE = "$BASE/ArvlInfoInqireService"
    private const val ROUTE_SERVICE = "$BASE/BusRouteInfoInqireService"
    private const val LOCATION_SERVICE = "$BASE/BusLcInfoInqireService"

    class ApiException(message: String) : Exception(message)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private const val MINUTE = 60_000L
    private const val DAY = 24 * 60 * MINUTE
    /** 노선 · 정류장처럼 거의 안 바뀌는 정보 */
    private const val STATIC_TTL = 7 * DAY
    /** 검색어별 결과 */
    private const val SEARCH_TTL = DAY

    @Volatile private var cacheDir: File? = null
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 디스크 캐시 위치를 잡는다. 앱이 켜질 때 한 번 부르면 된다 (안 불러도 메모리 캐시는 동작). */
    fun init(context: Context) {
        if (cacheDir == null) cacheDir = File(context.applicationContext.cacheDir, "tago").apply { mkdirs() }
    }

    /**
     * 도시 전체 정류장 · 노선 목록을 미리 받아 둔다 (백그라운드, 이미 있으면 아무것도 안 함).
     * 받아 두면 검색 · 주변 정류장 찾기가 서버를 거치지 않고 즉시 끝난다.
     */
    fun prefetch(serviceKey: String, cityCode: String) {
        if (serviceKey.isBlank()) return
        ensureStopIndex(serviceKey, cityCode)
        background.launch { runCatching { allRoutes(serviceKey, cityCode) } }
    }

    // ---------------------------------------------------------------- 공개 API

    /**
     * 정류소 이름으로 검색 (예: "명동", "춘천역").
     * 도시 전체 정류장 목록이 받아져 있으면 폰 안에서 바로 찾고, 아직이면 서버에 묻는다.
     */
    suspend fun searchStops(
        serviceKey: String,
        cityCode: String,
        keyword: String
    ): List<BusStop> = withContext(Dispatchers.IO) {
        stopIndexFor(cityCode)?.let { return@withContext filterStops(it, keyword) }
        ensureStopIndex(serviceKey, cityCode)

        val url = urlBuilder("$STOP_SERVICE/getSttnNoList", serviceKey)
            .addQueryParameter("cityCode", cityCode)
            .addQueryParameter("nodeNm", keyword)
            .addQueryParameter("numOfRows", "100")
            .addQueryParameter("pageNo", "1")
            .build()

        cachedItems(url, SEARCH_TTL).mapNotNull(::parseStop).distinctBy { it.nodeId }
    }

    /**
     * GPS 좌표 주변(반경 500m) 정류소.
     * 도시 전체 정류장 목록이 있으면 폰 안에서 거리로 골라 즉시 돌려준다.
     */
    suspend fun nearbyStops(
        serviceKey: String,
        lat: Double,
        lng: Double
    ): List<BusStop> = withContext(Dispatchers.IO) {
        stopIndex?.second?.let { all ->
            return@withContext all
                .mapNotNull { s ->
                    val la = s.gpsLat ?: return@mapNotNull null
                    val ln = s.gpsLng ?: return@mapNotNull null
                    s to distanceMeters(lat, lng, la, ln)
                }
                .filter { it.second <= 500.0 }
                .sortedBy { it.second }
                .take(50)
                .map { it.first }
        }
        val url = urlBuilder("$STOP_SERVICE/getCrdntPrxmtSttnList", serviceKey)
            .addQueryParameter("gpsLati", lat.toString())
            .addQueryParameter("gpsLong", lng.toString())
            .addQueryParameter("numOfRows", "50")
            .addQueryParameter("pageNo", "1")
            .build()

        items(get(url)).mapNotNull(::parseStop).distinctBy { it.nodeId }
    }

    /** 해당 정류소를 경유하는 전체 노선 목록 */
    suspend fun routesOfStop(
        serviceKey: String,
        cityCode: String,
        nodeId: String
    ): List<BusRoute> = withContext(Dispatchers.IO) {
        val url = urlBuilder("$STOP_SERVICE/getSttnThrghRouteList", serviceKey)
            .addQueryParameter("cityCode", cityCode)
            .addQueryParameter("nodeid", nodeId)
            .addQueryParameter("nodeId", nodeId)
            .addQueryParameter("numOfRows", "200")
            .addQueryParameter("pageNo", "1")
            .build()

        // 정류장을 지나는 노선은 거의 안 바뀐다 → 7일 캐시
        cachedItems(url, STATIC_TTL).mapNotNull { o ->
            val no = o.optString("routeno").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            BusRoute(
                routeId = o.optString("routeid"),
                routeNo = no,
                routeType = o.optString("routetp").takeIf { it.isNotBlank() },
                startNode = o.optString("startnodenm").takeIf { it.isNotBlank() },
                endNode = o.optString("endnodenm").takeIf { it.isNotBlank() }
            )
        }.distinctBy { it.routeNo }
            .sortedWith(compareBy({ it.routeNo.toIntOrNull() ?: Int.MAX_VALUE }, { it.routeNo }))
    }

    /** 정류소의 현재 도착 예정 정보 전체 */
    suspend fun arrivals(
        serviceKey: String,
        cityCode: String,
        nodeId: String
    ): List<BusArrival> = withContext(Dispatchers.IO) {
        val url = urlBuilder("$ARRIVAL_SERVICE/getSttnAcctoArvlPrearngeInfoList", serviceKey)
            .addQueryParameter("cityCode", cityCode)
            .addQueryParameter("nodeId", nodeId)
            .addQueryParameter("nodeid", nodeId)
            .addQueryParameter("numOfRows", "100")
            .addQueryParameter("pageNo", "1")
            .build()

        items(get(url)).mapNotNull { o ->
            val no = o.optString("routeno").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            BusArrival(
                routeId = o.optString("routeid"),
                routeNo = no,
                routeType = o.optString("routetp").takeIf { it.isNotBlank() },
                arrTimeSec = o.optInt("arrtime", -1),
                prevStationCount = o.optInt("arrprevstationcnt", -1),
                vehicleType = o.optString("vehicletp").takeIf { it.isNotBlank() }
            )
        }.filter { it.arrTimeSec >= 0 }
            .sortedBy { it.arrTimeSec }
    }

    /** 노선이 지나는 정류소 전체 목록 (순번 순) */
    suspend fun routeStops(
        serviceKey: String,
        cityCode: String,
        routeId: String
    ): List<RouteStop> = withContext(Dispatchers.IO) {
        val url = urlBuilder("$ROUTE_SERVICE/getRouteAcctoThrghSttnList", serviceKey)
            .addQueryParameter("cityCode", cityCode)
            .addQueryParameter("routeId", routeId)
            .addQueryParameter("numOfRows", "500")
            .addQueryParameter("pageNo", "1")
            .build()

        // 노선 경로(경유 정류장)는 거의 안 바뀐다 → 7일 캐시
        cachedItems(url, STATIC_TTL).mapNotNull { o ->
            val id = o.optString("nodeid").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            RouteStop(
                nodeId = id,
                nodeName = o.optString("nodenm"),
                nodeNo = o.optString("nodeno").takeIf { it.isNotBlank() && it != "null" },
                order = o.optInt("nodeord", 0),
                lat = o.optDouble("gpslati").takeIf { !it.isNaN() },
                lng = o.optDouble("gpslong").takeIf { !it.isNaN() },
                upDown = o.optString("updowncd").takeIf { it.isNotBlank() }
            )
        }.sortedBy { it.order }
    }

    /** 노선 기본 정보 (기점/종점, 첫차/막차, 배차간격) */
    suspend fun routeDetail(
        serviceKey: String,
        cityCode: String,
        routeId: String
    ): RouteDetail? = withContext(Dispatchers.IO) {
        val url = urlBuilder("$ROUTE_SERVICE/getRouteInfoIem", serviceKey)
            .addQueryParameter("cityCode", cityCode)
            .addQueryParameter("routeId", routeId)
            .build()

        cachedItems(url, STATIC_TTL).firstOrNull()?.let { o ->
            RouteDetail(
                routeId = o.optString("routeid").ifBlank { routeId },
                routeNo = o.optString("routeno"),
                routeType = o.optString("routetp").takeIf { it.isNotBlank() },
                startNode = o.optString("startnodenm").takeIf { it.isNotBlank() },
                endNode = o.optString("endnodenm").takeIf { it.isNotBlank() },
                firstBus = hhmm(o.optString("startvehicletime")),
                lastBus = hhmm(o.optString("endvehicletime")),
                intervalMin = o.optString("intervaltime").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }

    /**
     * 노선 번호로 검색 (검색 화면에서 쓴다).
     * 도시 전체 노선 목록(한 번 받아 7일 보관)에서 폰 안에서 바로 찾는다.
     * 목록을 못 받으면 예전처럼 서버에 번호로 묻는다. 번호를 비우면 전체 노선.
     */
    suspend fun searchRoutes(
        serviceKey: String,
        cityCode: String,
        routeNo: String
    ): List<RouteDetail> = withContext(Dispatchers.IO) {
        val all = runCatching { allRoutes(serviceKey, cityCode) }.getOrNull()
        if (!all.isNullOrEmpty()) return@withContext filterRoutes(all, routeNo)

        val url = urlBuilder("$ROUTE_SERVICE/getRouteNoList", serviceKey)
            .addQueryParameter("cityCode", cityCode)
            .apply { if (routeNo.isNotBlank()) addQueryParameter("routeNo", routeNo) }
            .addQueryParameter("numOfRows", "200")
            .addQueryParameter("pageNo", "1")
            .build()
        cachedItems(url, SEARCH_TTL).mapNotNull(::parseRouteDetail).distinctBy { it.routeId }
    }

    /** 노선을 달리는 버스들의 현재 위치 */
    suspend fun busLocations(
        serviceKey: String,
        cityCode: String,
        routeId: String
    ): List<BusLocation> = withContext(Dispatchers.IO) {
        val url = urlBuilder("$LOCATION_SERVICE/getRouteAcctoBusLcList", serviceKey)
            .addQueryParameter("cityCode", cityCode)
            .addQueryParameter("routeId", routeId)
            .addQueryParameter("numOfRows", "100")
            .addQueryParameter("pageNo", "1")
            .build()

        items(get(url)).map { o ->
            BusLocation(
                vehicleNo = o.optString("vehicleno"),
                nodeId = o.optString("nodeid").takeIf { it.isNotBlank() },
                nodeName = o.optString("nodenm").takeIf { it.isNotBlank() },
                order = o.optInt("nodeord", -1),
                lat = o.optDouble("gpslati").takeIf { !it.isNaN() },
                lng = o.optDouble("gpslong").takeIf { !it.isNaN() }
            )
        }
    }

    // ---------------------------------------------------------------- 도시 전체 목록 (로컬 검색용)

    /** 도시코드 → 그 도시의 전체 정류장 */
    @Volatile private var stopIndex: Pair<String, List<BusStop>>? = null
    private var stopIndexJob: Job? = null
    private var stopIndexFailedAt = 0L
    @Volatile private var routeIndex: Pair<String, List<RouteDetail>>? = null

    private fun stopIndexFor(cityCode: String): List<BusStop>? =
        stopIndex?.takeIf { it.first == cityCode }?.second

    /** 전체 정류장 목록이 없으면 백그라운드로 받기 시작한다. 실패하면 10분 뒤에 다시 시도. */
    private fun ensureStopIndex(serviceKey: String, cityCode: String) {
        synchronized(this) {
            if (stopIndexFor(cityCode) != null) return
            if (stopIndexJob?.isActive == true) return
            if (System.currentTimeMillis() - stopIndexFailedAt < 10 * MINUTE) return
            stopIndexJob = background.launch {
                val all = runCatching { loadAllStops(serviceKey, cityCode) }.getOrNull()
                if (all.isNullOrEmpty()) stopIndexFailedAt = System.currentTimeMillis()
                else stopIndex = cityCode to all
            }
        }
    }

    /** 이름 없이 정류장 목록을 부르면 도시 전체가 온다 (1000개씩, 춘천은 2~3쪽) */
    private fun loadAllStops(serviceKey: String, cityCode: String): List<BusStop> {
        val all = mutableListOf<BusStop>()
        for (page in 1..10) {
            val url = urlBuilder("$STOP_SERVICE/getSttnNoList", serviceKey)
                .addQueryParameter("cityCode", cityCode)
                .addQueryParameter("numOfRows", "1000")
                .addQueryParameter("pageNo", page.toString())
                .build()
            val batch = cachedItems(url, STATIC_TTL).mapNotNull(::parseStop)
            all += batch
            if (batch.size < 1000) break
        }
        return all.distinctBy { it.nodeId }
    }

    /** 도시 전체 노선 (한 번 받아 메모리 + 디스크 7일) */
    private fun allRoutes(serviceKey: String, cityCode: String): List<RouteDetail> {
        routeIndex?.takeIf { it.first == cityCode }?.let { return it.second }
        val url = urlBuilder("$ROUTE_SERVICE/getRouteNoList", serviceKey)
            .addQueryParameter("cityCode", cityCode)
            .addQueryParameter("numOfRows", "1000")
            .addQueryParameter("pageNo", "1")
            .build()
        val all = cachedItems(url, STATIC_TTL).mapNotNull(::parseRouteDetail).distinctBy { it.routeId }
        if (all.isNotEmpty()) routeIndex = cityCode to all
        return all
    }

    private fun normalize(text: String) = text.replace(" ", "").lowercase()

    /** 서버 검색과 같은 '포함' 검색. 이름이 검색어와 같은 것 → 검색어로 시작하는 것 → 나머지 순 */
    private fun filterStops(all: List<BusStop>, keyword: String): List<BusStop> {
        val q = normalize(keyword)
        if (q.isEmpty()) return emptyList()
        return all.asSequence()
            .map { it to normalize(it.nodeName) }
            .filter { (_, name) -> name.contains(q) }
            .sortedWith(compareBy<Pair<BusStop, String>>({ (_, name) ->
                when {
                    name == q -> 0
                    name.startsWith(q) -> 1
                    else -> 2
                }
            }, { (stop, _) -> stop.nodeName }))
            .map { it.first }
            .take(100)
            .toList()
    }

    /** 번호 '포함' 검색. 같은 번호 → 그 번호로 시작 → 나머지, 같은 순위 안에서는 번호 순 */
    private fun filterRoutes(all: List<RouteDetail>, routeNo: String): List<RouteDetail> {
        val q = normalize(routeNo)
        val hits = if (q.isEmpty()) all else all.filter { normalize(it.routeNo).contains(q) }
        return hits.sortedWith(compareBy<RouteDetail>({
            val no = normalize(it.routeNo)
            when {
                q.isEmpty() -> 0
                no == q -> 0
                no.startsWith(q) -> 1
                else -> 2
            }
        }, { it.routeNo.takeWhile { c -> c.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE }, { it.routeNo }))
    }

    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2).let { it * it } +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLng / 2).let { it * it }
        return 2 * r * Math.asin(Math.sqrt(a))
    }

    private fun parseStop(o: JSONObject): BusStop? {
        val id = o.optString("nodeid").takeIf { it.isNotBlank() } ?: return null
        return BusStop(
            nodeId = id,
            nodeName = o.optString("nodenm"),
            nodeNo = o.optString("nodeno").takeIf { it.isNotBlank() && it != "null" },
            gpsLat = o.optDouble("gpslati").takeIf { !it.isNaN() },
            gpsLng = o.optDouble("gpslong").takeIf { !it.isNaN() }
        )
    }

    private fun parseRouteDetail(o: JSONObject): RouteDetail? {
        val id = o.optString("routeid").takeIf { it.isNotBlank() } ?: return null
        return RouteDetail(
            routeId = id,
            routeNo = o.optString("routeno"),
            routeType = o.optString("routetp").takeIf { it.isNotBlank() },
            startNode = o.optString("startnodenm").takeIf { it.isNotBlank() },
            endNode = o.optString("endnodenm").takeIf { it.isNotBlank() },
            firstBus = hhmm(o.optString("startvehicletime")),
            lastBus = hhmm(o.optString("endvehicletime")),
            intervalMin = o.optString("intervaltime").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    // ---------------------------------------------------------------- 응답 캐시

    /**
     * 메모리 캐시. 키 = 서비스키를 뺀 주소 (키를 바꿔도 같은 데이터는 그대로 쓴다).
     * 이미 해석한 목록을 담아 두어 다시 쓸 때 JSON 을 또 해석하지 않는다.
     * 도시 전체 정류장처럼 큰 응답은 따로 목록(stopIndex)으로 들고 있으므로 여기엔 넣지 않는다.
     */
    private val memoryCache = object : LinkedHashMap<String, Pair<Long, List<JSONObject>>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, List<JSONObject>>>?) =
            size > 200
    }
    private const val MEMORY_CACHE_MAX_CHARS = 64 * 1024

    /**
     * [ttlMs] 안에 받아 둔 응답이 있으면 서버를 부르지 않고 그걸 쓴다 (메모리 → 디스크 → 서버 순).
     * 응답은 한 번만 해석한다. 오류 응답은 저장하지 않는다(해석 단계에서 예외).
     */
    private fun cachedItems(url: HttpUrl, ttlMs: Long): List<JSONObject> {
        val key = url.newBuilder().removeAllQueryParameters("serviceKey").build().toString()
        val now = System.currentTimeMillis()
        synchronized(memoryCache) {
            memoryCache[key]?.let { (savedAt, list) -> if (now - savedAt < ttlMs) return list }
        }
        val file = cacheDir?.let { File(it, sha1(key)) }
        val fromDisk = file?.takeIf { it.exists() && now - it.lastModified() < ttlMs }
            ?.let { f -> runCatching { f.readText() }.getOrNull()?.takeIf { it.isNotBlank() }?.let { f.lastModified() to it } }
        val (savedAt, body) = fromDisk ?: (now to get(url))
        val list = items(body)
        if (fromDisk == null) file?.let { runCatching { it.writeText(body) } }
        if (body.length <= MEMORY_CACHE_MAX_CHARS) synchronized(memoryCache) { memoryCache[key] = savedAt to list }
        return list
    }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }

    // ---------------------------------------------------------------- 내부 구현

    /** "0609" / "06:09" / "0609 " 등을 "06:09" 로 */
    private fun hhmm(raw: String?): String? {
        val digits = raw?.filter { it.isDigit() } ?: return null
        return when {
            digits.length >= 4 -> "${digits.substring(0, 2)}:${digits.substring(2, 4)}"
            else -> null
        }
    }

    private fun urlBuilder(endpoint: String, serviceKey: String): HttpUrl.Builder {
        val builder = endpoint.toHttpUrlOrThrow().newBuilder()
        // 포털에서 주는 키는 "Encoding"(%가 포함) / "Decoding" 두 가지가 있다.
        // 이미 인코딩된 키를 다시 인코딩하면 인증에 실패하므로 구분해서 넣는다.
        if (serviceKey.contains('%')) {
            builder.addEncodedQueryParameter("serviceKey", serviceKey)
        } else {
            builder.addQueryParameter("serviceKey", serviceKey)
        }
        builder.addQueryParameter("_type", "json")
        return builder
    }

    private fun String.toHttpUrlOrThrow(): HttpUrl =
        this.toHttpUrlOrNull() ?: throw ApiException("잘못된 주소: $this")

    private fun get(url: HttpUrl): String {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(request).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                throw ApiException("서버 오류 (HTTP ${res.code})")
            }
            return body
        }
    }

    /** response.body.items.item 을 JSONObject 리스트로 변환 */
    private fun items(raw: String): List<JSONObject> {
        val text = raw.trim()
        if (text.isEmpty()) return emptyList()

        // 인증 실패 등은 JSON 요청이어도 XML 로 돌아오는 경우가 있다.
        if (text.startsWith("<")) throw ApiException(parseXmlError(text))

        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw ApiException("응답을 해석할 수 없습니다. 서비스키와 활용신청 상태를 확인해 주세요.").apply { initCause(e) }
        }

        val response = root.optJSONObject("response") ?: throw ApiException("알 수 없는 응답 형식")
        val header = response.optJSONObject("header")
        val code = header?.optString("resultCode")
        if (code != null && code.isNotBlank() && code != "00" && code != "0") {
            throw ApiException(header.optString("resultMsg").ifBlank { "API 오류 ($code)" })
        }

        val bodyObj = response.opt("body")
        if (bodyObj !is JSONObject) return emptyList()

        val itemsAny = bodyObj.opt("items")
        if (itemsAny !is JSONObject) return emptyList()   // 결과 없으면 "" 로 온다

        return when (val item = itemsAny.opt("item")) {
            is JSONArray -> (0 until item.length()).mapNotNull { item.optJSONObject(it) }
            is JSONObject -> listOf(item)
            else -> emptyList()
        }
    }

    private fun parseXmlError(xml: String): String {
        val msg = Regex("<returnAuthMsg>(.*?)</returnAuthMsg>").find(xml)?.groupValues?.get(1)
            ?: Regex("<errMsg>(.*?)</errMsg>").find(xml)?.groupValues?.get(1)
            ?: Regex("<resultMsg>(.*?)</resultMsg>").find(xml)?.groupValues?.get(1)
        return when {
            msg == null -> "API 호출에 실패했습니다."
            msg.contains("SERVICE_KEY_IS_NOT_REGISTERED") ->
                "등록되지 않은 서비스키입니다. 공공데이터포털에서 해당 API 활용신청이 승인됐는지 확인해 주세요."
            msg.contains("LIMITED_NUMBER_OF_SERVICE_REQUESTS") ->
                "일일 호출 한도를 초과했습니다."
            else -> msg
        }
    }
}

