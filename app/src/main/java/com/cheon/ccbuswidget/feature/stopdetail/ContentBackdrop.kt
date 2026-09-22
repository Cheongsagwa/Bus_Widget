package com.cheon.ccbuswidget.feature.stopdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** 지도 대신 목록 뒤에 놓이는 유리 버튼용. 원본과 블러 복사본을 같은 프레임에 그린다. */
internal class ContentBackdrop(val layer: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
}

@Composable
internal fun rememberContentBackdrop(): ContentBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { ContentBackdrop(layer) }
}

internal fun Modifier.recordBackdrop(backdrop: ContentBackdrop): Modifier =
    onGloballyPositioned { backdrop.origin = it.positionInWindow() }.drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

@Composable
internal fun NavigationGlassSurface(
    shape: Shape,
    tint: Color,
    modifier: Modifier = Modifier,
    backdrop: ContentBackdrop? = null,
    content: @Composable BoxScope.() -> Unit
) {
    if (backdrop == null) {
        GlassSurface(shape, tint, modifier, blurRadius = 28.dp, content = content)
        return
    }
    val blurred = rememberGraphicsLayer()
    val radius = with(LocalDensity.current) { 28.dp.toPx() }
    val effect = remember(radius) { BlurEffect(radius, radius, TileMode.Clamp) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(modifier.onGloballyPositioned { origin = it.positionInWindow() }.clip(shape)) {
        Box(Modifier.matchParentSize().drawWithContent {
            blurred.renderEffect = effect
            // 원본 레이어 전체를 흐리게 하고 버튼 안만 클리핑한다. 작은 버튼의 경계도 흐려진다.
            blurred.record(size = backdrop.layer.size) { drawLayer(backdrop.layer) }
            val offset = backdrop.origin - origin
            translate(offset.x, offset.y) { drawLayer(blurred) }
        })
        Box(Modifier.matchParentSize().background(tint))
        content()
    }
}
