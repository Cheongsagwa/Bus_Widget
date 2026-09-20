package com.cheon.ccbuswidget.feature.stopdetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import com.cheon.ccbuswidget.R
import com.cheon.ccbuswidget.data.model.BusRoute
import com.cheon.ccbuswidget.data.model.BusStop
import com.cheon.ccbuswidget.data.model.FavoriteItem
import com.cheon.ccbuswidget.ui.theme.Tokens

/**
 * 즐겨찾기 창. 전환 자체는 [MorphingSheet] 가 맡고(정류장·노선 시트와 공유),
 * 여기서는 즐겨찾기 전용 버튼(접기 · 편집)과 목록만 얹는다.
 *
 * 편집(Figma 70:1539): 펼친 상태에서 오른쪽 위 연필 → 편집 모드.
 * 편집 중에는 각 줄 휴지통으로 삭제, 꾹 눌러 끌어서 순서 변경(정류장·노선 섞어서).
 * 바뀐 내용은 화면 아래 저장·취소 바([FavoritesEditActionBar])에서 저장해야 반영되고, 취소하면 버린다.
 */
@Composable
internal fun FavoritesSheet(
    expanded: Boolean, onExpandedChange: (Boolean) -> Unit,
    editing: Boolean, onStartEditing: () -> Unit,
    items: List<FavoriteItem>, apiKey: String, cityCode: String,
    listState: LazyListState, backdrop: ContentBackdrop,
    onStop: (BusStop) -> Unit, onRoute: (BusRoute) -> Unit,
    onDelete: (FavoriteItem) -> Unit,
    onReorder: (List<FavoriteItem>) -> Unit,
    /** 접힌 창을 아래로 쓸어내렸을 때 — 뒤로가기와 같다 (정류장·노선 창과 같은 원리) */
    onDismiss: () -> Unit
) {
    val surface = colorResource(R.color.favorites_surface)
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val buttonEnter = fadeIn(tween(Tokens.Motion.medium, easing = Tokens.Motion.easing)) +
        scaleIn(tween(Tokens.Motion.medium, easing = Tokens.Motion.easing), initialScale = 0.6f)
    val buttonExit = fadeOut(tween(Tokens.Motion.fast)) +
        scaleOut(tween(Tokens.Motion.medium, easing = Tokens.Motion.easing), targetScale = 0.6f)
    MorphingSheet(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        collapsedTint = glassColor(white = true),
        expandedTint = surface,
        handleDescription = "즐겨찾기 창 손잡이",
        // Figma: 목록은 접힘 y=29, 전체 화면에서는 상태바 아래 76dp부터.
        collapsedHandleHeight = Tokens.Sheet.morphHandleHeight,
        expandedHandleHeight = 76.dp,
        backdrop = backdrop,
        interactive = !editing,
        onDismiss = onDismiss,
        handle = { p ->
            if (p > 0.5f) {
                val buttonAlpha = (p - 0.5f) * 2f
                // 편집 중(Figma 70:1539)에는 위쪽 버튼 없이 아래 저장·취소 바만 쓴다
                AnimatedVisibility(
                    visible = !editing,
                    modifier = Modifier.padding(start = 10.dp, top = status + 8.dp).alpha(buttonAlpha),
                    enter = buttonEnter, exit = buttonExit
                ) {
                    RoundFloatingButton(R.drawable.ic_back, "즐겨찾기 창 접기",
                        background = surface, onClick = { onExpandedChange(false) })
                }
                AnimatedVisibility(
                    visible = !editing,
                    modifier = Modifier.align(Alignment.TopEnd)
                        .padding(end = 10.dp, top = status + 8.dp).alpha(buttonAlpha),
                    enter = buttonEnter, exit = buttonExit
                ) {
                    RoundFloatingButton(R.drawable.ic_edit, "즐겨찾기 편집",
                        background = surface, onClick = onStartEditing)
                }
            }
        }
    ) { p, _ ->
        FavoritesList(items, apiKey, cityCode, p, listState, editing,
            onStop, onRoute, onDelete, onReorder,
            Modifier.fillMaxWidth().weight(1f))
    }
}
