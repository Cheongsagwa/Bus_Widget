package com.cheon.ccbuswidget.feature.stopdetail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import com.cheon.ccbuswidget.ui.theme.Tokens

/**
 * 불투명한 확장창 위에 떠 있는 버튼([시간표 보기] 바 · 새로고침 버튼) 뒤를 흐리게 보이게 한다.
 * 검색창의 검색바와 같은 방식이다.
 *
 * 1) 본문([blurBehindContent])을 레이어에 한 번 담아 그대로 그리고
 * 2) 버튼([blurBehindHole])이 놓인 자리에만 같은 그림을 흐리게 해서 한 번 더 깐다.
 *    흐린 복사본에는 배경색도 함께 넣어, 밑의 선명한 글자가 반투명 버튼을 뚫고 보이지 않게 한다.
 * 3) 버튼 자체는 반투명 색(glassColor)만 칠한다.
 */
@Stable
internal class BlurBehindState(
    internal val sharp: GraphicsLayer,
    internal val blurred: GraphicsLayer,
    internal val effect: BlurEffect
) {
    internal var content: LayoutCoordinates? by mutableStateOf(null)
    internal val holes = mutableStateMapOf<String, Hole>()
    /** 본문이나 버튼 위치가 바뀌면 올려서 다시 그리게 한다 */
    internal var version by mutableIntStateOf(0)

    internal class Hole(val coordinates: LayoutCoordinates, val alpha: State<Float>)
}

@Composable
internal fun rememberBlurBehind(): BlurBehindState {
    val sharp = rememberGraphicsLayer()
    val blurred = rememberGraphicsLayer()
    val radiusPx = with(LocalDensity.current) { Tokens.Glass.blurRadius.toPx() }
    return remember(sharp, blurred, radiusPx) {
        BlurBehindState(sharp, blurred, BlurEffect(radiusPx, radiusPx, TileMode.Decal))
    }
}

/** 흐리게 비칠 본문. [background] 는 본문 뒤의 시트 배경색. */
internal fun Modifier.blurBehindContent(state: BlurBehindState, background: Color): Modifier =
    onGloballyPositioned {
        state.content = it
        state.version++
    }.drawWithContent {
        // drawContent() 는 한 번만 부를 수 있어서 레이어에 담아 두 번 그린다
        state.sharp.record { this@drawWithContent.drawContent() }
        drawLayer(state.sharp)

        state.version // 위치가 바뀌면 다시 그리도록 읽어 둔다
        val content = state.content?.takeIf { it.isAttached } ?: return@drawWithContent
        val visible = state.holes.values.filter { it.coordinates.isAttached && it.alpha.value > 0f }
        if (visible.isEmpty()) return@drawWithContent

        // 흐린 복사본은 한 번만 만들고, 버튼마다 그 자리만 잘라 쓴다
        state.blurred.renderEffect = state.effect
        state.blurred.record {
            drawRect(background)
            drawLayer(state.sharp)
        }
        for (hole in visible) {
            val r = content.localBoundingBoxOf(hole.coordinates, clipBounds = false)
            val radius = minOf(r.width, r.height) / 2f
            val path = Path().apply { addRoundRect(RoundRect(r, CornerRadius(radius, radius))) }
            drawIntoCanvas { canvas ->
                canvas.saveLayer(r, Paint().apply { alpha = hole.alpha.value.coerceIn(0f, 1f) })
                clipPath(path) { drawLayer(state.blurred) }
                canvas.restore()
            }
        }
    }

/**
 * 뒤를 흐리게 할 떠 있는 버튼. 모서리는 알약/원 모양(짧은 변의 절반)으로 자른다.
 * [alpha] 는 버튼이 나타나고 사라지는 정도 — 흐린 복사본도 같이 옅어진다.
 */
internal fun Modifier.blurBehindHole(state: BlurBehindState, key: String, alpha: State<Float>): Modifier =
    onGloballyPositioned {
        state.holes[key] = BlurBehindState.Hole(it, alpha)
        state.version++
    }

/**
 * 모핑 시트가 다 펼쳐진 뒤에만 1 이 되는 값 (짧게 스며든다).
 * 펼치는 동안에는 본문이 매 프레임 바뀌어 흐린 복사본도 매 프레임 새로 계산해야 하므로,
 * 버튼 뒤 블러는 전환이 끝난 뒤에 켠다. 그 사이 버튼은 반투명 색만 보인다.
 */
@Composable
internal fun morphSettledAlpha(p: Float): Float =
    animateFloatAsState(
        if (p >= 0.999f) 1f else 0f,
        tween(Tokens.Motion.fast, easing = Tokens.Motion.easing),
        label = "블러 켜기"
    ).value
