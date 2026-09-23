package com.cheon.ccbuswidget.feature.stopdetail
//지도, 공용유리 UI
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.cheon.ccbuswidget.R
import com.cheon.ccbuswidget.data.model.BusLocation
import com.cheon.ccbuswidget.data.model.BusStop
import com.cheon.ccbuswidget.data.model.RouteKind
import com.cheon.ccbuswidget.data.model.RouteStop
import com.cheon.ccbuswidget.feature.settings.SettingsActivity
import com.cheon.ccbuswidget.ui.theme.Tokens
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
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// ------------------------------------------------- 지도 위 유리 버튼 · 오버레이

/**
 * 지도 화면의 스냅샷.
 *
 * 안드로이드에서는 지도를 덮고 있는 뷰가 "뒤에 있는 것"을 직접 블러할 방법이 없다.
 * (Compose 는 지도처럼 별도 서피스로 그려지는 뷰를 캡처하지 못한다)
 * 그래서 지도를 TextureView 로 렌더링시키고, 화면을 축소해 떠 온 뒤
 * 그 비트맵을 유리 표면 뒤에 깔고 블러를 건다. Figma 의 BACKGROUND_BLUR 와 같은 결과다.
 */
@Stable
internal class MapBackdrop {
    /** 축소해서 떠 온 지도 화면 */
    var image by mutableStateOf<ImageBitmap?>(null)
    /** 그 스냅샷이 대응하는 실제 화면 크기(px) */
    var windowSize by mutableStateOf(IntSize.Zero)
}

internal val LocalMapBackdrop = staticCompositionLocalOf { MapBackdrop() }

/**
 * Figma 의 떠 있는 유리 표면.
 * 뒤쪽 지도 스냅샷을 자신의 위치만큼 잘라 깔고 → 블러 → 반투명 색을 덮는다.
 */
@Composable
internal fun GlassSurface(
    shape: Shape,
    tint: Color,
    modifier: Modifier = Modifier,
    blurRadius: Dp = Tokens.Glass.blurRadius,
    /**
     * false 면 뒤의 지도 블러를 그리지 않는다. 불투명한 색이 덮고 있어 어차피 안 보일 때
     * (전체 화면으로 펼친 시트) 화면 전체 블러를 매 프레임 계산하지 않도록 쓴다.
     */
    drawBackdrop: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val backdrop = LocalMapBackdrop.current
    // 이 표면의 윈도우 기준 좌표
    var origin by remember { mutableStateOf(IntOffset.Zero) }

    // 블러가 가장자리에서 끌어올 여유분.
    // RenderEffect 의 번짐은 반경보다 넓게 퍼지므로 넉넉히 2배를 준다.
    val padPx = with(LocalDensity.current) {
        (blurRadius * 2).roundToPx()
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                val p = coords.positionInWindow()
                origin = IntOffset(p.x.roundToInt(), p.y.roundToInt())
            }
            .clip(shape)
    ) {
        if (drawBackdrop) {
            // 스냅샷(backdrop.image)은 아래 drawBehind 안에서만 읽는다.
            // 여기(컴포지션 단계)에서 읽으면 스냅샷이 갱신될 때마다 초당 여덟 번씩
            // 시트 전체가 리컴포지션되면서 화면이 깜빡인다. 그리기 단계에서 읽으면
            // 다시 그리기만 일어난다.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    // 블러 레이어 자체를 표면보다 크게 잡는다.
                    // 표면 크기에 딱 맞추면 테두리가 빈 픽셀을 끌어와 흐려지지 않는데,
                    // 50dp 짜리 원형 버튼은 거의 전부가 '테두리'라 블러가 없는 것처럼 보였다.
                    // 넘치는 부분은 바깥 Box 의 clip(shape) 가 잘라 준다.
                    .layout { measurable, constraints ->
                        val w = constraints.maxWidth + padPx * 2
                        val h = constraints.maxHeight + padPx * 2
                        val placeable = measurable.measure(Constraints.fixed(w, h))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeable.place(-padPx, -padPx)
                        }
                    }
                    .blur(
                        radius = blurRadius,
                        edgeTreatment = BlurredEdgeTreatment.Unbounded
                    )
                    .drawBehind {
                        val image = backdrop.image ?: return@drawBehind
                        val win = backdrop.windowSize
                        if (win.width <= 0 || win.height <= 0) return@drawBehind

                        // 스냅샷을 잘라 쓰지 않는다.
                        // 화면 전체 크기로 늘린 뒤 이 노드의 위치만큼 밀어 그리고,
                        // 넘치는 부분은 클리핑에 맡긴다.
                        // (잘라내기 계산이 어긋나면 1픽셀이 늘어나 단색으로 보였다)
                        drawImage(
                            image = image,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(image.width, image.height),
                            dstOffset = IntOffset(
                                padPx - origin.x,
                                padPx - origin.y
                            ),
                            dstSize = IntSize(win.width, win.height),
                            filterQuality = FilterQuality.Low
                        )
                    }
            )
        }

        // Figma 의 반투명 색을 덮는다
        Box(modifier = Modifier.matchParentSize().background(tint))

        // 색을 지정하지 않은 Text 는 LocalContentColor 를 쓰는데,
        // 그 기본값이 Color.Black 이라 다크 모드에서도 검은 글씨로 남았다.
        // 유리 표면 위 기본 글자색을 테마에 맞춰 내려 준다.
        val boxScope = this
        CompositionLocalProvider(
            LocalContentColor provides colorResource(R.color.glass_on_surface)
        ) {
            with(boxScope) { content() }
        }
    }
}

