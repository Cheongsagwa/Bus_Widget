package com.cheon.ccbuswidget.feature.stopdetail
//정류장 시트, 정류장 상세
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.cheon.ccbuswidget.R
import com.cheon.ccbuswidget.alarm.AlarmStore
import com.cheon.ccbuswidget.alarm.BusAlarm
import com.cheon.ccbuswidget.data.model.RouteKind
import com.cheon.ccbuswidget.ui.theme.Tokens

/**
 * 정류장 시트 ↔ 정류장 세부 확장창 (Figma 6:23 → 40:372).
 * 즐겨찾기 창과 같은 모핑 전환([MorphingSheet])으로, 한 장의 카드가 그대로 전체 화면까지 펼쳐진다.
 * 헤더·그리드 여백은 시트 값에서 확장창 값으로 함께 보간한다.
 */
@Composable
internal fun StopMorphSheet(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    stopNodeId: String,
    stopName: String,
    stopNo: String?,
    updatedAt: String,
    loading: Boolean,
    error: String?,
    rows: List<RouteRow>,
    isFavorite: Boolean,
    onRefresh: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRouteClick: (RouteRow) -> Unit,
    /** 축소창이 되었는지 (현위치 버튼 자리를 옮기는 데 쓴다) */
    onMinimizedChange: (Boolean) -> Unit = {}
) {
    // 원래 시트: 손잡이(12+5) 아래 유형 칩 줄까지 16 → 손잡이 줄 29 기준으로 남는 만큼
    val collapsedHeaderTop = Tokens.Sheet.grabberTopPadding + Tokens.Sheet.grabberHeight +
        Tokens.Sheet.typeRowTop - Tokens.Sheet.morphHandleHeight
    // 떠 있는 새로고침 버튼 뒤로 노선 목록이 흐리게 비치도록 (검색바와 같은 방식)
    val blur = rememberBlurBehind()
    // 승차 알람 — 노선 칸마다 알람 아이콘, 축소창에서는 새로고침 자리에 상태만 보여 준다
    val alarms = LocalAlarmController.current
    val activeAlarm by AlarmStore.active.collectAsState()
    val boardingHere = activeAlarm?.takeIf { it.type == BusAlarm.Type.BOARD && it.stopNodeId == stopNodeId }
    MorphingSheet(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        collapsedTint = glassColor(white = true),
        expandedTint = expandedSurface(),
        handleDescription = "정류장 창 손잡이",
        // 위쪽 버튼 줄이 상태바 아래 8 에 놓이므로 그만큼 손잡이 영역도 늘린다
        expandedHandleHeight = Tokens.Expanded.topBarHeight + Tokens.Glass.topBarTop,
        // 아래로 쓸어내리면 먼저 헤더만 남은 축소창이 되고, 한 번 더 내리면 닫힌다
        minimizedHeight = Tokens.Sheet.minimizedHeight,
        onMinimizedChange = onMinimizedChange,
        onDismiss = onDismiss,
        handle = { p ->
            MorphTopBar(p, isFavorite, onBack = { onExpandedChange(false) }, onToggleFavorite = onToggleFavorite)
        },
        overlay = { p -> MorphRefreshButton(p, onRefresh, blur = blur, loading = loading) }
    ) { p, tint ->
        val actionsAlpha = collapsedOnlyAlpha(p)
        StopHeader(
            stopName = stopName,
            stopNo = stopNo,
            updatedAt = updatedAt,
            loading = loading,
            rows = rows,
            isFavorite = isFavorite,
            horizontalPadding = lerp(Tokens.Sheet.stopContentPadding, Tokens.Expanded.horizontalPadding, p),
            topPadding = lerp(collapsedHeaderTop, Tokens.Expanded.stopHeaderTop, p),
            // 펼칠수록 헤더의 새로고침·즐겨찾기는 사라지고 떠 있는 버튼이 대신한다
            showActions = actionsAlpha > 0f,
            actionsAlpha = actionsAlpha,
            refreshAlpha = 1f - LocalSheetMinimize.current,
            alarmOn = boardingHere != null,
            // 축소창의 알람 아이콘은 켜진 알람을 끄기만 한다 (켜는 것은 노선 칸에서)
            onAlarmOff = { if (boardingHere != null) alarms?.cancel() },
            onRefresh = onRefresh,
            onToggleFavorite = onToggleFavorite
        )

        // 축소창(Figma 90:2723)에는 헤더만 남는다 — 구분선·노선 목록은 줄어드는 동안 빠르게 사라진다
        val listAlpha = minimizedListAlpha(LocalSheetMinimize.current)
        HorizontalDivider(
            modifier = Modifier.padding(
                top = lerp(Tokens.Sheet.stopDividerTop, Tokens.Expanded.stopDividerTop, p)
            ).alpha(listAlpha),
            color = colorResource(R.color.divider)
        )

        StopRouteGrid(
            rows = rows,
            loading = loading,
            error = error,
            horizontalPadding = lerp(Tokens.Sheet.stopContentPadding, Tokens.Expanded.gridPadding, p),
            endPadding = lerp(Tokens.Sheet.stopContentPadding, Tokens.Expanded.gridPaddingEnd, p),
            fadeSurface = tint,
            modifier = Modifier.weight(1f).alpha(listAlpha).blurBehindContent(blur, tint),
            alarmOn = { row -> boardingHere?.routeId == row.routeId },
            onAlarm = { row ->
                alarms?.toggleBoarding(activeAlarm, stopNodeId, stopName, row.routeId, row.routeNo, row.routeType)
            },
            onRouteClick = onRouteClick
        )
    }
}

