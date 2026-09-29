package com.cheon.ccbuswidget.feature.widgetconfig

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import com.cheon.ccbuswidget.R
import com.cheon.ccbuswidget.data.api.TagoApi
import com.cheon.ccbuswidget.data.local.WidgetStore
import com.cheon.ccbuswidget.data.model.BusRoute
import com.cheon.ccbuswidget.data.model.BusStop
import com.cheon.ccbuswidget.data.model.RouteKind
import com.cheon.ccbuswidget.data.model.WidgetConfig
import com.cheon.ccbuswidget.ui.oneui.OneUiButton
import com.cheon.ccbuswidget.ui.oneui.OneUiCard
import com.cheon.ccbuswidget.ui.oneui.OneUiChip
import com.cheon.ccbuswidget.ui.oneui.OneUiDivider
import com.cheon.ccbuswidget.ui.oneui.OneUiFootnote
import com.cheon.ccbuswidget.ui.oneui.OneUiGroupLabel
import com.cheon.ccbuswidget.ui.oneui.OneUiRow
import com.cheon.ccbuswidget.ui.oneui.OneUiScreen
import com.cheon.ccbuswidget.ui.oneui.OneUiSliderRow
import com.cheon.ccbuswidget.ui.oneui.OneUiSwitchRow
import com.cheon.ccbuswidget.ui.oneui.OneUiTextField
import com.cheon.ccbuswidget.ui.theme.CcBusTheme
import com.cheon.ccbuswidget.widget.BusWidgetProvider
import kotlinx.coroutines.launch