/** Figma 의 떠 있는 유리 표면 색 */
@Composable
internal fun glassColor(white: Boolean = false): Color =
    colorResource(if (white) R.color.glass_surface_white else R.color.glass_surface)

/** Figma 그림자 (blur 65 / #40000000) */
@Composable
internal fun Modifier.glassShadow(shape: Shape): Modifier {
    val shadow = colorResource(R.color.glass_shadow)
    return this.shadow(
        elevation = Tokens.Glass.shadowElevation,
        shape = shape,
        ambientColor = shadow,
        spotColor = shadow
    )
}

/** 지도 위 원형 버튼 (더보기 / 검색 / 현위치) */
@Composable
internal fun GlassIconButton(
    iconRes: Int,
    description: String,
    modifier: Modifier = Modifier,
    tint: Color = colorResource(R.color.glass_on_surface),
    size: Dp = Tokens.Glass.buttonSize,
    iconSize: Dp = Tokens.Glass.buttonIconSize,
    onClick: () -> Unit
) {
    GlassSurface(
        shape = CircleShape,
        tint = glassColor(),
        modifier = modifier
            .size(size)
            .glassShadow(CircleShape)
            .clip(CircleShape)
            .clickable { onClick() }
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            tint = tint,
            modifier = Modifier
                .align(Alignment.Center)
                .size(iconSize)
        )
    }
}

/**
 * 목록 위쪽 그라데이션 (Figma 90:2501 / 90:2494, 높이 44).
 * 아래쪽 그라데이션과 반대로, 스크롤을 내리기 시작하면 나타나고 맨 위에 닿으면 사라진다.
 */
@Composable
internal fun BoxScope.TopFade(visible: Boolean, surface: Color) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Tokens.Sheet.topFadeHeight)
                .background(
                    Brush.verticalGradient(
                        listOf(surface.copy(alpha = 1f), surface.copy(alpha = 0f))
                    )
                )
        )
    }
}

/**
 * 목록 아래쪽 '더 있음' 그라데이션.
 * 스크롤이 끝까지 내려가면 사라진다.
 */
@Composable
internal fun BoxScope.BottomFade(visible: Boolean, surface: Color) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Tokens.Sheet.fadeHeight)
                .background(
                    Brush.verticalGradient(
                        listOf(surface.copy(alpha = 0f), surface.copy(alpha = 1f))
                    )
                )
        )
    }
}


// ============================================================ 확장창 · 검색창

/** 확장창 · 검색창의 불투명 배경 (다크 모드는 values-night) */
@Composable
internal fun expandedSurface(): Color = colorResource(R.color.expanded_surface)

/**
 * One UI 스타일 원형 플로팅 버튼.
 * 확장창·검색창은 배경이 불투명해서 지도를 블러할 게 없으므로 단색으로 그린다.
 */
@Composable
internal fun RoundFloatingButton(
    iconRes: Int,
    description: String,
    modifier: Modifier = Modifier,
    tint: Color = colorResource(R.color.glass_on_surface),
    background: Color = colorResource(R.color.expanded_button),
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(Tokens.Glass.buttonSize)
            .glassShadow(CircleShape)
            .background(background, CircleShape)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(Tokens.Glass.buttonIconSize)
        )
    }
}

