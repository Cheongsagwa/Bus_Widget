package com.cheon.ccbuswidget.feature.stopdetail
//Activitiy와 초기 화면 진입
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import com.cheon.ccbuswidget.data.model.BusStop
import com.cheon.ccbuswidget.feature.settings.SettingsActivity
import com.cheon.ccbuswidget.ui.theme.CcBusTheme

/**
 * 위젯을 눌렀을 때 열리는 화면.
 *
 *  - 지도 위 검색바로 다른 정류장을 찾을 수 있고
 *  - 하단 시트에서 그 정류장에 오는 모든 버스의 도착 예정 시간을 보여주며
 *  - 노선을 누르면 그 노선의 전체 경유 정류장과 버스들의 현재 위치를 보여준다.
 */
class StopDetailActivity : ComponentActivity() {

    companion object {
        const val EXTRA_NODE_ID = "node_id"
        const val EXTRA_NODE_NAME = "node_name"
        const val EXTRA_LAT = "lat"
        const val EXTRA_LNG = "lng"

        /** 준비 신호가 오지 않아도 이 시간이 지나면 스플래시를 걷는다 */
        private const val SPLASH_TIMEOUT_MS = 4_000L

        fun intent(
            context: Context,
            nodeId: String,
            nodeName: String,
            lat: Double?,
            lng: Double?,
            appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
        ): Intent = Intent(context, StopDetailActivity::class.java).apply {
            putExtra(EXTRA_NODE_ID, nodeId)
            putExtra(EXTRA_NODE_NAME, nodeName)
            if (lat != null && lng != null) {
                putExtra(EXTRA_LAT, lat)
                putExtra(EXTRA_LNG, lng)
            }
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
    }

    /**
     * 매니페스트의 configChanges="uiMode" 로 라이트/다크 전환 때 Activity 가 다시 만들어지지 않는다.
     * 시스템 바 배경(3버튼 내비게이션의 스크림 등)만 새 테마에 맞춰 다시 잡는다.
     * 상태바 아이콘 색은 MapScreen 의 LaunchedEffect(darkTheme) 가 이어서 맞춘다.
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        enableEdgeToEdge()
        applySystemBarIconColors()
    }

    /**
     * 상태바 · 내비게이션 바 아이콘 색을 지금 모드에 맞춘다 (라이트: 검정 / 다크: 흰색).
     * 스플래시가 걷힐 때 시스템이 바 아이콘 색을 되돌려 놓기 때문에, 걷힌 직후에도 한 번 더 부른다.
     * (예전에는 라이트 모드에서 앱을 켜면 상태바 아이콘이 흰색으로 남았다)
     */
    private fun applySystemBarIconColors() {
        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
    }

    private var ready = false
    private var splashProvider: SplashScreenViewProvider? = null
    private var splashExitStarted = false

    private fun finishSplashIfReady() {
        val provider = splashProvider ?: return
        if (!ready || splashExitStarted || isDestroyed) return
        splashExitStarted = true
        val duration = 320L
        // 위젯 등 런처가 아닌 곳에서 열면 안드로이드 12+ 는 아이콘 없는 빈 스플래시를 띄운다.
        // 그때 iconView 를 꺼내면 앱이 바로 꺼졌다(NullPointerException). 있을 때만 키운다.
        runCatching { provider.iconView }.getOrNull()?.animate()
            ?.scaleX(1.15f)?.scaleY(1.15f)
            ?.setDuration(duration)
            ?.start()
        provider.view.animate()
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction {
                provider.remove()
                splashProvider = null
                applySystemBarIconColors()
            }
            .start()
    }

    override fun onDestroy() {
        splashProvider?.let { provider ->
            runCatching { provider.iconView }.getOrNull()?.animate()?.cancel()
            provider.view.animate().withEndAction(null).cancel()
            provider.remove()
        }
        splashProvider = null
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // ---- 스플래시 (res/values/themes.xml 의 Theme.CcBusWidget.Starting)
        // 지도 SDK 가 뜨는 동안 앱 로고 화면을 보여 주고, 지도가 실제로 그려지면 페이드로 넘어간다.
        // 다크 모드에서 흰 화면이 한 번 번쩍이던 것도 이걸로 가려진다.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // KeepOnScreenCondition은 Activity의 그리기도 막는다. 지도 렌더링을 기다릴 때
        // 사용하면 서로 기다리게 된다. 스플래시 뷰만 남기고 뒤의 지도는 계속 그린다.
        splash.setOnExitAnimationListener { provider ->
            splashProvider = provider
            provider.view.isClickable = true
            finishSplashIfReady()
        }

        // 준비 신호가 어떤 이유로든(지도 SDK 이상 · 예상 못 한 흐름) 오지 않아도
        // 스플래시에 갇히지 않도록 마지막 안전장치. 보통은 그 전에 onReady 로 걷힌다.
        window.decorView.postDelayed({
            ready = true
            finishSplashIfReady()
        }, SPLASH_TIMEOUT_MS)

        // 상태바 · 내비게이션 바 뒤까지 화면을 채운다 (아래에서 인셋만큼 여백을 준다)
        enableEdgeToEdge()
        applySystemBarIconColors()

        // 런처에서는 지도부터. 위젯이 명시적으로 보낸 정류장만 바로 연다.
        val nodeId = intent.getStringExtra(EXTRA_NODE_ID).orEmpty()
        val nodeName = intent.getStringExtra(EXTRA_NODE_NAME).orEmpty()
        val lat = intent.takeIf { it.hasExtra(EXTRA_LAT) }?.getDoubleExtra(EXTRA_LAT, 0.0)
        val lng = intent.takeIf { it.hasExtra(EXTRA_LNG) }?.getDoubleExtra(EXTRA_LNG, 0.0)
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )

        setContent {
            CcBusTheme {
                MapScreen(
                    initialStop = BusStop(nodeId, nodeName, gpsLat = lat, gpsLng = lng),
                    appWidgetId = widgetId,
                    onOpenSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    },
                    onReady = {
                        ready = true
                        finishSplashIfReady()
                    }
                )
            }
        }
    }
}
