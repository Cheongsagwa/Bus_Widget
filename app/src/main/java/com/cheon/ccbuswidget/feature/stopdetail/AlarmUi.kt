package com.cheon.ccbuswidget.feature.stopdetail
// 승하차 알람 — 위쪽 배너 · 알람 아이콘 · 켜고 끄기(권한 요청 포함)
import android.Manifest
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cheon.ccbuswidget.R
import com.cheon.ccbuswidget.alarm.AlarmService
import com.cheon.ccbuswidget.alarm.AlarmText
import com.cheon.ccbuswidget.alarm.BusAlarm
import com.cheon.ccbuswidget.data.model.RouteKind
import com.cheon.ccbuswidget.data.model.RouteStop
import com.cheon.ccbuswidget.ui.theme.Tokens

/**
 * 알람을 켜고 끈다. 켤 때 필요한 권한(알림 · 하차는 위치)을 먼저 묻는다.
 * 화면 어디서나 [LocalAlarmController] 로 꺼내 쓴다.
 */
@Stable
internal class AlarmController(
    private val context: Context,
    private val ask: (Array<String>, (Map<String, Boolean>) -> Unit) -> Unit
) {
    /** 이 정류장에서 이 노선 승차 알람을 켜거나(다른 알람은 바뀐다) 끈다 */
    fun toggleBoarding(active: BusAlarm?, stopNodeId: String, stopName: String,
                       routeId: String, routeNo: String, routeType: String?) {
        if (active?.isBoardingAt(stopNodeId, routeId) == true) {
            cancel()
            return
        }
        val alarm = BusAlarm(BusAlarm.Type.BOARD, routeId, routeNo, routeType, stopNodeId, stopName)
        ask(notificationPermission()) {
            AlarmService.start(context, alarm)
            toast("$stopName · ${routeNo}번 승차 알람을 켰어요")
        }
    }

    /**
     * 하차 알람 — [stops] 에서 [fromIndex](탄 정류장)부터 [toIndex](내릴 정류장)까지 휴대폰 위치로 따라간다.
     */
    fun startAlighting(routeId: String, routeNo: String, routeType: String?,
                       stops: List<RouteStop>, fromIndex: Int, toIndex: Int) {
        val part = stops.subList(fromIndex, toIndex + 1).mapNotNull { s ->
            val lat = s.lat ?: return@mapNotNull null
            val lng = s.lng ?: return@mapNotNull null
            BusAlarm.Point(s.nodeId, s.nodeName, lat, lng)
        }
        if (part.size < 2) {
            toast("정류장 위치 정보가 없어 하차 알람을 켤 수 없어요")
            return
        }
        val target = stops[toIndex]
        val alarm = BusAlarm(
            BusAlarm.Type.ALIGHT, routeId, routeNo, routeType,
            stopNodeId = target.nodeId, stopName = target.nodeName,
            stops = part, fromNodeId = stops[fromIndex].nodeId
        )
        val perms = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION) +
            notificationPermission()
        ask(perms) {
            if (AlarmService.hasLocationPermission(context)) {
                AlarmService.start(context, alarm)
                val fine = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                // '대략적인 위치'만 허용하면 수백 m 단위라 정류장을 따라갈 수 없다
                toast(if (fine) "${target.nodeName} 하차 알람을 켰어요"
                    else "정확한 위치를 허용해야 하차 알람이 정류장을 따라갈 수 있어요 (설정 > 앱 > 권한 > 위치)")
            } else {
                toast("하차 알람은 위치 권한이 있어야 해요")
            }
        }
    }

    fun cancel() {
        AlarmService.cancel(context)
    }

    private fun notificationPermission(): Array<String> =
        if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()

    private fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

internal val LocalAlarmController = staticCompositionLocalOf<AlarmController?> { null }

/**
 * 알람 배너가 펼친 창과 주고받는 것.
 * - [blur]/[blurAlpha]: 펼친 창 내용을 배너 뒤에 실시간으로 흐리게 까는 바탕
 */
@Stable
internal class BannerSlot {
    var blur by mutableStateOf<BlurBehindState?>(null)
    var blurAlpha: State<Float> by mutableStateOf<State<Float>>(mutableFloatStateOf(0f))
}

internal val LocalBannerSlot = staticCompositionLocalOf<BannerSlot?> { null }

