package com.cheon.ccbuswidget.feature.stopdetail
//노선시트, 노선상세, 타임라인
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.cheon.ccbuswidget.R
import com.cheon.ccbuswidget.data.local.Timetable
import com.cheon.ccbuswidget.data.local.TimetableStore
import com.cheon.ccbuswidget.data.model.BusLocation
import com.cheon.ccbuswidget.data.model.BusModels
import com.cheon.ccbuswidget.data.model.RouteDetail
import com.cheon.ccbuswidget.data.model.RouteKind
import com.cheon.ccbuswidget.data.model.RouteLabel
import com.cheon.ccbuswidget.data.model.RouteStop
import com.cheon.ccbuswidget.ui.theme.Tokens

/**
 * 노선 시트 ↔ 노선 세부 확장창 (Figma 8:43 → 40:186).
 * 즐겨찾기 창과 같은 모핑 전환([MorphingSheet])을 쓴다.
 *
 * 펼친 상태에서는 아래 [시간표 보기] 바(Figma 78:934)를 누르면 타임라인 자리에
 * 시간표(Figma 81:937)가 뜬다. 뒤로가기 · 왼쪽 위 버튼 · 접기는 먼저 시간표를 닫는다.
 */
@Composable
internal fun RouteMorphSheet(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    routeId: String,
    routeNo: String,
    routeType: String?,
    detail: RouteDetail?,
    stops: List<RouteStop>,
    buses: List<BusLocation>,
    currentNodeId: String,
    loading: Boolean,
    error: String?,
    updatedAt: String,
    isFavorite: Boolean,
    onRefresh: () -> Unit,
    onToggleFavorite: () -> Unit,
    onStopClick: (RouteStop) -> Unit
) {
    // 원래 시트: 손잡이(12+5) 아래 헤더까지 12 → 손잡이 줄 29 기준으로 남는 만큼
    val collapsedHeaderTop = (Tokens.Sheet.grabberTopPadding + Tokens.Sheet.grabberHeight +
        Tokens.Sheet.headerTop - Tokens.Sheet.morphHandleHeight).coerceAtLeast(0.dp)
    val context = LocalContext.current

    // ---- 시간표 (assets/timetables/노선번호.txt) ----
    var showTimetable by rememberSaveable { mutableStateOf(false) }
    var timetable by remember { mutableStateOf<Timetable?>(null) }
    var timetableLoading by remember { mutableStateOf(true) }
    LaunchedEffect(routeNo, routeId) {
        timetable = TimetableStore.load(context, routeNo, routeId)
        timetableLoading = false
    }
    // 접히면 다음에 펼칠 때는 다시 타임라인부터
    LaunchedEffect(expanded) { if (!expanded) showTimetable = false }
    // 시간표를 보는 중에는 뒤로가기 = 타임라인으로
    BackHandler(enabled = expanded && showTimetable) { showTimetable = false }
    // 시간표를 봤다가 돌아와도 타임라인 스크롤 위치가 그대로 남도록 밖에서 들고 있는다
    val timelineState = rememberLazyListState()

    // ---- [시간표 보기] 바 뒤 블러 (검색창의 검색바와 같은 방식) ----
    // 본문(타임라인 + 아래 '더 있음' 그라데이션)을 레이어에 한 번 담아 그대로 그리고,
    // 바가 놓인 자리에만 같은 그림을 흐리게 해서 한 번 더 깐다.
    // 흐린 복사본에는 불투명 배경도 넣는다. 투명한 글자만 흐리게 해서 올리면
    // 밑의 선명한 글자가 반투명한 바를 그대로 뚫고 보여 블러가 없는 것처럼 보인다.
    val sharpLayer = rememberGraphicsLayer()
    val blurLayer = rememberGraphicsLayer()
    val blurPx = with(LocalDensity.current) { Tokens.Glass.blurRadius.toPx() }
    val blurEffect = remember(blurPx) { BlurEffect(blurPx, blurPx, TileMode.Decal) }
    var contentCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var barCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var barRect by remember { mutableStateOf<Rect?>(null) }
    fun updateBarRect() {
        val content = contentCoordinates
        val bar = barCoordinates
        barRect = if (content?.isAttached == true && bar?.isAttached == true) {
            content.localBoundingBoxOf(bar, clipBounds = false)
        } else null
    }
    val barShown = expanded && !showTimetable
    // 흐린 복사본도 바와 같이 나타나고 사라진다
    val barBlurAlpha = animateFloatAsState(
        if (barShown) 1f else 0f,
        tween(Tokens.Motion.medium, if (barShown) Tokens.Motion.morph / 2 else 0, Tokens.Motion.easing),
        label = "시간표 보기 블러"
    )
    // [시간표 보기] 바 위치 — 새로고침 버튼과 세로 가운데를 맞춘다 (Figma 78:934: 두 버튼 중심이 같은 줄)
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barBottom = (nav + Tokens.Expanded.fabBottom +
        (Tokens.Glass.buttonSize - Tokens.ActionBar.height) / 2).coerceAtLeast(0.dp)
    val refreshAlpha by animateFloatAsState(
        if (showTimetable) 0f else 1f,
        tween(Tokens.Motion.medium, easing = Tokens.Motion.easing), label = "새로고침 버튼"
    )

    MorphingSheet(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        collapsedTint = glassColor(),
        expandedTint = expandedSurface(),
        handleDescription = "노선 창 손잡이",
        expandedHandleHeight = Tokens.Expanded.topBarHeight,
        onDismiss = onDismiss,
        handle = { p ->
            MorphTopBar(p, isFavorite,
                onBack = { if (showTimetable) showTimetable = false else onExpandedChange(false) },
                onToggleFavorite = onToggleFavorite)
        },
        overlay = { p ->
            // 시간표 화면(Figma 81:937)에는 새로고침 버튼이 없다
            if (refreshAlpha > 0f) {
                Box(Modifier.matchParentSize().graphicsLayer { alpha = refreshAlpha }) {
                    MorphRefreshButton(p, onRefresh)
                }
            }
            val barSpec = tween<Float>(Tokens.Motion.medium, easing = Tokens.Motion.easing)
            AnimatedVisibility(
                visible = barShown,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = barBottom)
                    // 펼치는 동안에는 시트를 따라 서서히 나타나고, 끌어내리면 같이 흐려진다
                    .graphicsLayer { alpha = ((p - 0.5f) * 2f).coerceIn(0f, 1f) },
                // 툴바와 같은 확대/축소. 펼칠 때는 시트가 절반쯤 올라온 뒤에 커진다
                enter = scaleIn(tween(Tokens.Motion.medium, Tokens.Motion.morph / 2, Tokens.Motion.easing),
                    initialScale = Tokens.Toolbar.hiddenScale) +
                    fadeIn(tween(Tokens.Motion.medium, Tokens.Motion.morph / 2, Tokens.Motion.easing)),
                exit = scaleOut(barSpec, targetScale = Tokens.Toolbar.hiddenScale) + fadeOut(barSpec)
            ) {
                SingleActionBar(
                    "시간표 보기",
                    onClick = { showTimetable = true },
                    // 블러는 본문 쪽에서 이 자리에 깔아 주므로 바는 반투명 색만 칠한다
                    blurBehind = false,
                    modifier = Modifier.onGloballyPositioned {
                        barCoordinates = it
                        updateBarRect()
                    }
                )
            }
        }
    ) { p, tint ->
        val actionsAlpha = collapsedOnlyAlpha(p)
        RouteHeader(
            routeNo = routeNo,
            routeType = routeType,
            detail = detail,
            busCount = buses.size,
            updatedAt = updatedAt,
            loading = loading,
            horizontalPadding = lerp(Tokens.Sheet.stopContentPadding, Tokens.Expanded.horizontalPadding, p),
            topPadding = lerp(collapsedHeaderTop, Tokens.Expanded.routeHeaderTop, p),
            showActions = actionsAlpha > 0f,
            actionsAlpha = actionsAlpha,
            isFavorite = isFavorite,
            onRefresh = onRefresh,
            onToggleFavorite = onToggleFavorite
        )

        HorizontalDivider(
            modifier = Modifier.padding(top = lerp(Tokens.Sheet.dividerTop, Tokens.Expanded.routeDividerTop, p)),
            color = colorResource(R.color.divider)
        )

        // 타임라인 ↔ 시간표 (헤더는 그대로 두고 아래만 서로 겹쳐 바뀐다)
        // 펼치면 목록이 내비게이션 바 · [시간표 보기] 바 뒤까지 이어지도록 아래로 늘린다. (Figma 40:186)
        val underNav = lerp(0.dp, nav, p)
        val timelineBottom = lerp(0.dp, barBottom + Tokens.ActionBar.height + Tokens.Timetable.barClearance, p)
        val blurBackground = tint
        val barAlphaByMorph = ((p - 0.5f) * 2f).coerceIn(0f, 1f)
        AnimatedContent(
            targetState = showTimetable,
            modifier = Modifier.weight(1f).fillMaxWidth().extendBottom(underNav)
                .onGloballyPositioned {
                    contentCoordinates = it
                    updateBarRect()
                }
                .drawWithContent {
                    // drawContent() 는 한 번만 부를 수 있어서 레이어에 담아 두 번 그린다
                    sharpLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(sharpLayer)

                    val r = barRect ?: return@drawWithContent
                    val alpha = barBlurAlpha.value * barAlphaByMorph
                    if (alpha <= 0f) return@drawWithContent
                    blurLayer.renderEffect = blurEffect
                    blurLayer.alpha = alpha
                    blurLayer.record {
                        // 시트 배경색까지 함께 흐리게 해서 밑의 선명한 글자를 덮는다
                        drawRect(blurBackground)
                        drawLayer(sharpLayer)
                    }
                    clipPath(Path().apply {
                        addRoundRect(RoundRect(r, CornerRadius(r.height / 2f, r.height / 2f)))
                    }) { drawLayer(blurLayer) }
                },
            transitionSpec = {
                fadeIn(tween(Tokens.Motion.medium, easing = Tokens.Motion.easing)) togetherWith
                    fadeOut(tween(Tokens.Motion.fast, easing = Tokens.Motion.easing))
            },
            label = "타임라인 ↔ 시간표"
        ) { timetableShown ->
            if (timetableShown) {
                RouteTimetable(
                    routeNo = routeNo,
                    timetable = timetable,
                    loading = timetableLoading,
                    fadeSurface = tint,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                RouteTimeline(
                    routeNo = routeNo,
                    routeType = routeType,
                    stops = stops,
                    buses = buses,
                    currentNodeId = currentNodeId,
                    error = error,
                    fadeSurface = tint,
                    modifier = Modifier.fillMaxSize(),
                    listState = timelineState,
                    bottomPadding = timelineBottom,
                    onStopClick = onStopClick
                )
            }
        }
    }
}

