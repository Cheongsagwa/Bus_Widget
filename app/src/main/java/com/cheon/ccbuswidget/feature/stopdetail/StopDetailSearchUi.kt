package com.cheon.ccbuswidget.feature.stopdetail
//검색, 더보기 메뉴
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.cheon.ccbuswidget.R
import com.cheon.ccbuswidget.data.api.TagoApi
import com.cheon.ccbuswidget.data.local.WidgetStore
import com.cheon.ccbuswidget.data.model.BusRoute
import com.cheon.ccbuswidget.data.model.BusStop
import com.cheon.ccbuswidget.data.model.RouteDetail
import com.cheon.ccbuswidget.data.model.RouteKind
import com.cheon.ccbuswidget.data.model.SearchHistoryItem
import com.cheon.ccbuswidget.ui.theme.Tokens
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

// ---------------------------------------------------------------- 검색 화면

/**
 * 검색 전용 화면 (Figma 40:956 / 40:1513).
 *
 * 목록은 화면 전체를 채우고, 뒤로가기 버튼과 검색바가 그 위에 떠 있다.
 * 검색바는 반투명이라 뒤쪽 목록이 비쳐 보인다.
 */
@Composable
internal fun SearchScreen(
    apiKey: String,
    cityCode: String,
    onBack: () -> Unit,
    onPickStop: (BusStop) -> Unit,
    onPickRoute: (String, String, String?) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var stops by remember { mutableStateOf<List<BusStop>>(emptyList()) }
    var routes by remember { mutableStateOf<List<RouteDetail>>(emptyList()) }
    var history by remember { mutableStateOf(WidgetStore.getSearchHistory(context)) }

    // 입력이 멈추면 검색한다
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isBlank()) {
            stops = emptyList()
            routes = emptyList()
            searching = false
            return@LaunchedEffect
        }
        delay(Tokens.SearchScreen.typingDebounceMs.milliseconds)
        if (apiKey.isBlank()) return@LaunchedEffect
        searching = true
        // 정류장·노선을 동시에 불러와 둘 다 도착했을 때 한 번에 보여 준다.
        // (따로 넣으면 응답이 빠른 정류장이 먼저 떴다가 노선이 위로 끼어들어 순서가 튄다)
        val (foundStops, foundRoutes) = kotlinx.coroutines.coroutineScope {
            val s = async { runCatching { TagoApi.searchStops(apiKey, cityCode, q) }.getOrDefault(emptyList()) }
            val r = async { runCatching { TagoApi.searchRoutes(apiKey, cityCode, q) }.getOrDefault(emptyList()) }
            s.await() to r.await()
        }
        stops = foundStops
        routes = foundRoutes
        searching = false
    }

    // 기록에는 검색어가 아니라 '그 검색에서 무엇을 골랐는지'를 남긴다
    fun pickStop(picked: BusStop) {
        WidgetStore.addSearchHistory(context, picked)
        onPickStop(picked)
    }

    fun pickRoute(routeId: String, routeNo: String, routeType: String?) {
        WidgetStore.addSearchHistory(context, routeId, routeNo, routeType)
        onPickRoute(routeId, routeNo, routeType)
    }

    // 정류장마다 '어떤 유형이 서는지'는 따로 물어봐야 한다.
    // 화면에 실제로 보이는 줄만 한 번씩 불러 와서 여기에 담아 둔다.
    val kindCache = remember { mutableStateMapOf<String, List<RouteKind>>() }
    // 검색 기록에는 노선 번호만 남아 있어서 기점 → 종점은 그때그때 불러온다
    val routeInfoCache = remember { mutableStateMapOf<String, String>() }

    val listState = rememberLazyListState()
    val surface = expandedSurface()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // 검색바 뒤를 흐리게 보이려면 목록을 두 번 그려야 한다.
    // 한 번은 그대로, 한 번은 블러를 건 레이어로 검색바 자리에만.
    // 복사본에는 불투명 배경도 포함해야 한다. 투명한 글자만 블러해서
    // 원본 위에 올리면 원본 글자가 그대로 비쳐 '반투명'으로만 보인다.
    val sharpLayer = rememberGraphicsLayer()
    val blurLayer = rememberGraphicsLayer()
    val blurPx = with(LocalDensity.current) { Tokens.Glass.blurRadius.toPx() }
    val barCornerPx = with(LocalDensity.current) { Tokens.SearchScreen.barCorner.toPx() }
    val blurEffect = remember(blurPx) {
        BlurEffect(radiusX = blurPx, radiusY = blurPx, edgeTreatment = TileMode.Decal)
    }
    // drawWithContent는 LazyColumn의 로컬 좌표로 그린다. 검색바의 window 좌표를
    // 그대로 쓰면 인셋·애니메이션 상황에서 클립 영역이 검색바와 어긋난다.
    var listCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var barCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var backButtonCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var barRect by remember { mutableStateOf<Rect?>(null) }
    var backButtonRect by remember { mutableStateOf<Rect?>(null) }

    fun updateBlurRects() {
        val list = listCoordinates
        val bar = barCoordinates
        val backButton = backButtonCoordinates
        if (list?.isAttached != true) {
            barRect = null
            backButtonRect = null
            return
        }
        barRect = bar?.takeIf { it.isAttached }?.let {
            list.localBoundingBoxOf(it, clipBounds = false)
        }
        backButtonRect = backButton?.takeIf { it.isAttached }?.let {
            list.localBoundingBoxOf(it, clipBounds = false)
        }
    }

    CompositionLocalProvider(
        LocalContentColor provides colorResource(R.color.glass_on_surface)
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surface)
    ) {
        // ------------------------------------------------------------ 목록
        // 화면 전체를 채운다. 검색바는 그 위에 떠 있고 뒤쪽 목록이 흐리게 비친다.
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    listCoordinates = it
                    updateBlurRects()
                }
                .drawWithContent {
                    // drawContent() 는 한 번만 부를 수 있다.
                    // 그래서 목록을 레이어에 한 번 담고, 그 레이어를 두 번 그린다.
                    sharpLayer.record {
                        // 부모 Box의 배경은 drawContent()에 포함되지 않는다.
                        // 배경까지 기록해 블러 복사본이 선명한 원본을 덮도록 한다.
                        drawRect(surface)
                        this@drawWithContent.drawContent()
                    }
                    drawLayer(sharpLayer)

                    // 검색바가 놓인 자리에 같은 그림을 흐리게 한 번 더 깐다
                    // 좌표 객체 자체는 이동해도 같은 인스턴스일 수 있다.
                    // 위치 콜백에서 계산한 Rect 상태를 읽어 키보드 이동 때도 다시 그린다.
                    val r = barRect ?: return@drawWithContent
                    blurLayer.renderEffect = blurEffect
                    blurLayer.record { drawLayer(sharpLayer) }
                    fun drawBlurredCopy(rect: Rect, cornerRadius: Float) {
                        clipPath(
                            Path().apply {
                                addRoundRect(RoundRect(rect, CornerRadius(cornerRadius, cornerRadius)))
                            }
                        ) { drawLayer(blurLayer) }
                    }
                    drawBlurredCopy(r, barCornerPx)
                    backButtonRect?.let { drawBlurredCopy(it, it.width / 2f) }
                },
            contentPadding = PaddingValues(
                top = statusTop + Tokens.SearchScreen.listTopPadding,
                bottom = navBottom + Tokens.SearchScreen.listBottomPadding
            )
        ) {
            if (query.isBlank()) {
                // 검색 기록 — 예전에 고른 정류장 · 노선을 그대로 보여 준다
                items(history, key = { "h_${it.key}" }) { item ->
                    HorizontalDivider(color = colorResource(R.color.divider))
                    val remove: @Composable () -> Unit = {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = "기록 지우기",
                            tint = colorResource(R.color.search_secondary),
                            modifier = Modifier
                                .size(Tokens.SearchScreen.removeIconSize)
                                .clip(CircleShape)
                                .clickable {
                                    WidgetStore.removeSearchHistory(context, item)
                                    history = WidgetStore.getSearchHistory(context)
                                }
                        )
                    }
                    when (item) {
                        is SearchHistoryItem.Stop -> SearchStopRow(
                            stop = item.stop,
                            apiKey = apiKey,
                            cityCode = cityCode,
                            kindCache = kindCache,
                            trailing = remove,
                            onClick = { pickStop(item.stop) }
                        )

                        is SearchHistoryItem.Route -> SearchHistoryRouteRow(
                            route = item.route,
                            apiKey = apiKey,
                            cityCode = cityCode,
                            infoCache = routeInfoCache,
                            trailing = remove,
                            onClick = {
                                pickRoute(
                                    item.route.routeId,
                                    item.route.routeNo,
                                    item.route.routeType
                                )
                            }
                        )
                    }
                }
                if (history.isNotEmpty()) item { HorizontalDivider(color = colorResource(R.color.divider)) }
            } else {
                // 결과가 바뀌면 줄이 페이드로 나타나고/사라지고, 자리가 바뀌면 미끄러진다
                fun stopResults() = items(stops, key = { "s_${it.nodeId}" }) { s ->
                    Column(Modifier.animateItem()) {
                        HorizontalDivider(color = colorResource(R.color.divider))
                        SearchStopRow(
                            stop = s,
                            apiKey = apiKey,
                            cityCode = cityCode,
                            kindCache = kindCache,
                            onClick = { pickStop(s) }
                        )
                    }
                }
                fun routeResults() = items(routes, key = { "r_${it.routeId}" }) { r ->
                    Column(Modifier.animateItem()) {
                        HorizontalDivider(color = colorResource(R.color.divider))
                        SearchRouteRow(
                            routeNo = r.routeNo,
                            routeType = r.routeType,
                            info = listOfNotNull(r.startNode, r.endNode).joinToString(" → "),
                            onClick = { pickRoute(r.routeId, r.routeNo, r.routeType) }
                        )
                    }
                }
                // 검색어에 숫자가 들어 있으면 노선 번호를 찾는 경우가 많으므로 노선을 먼저,
                // 글자만 있으면 정류장을 먼저 보여 준다.
                if (query.any { it.isDigit() }) {
                    routeResults()
                    stopResults()
                } else {
                    stopResults()
                    routeResults()
                }
                item { HorizontalDivider(color = colorResource(R.color.divider)) }
            }
        }

        // ------------------------------------------------- 위 · 아래 그라데이션
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(statusTop + Tokens.SearchScreen.fadeHeight)
                .background(
                    Brush.verticalGradient(
                        listOf(surface.copy(alpha = 1f), surface.copy(alpha = 0f))
                    )
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(navBottom + Tokens.SearchScreen.fadeHeight)
                .background(
                    Brush.verticalGradient(
                        listOf(surface.copy(alpha = 0f), surface.copy(alpha = 1f))
                    )
                )
        )

        // ------------------------------------------------------- 뒤로가기
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(Tokens.Expanded.topBarMargin)
                .onGloballyPositioned {
                    backButtonCoordinates = it
                    updateBlurRects()
                }
        ) {
            RoundFloatingButton(
                iconRes = R.drawable.ic_back,
                description = "뒤로",
                background = glassColor(),
                onClick = onBack
            )
        }

        // --------------------------------------------------------- 검색바
        // 키보드가 올라오면 그 위로 붙는다 (ime · 내비게이션 바 중 큰 쪽만큼 띄운다)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                )
                .padding(
                    start = Tokens.SearchScreen.barMarginHorizontal,
                    end = Tokens.SearchScreen.barMarginHorizontal,
                    bottom = Tokens.SearchScreen.barMarginBottom
                )
        ) {
            SearchBar(
                query = query,
                searching = searching,
                onCoordinates = {
                    barCoordinates = it
                    updateBlurRects()
                },
                onQueryChange = { query = it }
            )
        }
    }
    }
}