@Composable
internal fun MissingKeyPane(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("네이버 지도 Key ID 가 없습니다", fontWeight = FontWeight.Bold)
        Text(
            "네이버 클라우드 플랫폼에서 Maps(Dynamic Map) 이용 신청 후 받은 Key ID 를 등록하면 지도가 표시됩니다.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )
        Button(onClick = onOpenSettings) { Text("설정 열기") }
    }
}

// ----------------------------------------------------------------------- 지도

/**
 * 좌표 한가운데에 놓이는 원형 마커.
 * 기본 마커는 핀 모양이라 아래쪽 끝이 좌표에 맞는데, 원은 가운데가 맞아야 한다.
 */
internal fun dotMarker(
    position: LatLng,
    iconRes: Int,
    sizeDp: Float,
    density: Float
): Marker = Marker().apply {
    this.position = position
    icon = overlayImageOf(iconRes)
    width = (density * sizeDp).toInt()
    height = (density * sizeDp).toInt()
    anchor = android.graphics.PointF(0.5f, 0.5f)
}

/**
 * 같은 아이콘은 OverlayImage 하나를 여러 마커가 함께 쓴다.
 * 마커마다 새로 만들면 주변 정류장 수백 개가 각자 비트맵을 들고 있게 된다. (네이버 SDK 권장 방식)
 */
private val overlayImages = HashMap<Int, OverlayImage>()
private fun overlayImageOf(iconRes: Int): OverlayImage =
    overlayImages.getOrPut(iconRes) { OverlayImage.fromResource(iconRes) }

/**
 * 지도 스냅샷을 언제 떠야 하는지 알려 주는 신호.
 * 지도 그림이 바뀔 때(카메라 이동 · 오버레이 · 야간 모드) [markDirty] 가 불리고,
 * 스냅샷 루프는 그 뒤 잠깐만 돌다가 다시 잠든다.
 */
private class SnapshotTrigger {
    @Volatile var changedAt = 0L
    @Volatile var cameraMovedAt = 0L
    val wake = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
    fun markDirty() {
        changedAt = android.os.SystemClock.uptimeMillis()
        wake.trySend(Unit)
    }
}

/**
 * 타일이 채워지기 전 지도 바탕색.
 *
 * SDK의 기본 야간 바탕은 앱 스플래시보다 훨씬 밝은 회색이라, 스플래시가 사라지는
 * 320ms 동안 한 번 번쩍였다. 앱 창·다크 스플래시와 같은 #17171A를 직접 준다.
 * 지도 타일이 준비된 뒤에는 이 색이 보이지 않는다.
 */
private fun mapBackgroundColor(night: Boolean): Int =
    if (night) android.graphics.Color.rgb(23, 23, 26)
    else android.graphics.Color.rgb(231, 236, 228)

/** MapView 안에서 실제로 지도를 그리는 TextureView 를 찾는다 */
internal fun findTextureView(view: View): TextureView? = when (view) {
    is TextureView -> view
    is ViewGroup -> (0 until view.childCount)
        .asSequence()
        .mapNotNull { findTextureView(view.getChildAt(it)) }
        .firstOrNull()
    else -> null
}

/**
 * 버스 마커. [버스 아이콘 원] + [차량번호 칩] 이 붙어 있는 모양이라
 * 레이아웃을 그려서 이미지로 만든 뒤, 원의 한가운데가 좌표에 오도록 앵커를 잡는다.
 */
@android.annotation.SuppressLint("InflateParams") // 마커는 부모 없이 그려서 이미지로만 쓴다
internal fun busMarker(
    context: Context,
    vehicleNo: String,
    position: LatLng,
    density: Float,
    kind: RouteKind?
): Marker {
    val view = LayoutInflater.from(context).inflate(R.layout.map_bus_marker, null, false)
    view.findViewById<TextView>(R.id.vehicle_no).apply {
        text = vehicleNo
        // 칩 배경도 노선 유형 색으로
        backgroundTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(context, kind?.colorRes ?: R.color.route_badge)
        )
    }
    kind?.let {
        view.findViewById<android.widget.ImageView>(R.id.bus_circle)
            .setImageResource(it.busMarkerRes)
    }
    view.measure(
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )
    val totalWidth = view.measuredWidth.coerceAtLeast(1)
    val circleCenter = density * Tokens.Map.busDotSize / 2f

    return Marker().apply {
        this.position = position
        icon = OverlayImage.fromView(view)
        anchor = android.graphics.PointF(circleCenter / totalWidth, 0.5f)
    }
}

