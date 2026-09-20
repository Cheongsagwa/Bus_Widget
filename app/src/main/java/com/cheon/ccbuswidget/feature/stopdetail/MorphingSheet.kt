package com.cheon.ccbuswidget.feature.stopdetail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.cheon.ccbuswidget.R
import com.cheon.ccbuswidget.ui.theme.Tokens

/**
 * 즐겨찾기 창에서 시작한 '모핑' 전환을 정류장·노선 시트도 함께 쓰도록 뽑아낸 공통 틀.
 *
 * 떠 있는 유리 카드를 없애고 새 화면을 덮는 대신, 같은 표면·같은 내용을 유지한 채
 * 높이 / 모서리 / 좌우·아래 여백 / 블러 반경 / 배경색을 진행도 p(0 접힘 ~ 1 전체 화면)로 함께 바꾼다.
 * 내용 쪽도 p 를 받아 여백·글자 크기 등을 같이 보간하면 한 장이 그대로 펼쳐지는 것처럼 보인다.
 *
 * - 손잡이 줄을 끌면 손가락을 따라가고, 놓으면 20dp / 600px/s 기준으로 펼치거나 접는다.
 * - 손잡이 줄을 누르면 펼침/접힘을 토글한다.
 * - [onDismiss] 가 있으면 접힌 상태에서 아래로 끌어 닫을 수 있다.
 * - 처음 나타날 때 72dp 아래에서 떠오르며 페이드 인 된다.
 */
@Composable
internal fun MorphingSheet(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    collapsedTint: Color,
    expandedTint: Color,
    handleDescription: String,
    modifier: Modifier = Modifier,
    /** 접혔을 때 손잡이 줄 높이 */
    collapsedHandleHeight: Dp = Tokens.Sheet.morphHandleHeight,
    /** 펼쳤을 때 손잡이 줄 높이 (상태바 아래부터) */
    expandedHandleHeight: Dp = 76.dp,
    /** 하단 툴바처럼 이 시트를 배경으로 흐리게 비춰야 하는 표면이 있을 때 */
    backdrop: ContentBackdrop? = null,
    /** false 면 끌기·누르기로 펼침 상태를 바꿀 수 없다 (즐겨찾기 편집 중) */
    interactive: Boolean = true,
    onDismiss: (() -> Unit)? = null,
    /** 손잡이 줄 안에 얹을 것 (상단 버튼 등) */
    handle: @Composable BoxScope.(p: Float) -> Unit = {},
    /** 표면 위에 떠 있는 것 (새로고침 버튼 등) */
    overlay: @Composable BoxScope.(p: Float) -> Unit = {},
    /** 본문. 현재 배경색(tint)을 함께 넘겨 아래쪽 그라데이션 색을 맞출 수 있게 한다 */
    content: @Composable ColumnScope.(p: Float, tint: Color) -> Unit
) {
    val density = LocalDensity.current
    val animation = remember { Animatable(if (expanded) 1f else 0f) }
    val appearance = remember { Animatable(0f) }
    var draggedProgress by remember { mutableStateOf<Float?>(null) }
    var dragStart by remember { mutableFloatStateOf(0f) }
    /** 접힌 상태에서 아래로 끌어 내린 거리(px) — 닫기 제스처 */
    var pullPx by remember { mutableFloatStateOf(0f) }
    val spec = remember { tween<Float>(Tokens.Motion.morph, easing = Tokens.Motion.easing) }
    val handleInteraction = remember { MutableInteractionSource() }
    LaunchedEffect(expanded) { animation.animateTo(if (expanded) 1f else 0f, spec) }
    LaunchedEffect(Unit) {
        appearance.animateTo(1f, tween(Tokens.Motion.appear, easing = Tokens.Motion.easing))
    }

    BoxWithConstraints(
        modifier.fillMaxSize().then(if (backdrop != null) Modifier.recordBackdrop(backdrop) else Modifier)
    ) {
        val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val peek = minOf(Tokens.Sheet.peekHeight, maxHeight - nav - status)
        val travelPx = with(density) { (maxHeight - peek).toPx().coerceAtLeast(1f) }
        val p = draggedProgress ?: animation.value
        val shape = RoundedCornerShape(lerp(Tokens.Sheet.cornerRadius, 0.dp, p))
        // 먼저 지도를 더 흐리게 만들고, 후반부에 불투명 배경으로 부드럽게 채운다.
        val opacityProgress = ((p - 0.25f) / 0.75f).coerceIn(0f, 1f)
        val tint = lerpColor(collapsedTint, expandedTint,
            opacityProgress * opacityProgress * (3f - 2f * opacityProgress))

        val dragState = rememberDraggableState { delta ->
            val current = draggedProgress ?: animation.value
            if (onDismiss != null && (pullPx > 0f || (current <= 0f && delta > 0f))) {
                pullPx = (pullPx + delta).coerceAtLeast(0f)
            } else {
                draggedProgress = (current - delta / travelPx).coerceIn(0f, 1f)
            }
        }

        GlassSurface(shape, tint,
            Modifier.align(Alignment.BottomCenter)
                .padding(bottom = lerp(nav, 0.dp, p),
                    start = lerp(Tokens.Sheet.sideMargin, 0.dp, p), end = lerp(Tokens.Sheet.sideMargin, 0.dp, p))
                .fillMaxWidth().height(lerp(peek, maxHeight, p))
                .graphicsLayer {
                    translationY = (1f - appearance.value) * 72.dp.toPx() + pullPx
                    alpha = appearance.value
                }
                .glassShadow(shape),
            blurRadius = lerp(Tokens.Glass.blurRadius, Tokens.Sheet.morphBlurRadius, p)) {
            CompositionLocalProvider(LocalContentColor provides colorResource(R.color.glass_on_surface)) {
                Column(Modifier.fillMaxSize().padding(bottom = lerp(0.dp, nav, p))) {
                    Box(Modifier.fillMaxWidth()
                        .height(lerp(collapsedHandleHeight, status + expandedHandleHeight, p))
                        .semantics { contentDescription = handleDescription }
                        // 눌렀을 때 손잡이 줄 전체가 어두워지는 리플은 넣지 않는다
                        .clickable(interactionSource = handleInteraction, indication = null,
                            enabled = interactive,
                            onClickLabel = if (expanded) "접기" else "전체 화면으로 펼치기") {
                            onExpandedChange(!expanded)
                        }
                        .draggable(dragState, Orientation.Vertical, enabled = interactive,
                            onDragStarted = {
                                animation.stop()
                                dragStart = animation.value
                                draggedProgress = animation.value
                            },
                            onDragStopped = { velocity ->
                                if (pullPx > 0f) {
                                    // 접힌 카드를 아래로 끌어내린 경우: 충분하면 닫고, 아니면 제자리로
                                    draggedProgress = null
                                    val dismissPx = with(density) { Tokens.Sheet.collapseDrop.toPx() }
                                    val release = tween<Float>(Tokens.Motion.medium, easing = Tokens.Motion.easing)
                                    if (onDismiss != null && (pullPx > dismissPx || velocity > 600f)) {
                                        val gone = with(density) { (peek + nav).toPx() }
                                        animate(pullPx, gone, animationSpec = release) { v, _ -> pullPx = v }
                                        onDismiss()
                                    } else {
                                        animate(pullPx, 0f, animationSpec = release) { v, _ -> pullPx = v }
                                    }
                                } else {
                                    val end = draggedProgress ?: animation.value
                                    val threshold = with(density) { Tokens.Sheet.expandLift.toPx() } / travelPx
                                    val target = when {
                                        velocity < -600f -> true
                                        velocity > 600f -> false
                                        end > dragStart + threshold -> true
                                        end < dragStart - threshold -> false
                                        else -> expanded
                                    }
                                    animation.snapTo(end)
                                    draggedProgress = null
                                    onExpandedChange(target)
                                    if (target == expanded) animation.animateTo(if (target) 1f else 0f, spec)
                                }
                            })) {
                        Box(Modifier.alpha(1f - p)) { SheetGrabber() }
                        handle(p)
                    }
                    content(p, tint)
                }
                overlay(p)
            }
        }
    }
}