/** 아래에 떠 있는 검색바 (One UI Simple lower bar) */
@Composable
internal fun SearchBar(
    query: String,
    searching: Boolean,
    onCoordinates: (LayoutCoordinates) -> Unit,
    onQueryChange: (String) -> Unit
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(Tokens.SearchScreen.barCorner)

    // 화면에 들어오면 바로 입력할 수 있게 한다
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Tokens.SearchScreen.barHeight)
            // 목록 쪽에서 이 자리만 흐리게 그려 준다
            .onGloballyPositioned(onCoordinates)
            .glassShadow(shape)
            .background(colorResource(R.color.search_bar_surface), shape)
            .padding(horizontal = Tokens.SearchScreen.barPaddingHorizontal),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    "검색",
                    fontSize = Tokens.SearchScreen.barText,
                    color = colorResource(R.color.search_secondary)
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = Tokens.SearchScreen.barText,
                    color = colorResource(R.color.glass_on_surface)
                ),
                cursorBrush = SolidColor(colorResource(R.color.glass_on_surface)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
            )
        }
        // Figma 에는 검색 버튼이 없다. 글자를 칠 때마다 바로 검색하고,
        // 불러오는 중일 때만 작은 표시를 보여 준다.
        if (searching) {
            CircularProgressIndicator(
                modifier = Modifier.size(Tokens.Misc.smallSpinnerSize),
                strokeWidth = Tokens.Misc.spinnerStroke,
                color = colorResource(R.color.glass_on_surface)
            )
        }
    }
}