@Composable
internal fun NaverMapPane(
    keyId: String,
    stop: BusStop,
    routeStops: List<RouteStop>,
    buses: List<BusLocation>,
    routeKind: RouteKind?,
    nearbyStops: List<BusStop>,
    onStopPick: (BusStop) -> Unit,
    sheetVisible: Boolean,
    /** 지도가 실제로 보이는 중인지. 가려져 있으면 스냅샷 뜨는 일을 쉰다 */
    active: Boolean,
    /** 시스템이 다크 모드면 지도도 야간 모드로 */
    nightMode: Boolean,
    moveToMyLocation: Int,
    backdrop: MapBackdrop,
    onCameraIdle: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
    /** 초기 위치를 정한 뒤 그 위치의 지도 렌더링까지 기다린다. */
    initialLocationSettled: Boolean = true,
    /** 지도 또는 로딩 실패 안내가 표시될 준비를 마쳤을 때. */
    onReady: () -> Unit = {},
    /** 현위치로 옮기기를 한 번 시도한 뒤 (성공 · 실패 모두) */
    onMyLocationSettled: () -> Unit = {}
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val lifecycleOwner = LocalLifecycleOwner.current
    var authError by remember { mutableStateOf<String?>(null) }
    var startupError by remember { mutableStateOf<String?>(null) }
    /** 지도가 마지막으로 '모든 데이터를 다 그림(fully)' 을 알린 시각 (0 = 아직 한 번도) */
    var lastFullyRenderedAt by remember { mutableStateOf(0L) }
    var startupFinished by remember { mutableStateOf(false) }
    var naverMap by remember { mutableStateOf<NaverMap?>(null) }
    // 오버레이를 용도별로 나눠 둔다.
    // 버스 위치만 바뀔 때 정류장 마커 수백 개를 다시 만들지 않기 위해서다.
    val routeOverlays = remember { mutableListOf<Overlay>() }
    /** 버스 마커는 차량별로 들고 있다가 30초마다 위치만 옮긴다 (매번 새로 그리지 않음) */
    val busOverlays = remember { mutableMapOf<String, Marker>() }
    val nearbyOverlays = remember { mutableMapOf<String, Overlay>() }
    val snapshot = remember { SnapshotTrigger() }
    val latestOnReady by rememberUpdatedState(onReady)
    val latestOnCameraIdle by rememberUpdatedState(onCameraIdle)
    val latestOnMyLocationSettled by rememberUpdatedState(onMyLocationSettled)

    val mapView = remember {
        NaverMapSdk.getInstance(context).setClient(NaverMapSdk.NcpKeyClient(keyId))
        NaverMapSdk.getInstance(context).setOnAuthFailedListener { e ->
            authError = "지도 인증 실패 (${e.message})"
        }
        // 유리 표면 뒤에 깔 스냅샷을 뜨려면 지도가 TextureView 로 그려져야 한다.
        // (기본값인 GLSurfaceView 는 화면을 읽어 올 수 없다)
        // 다크 모드면 처음부터 야간 지도 + 어두운 바탕으로 만든다.
        // (만든 뒤에 야간 모드를 켜면 밝은 지도·밝은 바탕이 한두 프레임 그려졌다가 바뀌어 깜빡인다)
        MapView(
            context,
            NaverMapOptions()
                .useTextureView(true)
                .nightModeEnabled(nightMode)
                .backgroundColor(mapBackgroundColor(nightMode))
        ).apply { onCreate(null) }
    }

    // 블러용 스냅샷은 잠들 수 있으므로 시작 완료 판정에 사용하지 않는다.
    // 인증 실패/장시간 로딩은 미완성 지도 대신 재시도 가능한 안내 화면으로 전환한다.
    LaunchedEffect(mapView, startupFinished) {
        if (startupFinished) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(15.seconds)
            startupError = "지도를 불러오지 못했어요. 인터넷 연결을 확인하고 다시 시도해 주세요."
        }
    }
    LaunchedEffect(authError) {
        if (authError != null) startupError = "지도 인증에 실패했어요. 설정에서 지도 API 키를 확인해 주세요."
    }
    // 시작 완료 판정.
    // 예전에는 '다 그려짐' 플래그를 카메라 이동 · 야간 모드 적용 · 현위치 이동 때 false 로 되돌렸는데,
    // 되돌린 뒤 지도가 다시 그릴 게 없으면(이미 받은 타일 · 같은 모드) 렌더 알림이 다시 오지 않아
    // 플래그가 영영 false 로 남았다 → 위젯에서 열면(시트 여백 이동) 스플래시에 갇혔다.
    // 이제는 되돌리지 않고 '시각'으로 판단한다:
    //  1) 지도가 한 번이라도 다 그려졌고
    //  2) 시작 위치가 정해진 뒤(위젯 정류장 / 내 위치) 다시 다 그려지면 → 완료.
    //     단, 그 뒤 새 렌더 알림이 안 오더라도 1.2초 뒤에는 완료로 본다.
    LaunchedEffect(initialLocationSettled, startupError) {
        if (startupFinished) return@LaunchedEffect
        if (startupError == null) {
            if (!initialLocationSettled) return@LaunchedEffect
            val settledAt = android.os.SystemClock.uptimeMillis()
            kotlinx.coroutines.withTimeoutOrNull(1_200.milliseconds) {
                androidx.compose.runtime.snapshotFlow { lastFullyRenderedAt }.first { it >= settledAt }
            }
            // 지도가 아직 한 번도 안 그려졌으면 그려질 때까지 (못 그리면 15초 뒤 오류 화면)
            androidx.compose.runtime.snapshotFlow { lastFullyRenderedAt }.first { it > 0L }
        }
        // 렌더링 콜백/오류 상태 변경이 실제 화면에 반영될 프레임을 허용한다.
        repeat(2) { androidx.compose.runtime.withFrameNanos { } }
        startupFinished = true
        latestOnReady()
    }

    // 지도 화면을 축소해서 떠 온다 — 유리 표면들이 이걸 잘라서 블러한다.
    //
    // 배터리: 예전에는 0.3초마다 끝없이(앱이 뒤로 가 있을 때도) 새 비트맵을 만들어 떴다.
    // 이제는
    //  - 지도 그림이 바뀔 때만 뜬다 (카메라 이동 · 오버레이 · 야간 모드 · 다시 보일 때),
    //    바뀐 뒤 타일이 다 그려질 때까지 잠깐(backdropSettleMs) 더 뜨고 잠든다.
    //  - 앱이 화면에 있을 때(RESUMED)만 돈다.
    //  - 비트맵 두 장을 번갈아 재사용한다 (매번 새로 할당하지 않음).
    // 블러 모양 · 부드러움은 그대로다 (움직이는 동안의 간격은 같음).
    LaunchedEffect(mapView, active) {
        if (!active) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val buffers = arrayOfNulls<android.graphics.Bitmap>(2)
            var front = 0
            snapshot.markDirty()
            while (true) {
                val now = android.os.SystemClock.uptimeMillis()
                if (now - snapshot.changedAt > Tokens.Map.backdropSettleMs) {
                    snapshot.wake.receive()   // 다음 변화까지 잠든다
                    continue
                }
                val texture = findTextureView(mapView)
                if (texture != null && texture.isAvailable && texture.width > 0 && texture.height > 0) {
                    val scale = Tokens.Map.backdropDownscale
                    val w = (texture.width / scale).coerceAtLeast(1)
                    val h = (texture.height / scale).coerceAtLeast(1)
                    val back = 1 - front
                    val bmp = buffers[back]?.takeIf { it.width == w && it.height == h }
                        ?: createBitmap(w, h)
                            .also { buffers[back] = it }
                    val captured = runCatching { texture.getBitmap(bmp) }.getOrNull() != null
                    // 지도가 아직 그려지지 않은 순간에는 텅 빈(투명) 프레임이 넘어온다.
                    // 그대로 깔면 유리 표면이 한 번씩 번쩍이므로 버리고 직전 프레임을 유지한다.
                    val filled = captured && (
                        bmp[w / 2, h / 2] or
                            bmp[w / 4, h / 4] or
                            bmp[w * 3 / 4, h * 3 / 4]
                        ) != 0
                    if (filled) {
                        front = back
                        backdrop.image = bmp.asImageBitmap()
                        backdrop.windowSize = IntSize(texture.width, texture.height)
                    }
                }
                val moving = now - snapshot.cameraMovedAt < 300
                delay((if (moving) Tokens.Map.backdropIntervalMs else Tokens.Map.backdropIdleIntervalMs).milliseconds)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(mapView) {
        var disposed = false
        var attachedMap: NaverMap? = null
        val renderedListener = NaverMap.OnMapRenderedListener { fully, stable ->
            // fully는 모든 지도 데이터가 그려졌다는 뜻이다. stable까지 기다리면
            // 위치 표시 등의 애니메이션 때문에 시작 완료가 늦어질 수 있다.
            if (fully) lastFullyRenderedAt = android.os.SystemClock.uptimeMillis()
            if (fully && stable) snapshot.markDirty()
        }
        val cameraListener = NaverMap.OnCameraChangeListener { _, _ ->
            snapshot.cameraMovedAt = android.os.SystemClock.uptimeMillis()
            snapshot.markDirty()
        }
        val idleListener = NaverMap.OnCameraIdleListener {
            attachedMap?.cameraPosition?.target?.let { c ->
                latestOnCameraIdle(c.latitude, c.longitude)
            }
        }
        mapView.getMapAsync { map ->
            if (disposed) return@getMapAsync
            attachedMap = map
            map.uiSettings.apply {
                isZoomControlEnabled = false   // +/- 버튼 제거
                isScaleBarEnabled = false      // 축척 막대 제거
                isCompassEnabled = false       // 나침반 제거
            }
            // 위쪽은 검색바, 아래쪽은 시트가 덮는다.
            // 이 영역을 빼고 카메라를 잡아야 마커가 가려지지 않고,
            // 네이버 로고도 시트 위로 올라온다. (로고는 가리면 안 된다)
            map.addOnCameraChangeListener(cameraListener)
            // 카메라가 멈추면 그 언저리 정류장을 불러 온다 (메인 지도)
            map.addOnCameraIdleListener(idleListener)
            map.addOnMapRenderedListener(renderedListener)

            naverMap = map
        }
        onDispose {
            disposed = true
            attachedMap?.let { map ->
                map.removeOnMapRenderedListener(renderedListener)
                map.removeOnCameraChangeListener(cameraListener)
                map.removeOnCameraIdleListener(idleListener)
            }
        }
    }

    // 시스템 다크 모드에 맞춰 지도도 야간 모드로.
    // (기본 지도 유형은 Basic 이라 야간 모드를 지원한다)
    LaunchedEffect(naverMap, nightMode) {
        val map = naverMap ?: return@LaunchedEffect
        map.isNightModeEnabled = nightMode
        map.setBackgroundColor(mapBackgroundColor(nightMode))
        snapshot.markDirty()
    }

    // 위쪽은 버튼, 아래쪽은 시트가 덮는다.
    // 이 영역을 빼고 카메라를 잡아야 마커가 가려지지 않고,
    // 네이버 로고도 시트 위로 올라온다. (로고는 가리면 안 된다)
    LaunchedEffect(naverMap, sheetVisible) {
        val map = naverMap ?: return@LaunchedEffect
        map.setContentPadding(
            0,
            (density * Tokens.Map.contentPaddingTop).toInt(),
            0,
            if (sheetVisible) {
                (density * (Tokens.Sheet.peekHeight.value + Tokens.Map.contentPaddingBottomExtra)).toInt()
            } else {
                (density * Tokens.Map.contentPaddingBottomExtra).toInt()
            }
        )
    }

    // 현위치로 카메라를 옮긴다 (앱을 켤 때 1번 + 현위치 버튼을 누를 때마다).
    // 첫 이동은 기본 위치(서울시청)에서 날아가는 게 보이지 않도록 애니메이션 없이 바로 옮긴다.
    var movedToMyLocationOnce by remember { mutableStateOf(false) }
    LaunchedEffect(moveToMyLocation, naverMap) {
        if (moveToMyLocation == 0) return@LaunchedEffect
        val map = naverMap ?: return@LaunchedEffect
        if (ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            latestOnMyLocationSettled()
            return@LaunchedEffect
        }

        val here = currentLocationOrNull(context)
        if (here != null) {
            val target = LatLng(here.latitude, here.longitude)
            map.locationOverlay.isVisible = true
            map.locationOverlay.position = target
            val update = CameraUpdate.scrollAndZoomTo(target, Tokens.Map.STOP_ZOOM)
            map.moveCamera(
                if (movedToMyLocationOnce) update.animate(com.naver.maps.map.CameraAnimation.Easing)
                else update
            )
            movedToMyLocationOnce = true
        } else {
            Toast.makeText(context, "현재 위치를 알 수 없습니다", Toast.LENGTH_SHORT).show()
        }
        latestOnMyLocationSettled()
    }

    // 1) 메인 지도의 주변 정류장 — 새로 들어온 것만 더하고 사라진 것만 지운다
    LaunchedEffect(naverMap, nearbyStops) {
        val map = naverMap ?: return@LaunchedEffect
        val wanted = nearbyStops.associateBy { it.nodeId }

        nearbyOverlays.keys.toList().forEach { id ->
            if (!wanted.containsKey(id)) {
                nearbyOverlays.remove(id)?.map = null
            }
        }
        wanted.forEach { (id, bs) ->
            if (nearbyOverlays.containsKey(id)) return@forEach
            val la = bs.gpsLat ?: return@forEach
            val ln = bs.gpsLng ?: return@forEach
            nearbyOverlays[id] = dotMarker(
                position = LatLng(la, ln),
                iconRes = R.drawable.map_stop_marker,
                sizeDp = Tokens.Map.stopMarkerSize,
                density = density
            ).apply {
                // 마커는 화면 픽셀 크기라 확대·축소해도 크기가 그대로다.
                // 너무 줄이면 화면이 마커로 덮이므로 일정 줌 이상에서만 보인다.
                minZoom = Tokens.Map.STOP_MARKER_MIN_ZOOM
                isMinZoomInclusive = true
                setOnClickListener {
                    onStopPick(bs)
                    true
                }
                this.map = map
            }
        }
        snapshot.markDirty()
    }

    // 2) 노선 경로 · 경유 정류장 · 지금 보고 있는 정류장
    // nightMode 도 키에 넣어 라이트/다크 전환 때 정류장 이름 색을 다시 칠한다
    LaunchedEffect(naverMap, stop.nodeId, stop.gpsLat, routeStops, routeKind, nightMode) {
        val map = naverMap ?: return@LaunchedEffect

        routeOverlays.forEach { it.map = null }
        routeOverlays.clear()

        val path = routeStops.mapNotNull { rs ->
            val la = rs.lat
            val ln = rs.lng
            if (la != null && ln != null) LatLng(la, ln) else null
        }

        if (path.size >= 2) {
            routeOverlays += PathOverlay().apply {
                coords = path
                width = (density * Tokens.Map.pathWidth.value).toInt()
                // 경로선도 노선 유형 색을 따른다
                color = ContextCompat.getColor(
                    context,
                    routeKind?.colorRes ?: R.color.map_route_path
                )
                outlineWidth = 0
                this.map = map
            }

            // 경유 정류장 — 노선색 작은 점 (세부정보창의 정류장 점과 같은 모양)
            routeStops.forEach { rs ->
                val la = rs.lat ?: return@forEach
                val ln = rs.lng ?: return@forEach
                routeOverlays += dotMarker(
                    position = LatLng(la, ln),
                    iconRes = routeKind?.stopDotRes ?: R.drawable.map_route_stop_dot,
                    sizeDp = Tokens.Map.routeStopDotSize,
                    density = density
                ).apply {
                    // 축소하면 점이 너무 많아 정신없다. 일정 줌 이상에서만 보인다
                    minZoom = Tokens.Map.ROUTE_STOP_MIN_ZOOM
                    isMinZoomInclusive = true
                    this.map = map
                }
            }
        }

        // 지금 보고 있는 정류장
        val sLat = stop.gpsLat
        val sLng = stop.gpsLng
        if (sLat != null && sLng != null) {
            routeOverlays += dotMarker(
                position = LatLng(sLat, sLng),
                iconRes = R.drawable.map_stop_marker,
                sizeDp = Tokens.Map.stopMarkerSize,
                density = density
            ).apply {
                captionText = stop.nodeName
                captionTextSize = Tokens.Map.captionText
                // 정류장 이름 — 라이트: 검은 글씨 + 흰 테두리 / 다크: 흰 글씨, 테두리 없음
                if (nightMode) {
                    captionColor = android.graphics.Color.WHITE
                    captionHaloColor = android.graphics.Color.TRANSPARENT
                } else {
                    captionColor = ContextCompat.getColor(context, R.color.widget_text_light)
                    captionHaloColor = android.graphics.Color.WHITE
                }
                this.map = map
            }
        }
        snapshot.markDirty()
    }

    // 3) 운행 중인 버스 — 30초마다 이것만 갱신한다.
    // 이미 있는 차량은 위치만 옮기고, 새로 나타난 차량만 마커(레이아웃 → 비트맵)를 만든다.
    LaunchedEffect(naverMap, buses, routeKind) {
        val map = naverMap ?: return@LaunchedEffect
        val wanted = buses.mapNotNull { bus ->
            val la = bus.lat ?: return@mapNotNull null
            val ln = bus.lng ?: return@mapNotNull null
            val key = "${routeKind?.name}:" + bus.vehicleNo.ifBlank { "$la,$ln" }
            key to (bus to LatLng(la, ln))
        }.toMap()
        busOverlays.keys.toList().forEach { key ->
            if (key !in wanted) busOverlays.remove(key)?.map = null
        }
        wanted.forEach { (key, value) ->
            val (bus, position) = value
            val existing = busOverlays[key]
            if (existing != null) {
                existing.position = position
            } else {
                busOverlays[key] = busMarker(context, bus.shortVehicleNo, position, density, routeKind)
                    .apply { this.map = map }
            }
        }
        snapshot.markDirty()
    }

    // 카메라 이동은 따로 둔다.
    // 주변 정류장이 늘어날 때마다 화면이 튀면 안 되기 때문이다.
    LaunchedEffect(naverMap, stop.nodeId, routeStops.size) {
        val map = naverMap ?: return@LaunchedEffect
        val path = routeStops.mapNotNull { rs ->
            val la = rs.lat
            val ln = rs.lng
            if (la != null && ln != null) LatLng(la, ln) else null
        }
        val sLat = stop.gpsLat
        val sLng = stop.gpsLng
        when {
            path.size >= 2 -> {
                val bounds = LatLngBounds.Builder().apply { path.forEach { include(it) } }.build()
                map.moveCamera(
                    CameraUpdate.fitBounds(bounds, (density * Tokens.Map.fitPadding.value).toInt())
                )
            }

            sLat != null && sLng != null ->
                map.moveCamera(CameraUpdate.scrollAndZoomTo(LatLng(sLat, sLng), Tokens.Map.STOP_ZOOM))
        }
    }

    Box(modifier = modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        startupError?.let { message ->
            MapStartupErrorPane(
                message = message,
                onRetry = { (context as? android.app.Activity)?.recreate() },
                onOpenSettings = {
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                }
            )
        }
    }
}

/**
 * 지금 위치. 2분 안에 잡힌 마지막 위치가 있으면 그걸 바로 쓰고,
 * 없으면 새로 한 번 받아 온다(최대 5초). 그래도 없으면 오래된 마지막 위치라도 돌려준다.
 * 앱을 막 켰을 때는 마지막 위치가 비어 있는 경우가 많아서 새로 받는 단계가 필요하다.
 */
@android.annotation.SuppressLint("MissingPermission")
private suspend fun currentLocationOrNull(context: Context): android.location.Location? {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val enabled = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
    val last = enabled
        .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }
    if (last != null && System.currentTimeMillis() - last.time < 2 * 60_000L) return last

    // 빠른 순서: fused(구글 위치) → 네트워크 → GPS
    val provider = listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        .firstOrNull { it in enabled } ?: return last
    val fresh = kotlinx.coroutines.withTimeoutOrNull(5.seconds) {
        kotlinx.coroutines.suspendCancellableCoroutine<android.location.Location?> { cont ->
            val signal = android.os.CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            runCatching {
                androidx.core.location.LocationManagerCompat.getCurrentLocation(
                    lm, provider, signal, ContextCompat.getMainExecutor(context)
                ) { location -> if (cont.isActive) cont.resumeWith(Result.success(location)) }
            }.onFailure { if (cont.isActive) cont.resumeWith(Result.success(null)) }
        }
    }
    return fresh ?: last
}