/** 펼쳤을 때만 보이는 윗줄: 왼쪽 접기 · 오른쪽 즐겨찾기 (정류장·노선 확장창, Figma 40:372 / 40:186) */
@Composable
internal fun BoxScope.MorphTopBar(
    p: Float,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    if (p <= 0.5f) return
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Row(
        Modifier.align(Alignment.TopStart).fillMaxWidth()
            .padding(top = status).height(Tokens.Expanded.topBarHeight)
            .padding(horizontal = Tokens.Expanded.topBarMargin)
            .alpha((p - 0.5f) * 2f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundFloatingButton(R.drawable.ic_back, "접기", onClick = onBack)
        Spacer(Modifier.weight(1f))
        RoundFloatingButton(
            iconRes = if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_outline,
            description = if (isFavorite) "즐겨찾기 해제" else "즐겨찾기",
            tint = if (isFavorite) colorResource(R.color.favorite_on)
            else colorResource(R.color.glass_on_surface),
            onClick = onToggleFavorite
        )
    }
}

/** 펼쳤을 때만 보이는 오른쪽 아래 새로고침 버튼 */
@Composable
internal fun BoxScope.MorphRefreshButton(p: Float, onRefresh: () -> Unit) {
    if (p <= 0.5f) return
    RoundFloatingButton(
        iconRes = R.drawable.ic_refresh,
        description = "새로고침",
        modifier = Modifier.align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .padding(end = Tokens.Expanded.fabMargin, bottom = Tokens.Expanded.fabBottom)
            .alpha((p - 0.5f) * 2f),
        onClick = onRefresh
    )
}

/** 접혔을 때 버튼들이 사라지는 정도 (0.5 에서 완전히 사라진다) */
internal fun collapsedOnlyAlpha(p: Float): Float = (1f - p * 2f).coerceIn(0f, 1f)