/**
 * 검색 결과 · 기록 — 정류장.
 * Figma: 이름 20(Medium) 위 3, 둘째 줄 위 28 에 [번호 30폭][유형 칩 30x16 …]
 */
@Composable
internal fun SearchStopRow(
    stop: BusStop,
    apiKey: String,
    cityCode: String,
    kindCache: MutableMap<String, List<RouteKind>>,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    // 이 정류장에 서는 노선 유형 — 화면에 보이는 줄만 한 번 불러 온다
    var kinds by remember(stop.nodeId) {
        mutableStateOf(kindCache[stop.nodeId].orEmpty())
    }
    LaunchedEffect(stop.nodeId) {
        if (kinds.isNotEmpty() || apiKey.isBlank()) return@LaunchedEffect
        val found = runCatching { TagoApi.routesOfStop(apiKey, cityCode, stop.nodeId) }
            .getOrDefault(emptyList())
            .map { RouteKind.of(it.routeNo, it.routeType) }
            .distinct()
            .sortedBy { it.ordinal }
        if (found.isNotEmpty()) {
            kindCache[stop.nodeId] = found
            kinds = found
        }
    }

    SearchRowFrame(onClick = onClick, trailing = trailing) {
        Text(
            stop.nodeName,
            modifier = Modifier.padding(top = Tokens.SearchScreen.stopNameTop),
            fontSize = Tokens.SearchScreen.stopNameText,
            lineHeight = Tokens.SearchScreen.stopNameText,
            // Figma: 정류장 이름은 SemiBold
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.padding(top = Tokens.SearchScreen.stopSubGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stop.nodeNo.orEmpty(),
                modifier = Modifier.width(Tokens.SearchScreen.stopNoWidth),
                fontSize = Tokens.SearchScreen.stopNoText,
                lineHeight = Tokens.SearchScreen.stopNoText,
                fontWeight = FontWeight.Medium,
                color = colorResource(R.color.search_secondary),
                maxLines = 1
            )
            kinds.forEach { k ->
                Spacer(modifier = Modifier.width(Tokens.SearchScreen.chipGap))
                RouteTypeChip(kind = k, small = true)
            }
        }
    }
}

