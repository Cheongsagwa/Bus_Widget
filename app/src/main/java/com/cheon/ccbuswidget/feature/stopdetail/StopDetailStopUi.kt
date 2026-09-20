package com.cheon.ccbuswidget.feature.stopdetail
//정류장 시트, 정류장 상세
import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.unit.lerp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.view.LayoutInflater
import android.view.TextureView
import android.view.ViewGroup
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cheon.ccbuswidget.feature.settings.SettingsActivity
import com.cheon.ccbuswidget.R
import com.cheon.ccbuswidget.data.model.BusArrival
import com.cheon.ccbuswidget.data.model.BusLocation
import com.cheon.ccbuswidget.data.model.BusStop
import com.cheon.ccbuswidget.data.model.BusModels
import com.cheon.ccbuswidget.data.model.BusRoute
import com.cheon.ccbuswidget.data.model.RouteDetail
import com.cheon.ccbuswidget.data.model.RouteKind
import com.cheon.ccbuswidget.data.model.RouteLabel
import com.cheon.ccbuswidget.data.model.RouteStop
import com.cheon.ccbuswidget.data.model.SearchHistoryItem
import com.cheon.ccbuswidget.data.api.TagoApi
import com.cheon.ccbuswidget.data.local.WidgetStore
import com.cheon.ccbuswidget.ui.theme.CcBusTheme
import com.cheon.ccbuswidget.ui.theme.Tokens
import androidx.core.content.ContextCompat
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.NaverMapOptions
import com.naver.maps.map.NaverMapSdk
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.Overlay
import com.naver.maps.map.overlay.OverlayImage
import com.naver.maps.map.overlay.PathOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

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
    stopName: String,
    stopNo: String?,
    updatedAt: String,
    loading: Boolean,
    error: String?,
    rows: List<RouteRow>,
    isFavorite: Boolean,
    onRefresh: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRouteClick: (RouteRow) -> Unit
) {
    // 원래 시트: 손잡이(12+5) 아래 유형 칩 줄까지 16 → 손잡이 줄 29 기준으로 남는 만큼
    val collapsedHeaderTop = Tokens.Sheet.grabberTopPadding + Tokens.Sheet.grabberHeight +
        Tokens.Sheet.typeRowTop - Tokens.Sheet.morphHandleHeight
    MorphingSheet(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        collapsedTint = glassColor(white = true),
        expandedTint = expandedSurface(),
        handleDescription = "정류장 창 손잡이",
        expandedHandleHeight = Tokens.Expanded.topBarHeight,
        onDismiss = onDismiss,
        handle = { p ->
            MorphTopBar(p, isFavorite, onBack = { onExpandedChange(false) }, onToggleFavorite = onToggleFavorite)
        },
        overlay = { p -> MorphRefreshButton(p, onRefresh) }
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
            onRefresh = onRefresh,
            onToggleFavorite = onToggleFavorite
        )

        HorizontalDivider(
            modifier = Modifier.padding(
                top = lerp(Tokens.Sheet.stopDividerTop, Tokens.Expanded.stopDividerTop, p)
            ),
            color = colorResource(R.color.divider)
        )

        StopRouteGrid(
            rows = rows,
            loading = loading,
            error = error,
            horizontalPadding = lerp(Tokens.Sheet.stopContentPadding, Tokens.Expanded.gridPadding, p),
            endPadding = lerp(Tokens.Sheet.stopContentPadding, Tokens.Expanded.gridPaddingEnd, p),
            fadeSurface = tint,
            modifier = Modifier.weight(1f),
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
                            .clickable { onRefresh() }
                    )
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
    endPadding: Dp = horizontalPadding,
    fadeSurface: Color,
    modifier: Modifier = Modifier,
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
                        StopRouteCell(row) { onRouteClick(row) }
                    }
                }
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
internal fun StopRouteCell(row: RouteRow, onClick: () -> Unit) {
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
    }
}

