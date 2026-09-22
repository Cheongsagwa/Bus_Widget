package com.cheon.ccbuswidget.feature.stopdetail
//화면 상태, 데이터 로딩, 화면 전환
import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.repeatOnLifecycle
import com.cheon.ccbuswidget.R
import com.cheon.ccbuswidget.data.api.TagoApi
import com.cheon.ccbuswidget.data.local.WidgetStore
import com.cheon.ccbuswidget.data.model.BusArrival
import com.cheon.ccbuswidget.data.model.BusLocation
import com.cheon.ccbuswidget.data.model.BusStop
import com.cheon.ccbuswidget.data.model.FavoriteItem
import com.cheon.ccbuswidget.data.model.RouteDetail
import com.cheon.ccbuswidget.data.model.RouteKind
import com.cheon.ccbuswidget.data.model.RouteStop
import com.cheon.ccbuswidget.feature.settings.SettingsActivity
import com.cheon.ccbuswidget.ui.theme.Tokens
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

/** 지금 보고 있는 화면 */
internal sealed interface Screen {
    /** 지도만 — 앱을 켜면 여기서 시작한다 */
    data object Main : Screen
    /** 지도 + 즐겨찾기 (전용 모핑 애니메이션) */
    data object Favorites : Screen
    /** 지도 + 정류장 시트 */
    data object Stop : Screen
    /** 지도 + 노선 시트 */
    data object Route : Screen
    /** 정류장 세부 확장창 (그랩바를 끌어올렸을 때) */
    data object StopExpanded : Screen
    /** 노선 세부 확장창 */
    data object RouteExpanded : Screen
    /** 검색 화면 */
    data object Search : Screen
}

/** 노선 시트가 보고 있는 노선 */
internal data class RouteRef(val routeId: String, val routeNo: String, val routeType: String?)

/** 정류장 시트에 한 줄로 보여줄 노선 */
internal data class RouteRow(
    val routeId: String,
    val routeNo: String,
    val routeType: String?,
    val first: BusArrival?,
    val second: BusArrival?
)