/**
 * 노선 헤더 — 유형 칩 + 노선번호 / 기점·종점·첫차·막차 / 운행 대수.
 * 시트와 확장창이 같이 쓴다. (Figma 8:43, 40:186)
 */
@Composable
internal fun RouteHeader(
    routeNo: String,
    routeType: String?,
    detail: RouteDetail?,
    busCount: Int,
    updatedAt: String,
    loading: Boolean,
    horizontalPadding: Dp,
    topPadding: Dp,
    /** 확장창에서는 새로고침·즐겨찾기가 떠 있는 버튼이라 헤더에는 두지 않는다 */
    showActions: Boolean = false,
    /** 모핑 중 헤더 버튼이 흐려지는 정도 */
    actionsAlpha: Float = 1f,
    isFavorite: Boolean = false,
    onRefresh: () -> Unit = {},
    onToggleFavorite: () -> Unit = {}
) {
    val kind = RouteKind.of(routeNo, routeType)
    val label = RouteLabel.of(routeNo)

    Box(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = topPadding
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RouteTypeChip(kind)
            Row(
                modifier = Modifier
                    .padding(start = Tokens.Header.routeNoStart)
                    .weight(1f),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    label.main,
                    fontSize = Tokens.Header.routeNoText,
                    // 줄 높이를 고정하지 않으면 글꼴 여백 때문에 헤더가 훌쩍 커진다
                    lineHeight = Tokens.Header.routeNoLine,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
                label.sub?.let {
                    Text(
                        it,
                        modifier = Modifier.padding(start = 3.dp, bottom = 3.dp),
                        fontSize = Tokens.Header.metaText,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }

        detail?.let { d ->
            // 첫 줄 — 기점 → 종점 (Figma: 12 Medium, 본문색)
            val route = d.startNode?.let { st -> d.endNode?.let { e -> "$st → $e" } }
            if (!route.isNullOrBlank()) {
                Text(
                    route,
                    modifier = Modifier.padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = Tokens.Header.infoTop
                    ),
                    fontSize = Tokens.Header.infoText,
                    lineHeight = Tokens.Header.infoLine,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 둘째 줄 — 첫차 · 막차   배차 (Figma: 12 Regular, 흐린색)
            val schedule = listOfNotNull(
                d.firstBus?.let { f -> d.lastBus?.let { l -> "첫차 $f · 막차 $l" } },
                d.intervalMin?.let { "배차 ${it}분" }
            ).joinToString("   ")
            if (schedule.isNotBlank()) {
                Text(
                    schedule,
                    modifier = Modifier.padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = Tokens.Header.scheduleTop
                    ),
                    fontSize = Tokens.Header.infoText,
                    lineHeight = Tokens.Header.infoLine,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 셋째 줄 — 운행 중 N대 · 갱신 시각
        Text(
            buildString {
                append("운행 중 ${busCount}대")
                if (updatedAt.isNotBlank()) append(" · $updatedAt 기준")
            },
            modifier = Modifier.padding(
                start = horizontalPadding,
                top = Tokens.Header.runningTop
            ),
            fontSize = Tokens.Header.infoText,
            lineHeight = Tokens.Header.infoLine,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // Figma: 새로고침 · 즐겨찾기는 노선번호 줄이 아니라
    // 정류장 시트와 같은 높이(헤더 위에서 32)에 놓인다
    if (showActions) {
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(
                    top = topPadding + Tokens.Header.actionsTop,
                    end = horizontalPadding
                )
                .alpha(actionsAlpha),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Tokens.Header.iconSize),
                    strokeWidth = Tokens.Misc.spinnerStroke,
                    color = colorResource(R.color.glass_on_surface)
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh),
                    contentDescription = "새로고침",
                    tint = colorResource(R.color.glass_on_surface),
                    modifier = Modifier
                        .size(Tokens.Header.iconSize)
                        .clip(CircleShape)
                        .clickable { onRefresh() }
                )
            }
            Spacer(modifier = Modifier.width(Tokens.Sheet.headerIconGap))
            FavoriteIcon(isFavorite, Tokens.Header.iconSize, onToggleFavorite)
        }
    }
    }
}

/** 경유 정류장 타임라인 + 아래쪽 '더 있음' 그라데이션 */
@Composable
internal fun RouteTimeline(
    routeNo: String,
    routeType: String?,
    stops: List<RouteStop>,
    buses: List<BusLocation>,
    currentNodeId: String,
    error: String?,
    fadeSurface: Color,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    /** 목록 맨 아래 여백 (펼친 창에서 마지막 정류장이 [시간표 보기] 바에 가리지 않도록) */
    bottomPadding: Dp = 0.dp,
    onStopClick: (RouteStop) -> Unit
) {
    // 정류장별로 지금 그 자리에 있는 버스
    val busByNode = remember(buses) {
        buses.groupBy { it.nodeId ?: it.nodeName.orEmpty() }
    }
    val kindColor = colorResource(RouteKind.of(routeNo, routeType).colorRes)

    Box(modifier = modifier.fillMaxWidth()) {
        if (error != null) {
            Text(
                error,
                modifier = Modifier.padding(Tokens.Sheet.horizontalPadding),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(stops, key = { "${it.order}_${it.nodeId}" }) { rs ->
                    TimelineStopItem(
                        stop = rs,
                        busesHere = busByNode[rs.nodeId].orEmpty(),
                        isCurrent = rs.nodeId == currentNodeId,
                        lineColor = kindColor,
                        onClick = { onStopClick(rs) }
                    )
                }
                // 펼친 창의 목록 아래 여백 — 비워 두지 않고 세로선을 이어 그려 노선이 계속되는 느낌을 준다
                // (Figma 40:186: 타임라인이 [시간표 보기] 바 · 내비게이션 바 뒤까지 이어진다)
                if (stops.isNotEmpty() && bottomPadding > 0.dp) {
                    item(key = "timeline_tail") {
                        Row(Modifier.fillMaxWidth().height(bottomPadding)) {
                            Spacer(Modifier.width(Tokens.Timeline.chipColumnWidth))
                            Box(
                                Modifier.width(Tokens.Timeline.columnWidth).fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    Modifier.width(Tokens.Timeline.lineWidth).fillMaxHeight()
                                        .background(kindColor)
                                )
                            }
                        }
                    }
                }
            }
            BottomFade(visible = listState.canScrollForward, surface = fadeSurface)
        }
    }
}

