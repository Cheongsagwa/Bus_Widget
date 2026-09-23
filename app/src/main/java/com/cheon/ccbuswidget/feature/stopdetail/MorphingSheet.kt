package com.cheon.ccbuswidget.feature.stopdetail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.cheon.ccbuswidget.R
import com.cheon.ccbuswidget.ui.theme.Tokens
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.lerp as lerpColor

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
 * - [minimizedHeight] 가 있으면 먼저 헤더만 남은 축소창으로 줄고, 축소창에서 한 번 더 내려야 닫힌다.
 *   축소창에서 손잡이를 누르거나 위로 끌면 다시 기본 시트로 돌아온다.
 * - 처음 나타날 때 72dp 아래에서 떠오르며 페이드 인 된다.
 */
// maxHeight 를 실제로 쓰고 있는데도 린트가 오류로 잡는 경우가 있어 명시적으로 끈다 (오탐)
@android.annotation.SuppressLint("UnusedBoxWithConstraintsScope")
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
    /** 축소창 높이. null 이면 축소창 없이 바로 닫힌다 (즐겨찾기) */
    minimizedHeight: Dp? = null,
    /** 축소창이 되었는지 알려 준다 (손을 뗀 뒤 목표 상태 기준) */
    onMinimizedChange: (Boolean) -> Unit = {},
    /** 손잡이 줄 안에 얹을 것 (상단 버튼 등) */
    handle: @Composable BoxScope.(p: Float) -> Unit = {},
    /** 표면 위에 떠 있는 것 (새로고침 버튼 등) */
    overlay: @Composable BoxScope.(p: Float) -> Unit = {},
    /** 본문. 현재 배경색(tint)을 함께 넘겨 아래쪽 그라데이션 색을 맞출 수 있게 한다 */
    content: @Composable ColumnScope.(p: Float, tint: Color) -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val animation = remember { Animatable(if (expanded) 1f else 0f) }
    val appearance = remember { Animatable(0f) }
    var draggedProgress by remember { mutableStateOf<Float?>(null) }
    var dragStart by remember { mutableFloatStateOf(0f) }
    /** 접힌 상태에서 아래로 끌어 내린 거리(px) — 닫기 제스처 */
    var pullPx by remember { mutableFloatStateOf(0f) }
    val spec = remember { tween<Float>(Tokens.Motion.morph, easing = Tokens.Motion.easing) }
    val handleInteraction = remember { MutableInteractionSource() }
    /** 축소 정도 (0 기본 시트 ~ 1 축소창) */
    val minimize = remember { Animatable(0f) }
    var draggedMinimize by remember { mutableStateOf<Float?>(null) }
    var minimizeStart by remember { mutableFloatStateOf(0f) }
    val latestOnMinimizedChange by rememberUpdatedState(onMinimizedChange)
    val minimizeSpec = remember { tween<Float>(Tokens.Motion.medium, easing = Tokens.Motion.easing) }
    LaunchedEffect(expanded) {
        // 밖에서 펼치라고 하면 축소창도 풀어 준다
        if (expanded && minimize.value > 0f) {
            minimize.snapTo(0f)
            latestOnMinimizedChange(false)
        }
        animation.animateTo(if (expanded) 1f else 0f, spec)
    }
    LaunchedEffect(Unit) {
        appearance.animateTo(1f, tween(Tokens.Motion.appear, easing = Tokens.Motion.easing))
    }

    BoxWithConstraints(
        modifier.fillMaxSize().then(if (backdrop != null) Modifier.recordBackdrop(backdrop) else Modifier)
    ) {
        // 이 상자(=화면)의 높이. 스코프를 'this.' 로 명시해 두어야 린트가
        // "BoxWithConstraints scope is not used" 오류로 잘못 잡지 않는다.
        val fullHeight = this.maxHeight
        val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val peek = minOf(Tokens.Sheet.peekHeight, fullHeight - nav - status)
        val travelPx = with(density) { (fullHeight - peek).toPx().coerceAtLeast(1f) }
        val minHeight = minimizedHeight?.coerceAtMost(peek)
        val minimizeTravelPx = with(density) { (peek - (minHeight ?: peek)).toPx().coerceAtLeast(1f) }
        val p = draggedProgress ?: animation.value
        val m = if (minHeight == null) 0f else (draggedMinimize ?: minimize.value)
        /** 펼침이 0 일 때의 시트 높이 (기본 ↔ 축소창) */
        val baseHeight = if (minHeight == null) peek else lerp(peek, minHeight, m)
        val shape = RoundedCornerShape(lerp(Tokens.Sheet.cornerRadius, 0.dp, p))
        // 먼저 지도를 더 흐리게 만들고, 후반부에 불투명 배경으로 부드럽게 채운다.
        val opacityProgress = ((p - 0.25f) / 0.75f).coerceIn(0f, 1f)
        val tint = lerpColor(collapsedTint, expandedTint,
            opacityProgress * opacityProgress * (3f - 2f * opacityProgress))

        val dragState = rememberDraggableState { delta ->
            val current = draggedProgress ?: animation.value
            val mNow = draggedMinimize ?: minimize.value
            if (minHeight != null && current <= 0f && pullPx <= 0f &&
                ((delta > 0f && mNow < 1f) || (delta < 0f && mNow > 0f))) {
                // 기본 시트 ↔ 축소창 사이를 손가락을 따라 오간다
                draggedMinimize = (mNow + delta / minimizeTravelPx).coerceIn(0f, 1f)
            } else if (onDismiss != null && (pullPx > 0f || (current <= 0f && delta > 0f))) {
                pullPx = (pullPx + delta).coerceAtLeast(0f)
            } else {
                draggedProgress = (current - delta / travelPx).coerceIn(0f, 1f)
            }
        }

        // 손잡이 줄의 누르기·끌기. 손잡이 줄과, 그 아래로 조금 더 늘린 터치 영역이 함께 쓴다.
        val handleGestures = Modifier
                        // 눌렀을 때 손잡이 줄 전체가 어두워지는 리플은 넣지 않는다
                        .clickable(interactionSource = handleInteraction, indication = null,
                            enabled = interactive,
                            onClickLabel = when {
                                m > 0f -> "기본 크기로 펼치기"
                                expanded -> "접기"
                                else -> "전체 화면으로 펼치기"
                            }) {
                            if (m > 0f) {
                                // 축소창을 누르면 기본 시트로 돌아온다
                                latestOnMinimizedChange(false)
                                scope.launch { minimize.animateTo(0f, minimizeSpec) }
                            } else {
                                onExpandedChange(!expanded)
                            }
                        }
                        .draggable(dragState, Orientation.Vertical, enabled = interactive,
                            onDragStarted = {
                                animation.stop()
                                minimize.stop()
                                dragStart = animation.value
                                draggedProgress = animation.value
                                minimizeStart = minimize.value
                                draggedMinimize = minimize.value
                            },
                            onDragStopped = { velocity ->
                                if (pullPx > 0f) {
                                    // 접힌 카드를 아래로 끌어내린 경우: 충분하면 닫고, 아니면 제자리로
                                    draggedProgress = null
                                    val dismissPx = with(density) { Tokens.Sheet.collapseDrop.toPx() }
                                    val release = tween<Float>(Tokens.Motion.medium, easing = Tokens.Motion.easing)
                                    draggedMinimize = null
                                    if (onDismiss != null && (pullPx > dismissPx || velocity > 600f)) {
                                        val gone = with(density) { (baseHeight + nav).toPx() }
                                        animate(pullPx, gone, animationSpec = release) { v, _ -> pullPx = v }
                                        onDismiss()
                                    } else {
                                        animate(pullPx, 0f, animationSpec = release) { v, _ -> pullPx = v }
                                    }
                                } else if (minHeight != null &&
                                    kotlin.math.abs((draggedMinimize ?: minimizeStart) - minimizeStart) > 0.001f) {
                                    // 기본 시트 ↔ 축소창 사이에서 놓은 경우
                                    val end = draggedMinimize ?: minimize.value
                                    val threshold = with(density) { Tokens.Sheet.collapseDrop.toPx() } / minimizeTravelPx
                                    val toMin = when {
                                        velocity > 600f -> true
                                        velocity < -600f -> false
                                        end > minimizeStart + threshold -> true
                                        end < minimizeStart - threshold -> false
                                        else -> minimizeStart >= 0.5f
                                    }
                                    draggedProgress = null
                                    minimize.snapTo(end)
                                    draggedMinimize = null
                                    latestOnMinimizedChange(toMin)
                                    minimize.animateTo(if (toMin) 1f else 0f, minimizeSpec)
                                } else {
                                    draggedMinimize = null
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

                            })

        GlassSurface(shape, tint,
            Modifier.align(Alignment.BottomCenter)
                .padding(bottom = lerp(nav, 0.dp, p),
                    start = lerp(Tokens.Sheet.sideMargin, 0.dp, p), end = lerp(Tokens.Sheet.sideMargin, 0.dp, p))
                .fillMaxWidth().height(lerp(baseHeight, fullHeight, p))
                .graphicsLayer {
                    translationY = (1f - appearance.value) * 72.dp.toPx() + pullPx
                    alpha = appearance.value
                }
                .glassShadow(shape),
            // 블러 반경은 고정한다. 반경이 매 프레임 바뀌면 화면 전체 크기의 블러(RenderEffect)를
            // 매 프레임 새로 만들고 블러 레이어 크기도 다시 재서, 세부 확장창으로 펼칠 때만 버벅였다.
            // (펼칠수록 배경색이 불투명해져 지도가 가려지므로 반경을 키우지 않아도 모습 차이는 거의 없다)
            blurRadius = Tokens.Glass.blurRadius,
            // 배경색이 완전히 불투명해지면(펼친 뒤) 가려진 지도 블러는 그리지 않는다 — 스크롤·전환이 가벼워진다
            drawBackdrop = tint.alpha < 0.98f) {
            CompositionLocalProvider(LocalContentColor provides colorResource(R.color.glass_on_surface)) {
                Column(Modifier.fillMaxSize().padding(bottom = lerp(0.dp, nav, p))) {
                    Box(Modifier.fillMaxWidth()
                        .height(lerp(collapsedHandleHeight, status + expandedHandleHeight, p))
                        .semantics { contentDescription = handleDescription }
                        .then(handleGestures)) {
                        Box(Modifier.alpha(1f - p)) { SheetGrabber() }
                        handle(p)
                    }
                    CompositionLocalProvider(LocalSheetMinimize provides m) {
                        content(p, tint)
                    }
                }
                // 그랩바 터치 영역을 아래로 더 늘린다 (그랩바가 얇아 잡기 어렵다).
                // 접힌 카드에서만 — 펼친 뒤에는 이 자리에 뒤로·즐겨찾기 버튼이 있다.
                if (p < 0.5f) {
                    Box(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                        .height(collapsedHandleHeight + Tokens.Sheet.grabberTouchExtra)
                        .then(handleGestures))
                }
                overlay(p)
            }
        }
    }
}

