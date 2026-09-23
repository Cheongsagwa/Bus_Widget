package com.cheon.ccbuswidget.data.sync

import android.content.Context
import android.content.SharedPreferences
import com.cheon.ccbuswidget.BuildConfig
import com.cheon.ccbuswidget.data.local.WidgetStore
import com.cheon.ccbuswidget.data.local.WidgetStore.FavoritesSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

/**
 * 즐겨찾기 · 검색 기록 ↔ 동기화 서버(sync-server, Cloudflare Workers).
 *
 * - 로그인 직후 · 앱을 켤 때: 서버 것을 받아 이 기기 것과 합친 뒤 다시 올린다.
 *   (다시 깐 앱은 비어 있으므로 서버 것이 그대로 돌아온다)
 * - 그 뒤로는 즐겨찾기가 바뀔 때마다 잠깐 기다렸다가 통째로 올린다.
 *
 * 서버 주소는 local.properties 의 sync.url 에서 빌드 때 들어온다.
 */
object FavoriteSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var pushJob: Job? = null
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    @Volatile
    private var startupSyncDone = false

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private val _revision = MutableStateFlow(0)
    /** 서버 것을 받아 즐겨찾기를 바꿀 때마다 1씩 오른다. 화면은 이걸 보고 목록을 다시 읽는다. */
    val revision: StateFlow<Int> = _revision

    /** 네이버 키와 서버 주소가 모두 설정돼 있는지 */
    val isAvailable: Boolean get() = NaverAccount.isConfigured && BuildConfig.SYNC_URL.isNotBlank()

    private val endpoint: String get() = BuildConfig.SYNC_URL.trimEnd('/') + "/favorites"

    /** 앱을 켤 때 한 번. 변경 감시를 걸고, 로그인돼 있으면 서버와 한 번 맞춘다. */
    fun init(context: Context) {
        val app = context.applicationContext
        NaverAccount.init(app)
        if (listener == null) {
            val l = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (WidgetStore.isFavoritesKey(key)) schedulePush(app)
            }
            listener = l
            WidgetStore.registerChangeListener(app, l)
        }
        if (!startupSyncDone && isAvailable && NaverAccount.isLoggedIn(app)) {
            startupSyncDone = true
            scope.launch { syncNow(app) }
        }
    }

    /**
     * 서버 것을 받아 합치고 다시 올린다.
     * 성공하면 합친 뒤 즐겨찾기 개수를 돌려준다.
     */
    suspend fun syncNow(context: Context): Result<Int> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val remote = pull(context)
                val local = WidgetStore.favoritesSnapshot(context)
                val merged = merge(local, remote)
                if (merged != local) {
                    WidgetStore.applyFavoritesSnapshot(context, merged)
                    _revision.value += 1
                }
                if (merged != remote) push(context, merged)
                NaverAccount.markSynced(context)
                merged.stops.size + merged.routes.size
            }
        }
    }

    /** 서버에 저장된 즐겨찾기를 지운다 (계정 연결 해제) */
    suspend fun deleteRemote(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { call(context) { it.delete() }; Unit }
    }

    private fun schedulePush(context: Context) {
        if (!isAvailable || !NaverAccount.isLoggedIn(context)) return
        pushJob?.cancel()
        pushJob = scope.launch {
            // 즐겨찾기 편집 저장처럼 여러 값이 연달아 바뀌는 것을 한 번에 올린다
            delay(1500.milliseconds)
            mutex.withLock {
                runCatching {
                    push(context, WidgetStore.favoritesSnapshot(context))
                    NaverAccount.markSynced(context)
                }
            }
        }
    }

    /**
     * 이 기기 것을 기준으로, 서버에만 있는 것을 뒤에 붙인다.
     * 한쪽이 비어 있으면 다른 쪽을 그대로 쓴다.
     */
    internal fun merge(local: FavoritesSnapshot, remote: FavoritesSnapshot): FavoritesSnapshot {
        // 검색 기록: 이 기기 것(최근 것)을 앞에, 서버에만 있는 것을 뒤에 붙여 20개까지
        // 같은 정류장·노선은 "S|nodeId" / "R|routeId" 로 가려 한 번만 남긴다
        val seen = local.history.map { it.historyId() }.toSet()
        val history = (local.history + remote.history.filterNot { it.historyId() in seen })
            .take(WidgetStore.SEARCH_HISTORY_MAX)

        val favorites = when {
            remote.isEmpty -> local
            local.isEmpty -> remote
            else -> {
                fun union(a: Set<String>, b: Set<String>): Set<String> {
                    val ids = a.map { it.substringBefore("|") }.toSet()
                    return a + b.filterNot { it.substringBefore("|") in ids }
                }
                FavoritesSnapshot(
                    stops = union(local.stops, remote.stops),
                    routes = union(local.routes, remote.routes),
                    order = local.order + remote.order.filterNot { it in local.order }
                )
            }
        }
        return favorites.copy(history = history)
    }

    private fun String.historyId(): String = split("|").take(2).joinToString("|")

    // ---- 서버 요청 -----------------------------------------------------------

    private suspend fun pull(context: Context): FavoritesSnapshot {
        val o = JSONObject(call(context) { it.get() })
        return FavoritesSnapshot(
            stops = o.optJSONArray("stops").strings().toSet(),
            routes = o.optJSONArray("routes").strings().toSet(),
            order = o.optJSONArray("order").strings(),
            history = o.optJSONArray("history").strings()
        )
    }

    private suspend fun push(context: Context, s: FavoritesSnapshot) {
        val body = JSONObject()
            .put("stops", JSONArray(s.stops.toList()))
            .put("routes", JSONArray(s.routes.toList()))
            .put("order", JSONArray(s.order))
            .put("history", JSONArray(s.history))
            .toString()
        call(context) { it.put(body.toRequestBody(jsonType)) }
    }

    /** 토큰을 붙여 요청한다. 401 이면 토큰을 갱신해서 한 번 더 시도한다. */
    private suspend fun call(context: Context, method: (Request.Builder) -> Request.Builder): String {
        check(isAvailable) { "동기화 서버가 설정되지 않았습니다" }
        repeat(2) { attempt ->
            val token = NaverAccount.accessToken(context, forceRefresh = attempt > 0)
                ?: error("다시 로그인해 주세요")
            val request = method(Request.Builder().url(endpoint))
                .header("Authorization", "Bearer $token")
                .build()
            http.newCall(request).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (res.code == 401 && attempt == 0) return@use // 토큰 갱신 후 재시도
                if (!res.isSuccessful) error("동기화 서버 오류 (${res.code})")
                return text
            }
        }
        error("다시 로그인해 주세요")
    }

    private fun JSONArray?.strings(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { i -> optString(i).takeIf { it.isNotBlank() } }
    }
}