/**
 * 검색 기록의 노선 한 줄.
 * 기록에는 노선 번호만 저장돼 있어서 기점 → 종점을 한 번 불러와 캐시에 담아 둔다.
 */
@Composable
internal fun SearchHistoryRouteRow(
    route: BusRoute,
    apiKey: String,
    cityCode: String,
    infoCache: MutableMap<String, String>,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    // 저장해 둔 값이 있으면 그대로, 없으면 캐시, 그것도 없으면 조회
    val saved = listOfNotNull(route.startNode, route.endNode)
        .takeIf { it.size == 2 }?.joinToString(" → ")

    var info by remember(route.routeId) {
        mutableStateOf(saved ?: infoCache[route.routeId])
    }
    LaunchedEffect(route.routeId) {
        if (!info.isNullOrBlank() || apiKey.isBlank()) return@LaunchedEffect
        val d = runCatching { TagoApi.routeDetail(apiKey, cityCode, route.routeId) }.getOrNull()
        val text = listOfNotNull(d?.startNode, d?.endNode)
            .takeIf { it.size == 2 }?.joinToString(" → ") ?: return@LaunchedEffect
        infoCache[route.routeId] = text
        info = text
    }

    SearchRouteRow(
        routeNo = route.routeNo,
        routeType = route.routeType,
        info = info,
        trailing = trailing,
        onClick = onClick
    )
}

/** 검색 결과 · 기록 — 노선 (뱃지 / 유형 칩 + 기점 → 종점) */
@Composable
internal fun SearchRouteRow(
    routeNo: String,
    routeType: String?,
    info: String?,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val kind = RouteKind.of(routeNo, routeType)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(
                start = Tokens.SearchScreen.rowPaddingHorizontal,
                end = Tokens.SearchScreen.rowPaddingHorizontal,
                top = Tokens.SearchScreen.rowPaddingTop,
                bottom = Tokens.SearchScreen.rowPaddingBottom
            )
            .height(Tokens.SearchScreen.rowHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RouteBadge(routeNo, routeType)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Tokens.SearchScreen.badgeGap),
            verticalArrangement = Arrangement.Center
        ) {
            RouteTypeChip(kind, small = true)
            if (!info.isNullOrBlank()) {
                Text(
                    info,
                    modifier = Modifier.padding(top = Tokens.SearchScreen.routeInfoGap),
                    fontSize = Tokens.SearchScreen.routeInfoText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing?.invoke()
    }
}

/** 검색 결과 한 줄의 공통 틀 (Figma: 구분선 간격 57 = 4 + 50 + 3) */
@Composable
internal fun SearchRowFrame(
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)?,
    content: @Composable ColumnScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(
                start = Tokens.SearchScreen.rowPaddingHorizontal,
                end = Tokens.SearchScreen.rowPaddingHorizontal,
                top = Tokens.SearchScreen.rowPaddingTop,
                bottom = Tokens.SearchScreen.rowPaddingBottom
            )
            .height(Tokens.SearchScreen.rowHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            content = content
        )
        trailing?.invoke()
    }
}