/** 위젯을 홈에 놓을 때 · 길게 눌러 수정할 때 뜨는 설정 화면 (One UI 모양) */
class WidgetConfigurationActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            CcBusTheme {
                ConfigureScreen(
                    appWidgetId = appWidgetId,
                    onBack = { finish() },
                    onSaved = {
                        BusWidgetProvider.requestRefresh(this, appWidgetId)
                        setResult(
                            RESULT_OK,
                            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        )
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfigureScreen(appWidgetId: Int, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apiKey by remember { mutableStateOf(WidgetStore.getApiKey(context)) }
    var apiKeySaved by remember { mutableStateOf(WidgetStore.getApiKey(context).isNotBlank()) }

    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<BusStop>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    var selectedStop by remember { mutableStateOf<BusStop?>(null) }
    var routes by remember { mutableStateOf<List<BusRoute>>(emptyList()) }
    var loadingRoutes by remember { mutableStateOf(false) }
    val selectedRoutes = remember { mutableStateListOf<String>() }

    var alpha by remember { mutableStateOf(55f) }
    var darkStyle by remember { mutableStateOf(true) }
    var oneUiBlur by remember { mutableStateOf(WidgetStore.isSamsung) }
    var maxRows by remember { mutableStateOf(4f) }

    // 기존 설정 불러오기 (위젯 재설정)
    LaunchedEffect(appWidgetId) {
        WidgetStore.load(context, appWidgetId)?.let { cfg ->
            selectedStop = BusStop(
                nodeId = cfg.nodeId,
                nodeName = cfg.nodeName,
                gpsLat = cfg.gpsLat,
                gpsLng = cfg.gpsLng
            )
            selectedRoutes.clear()
            selectedRoutes.addAll(cfg.routeNumbers)
            alpha = cfg.backgroundAlpha.toFloat()
            darkStyle = cfg.darkStyle
            maxRows = cfg.maxRows.toFloat()
            oneUiBlur = cfg.oneUiBlur
        }
    }

    // 정류장이 정해지면 경유 노선을 불러온다
    LaunchedEffect(selectedStop?.nodeId, apiKeySaved) {
        val stop = selectedStop ?: return@LaunchedEffect
        if (!apiKeySaved) return@LaunchedEffect
        loadingRoutes = true
        error = null
        val key = WidgetStore.getApiKey(context)
        val city = WidgetStore.getCityCode(context)
        routes = try {
            TagoApi.routesOfStop(key, city, stop.nodeId).ifEmpty {
                // 경유노선 조회가 비면 현재 도착정보에 있는 노선이라도 보여준다
                TagoApi.arrivals(key, city, stop.nodeId)
                    .map { BusRoute(it.routeId, it.routeNo, it.routeType) }
                    .distinctBy { it.routeNo }
            }
        } catch (e: Exception) {
            error = e.message
            emptyList()
        }
        loadingRoutes = false
    }

    fun search() {
        if (query.isBlank() || searching) return
        scope.launch {
            searching = true
            error = null
            results = try {
                TagoApi.searchStops(WidgetStore.getApiKey(context), WidgetStore.getCityCode(context), query.trim())
            } catch (e: Exception) {
                error = e.message
                emptyList()
            }
            if (error == null && results.isEmpty()) error = "검색 결과가 없습니다"
            searching = false
        }
    }

    OneUiScreen(title = "위젯 설정", onBack = onBack) {
        if (!apiKeySaved) {
            OneUiGroupLabel("공공데이터 서비스키")
            OneUiCard {
                OneUiTextField(
                    label = "serviceKey",
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    placeholder = "data.go.kr 에서 발급받은 인증키",
                    singleLine = false
                )
                OneUiFootnote(
                    "data.go.kr 에서 '국토교통부(TAGO) 버스도착정보'와 '버스정류소정보'를 활용신청하고 " +
                        "발급받은 인증키를 넣어 주세요."
                )
            }
            OneUiButton(
                text = "키 저장",
                enabled = apiKey.isNotBlank(),
                onClick = {
                    WidgetStore.setApiKey(context, apiKey)
                    apiKeySaved = apiKey.isNotBlank()
                }
            )
        }

        // ---------------- 1. 정류장 ----------------
        OneUiGroupLabel("정류장")
        val stop = selectedStop
        if (stop == null) {
            OneUiCard {
                OneUiTextField(
                    label = "정류장 이름",
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "예: 춘천역, 명동"
                )
                OneUiRow(
                    title = if (searching) "찾는 중…" else "검색",
                    iconRes = R.drawable.ic_search,
                    enabled = apiKeySaved && query.isNotBlank() && !searching,
                    onClick = { search() }
                )
            }
            if (results.isNotEmpty()) {
                OneUiCard {
                    results.take(40).forEachIndexed { i, s ->
                        if (i > 0) OneUiDivider()
                        OneUiRow(
                            title = s.nodeName,
                            summary = listOfNotNull(s.nodeNo?.let { "정류장번호 $it" }, s.nodeId).joinToString(" · "),
                            onClick = {
                                selectedStop = s
                                selectedRoutes.clear()
                                results = emptyList()
                            }
                        )
                    }
                }
            }
        } else {
            OneUiCard {
                OneUiRow(
                    title = stop.nodeName,
                    summary = "눌러서 다른 정류장으로 변경",
                    iconRes = R.drawable.ic_bus,
                    onClick = {
                        selectedStop = null
                        routes = emptyList()
                        selectedRoutes.clear()
                    }
                )
            }

            // ---------------- 2. 노선 ----------------
            OneUiGroupLabel("표시할 노선")
            OneUiCard {
                when {
                    loadingRoutes -> Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = colorResource(R.color.app_accent)
                        )
                    }
                    routes.isEmpty() -> OneUiFootnote(
                        "노선 목록을 불러오지 못했습니다. 노선을 고르지 않으면 도착이 빠른 순서대로 표시됩니다."
                    )
                    else -> {
                        FlowRow(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            routes.forEach { r ->
                                val on = selectedRoutes.contains(r.routeNo)
                                OneUiChip(
                                    text = r.routeNo,
                                    selected = on,
                                    selectedColor = colorResource(RouteKind.of(r.routeNo, r.routeType).colorRes)
                                ) {
                                    if (on) selectedRoutes.remove(r.routeNo) else selectedRoutes.add(r.routeNo)
                                }
                            }
                        }
                        OneUiFootnote(
                            if (selectedRoutes.isEmpty()) "선택 안 함 = 도착이 빠른 순서대로 표시"
                            else "선택한 순서대로 표시됩니다: ${selectedRoutes.joinToString(", ")}"
                        )
                    }
                }
            }

            // ---------------- 3. 모양 ----------------
            OneUiGroupLabel("위젯 모양")
            OneUiCard {
                OneUiSwitchRow(
                    title = "배경 블러 (One UI)",
                    summary = if (WidgetStore.isSamsung)
                        "One UI 7.0 이상에서 런처가 위젯 뒤 배경화면을 블러 처리합니다"
                    else "삼성 기기가 아니면 효과가 없습니다. 끄면 반투명 패널로 그립니다",
                    checked = oneUiBlur,
                    onCheckedChange = { oneUiBlur = it }
                )
                OneUiDivider()
                OneUiSliderRow(
                    title = "배경 불투명도",
                    valueText = "${alpha.toInt()}%",
                    summary = if (oneUiBlur) "블러 위에 덮이는 색의 진하기입니다 (너무 낮거나 100%면 블러가 꺼집니다)"
                    else "0% 에 가까울수록 배경화면이 그대로 비칩니다",
                    value = alpha,
                    range = if (oneUiBlur) 5f..95f else 0f..100f,
                    onValueChange = { alpha = it }
                )
                OneUiDivider()
                OneUiSwitchRow(title = "어두운 배경", checked = darkStyle, onCheckedChange = { darkStyle = it })
                OneUiDivider()
                OneUiSliderRow(
                    title = "표시할 줄 수",
                    valueText = "${maxRows.toInt()}줄",
                    value = maxRows,
                    range = 1f..6f,
                    steps = 4,
                    onValueChange = { maxRows = it }
                )
            }

            OneUiButton(
                text = "저장",
                enabled = apiKeySaved,
                onClick = {
                    WidgetStore.save(
                        context,
                        WidgetConfig(
                            appWidgetId = appWidgetId,
                            nodeId = stop.nodeId,
                            nodeName = stop.nodeName,
                            gpsLat = stop.gpsLat,
                            gpsLng = stop.gpsLng,
                            routeNumbers = selectedRoutes.toList(),
                            backgroundAlpha = alpha.toInt(),
                            darkStyle = darkStyle,
                            maxRows = maxRows.toInt(),
                            oneUiBlur = oneUiBlur
                        )
                    )
                    onSaved()
                }
            )
        }

        error?.let { OneUiFootnote(it) }
    }
}