/**
 * 타임라인 왼쪽의 알약 칩.
 * Figma "차량 위치": 높이 13, 모서리 6, 글자 흰색 가운데, 좌우 여백 5.
 * 폭은 글자에 맞춰 늘어난다. (차량번호 36 / 차종 49)
 */
@Composable
internal fun VehicleChip(
    text: String,
    color: Color,
    height: Dp,
    fontSize: TextUnit,
    fontWeight: FontWeight
) {
    Box(
        modifier = Modifier
            .height(height)
            .clip(RoundedCornerShape(Tokens.Timeline.vehicleChipCorner))
            .background(color)
            .padding(horizontal = Tokens.Timeline.vehicleChipPaddingHorizontal),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = colorResource(R.color.route_badge_text),
            fontSize = fontSize,
            lineHeight = fontSize,
            fontWeight = fontWeight,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
internal fun TimelineStopItem(
    stop: RouteStop,
    busesHere: List<BusLocation>,
    isCurrent: Boolean,
    lineColor: Color,
    onClick: () -> Unit
) {
    val bus = busesHere.firstOrNull()

    // Figma: 현재 정류장 표시는 노선 흐름(세로선) 오른쪽부터 끝까지만 덮는다.
    // 라이트 #000000 5% / 다크 #FFFFFF 5%
    val overlayColor = colorResource(R.color.timeline_current_overlay)
    val overlayStart = Tokens.Timeline.currentOverlayStart

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 줄 높이를 고정해서 정류장 사이 간격을 일정하게 둔다
            .height(Tokens.Timeline.rowHeight)
            .drawBehind {
                if (!isCurrent) return@drawBehind
                val x = overlayStart.toPx()
                if (x >= size.width) return@drawBehind
                drawRect(
                    color = overlayColor,
                    topLeft = Offset(x, 0f),
                    size = Size(size.width - x, size.height)
                )
            }
            .clickable { onClick() }
    ) {
        // 1) 차량번호 칩 (+ 차종 칩) — 타임라인 바깥 왼쪽에 붙는다
        Box(
            modifier = Modifier
                .width(Tokens.Timeline.chipColumnWidth)
                .fillMaxHeight()
                .padding(end = Tokens.Timeline.chipColumnEndPadding),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (bus != null) {
                // 차량번호로 차종을 찾는다. DB 에 없는 차량이면 아래 칩은 생략
                val modelShort = BusModels.shortName(bus.vehicleNo)
                Column(
                    // Figma: 폭이 다른 두 칩이 서로 가운데를 맞춘다
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Tokens.Timeline.modelChipGap)
                ) {
                    VehicleChip(
                        // 차량번호는 뒤 4자리만. 두 대 이상이면 뒤에 대수를 붙인다
                        text = if (busesHere.size > 1) "${bus.shortVehicleNo} +${busesHere.size - 1}"
                        else bus.shortVehicleNo,
                        color = lineColor,
                        height = Tokens.Timeline.vehicleChipHeight,
                        fontSize = Tokens.Timeline.vehicleChipText,
                        fontWeight = FontWeight.Bold
                    )
                    if (modelShort != null) {
                        VehicleChip(
                            text = modelShort,
                            color = lineColor,
                            height = Tokens.Timeline.modelChipHeight,
                            fontSize = Tokens.Timeline.modelChipText,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 2) 세로선과 정류장 점 (버스가 있으면 점 대신 버스 아이콘 원)
        Box(
            modifier = Modifier
                .width(Tokens.Timeline.columnWidth)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(Tokens.Timeline.lineWidth)
                    .fillMaxHeight()
                    .background(lineColor)
            )
            if (bus != null) {
                Box(
                    modifier = Modifier
                        .size(Tokens.Timeline.busDotSize)
                        .background(lineColor, CircleShape)
                        .border(Tokens.Timeline.dotBorder, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_bus),
                        contentDescription = null,
                        tint = colorResource(R.color.route_badge_text),
                        modifier = Modifier.size(Tokens.Timeline.busIconSize)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(Tokens.Timeline.dotSize)
                        .background(Color.White, CircleShape)
                        .border(Tokens.Timeline.dotBorder, lineColor, CircleShape)
                )
            }
        }

        // 3) 정류장 이름 + 정류장 번호
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(
                    start = Tokens.Timeline.textGap,
                    end = Tokens.Sheet.horizontalPadding
                ),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                stop.nodeName,
                fontSize = Tokens.Timeline.nameText,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                // 정류장 번호만 표시 (번호가 없으면 노선 내 순번)
                stop.nodeNo ?: "${stop.order}번째",
                fontSize = Tokens.Timeline.infoText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 노선 유형의 색 (간선 주황 · 지선 청록 · 마을 보라) */
@Composable
internal fun routeKindColor(routeNo: String, routeType: String?): Color =
    colorResource(RouteKind.of(routeNo, routeType).colorRes)

/**
 * 원형 노선 번호 뱃지.
 * "서면5(서면100)" 처럼 괄호가 붙으면 주번호를 크게, 괄호를 작게 두 줄로 쓴다. (Figma)
 */
@Composable
internal fun RouteBadge(
    routeNo: String,
    routeType: String? = null,
    size: Dp = Tokens.Badge.size,
    textSize: TextUnit = Tokens.Badge.text
) {
    val label = RouteLabel.of(routeNo)
    Box(
        modifier = Modifier
            .size(size)
            .background(routeKindColor(routeNo, routeType), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label.main,
                color = colorResource(R.color.route_badge_text),
                // 주노선번호는 최대한 두껍게, 괄호 번호는 그대로 Bold
                fontWeight = FontWeight.Black,
                fontSize = if (label.main.length >= 4) textSize * (11f / 13f)
                else textSize,
                lineHeight = textSize,
                maxLines = 1
            )
            label.sub?.let {
                Text(
                    it,
                    color = colorResource(R.color.route_badge_text),
                    fontWeight = FontWeight.Bold,
                    fontSize = Tokens.Badge.textSub,
                    lineHeight = Tokens.Badge.textSub,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 노선 유형 칩 (지선 / 간선 / 마을).
 * Figma: 모서리 6, 좌우 8 · 위아래 3, 굵은 흰 글씨.
 */
@Composable
internal fun RouteTypeChip(
    kind: RouteKind,
    modifier: Modifier = Modifier,
    small: Boolean = false
) {
    // Figma 는 칩 크기가 고정이다 (헤더 39x20 / 작은 칩 30x16).
    // 글꼴 줄 높이에 맡기면 한글 글자 높이 때문에 훨씬 커진다.
    Box(
        modifier = modifier
            .size(
                width = if (small) Tokens.TypeChip.widthSmall else Tokens.TypeChip.width,
                height = if (small) Tokens.TypeChip.heightSmall else Tokens.TypeChip.height
            )
            .background(
                colorResource(kind.colorRes),
                RoundedCornerShape(Tokens.TypeChip.corner)
            ),
        contentAlignment = Alignment.Center
    ) {
        val size = if (small) Tokens.TypeChip.textSmall else Tokens.TypeChip.text
        Text(
            kind.label,
            color = colorResource(R.color.route_badge_text),
            fontSize = size,
            lineHeight = size,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/**
 * 레이아웃에서는 원래 높이를 차지하되, 내용은 [extra] 만큼 아래로 더 길게 그린다.
 * 펼친 시트에서 목록을 내비게이션 바 뒤까지 이어 그리는 데 쓴다 (넘친 부분은 시트가 잘라 준다).
 */
private fun Modifier.extendBottom(extra: Dp): Modifier = layout { measurable, constraints ->
    val extraPx = extra.roundToPx().coerceAtLeast(0)
    val placeable = measurable.measure(
        if (constraints.hasBoundedHeight) constraints.copy(
            minHeight = constraints.minHeight + extraPx,
            maxHeight = constraints.maxHeight + extraPx
        ) else constraints
    )
    layout(placeable.width, (placeable.height - extraPx).coerceAtLeast(0)) { placeable.place(0, 0) }
}
