package com.cheon.ccbuswidget.feature.stopdetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.zIndex
import com.cheon.ccbuswidget.R
import com.cheon.ccbuswidget.data.api.TagoApi
import com.cheon.ccbuswidget.data.model.BusRoute
import com.cheon.ccbuswidget.data.model.BusStop
import com.cheon.ccbuswidget.data.model.FavoriteItem
import com.cheon.ccbuswidget.data.model.RouteKind
import com.cheon.ccbuswidget.ui.theme.Tokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 즐겨찾기 목록. 정류장과 노선이 한 목록에 섞여 있고, 편집 중에는 서로 자리를 바꿀 수 있다.
 * 검색 목록과 다른 Figma 규격: 정류장 22→20sp, 노선 배지 42dp, 기종점 14sp.
 */
@Composable
internal fun FavoritesList(
    items: List<FavoriteItem>,
    apiKey: String, cityCode: String,
    progress: Float, listState: LazyListState,
    editing: Boolean,
    onStop: (BusStop) -> Unit, onRoute: (BusRoute) -> Unit,
    onDelete: (FavoriteItem) -> Unit,
    onReorder: (List<FavoriteItem>) -> Unit,
    modifier: Modifier = Modifier
) {
    val kinds = remember(apiKey, cityCode) { mutableStateMapOf<String, List<RouteKind>>() }
    val endpoints = remember(apiKey, cityCode) { mutableStateMapOf<String, String>() }

    // 끄는 동안에는 화면 안에서만 순서를 바꾸고, 손을 떼면 한 번에 저장한다.
    var localItems by remember(items) { mutableStateOf(items) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val drag = remember(listState) { FavoriteDragState(listState, scope) }
    LaunchedEffect(editing) { if (!editing) drag.cancel() }

    val latestOnReorder by rememberUpdatedState(onReorder)
    // 목록이 바뀌면(저장·삭제 후) 새 상태를 잡도록 제스처를 다시 건다.
    val reorderModifier = if (editing) Modifier.pointerInput(items) {
        detectDragGesturesAfterLongPress(
            onDragStart = { offset ->
                if (drag.start(offset.y)) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onDrag = { change, amount ->
                if (drag.key == null) return@detectDragGesturesAfterLongPress
                change.consume()
                drag.drag(amount.y) { from, to -> localItems = localItems.moved(from, to) }
            },
            onDragEnd = {
                if (drag.key != null) latestOnReorder(localItems)
                drag.cancel()
            },
            onDragCancel = {
                if (drag.key != null) latestOnReorder(localItems)
                drag.cancel()
            }
        )
    } else Modifier

    val deleteTint = colorResource(R.color.search_secondary)
    val draggingSurface = colorResource(R.color.favorites_surface)

    LazyColumn(modifier.then(reorderModifier), state = listState, contentPadding = PaddingValues(bottom = 80.dp)) {
        if (localItems.isEmpty()) {
            item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("아직 즐겨찾기가 없어요", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("정류장이나 노선에서 별을 눌러 추가해 주세요.", fontSize = 14.sp,
                        color = colorResource(R.color.search_secondary))
                }
            }
        }
        items(localItems, key = { it.key }) { item ->
            Column(Modifier.reorderable(this, drag, item.key, draggingSurface)) {
                HorizontalDivider(color = colorResource(R.color.divider))
                when (item) {
                    is FavoriteItem.Stop -> {
                        val stop = item.stop
                        LaunchedEffect(stop.nodeId, apiKey, cityCode) {
                            if (apiKey.isNotBlank() && !kinds.containsKey(stop.nodeId)) {
                                val found = favoriteInfoOrNull { TagoApi.routesOfStop(apiKey, cityCode, stop.nodeId) }
                                kinds[stop.nodeId] = found.orEmpty().map { RouteKind.of(it.routeNo, it.routeType) }
                                    .distinct().sortedBy { it.ordinal }
                            }
                        }
                        Row(Modifier.fillMaxWidth().clickable(enabled = !editing) { onStop(stop) }
                            .padding(horizontal = lerp(25.dp, 27.dp, progress)).heightIn(min = 56.dp)
                            .padding(top = 6.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                // 글자 크기: Tokens.Favorites (펼치면 검색창과 같은 22sp · Bold)
                                val textSize = lerp(Tokens.Favorites.stopNameCollapsed,
                                    Tokens.Favorites.stopNameExpanded, progress)
                                Text(stop.nodeName, fontSize = textSize, lineHeight = textSize,
                                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(stop.nodeNo.orEmpty(), Modifier.widthIn(min = 30.dp),
                                        fontSize = lerp(Tokens.Favorites.stopNoCollapsed,
                                            Tokens.Favorites.stopNoExpanded, progress), lineHeight = 16.sp,
                                        fontWeight = FontWeight.Medium, color = colorResource(R.color.search_secondary),
                                        maxLines = 1)
                                    kinds[stop.nodeId].orEmpty().forEach { RouteTypeChip(it, small = true) }
                                }
                            }
                            DeleteButton(editing, "${stop.nodeName} 즐겨찾기 삭제", deleteTint) { onDelete(item) }
                        }
                    }
                    is FavoriteItem.Route -> {
                        val route = item.route
                        val saved = listOfNotNull(route.startNode, route.endNode).takeIf { it.size == 2 }
                            ?.joinToString(" → ")
                        LaunchedEffect(route.routeId, apiKey, cityCode) {
                            if (saved == null && apiKey.isNotBlank() && !endpoints.containsKey(route.routeId)) {
                                val detail = favoriteInfoOrNull { TagoApi.routeDetail(apiKey, cityCode, route.routeId) }
                                endpoints[route.routeId] = listOfNotNull(detail?.startNode, detail?.endNode)
                                    .takeIf { it.size == 2 }?.joinToString(" → ").orEmpty()
                            }
                        }
                        Row(Modifier.fillMaxWidth().clickable(enabled = !editing) { onRoute(route) }
                            .padding(horizontal = lerp(25.dp, 27.dp, progress)).heightIn(min = 56.dp)
                            .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            RouteBadge(route.routeNo, route.routeType, size = 42.dp, textSize = 15.sp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                RouteTypeChip(RouteKind.of(route.routeNo, route.routeType),
                                    modifier = Modifier.requiredSize(34.dp, 18.dp))
                                Text((saved ?: endpoints[route.routeId]).orEmpty().ifBlank { "기점·종점 정보 없음" },
                                    fontSize = 14.sp, lineHeight = 17.sp, color = colorResource(R.color.search_secondary),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            DeleteButton(editing, "${route.routeNo}번 즐겨찾기 삭제", deleteTint) { onDelete(item) }
                        }
                    }
                }
            }
        }
    }
}

/** 편집 중에만 오른쪽에서 밀려 나오는 휴지통 (Figma delete_outline 24dp, 행 오른쪽 끝) */
@Composable
private fun DeleteButton(visible: Boolean, description: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    AnimatedVisibility(visible,
        enter = fadeIn(tween(Tokens.Motion.medium)) +
            expandHorizontally(tween(Tokens.Motion.medium, easing = Tokens.Motion.easing), Alignment.Start),
        exit = fadeOut(tween(Tokens.Motion.fast)) +
            shrinkHorizontally(tween(Tokens.Motion.medium, easing = Tokens.Motion.easing), Alignment.Start)) {
        Box(Modifier.padding(start = 12.dp).size(24.dp).clip(CircleShape).clickable(onClick = onClick),
            contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_delete_outline), description, Modifier.size(24.dp), tint = tint)
        }
    }
}

/** 끌고 있는 줄은 손가락을 따라 떠오르고, 나머지 줄은 자리를 비켜 주며 미끄러진다. */
private fun Modifier.reorderable(
    scope: LazyItemScope, drag: FavoriteDragState, key: String,
    surface: androidx.compose.ui.graphics.Color
): Modifier {
    val dragging = drag.key == key
    return with(scope) {
        if (dragging) {
            this@reorderable.zIndex(1f)
                .graphicsLayer {
                    translationY = drag.offset
                    scaleX = 1.02f
                    scaleY = 1.02f
                    shadowElevation = 12.dp.toPx()
                }
                .background(surface)
        } else {
            this@reorderable.animateItem()
        }
    }
}

/**
 * 꾹 눌러 끌기 상태. 줄을 key 로 추적해서, 순서가 바뀌어도 끌던 줄을 놓치지 않는다.
 * 정류장과 노선을 섞어서 어느 자리로든 옮길 수 있다.
 */
private class FavoriteDragState(val listState: LazyListState, val scope: CoroutineScope) {
    var key by mutableStateOf<String?>(null)
        private set
    private var initialOffset by mutableIntStateOf(0)
    private var delta by mutableFloatStateOf(0f)
    /**
     * 자리를 바꾼 뒤 목록이 다시 배치될 때까지 기다리는 인덱스.
     * 배치 전의 옛 좌표로 한 번 더 계산하면 방금 바꾼 두 줄이 도로 뒤바뀐다.
     */
    private var awaitingIndex: Int? = null

    private val current: LazyListItemInfo?
        get() = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }

    /** 끌고 있는 줄이 원래 자리에서 벗어난 거리 */
    val offset: Float
        get() = current?.let { initialOffset + delta - it.offset } ?: 0f

    fun start(y: Float): Boolean {
        val hit = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { y.toInt() in it.offset until it.offset + it.size } ?: return false
        val k = hit.key as? String ?: return false
        if (!k.startsWith(FavoriteItem.STOP_PREFIX) && !k.startsWith(FavoriteItem.ROUTE_PREFIX)) return false
        key = k
        initialOffset = hit.offset
        delta = 0f
        return true
    }

    fun drag(dy: Float, onMove: (from: String, to: String) -> Unit) {
        val k = key ?: return
        delta += dy
        val item = current ?: return
        val top = initialOffset + delta
        val middle = (top + item.size / 2f).toInt()
        awaitingIndex?.let { if (item.index == it) awaitingIndex = null }
        val target = if (awaitingIndex != null) null else listState.layoutInfo.visibleItemsInfo.firstOrNull {
            val tk = it.key as? String
            // 정류장·노선 구분 없이 어느 줄과도 자리를 바꾼다
            tk != null && tk != k &&
                (tk.startsWith(FavoriteItem.STOP_PREFIX) || tk.startsWith(FavoriteItem.ROUTE_PREFIX)) &&
                middle in it.offset until it.offset + it.size
        }
        if (target != null) {
            val targetKey = target.key as String
            awaitingIndex = target.index
            // 첫 줄과 자리를 바꾸면 LazyColumn 이 그 줄을 따라 스크롤을 옮겨 버리므로 제자리에 붙잡는다.
            val first = listState.firstVisibleItemIndex
            val keepIndex = when (first) {
                target.index -> item.index
                item.index -> target.index
                else -> null
            }
            if (keepIndex != null) {
                val firstOffset = listState.firstVisibleItemScrollOffset
                scope.launch {
                    listState.scrollToItem(keepIndex, firstOffset)
                    onMove(k, targetKey)
                }
            } else {
                onMove(k, targetKey)
            }
        }

        // 화면 위/아래 끝에 닿으면 목록을 밀어 준다
        val viewStart = listState.layoutInfo.viewportStartOffset
        val viewEnd = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.afterContentPadding
        val overscroll = when {
            dy > 0 && top + item.size > viewEnd -> top + item.size - viewEnd
            dy < 0 && top < viewStart -> top - viewStart
            else -> 0f
        }
        if (overscroll != 0f) {
            val step = overscroll.coerceIn(-item.size / 2f, item.size / 2f)
            scope.launch { listState.scrollBy(step) }
        }
    }

    fun cancel() {
        awaitingIndex = null
        key = null
        delta = 0f
        initialOffset = 0
    }
}

/** fromKey 항목을 toKey 자리로 옮긴 새 목록 */
private fun List<FavoriteItem>.moved(fromKey: String, toKey: String): List<FavoriteItem> {
    val from = indexOfFirst { it.key == fromKey }
    val to = indexOfFirst { it.key == toKey }
    if (from < 0 || to < 0 || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

/** 보조 정보가 없어도 저장된 즐겨찾기는 계속 보여 준다. 화면 종료 취소는 전파한다. */
private suspend fun <T> favoriteInfoOrNull(block: suspend () -> T): T? = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}