/** 축소창으로 줄어든 정도 (0 기본 시트 ~ 1 축소창). 헤더가 축소창에서 숨길 것을 정할 때 쓴다. */
internal val LocalSheetMinimize = compositionLocalOf { 0f }

/** 축소창으로 줄어드는 동안 헤더 아래(구분선·목록)가 사라지는 정도. 절반쯤 줄었을 때 이미 다 사라진다. */
internal fun minimizedListAlpha(minimize: Float): Float = (1f - minimize * 2f).coerceIn(0f, 1f)

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
internal fun BoxScope.MorphRefreshButton(
    p: Float,
    onRefresh: () -> Unit,
    /** 주면 뒤의 본문을 흐리게 비추는 유리 버튼이 된다 ([시간표 보기] 바와 같은 반투명) */
    blur: BlurBehindState? = null,
    /** 바깥에서 따로 흐리게 할 때 (노선 창의 시간표 화면 등) */
    extraAlpha: Float = 1f
) {
    if (p <= 0.5f) return
    // 뒤 블러는 펼치기가 끝난 뒤에 스며들게 한다 (펼치는 동안 매 프레임 본문 블러를 새로 만들지 않도록)
    val alpha = rememberUpdatedState(((p - 0.5f) * 2f).coerceIn(0f, 1f) * extraAlpha * morphSettledAlpha(p))
    RoundFloatingButton(
        iconRes = R.drawable.ic_refresh,
        description = "새로고침",
        modifier = Modifier.align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .padding(end = Tokens.Expanded.fabMargin, bottom = Tokens.Expanded.fabBottom)
            .alpha((p - 0.5f) * 2f)
            .then(if (blur != null) Modifier.blurBehindHole(blur, "refresh", alpha) else Modifier),
        background = if (blur != null) glassColor() else colorResource(R.color.expanded_button),
        onClick = onRefresh
    )
}

/** 접혔을 때 버튼들이 사라지는 정도 (0.5 에서 완전히 사라진다) */
internal fun collapsedOnlyAlpha(p: Float): Float = (1f - p * 2f).coerceIn(0f, 1f)