@Composable
internal fun MapScreen(
    initialStop: BusStop,
    appWidgetId: Int,
    onOpenSettings: () -> Unit,
    /** 첫 화면이 다 준비됐을 때 (스플래시를 걷어도 될 때) 한 번 부른다 */
    onReady: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.KOREA) }

    var apiKey by remember { mutableStateOf(WidgetStore.getApiKey(context)) }
    var cityCode by remember { mutableStateOf(WidgetStore.getCityCode(context)) }
    var naverKey by remember { mutableStateOf(WidgetStore.getNaverKeyId(context)) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                apiKey = WidgetStore.getApiKey(context)
                cityCode = WidgetStore.getCityCode(context)
                naverKey = WidgetStore.getNaverKeyId(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 유리 표면들이 공유하는 지도 스냅샷
    val backdrop = remember { MapBackdrop() }

    val darkTheme = isSystemInDarkTheme()
    val decorView = LocalView.current

    var stop by remember { mutableStateOf(initialStop) }

    // 위젯에서 들어왔으면 그 정류장부터, 앱 아이콘으로 켰으면 지도만
    var screen by remember {
        mutableStateOf<Screen>(if (initialStop.nodeId.isBlank()) Screen.Main else Screen.Stop)
    }
    var route by remember { mutableStateOf<RouteRef?>(null) }
    /** 검색 화면에 들어오기 직전 화면 (뒤로가기로 돌아갈 곳) */
    var searchFrom by remember { mutableStateOf<Screen>(Screen.Main) }
    var stopFrom by remember { mutableStateOf<Screen>(Screen.Main) }
    var routeFrom by remember { mutableStateOf<Screen>(Screen.Main) }
    var favoritesExpanded by remember { mutableStateOf(false) }
    val favoritesListState = rememberLazyListState()
    val favoritesBackdrop = rememberContentBackdrop()
    /** 즐겨찾기 정류장·노선을 섞은 목록 (편집에서 정한 순서) */
    var favoriteItems by remember { mutableStateOf(WidgetStore.getFavoriteItems(context)) }
    LaunchedEffect(screen) {
        if (screen == Screen.Favorites) favoriteItems = WidgetStore.getFavoriteItems(context)
    }
    /**
     * 즐겨찾기 편집 중인 목록 (편집 중이 아니면 null).
     * 삭제·순서 변경은 여기에만 반영되고, 저장을 눌러야 실제로 저장된다. 취소하면 버린다.
     * 편집 중에는 하단 툴바 대신 저장·취소 바가 뜬다.
     */
    var favoritesDraft by remember { mutableStateOf<List<FavoriteItem>?>(null) }
    val favoritesEditing = favoritesDraft != null
    // 편집 중 창을 벗어나면(접힘·다른 화면) 저장하지 않은 편집은 버린다
    LaunchedEffect(screen, favoritesExpanded) {
        if (screen != Screen.Favorites || !favoritesExpanded) favoritesDraft = null
    }

    // 정류장 시트
    var rows by remember { mutableStateOf<List<RouteRow>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var updatedAt by remember { mutableStateOf("") }

    // 노선 시트
    var routeStops by remember { mutableStateOf<List<RouteStop>>(emptyList()) }
    var buses by remember { mutableStateOf<List<BusLocation>>(emptyList()) }
    var routeDetail by remember { mutableStateOf<RouteDetail?>(null) }
    var routeLoading by remember { mutableStateOf(false) }
    var routeError by remember { mutableStateOf<String?>(null) }

    // 메인 지도에 뿌릴 주변 정류장
    var nearby by remember { mutableStateOf<List<BusStop>>(emptyList()) }
    /** 마지막으로 주변 정류장을 불러온 지점 (너무 자주 부르지 않으려고) */
    var lastNearbyAt by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    // 더보기 메뉴
    var showMenu by remember { mutableStateOf(false) }

    // 즐겨찾기 — 정류장 / 노선
    var favorite by remember { mutableStateOf(false) }
    LaunchedEffect(stop.nodeId) {
        favorite = stop.nodeId.isNotBlank() && WidgetStore.isFavorite(context, stop.nodeId)
    }
    var favoriteRoute by remember { mutableStateOf(false) }
    LaunchedEffect(route?.routeId) {
        val id = route?.routeId
        favoriteRoute = id != null && WidgetStore.isFavoriteRoute(context, id)
    }

    // 도시 전체 정류장 · 노선 목록을 미리 받아 둔다 (검색 · 주변 정류장을 폰 안에서 바로 찾도록).
    // 한 번 받으면 7일 동안 디스크에 보관되므로 다음 실행부터는 거의 즉시 끝난다.
    LaunchedEffect(apiKey, cityCode) {
        TagoApi.init(context)
        TagoApi.prefetch(apiKey, cityCode)
    }

    // 현위치로 카메라를 옮겨 달라는 요청 (지도 쪽에서 소비한다)
    var moveToMyLocation by remember { mutableStateOf(0) }
    // 정확한 위치·대략적 위치를 함께 묻는다 (안드로이드 12+ 에서 '대략적'만 골라도 동작하도록)
    val locationPermissions = remember {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> if (result.values.any { it }) moveToMyLocation++ }
    fun hasLocationPermission() = locationPermissions.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    // 위치 이동과 이동한 곳의 렌더링이 모두 끝나야 지도 쪽에서 스플래시를 걷는다.
    var startLocationSettled by remember { mutableStateOf(initialStop.nodeId.isNotBlank()) }
    val latestOnReady by rememberUpdatedState(onReady)
    LaunchedEffect(naverKey) {
        if (naverKey.isBlank()) {
            // 키가 없으면 지도 대신 설정 안내 화면을 보여 준다.
            repeat(2) { androidx.compose.runtime.withFrameNanos { } }
            latestOnReady()
        }
    }

    // 앱 아이콘으로 켰을 때(위젯에서 정류장을 들고 오지 않았을 때)는 내 위치에서 시작한다.
    // 권한이 없으면 처음 한 번 물어보고, 허용하면 그때 옮긴다.
    LaunchedEffect(Unit) {
        if (initialStop.nodeId.isNotBlank()) return@LaunchedEffect
        if (hasLocationPermission()) moveToMyLocation++
        else {
            // 권한 창은 스플래시 뒤에서 기다리게 하지 않는다
            startLocationSettled = true
            locationPermission.launch(locationPermissions)
        }
    }

    suspend fun loadStop() {
        if (stop.nodeId.isBlank()) {
            rows = emptyList()
            loading = false
            return
        }
        if (apiKey.isBlank()) {
            error = "공공데이터 서비스키가 없습니다. 앱에서 먼저 등록해 주세요."
            loading = false
            return
        }
        loading = true
        try {
            // 도착 정보(실시간)와 경유 노선(캐시됨)을 동시에 부른다
            val (arrivals, passing) = kotlinx.coroutines.coroutineScope {
                val arrivalsJob = async { TagoApi.arrivals(apiKey, cityCode, stop.nodeId) }
                val passingJob = async {
                    runCatching { TagoApi.routesOfStop(apiKey, cityCode, stop.nodeId) }
                        .getOrDefault(emptyList())
                }
                arrivalsJob.await() to passingJob.await()
            }
            val byRoute = arrivals.groupBy { it.routeNo }

            val withArrival = byRoute.entries.map { (no, list) ->
                val sorted = list.sortedBy { it.arrTimeSec }
                RouteRow(
                    routeId = sorted.first().routeId,
                    routeNo = no,
                    routeType = sorted.first().routeType,
                    first = sorted.getOrNull(0),
                    second = sorted.getOrNull(1)
                )
            }.sortedBy { it.first?.arrTimeSec ?: Int.MAX_VALUE }

            val idle = passing
                .filter { !byRoute.containsKey(it.routeNo) }
                .map { RouteRow(it.routeId, it.routeNo, it.routeType, null, null) }
                .sortedWith(compareBy({ it.routeNo.toIntOrNull() ?: Int.MAX_VALUE }, { it.routeNo }))

            rows = withArrival + idle
            updatedAt = timeFormat.format(Date())
            error = null

            if (stop.gpsLat == null || stop.gpsLng == null || stop.nodeNo == null) {
                runCatching { TagoApi.searchStops(apiKey, cityCode, stop.nodeName) }
                    .getOrNull()
                    ?.firstOrNull { it.nodeId == stop.nodeId }
                    ?.let { found ->
                        stop = stop.copy(
                            nodeNo = stop.nodeNo ?: found.nodeNo,
                            gpsLat = stop.gpsLat ?: found.gpsLat,
                            gpsLng = stop.gpsLng ?: found.gpsLng
                        )
                        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                            WidgetStore.load(context, appWidgetId)
                                ?.takeIf { it.nodeId == found.nodeId }
                                ?.let { cfg ->
                                    WidgetStore.save(
                                        context,
                                        cfg.copy(gpsLat = found.gpsLat, gpsLng = found.gpsLng)
                                    )
                                }
                        }
                    }
            }
        } catch (e: Exception) {
            error = e.message ?: "도착 정보를 불러오지 못했습니다"
        }
        loading = false
    }

    suspend fun loadRoute(routeId: String, withStops: Boolean) {
        if (apiKey.isBlank()) return
        routeLoading = true
        try {
            // 경로 · 노선 정보(캐시됨)와 버스 위치(실시간)를 동시에 부른다
            kotlinx.coroutines.coroutineScope {
                val stopsJob = if (withStops) async { TagoApi.routeStops(apiKey, cityCode, routeId) } else null
                val detailJob = if (withStops) async {
                    runCatching { TagoApi.routeDetail(apiKey, cityCode, routeId) }.getOrNull()
                } else null
                val busesJob = async {
                    runCatching { TagoApi.busLocations(apiKey, cityCode, routeId) }.getOrDefault(emptyList())
                }
                stopsJob?.let { routeStops = it.await() }
                detailJob?.let { routeDetail = it.await() }
                buses = busesJob.await()
            }
            updatedAt = timeFormat.format(Date())
            routeError = if (routeStops.isEmpty()) "노선 경로 정보를 불러오지 못했습니다" else null
        } catch (e: Exception) {
            routeError = e.message ?: "노선 정보를 불러오지 못했습니다"
        }
        routeLoading = false
    }

    /** 정류장을 골랐을 때 (지도 마커 · 검색 결과 공통) */
    fun openStop(picked: BusStop) {
        val origin = if (screen == Screen.Search) searchFrom else screen
        stopFrom = if (origin == Screen.Favorites ||
            ((origin == Screen.Route || origin == Screen.RouteExpanded) && routeFrom == Screen.Favorites))
            Screen.Favorites else Screen.Main
        stop = picked
        route = null
        routeStops = emptyList()
        buses = emptyList()
        routeDetail = null
        rows = emptyList()
        updatedAt = ""
        screen = Screen.Stop
    }

    /** 노선을 골랐을 때 */
    fun openRoute(routeId: String, routeNo: String, routeType: String?) {
        val origin = if (screen == Screen.Search) searchFrom else screen
        routeFrom = when (origin) {
            Screen.Stop, Screen.StopExpanded -> Screen.Stop
            Screen.Favorites -> Screen.Favorites
            else -> Screen.Main
        }
        routeStops = emptyList()
        buses = emptyList()
        routeDetail = null
        routeError = null
        route = RouteRef(routeId, routeNo, routeType)
        screen = Screen.Route
    }

    // 정류장을 보는 동안 30초마다 도착정보 갱신 (확장창 포함).
    // - 펼침/접힘만 바뀔 때는 다시 부르지 않는다 (screen 대신 '보고 있는지'를 키로).
    // - 앱이 화면에 없을 때(홈으로 나감 · 화면 꺼짐)는 멈췄다가 돌아오면 바로 새로 부른다.
    val watchingStop = screen == Screen.Stop || screen == Screen.StopExpanded
    LaunchedEffect(stop.nodeId, watchingStop, apiKey, cityCode) {
        if (!watchingStop) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                loadStop()
                delay(30.seconds)
            }
        }
    }

    // 노선을 보는 동안: 진입 시 전체 로딩 후 30초마다 버스 위치만 갱신 (앱이 화면에 있을 때만)
    val watchingRoute = screen == Screen.Route || screen == Screen.RouteExpanded
    LaunchedEffect(route?.routeId, watchingRoute, apiKey, cityCode) {
        if (!watchingRoute) return@LaunchedEffect
        val r = route ?: return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            loadRoute(r.routeId, withStops = routeStops.isEmpty())
            while (true) {
                delay(30.seconds)
                loadRoute(r.routeId, withStops = false)
            }
        }
    }

    /** 즐겨찾기 편집 저장: 초안에서 빠진 것은 지우고, 초안의 순서를 저장한다 */
    fun saveFavoritesDraft() {
        val draft = favoritesDraft ?: return
        val kept = draft.map { it.key }.toSet()
        favoriteItems.filterNot { it.key in kept }.forEach { removed ->
            when (removed) {
                is FavoriteItem.Stop -> {
                    WidgetStore.removeFavorite(context, removed.stop.nodeId)
                    if (stop.nodeId == removed.stop.nodeId) favorite = false
                }
                is FavoriteItem.Route -> {
                    WidgetStore.removeFavoriteRoute(context, removed.route.routeId)
                    if (route?.routeId == removed.route.routeId) favoriteRoute = false
                }
            }
        }
        WidgetStore.setFavoriteOrder(context, draft.map { it.key })
        favoriteItems = draft
        favoritesDraft = null
    }

    // 시스템 뒤로가기
    BackHandler(enabled = screen != Screen.Main) {
        when (screen) {
            Screen.Stop -> screen = stopFrom
            Screen.Route -> screen = routeFrom
            Screen.Favorites -> when {
                favoritesEditing -> favoritesDraft = null   // 뒤로가기 = 취소
                favoritesExpanded -> favoritesExpanded = false
                else -> screen = Screen.Main
            }
            Screen.StopExpanded -> screen = Screen.Stop
            Screen.RouteExpanded -> screen = Screen.Route
            Screen.Search -> screen = searchFrom
            else -> Unit
        }
    }
    // 더보기 메뉴가 열려 있으면 그것부터 닫는다
    BackHandler(enabled = showMenu) { showMenu = false }

    val sheetVisible = screen == Screen.Stop || screen == Screen.Route

    /**
     * 검색창 아래에 깔려 있는 화면. 검색창이 페이드로 덮이고 걷히는 동안
     * 원래 보던 시트가 사라지지 않고 그대로 비쳐 보이도록 검색 직전 화면을 계속 그린다.
     */
    val underScreen = if (screen == Screen.Search) searchFrom else screen
    /** 정류장·노선 모핑 시트가 떠 있는지 (접힘·펼침 모두) */
    val stopMode = underScreen == Screen.Stop || underScreen == Screen.StopExpanded
    val routeMode = underScreen == Screen.Route || underScreen == Screen.RouteExpanded

    val kind = route?.let { RouteKind.of(it.routeNo, it.routeType) }

    // 시스템 바 아이콘 색 — 화면과 상관없이 테마를 따른다.
    // 라이트 모드: 검은 아이콘 / 다크 모드: 흰 아이콘.
    // (지도도 다크 모드에서는 야간 지도라 어두우므로 지도 위에서도 흰 아이콘이 맞다)
    LaunchedEffect(darkTheme, decorView) {
        val window = (decorView.context as? android.app.Activity)?.window
            ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    // 메인화면에서는 뒤로가기를 두 번 눌러야 나간다
    var lastBackAt by remember { mutableStateOf(0L) }
    BackHandler(enabled = screen == Screen.Main && !showMenu) {
        val now = System.currentTimeMillis()
        if (now - lastBackAt < 2000) {
            (context as? android.app.Activity)?.finish()
        } else {
            lastBackAt = now
            Toast.makeText(context, "종료하려면 한 번 더 누르세요.", Toast.LENGTH_SHORT).show()
        }
    }

    // 하단 툴바: 화면 맨 아래에서 32 위 (Figma y=826). 내비게이션 바가 더 높으면 그 위에 붙인다.
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val toolbarBottom = maxOf(Tokens.Toolbar.bottomMargin, navBarInset)

    CompositionLocalProvider(LocalMapBackdrop provides backdrop) {
        Box(modifier = Modifier.fillMaxSize().background(colorResource(R.color.expanded_surface))) {
            // ---------------------------------------------------------- 지도
            if (naverKey.isBlank()) {
                MissingKeyPane { context.startActivity(Intent(context, SettingsActivity::class.java)) }
            } else {
                // API 키를 저장하고 돌아왔을 때 인증 실패한 이전 지도 객체를 재사용하지 않는다.
                androidx.compose.runtime.key(naverKey) {
                NaverMapPane(
                    keyId = naverKey,
                    stop = if (screen == Screen.Main || screen == Screen.Favorites) BusStop("", "") else stop,
                    routeStops = if (screen == Screen.Route) routeStops else emptyList(),
                    buses = if (screen == Screen.Route) buses else emptyList(),
                    routeKind = kind,
                    nearbyStops = if (screen == Screen.Main || screen == Screen.Favorites) nearby else emptyList(),
                    onStopPick = { picked -> openStop(picked) },
                    sheetVisible = sheetVisible,
                    // 검색창이 덮고 있으면 지도와 스냅샷을 쉬게 한다.
                    // 확장창은 모핑 전환 동안 블러할 지도가 필요해서 계속 돌린다 (즐겨찾기와 같음).
                    active = screen != Screen.Search,
                    nightMode = darkTheme,
                    moveToMyLocation = moveToMyLocation,
                    backdrop = backdrop,
                    initialLocationSettled = startLocationSettled,
                    onReady = { latestOnReady() },
                    onMyLocationSettled = { if (!startLocationSettled) startLocationSettled = true },
                    onCameraIdle = { lat, lng ->
                        // 지도를 조금 움직일 때마다 부르면 API 호출이 쏟아진다.
                        // 마지막으로 불러온 곳에서 충분히 멀어졌을 때만 부른다.
                        val moved = lastNearbyAt.let { last ->
                            last == null ||
                                kotlin.math.hypot(
                                    (lat - last.first) * 111_000.0,
                                    (lng - last.second) * 88_000.0
                                ) > Tokens.Map.NEARBY_RELOAD_METERS
                        }
                        if ((screen == Screen.Main || screen == Screen.Favorites) && apiKey.isNotBlank() && moved) {
                            lastNearbyAt = lat to lng
                            scope.launch {
                                val found = runCatching { TagoApi.nearbyStops(apiKey, lat, lng) }
                                    .getOrDefault(emptyList())
                                if (found.isNotEmpty()) {
                                    nearby = (nearby + found).distinctBy { it.nodeId }.takeLast(300)
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                }
            }

            // ------------------------------------------------ 떠 있는 시트
            // 즐겨찾기 창과 같은 모핑 전환. 손잡이를 끌어올리거나 누르면 그 카드가 그대로 전체 화면으로
            // 펼쳐지고, 접힌 상태에서 조금만 끌어내리면 메인화면으로 돌아간다.
            // 다른 정류장·노선으로 바뀌면 새 카드가 아래에서 떠오른다.
            val closeSheet = {
                screen = Screen.Main
                route = null
            }
            // 시트가 닫힐 때(뒤로가기 · 다른 탭 · 다른 시트로 전환)는 아래로 미끄러지며 사라진다.
            // 나타날 때는 MorphingSheet 가 직접 떠오르므로 여기서는 따로 넣지 않는다.
            // 사라지는 동안 펼침 상태·노선이 바뀌어 모양이 튀지 않도록 마지막 값을 붙잡아 둔다.
            val sheetExit = slideOutVertically(
                tween(Tokens.Motion.medium, easing = Tokens.Motion.easing)
            ) { it / 3 } + fadeOut(tween(Tokens.Motion.medium, easing = Tokens.Motion.easing))
            val stopExpanded = rememberLatest(stopMode, underScreen == Screen.StopExpanded)
            AnimatedVisibility(stopMode, enter = EnterTransition.None, exit = sheetExit) {
                androidx.compose.runtime.key("stop:" + stop.nodeId) {
                    StopMorphSheet(
                        expanded = stopExpanded,
                        onExpandedChange = { screen = if (it) Screen.StopExpanded else Screen.Stop },
                        onDismiss = closeSheet,
                        stopName = stop.nodeName,
                        stopNo = stop.nodeNo,
                        updatedAt = updatedAt,
                        loading = loading,
                        error = error,
                        rows = rows,
                        isFavorite = favorite,
                        onRefresh = { scope.launch { loadStop() } },
                        onToggleFavorite = { favorite = WidgetStore.toggleFavorite(context, stop) },
                        onRouteClick = { row -> openRoute(row.routeId, row.routeNo, row.routeType) }
                    )
                }
            }
            val shownRoute = rememberLatest(route != null, route)
            val routeExpanded = rememberLatest(routeMode, underScreen == Screen.RouteExpanded)
            AnimatedVisibility(routeMode && route != null, enter = EnterTransition.None, exit = sheetExit) {
                val r = shownRoute ?: return@AnimatedVisibility
                androidx.compose.runtime.key("route:" + r.routeId) {
                    RouteMorphSheet(
                        expanded = routeExpanded,
                        onExpandedChange = { screen = if (it) Screen.RouteExpanded else Screen.Route },
                        onDismiss = closeSheet,
                        routeNo = r.routeNo,
                        routeType = r.routeType,
                        detail = routeDetail,
                        stops = routeStops,
                        buses = buses,
                        currentNodeId = stop.nodeId,
                        loading = routeLoading,
                        error = routeError,
                        updatedAt = updatedAt,
                        isFavorite = favoriteRoute,
                        onRefresh = { scope.launch { loadRoute(r.routeId, true) } },
                        onToggleFavorite = {
                            favoriteRoute = WidgetStore.toggleFavoriteRoute(
                                context, r.routeId, r.routeNo, r.routeType
                            )
                        },
                        onStopClick = { rs ->
                            openStop(BusStop(rs.nodeId, rs.nodeName, rs.nodeNo, rs.lat, rs.lng))
                        }
                    )
                }
            }

            val favoritesMode = underScreen == Screen.Favorites
            val favoritesShownExpanded = rememberLatest(favoritesMode, favoritesExpanded)
            AnimatedVisibility(favoritesMode, enter = EnterTransition.None, exit = sheetExit) {
                FavoritesSheet(
                    expanded = favoritesShownExpanded,
                    onExpandedChange = { favoritesExpanded = it },
                    editing = favoritesEditing,
                    onStartEditing = { favoritesDraft = favoriteItems },
                    items = favoritesDraft ?: favoriteItems,
                    apiKey = apiKey, cityCode = cityCode,
                    listState = favoritesListState, backdrop = favoritesBackdrop,
                    onStop = { openStop(it) },
                    onRoute = { openRoute(it.routeId, it.routeNo, it.routeType) },
                    // 편집 중 삭제·순서 변경은 초안에만 반영한다 (저장 전까지 실제로 지우지 않음)
                    onDelete = { removed ->
                        favoritesDraft = favoritesDraft?.filterNot { it.key == removed.key }
                    },
                    onReorder = { newItems -> if (favoritesDraft != null) favoritesDraft = newItems },
                    // 접힌 즐겨찾기 창을 쓸어내리면 뒤로가기처럼 메인화면으로
                    onDismiss = {
                        screen = Screen.Main
                        favoritesExpanded = false
                    }
                )
            }
            // ------------------------------------------- 지도 위 플로팅 버튼
            // 더보기 메뉴가 열려 있는 동안에는 보이지 않는다
            AnimatedVisibility(
                visible = !showMenu && sheetVisible,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = fadeIn(tween(Tokens.Motion.medium, easing = Tokens.Motion.easing)),
                exit = fadeOut(tween(Tokens.Motion.fast))
            ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(
                        horizontal = Tokens.Glass.buttonMargin,
                        vertical = Tokens.Glass.topBarTop
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                    GlassIconButton(
                        iconRes = R.drawable.ic_back,
                        description = "뒤로"
                    ) {
                        screen = if (screen == Screen.Route) routeFrom else stopFrom
                    }

                Spacer(modifier = Modifier.weight(1f))

                GlassIconButton(
                    iconRes = R.drawable.ic_search,
                    description = "검색"
                ) {
                    searchFrom = screen
                    screen = Screen.Search
                }
            }

            }

            // 현위치 (Figma 40x40, 아이콘 22)
            // 메인: 툴바 윗변 위 25, 오른쪽 10 / 정류장·노선: 시트 윗변 위 35, 오른쪽 15
            // 나타나고 사라질 때는 커지고 작아지며, 메인 ↔ 시트로 자리가 바뀔 때는 미끄러지듯 옮겨 간다.
            val myLocationSpec = tween<Dp>(Tokens.Motion.morph, easing = Tokens.Motion.easing)
            val myLocationEnd by androidx.compose.animation.core.animateDpAsState(
                if (sheetVisible) Tokens.MyLocation.sheetMargin else Tokens.MyLocation.margin,
                myLocationSpec, label = "현위치 오른쪽 여백"
            )
            val myLocationBottom by androidx.compose.animation.core.animateDpAsState(
                if (sheetVisible) navBarInset + Tokens.Sheet.peekHeight + Tokens.MyLocation.gapAboveSheet
                else toolbarBottom + Tokens.Toolbar.height + Tokens.MyLocation.gapAboveToolbar,
                myLocationSpec, label = "현위치 아래 여백"
            )
            val myLocationFade = tween<Float>(Tokens.Motion.medium, easing = Tokens.Motion.easing)
            AnimatedVisibility(
                visible = !showMenu && (screen == Screen.Main || sheetVisible),
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(end = myLocationEnd, bottom = myLocationBottom),
                enter = scaleIn(myLocationFade, initialScale = Tokens.Toolbar.hiddenScale) + fadeIn(myLocationFade),
                exit = scaleOut(myLocationFade, targetScale = Tokens.Toolbar.hiddenScale) + fadeOut(myLocationFade)
            ) {
                GlassIconButton(
                    iconRes = R.drawable.ic_my_location,
                    description = "현위치",
                    size = Tokens.MyLocation.size,
                    iconSize = Tokens.MyLocation.iconSize
                ) {
                    if (hasLocationPermission()) moveToMyLocation++
                    else locationPermission.launch(locationPermissions)
                }
            }

            // 메인 하단 바는 즐겨찾기가 전체 화면이어도 같은 위치에 남는다.
            // 새로 나타날 때는 언제나(앱 시작 · 시트/검색/메뉴를 닫고 돌아올 때 · 편집 종료) 가운데서 커지며 생기고,
            // 사라질 때는 줄어들며 없어진다.
            val toolbarVisible = (screen == Screen.Main || screen == Screen.Favorites) &&
                !showMenu && !favoritesEditing
            val toolbarState = remember { MutableTransitionState(false) }
            SideEffect { toolbarState.targetState = toolbarVisible }
            // 사라지는 동안 선택 강조가 튀지 않도록 마지막 탭을 기억한다
            val lastToolbarTab = remember { mutableStateOf(false) }
            val toolbarFavorites = when (screen) {
                Screen.Main -> false
                Screen.Favorites -> true
                else -> lastToolbarTab.value
            }
            SideEffect { lastToolbarTab.value = toolbarFavorites }
            val toolbarSpec = tween<Float>(Tokens.Motion.medium, easing = Tokens.Motion.easing)
            AnimatedVisibility(
                visibleState = toolbarState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = toolbarBottom),
                enter = scaleIn(toolbarSpec, initialScale = Tokens.Toolbar.hiddenScale) + fadeIn(toolbarSpec),
                exit = scaleOut(toolbarSpec, targetScale = Tokens.Toolbar.hiddenScale) + fadeOut(toolbarSpec)
            ) {
                MainFloatingToolbar(
                    favoritesSelected = toolbarFavorites,
                    onMap = { screen = Screen.Main; favoritesExpanded = false },
                    onFavorites = { screen = Screen.Favorites },
                    onSearch = { searchFrom = screen; screen = Screen.Search },
                    onMenu = { showMenu = true },
                    backdrop = favoritesBackdrop.takeIf { screen == Screen.Favorites }
                )
            }
            // 즐겨찾기 편집 중에는 같은 자리에 저장·취소 바 (Figma 70:1913). 툴바와 같은 확대/축소로 바뀐다.
            AnimatedVisibility(
                visible = favoritesEditing && screen == Screen.Favorites,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = toolbarBottom),
                enter = scaleIn(toolbarSpec, initialScale = Tokens.Toolbar.hiddenScale) + fadeIn(toolbarSpec),
                exit = scaleOut(toolbarSpec, targetScale = Tokens.Toolbar.hiddenScale) + fadeOut(toolbarSpec)
            ) {
                FavoritesEditActionBar(
                    onSave = { saveFavoritesDraft() },
                    onCancel = { favoritesDraft = null },
                    backdrop = favoritesBackdrop
                )
            }
            if (screen == Screen.Main || screen == Screen.Favorites) {
                MainMenuPopup(
                    visible = showMenu,
                    onDismiss = { showMenu = false },
                    onSettings = { showMenu = false; onOpenSettings() },
                    onAccount = { Toast.makeText(context, "계정 설정은 준비 중입니다", Toast.LENGTH_SHORT).show() },
                    onPlaceholder = { Toast.makeText(context, "준비 중인 기능입니다", Toast.LENGTH_SHORT).show() },
                    backdrop = favoritesBackdrop.takeIf { screen == Screen.Favorites }
                )
            }
        }

        // -------------------------------------------------------- 전체 화면
        // (정류장·노선 확장창은 이제 떠 있는 시트가 직접 펼쳐지므로 검색창만 따로 덮는다)
        // 검색창은 지도 · 시트 위로 페이드 인 되고, 닫을 때 페이드 아웃 되며 원래 화면이 드러난다.
        val fadeIn = fadeIn(tween(Tokens.Motion.searchFade, easing = Tokens.Motion.easing))
        val fadeOut = fadeOut(tween(Tokens.Motion.searchFade, easing = Tokens.Motion.easing))

        AnimatedVisibility(screen == Screen.Search, enter = fadeIn, exit = fadeOut) {
            SearchScreen(
                apiKey = apiKey,
                cityCode = cityCode,
                onBack = { screen = searchFrom },
                onPickStop = { picked -> openStop(picked) },
                onPickRoute = { id, no, type -> openRoute(id, no, type) }
            )
        }
    }
}

/** [active] 인 동안의 최신 값을 기억해 두었다가, 아닐 때(사라지는 애니메이션 동안)는 마지막 값을 돌려준다. */
@Composable
private fun <T> rememberLatest(active: Boolean, value: T): T {
    val holder = remember { LatestHolder(value) }
    if (active) holder.value = value
    return holder.value
}

private class LatestHolder<T>(var value: T)