@Composable
internal fun rememberAlarmController(): AlarmController {
    val context = LocalContext.current
    // 권한 창이 닫힌 뒤 이어서 할 일
    val pending = remember { arrayOfNulls<(Map<String, Boolean>) -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        pending[0]?.invoke(result)
        pending[0] = null
    }
    val latestLauncher by rememberUpdatedState(launcher)
    return remember(context) {
        AlarmController(context) { perms, then ->
            if (perms.isEmpty()) {
                then(emptyMap())
            } else {
                pending[0] = then
                latestLauncher.launch(perms)
            }
        }
    }
}

/** 알람 아이콘 — 꺼져 있으면 테두리만, 켜지면 노란색 (Figma alarm_outline / alarm) */
@Composable
internal fun AlarmIcon(
    on: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = Tokens.Alarm.iconSize,
    /** 꺼진 아이콘의 진하기 (Figma: 노선 칸 55%, 하차 설정 목록 50%) */
    offAlpha: Float = 1f,
    enabled: Boolean = true,
    onClick: (() -> Unit)?
) {
    Icon(
        painter = painterResource(if (on) R.drawable.ic_alarm_on else R.drawable.ic_alarm),
        contentDescription = if (on) "알람 끄기" else "알람 켜기",
        tint = if (on) Color.Unspecified
        else colorResource(R.color.glass_on_surface).copy(alpha = offAlpha),
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled) { onClick() } else Modifier)
    )
}

/**
 * 위쪽 알람 배너 (Figma Toast 96:2208) — 알람이 켜져 있으면 어느 화면에서나 뜬다.
 * (버스 원) 300 탑승중 (6개 남음) / 다음정류장 하차   (해제)
 */
@Composable
internal fun AlarmBanner(
    alarm: BusAlarm,
    remaining: Int?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    /** 펼친 창 위에서는 창 내용을 뒤에 흐리게 깐다 (없으면 지도 블러) */
    sheetBlur: BlurBehindState? = null,
    sheetBlurAlpha: State<Float>? = null,
    /** 배너를 누르면 (해제 버튼 말고) */
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(Tokens.Alarm.bannerCorner)
    val onSurface = colorResource(R.color.glass_on_surface)
    val useSheet = sheetBlur != null && sheetBlurAlpha != null
    GlassSurface(
        shape = shape,
        tint = glassColor(),
        // 펼친 창이 배너 뒤를 다 덮으면 지도 스냅샷(멈춰 있음)은 그리지 않는다
        drawBackdrop = !useSheet || sheetBlurAlpha.value < 1f,
        modifier = modifier
            .then(if (useSheet) Modifier.blurBehindHole(sheetBlur, "alarm_banner", sheetBlurAlpha) else Modifier)
            .size(Tokens.Alarm.bannerWidth, Tokens.Alarm.bannerHeight)
            .glassShadow(shape)
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Row(
            Modifier.fillMaxSize().padding(
                horizontal = Tokens.Alarm.bannerPaddingHorizontal,
                vertical = Tokens.Alarm.bannerPaddingVertical
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(Tokens.Alarm.bannerIconSize)
                    .background(colorResource(RouteKind.of(alarm.routeNo, alarm.routeType).colorRes), CircleShape)
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.ic_alarm_bus), null,
                    tint = Color.White,
                    modifier = Modifier.size(Tokens.Alarm.bannerBusIconSize)
                )
            }
            Spacer(Modifier.width(Tokens.Alarm.bannerGap))
            Text(
                AlarmText.title(alarm, remaining) + "\n" + AlarmText.subtitle(alarm, remaining),
                modifier = Modifier.weight(1f),
                color = onSurface,
                fontSize = Tokens.Alarm.bannerText,
                lineHeight = Tokens.Alarm.bannerLine,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(Tokens.Alarm.bannerGap))
            val cancelShape = RoundedCornerShape(Tokens.Alarm.cancelCorner)
            Box(
                Modifier.clip(cancelShape)
                    .background(Color.White.copy(alpha = if (isSystemInDarkTheme()) 0.18f else 0.5f), cancelShape)
                    .clickable { onCancel() }
                    .padding(
                        horizontal = Tokens.Alarm.cancelPaddingHorizontal,
                        vertical = Tokens.Alarm.cancelPaddingVertical
                    )
            ) {
                Text(
                    "해제",
                    color = onSurface,
                    fontSize = Tokens.Alarm.bannerText,
                    lineHeight = Tokens.Alarm.bannerLine,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}