// ---------------------------------------------------------------- 시트 공통

/**
 * 카드 맨 위 손잡이.
 * 위로 끌어올리거나 톡 누르면 전체 화면 확장창이 열린다.
 */
@Composable
internal fun SheetGrabber() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Tokens.Sheet.grabberTopPadding),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(Tokens.Sheet.grabberWidth, Tokens.Sheet.grabberHeight)
                .background(
                    colorResource(R.color.glass_on_surface).copy(alpha = 0.25f),
                    RoundedCornerShape(2.dp)
                )
        )
    }
}

/**
 * 정류장 헤더 — 유형 칩 줄 / 정류장 이름 + 새로고침·즐겨찾기 / 갱신 안내.
 * 시트와 확장창이 같이 쓴다. (Figma 40:764)
 */
@Composable
internal fun StopHeader(
    stopName: String,
    stopNo: String?,
    updatedAt: String,
    loading: Boolean,
    rows: List<RouteRow>,
    isFavorite: Boolean,
    horizontalPadding: Dp,
    topPadding: Dp,
    /** 확장창에서는 새로고침·즐겨찾기가 떠 있는 버튼이라 헤더에는 두지 않는다 */
    showActions: Boolean = true,
    /** 모핑 중 헤더 버튼이 흐려지는 정도 */
    actionsAlpha: Float = 1f,
    /** 새로고침만 따로 흐리게 할 때 (축소창에서는 새로고침이 없다 — Figma 90:2723). 자리는 그대로 둔다 */
    refreshAlpha: Float = 1f,
    /** 축소창에서 새로고침 자리에 보이는 알람 아이콘의 상태 (Figma 90:2723) */
    alarmOn: Boolean = false,
    onAlarmOff: () -> Unit = {},
    onRefresh: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    // 이 정류장에 서는 노선의 유형만 칩으로 보여 준다 (지선 → 간선 → 마을 순)
    val kinds = remember(rows) {
        rows.map { RouteKind.of(it.routeNo, it.routeType) }
            .distinct()
            .sortedBy { it.ordinal }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = horizontalPadding, end = horizontalPadding, top = topPadding)
    ) {
        if (kinds.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.TypeChip.gap)) {
                kinds.forEach { RouteTypeChip(it) }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (kinds.isEmpty()) 0.dp else Tokens.Sheet.stopNameTop),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stopName.ifBlank { "정류장" },
                modifier = Modifier.weight(1f),
                fontSize = Tokens.Header.stopNameText,
                // Figma 텍스트 상자 높이(28)에 맞춘다 — 기본 줄 높이를 쓰면
                // 글꼴 여백 때문에 헤더 전체가 아래로 밀린다
                lineHeight = Tokens.Header.stopNameLine,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (showActions) {
              Row(
                  modifier = Modifier.alpha(actionsAlpha),
                  verticalAlignment = Alignment.CenterVertically
              ) {
                Box(contentAlignment = Alignment.Center) {
                // 축소창: 새로고침 대신 알람 상태
                if (refreshAlpha < 1f) {
                    AlarmIcon(
                        on = alarmOn,
                        modifier = Modifier.alpha(1f - refreshAlpha),
                        size = Tokens.Header.iconSize,
                        enabled = refreshAlpha < 0.5f,
                        onClick = onAlarmOff
                    )
                }
                Box(Modifier.alpha(refreshAlpha)) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Tokens.Misc.spinnerSize),
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
                            .clickable(enabled = refreshAlpha > 0.5f) { onRefresh() }
                    )
                }
                }
                }

                Spacer(modifier = Modifier.width(Tokens.Sheet.headerIconGap))

                FavoriteIcon(
                    isFavorite = isFavorite,
                    size = Tokens.Header.iconSize,
                    onClick = onToggleFavorite
                )
              }
            }
        }

        Text(
            if (updatedAt.isBlank()) "불러오는 중…"
            else listOfNotNull(stopNo, "$updatedAt 기준").joinToString(" · "),
            modifier = Modifier.padding(top = Tokens.Header.metaTop),
            fontSize = Tokens.Header.metaText,
            lineHeight = Tokens.Header.metaLine,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** 노선 2열 그리드 + 아래쪽 '더 있음' 그라데이션 */
@Composable
internal fun StopRouteGrid(
    rows: List<RouteRow>,
    loading: Boolean,
    error: String?,
    horizontalPadding: Dp,
    fadeSurface: Color,
    modifier: Modifier = Modifier,
    endPadding: Dp = horizontalPadding,
    alarmOn: (RouteRow) -> Boolean = { false },
    onAlarm: ((RouteRow) -> Unit)? = null,
    onRouteClick: (RouteRow) -> Unit
) {
    Box(modifier = modifier.fillMaxWidth()) {
        when {
            error != null -> Text(
                error,
                modifier = Modifier.padding(horizontalPadding),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )

            rows.isEmpty() && !loading -> Text(
                "지금은 도착 예정 정보가 없습니다",
                modifier = Modifier.fillMaxWidth().padding(28.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            else -> {
                val gridState = rememberLazyGridState()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(Tokens.RouteRow.columnGap),
                    contentPadding = PaddingValues(
                        start = horizontalPadding,
                        end = endPadding,
                        top = Tokens.Sheet.stopGridTop
                    ),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(rows, key = { it.routeNo }) { row ->
                        StopRouteCell(
                            row,
                            alarmOn = alarmOn(row),
                            onAlarm = onAlarm?.let { f -> { f(row) } }
                        ) { onRouteClick(row) }
                    }
                }
                TopFade(visible = gridState.canScrollBackward, surface = fadeSurface)
                BottomFade(visible = gridState.canScrollForward, surface = fadeSurface)
            }
        }
    }
}

/** 즐겨찾기 별 — 꺼져 있으면 테두리만, 켜지면 노란색으로 채운다 (Figma) */
@Composable
internal fun FavoriteIcon(isFavorite: Boolean, size: Dp, onClick: () -> Unit) {
    Icon(
        painter = painterResource(
            if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_outline
        ),
        contentDescription = if (isFavorite) "즐겨찾기 해제" else "즐겨찾기",
        tint = if (isFavorite) colorResource(R.color.favorite_on)
        else colorResource(R.color.glass_on_surface),
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable { onClick() }
    )
}

/** 정류장 시트의 노선 한 칸: [원형 뱃지] 남은시간 / 부가정보 */
@Composable
internal fun StopRouteCell(
    row: RouteRow,
    alarmOn: Boolean = false,
    /** 주면 칸 오른쪽에 승차 알람 아이콘을 둔다 (Figma 6:12) */
    onAlarm: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(Tokens.RouteRow.height)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        RouteBadge(row.routeNo, row.routeType)

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Tokens.Badge.gap),
            verticalArrangement = Arrangement.Center
        ) {
            val first = row.first
            if (first == null) {
                Text(
                    "정보 없음",
                    fontSize = Tokens.RouteRow.idleText,
                    lineHeight = Tokens.RouteRow.idleText,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    row.routeType.orEmpty(),
                    modifier = Modifier.padding(top = Tokens.RouteRow.subGap),
                    fontSize = Tokens.RouteRow.subText,
                    lineHeight = Tokens.RouteRow.subText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            } else {
                Text(
                    first.displayTime,
                    fontSize = Tokens.RouteRow.timeText,
                    lineHeight = Tokens.RouteRow.timeText,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    buildString {
                        append(first.displayStationsShort)
                        row.second?.let { append(" · 다음 ${it.arrTimeSec / 60}분") }
                    },
                    modifier = Modifier.padding(top = Tokens.RouteRow.subGap),
                    fontSize = Tokens.RouteRow.subText,
                    lineHeight = Tokens.RouteRow.subText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (onAlarm != null) {
            AlarmIcon(on = alarmOn, offAlpha = 0.55f, onClick = onAlarm)
        }
    }
}

