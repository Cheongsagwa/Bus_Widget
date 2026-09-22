package com.cheon.ccbuswidget.feature.widgetconfig

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cheon.ccbuswidget.data.api.TagoApi
import com.cheon.ccbuswidget.data.local.WidgetStore
import com.cheon.ccbuswidget.data.model.BusRoute
import com.cheon.ccbuswidget.data.model.BusStop
import com.cheon.ccbuswidget.data.model.WidgetConfig
import com.cheon.ccbuswidget.ui.theme.CcBusTheme
import com.cheon.ccbuswidget.widget.BusWidgetProvider
import kotlinx.coroutines.launch

class WidgetConfigurationActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ConfigureScreen(appWidgetId: Int, onSaved: () -> Unit) {
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
            val list = TagoApi.routesOfStop(key, city, stop.nodeId)
            list.ifEmpty {
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

    Scaffold(topBar = { TopAppBar(title = { Text("위젯 설정") }) }) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!apiKeySaved) {
                Text("공공데이터 서비스키", style = MaterialTheme.typography.titleMedium)
                Text(
                    "data.go.kr 에서 '국토교통부(TAGO) 버스도착정보'와 '버스정류소정보'를 활용신청하고 " +
                        "발급받은 인증키를 넣어 주세요.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("serviceKey") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        WidgetStore.setApiKey(context, apiKey)
                        apiKeySaved = apiKey.isNotBlank()
                    },
                    enabled = apiKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("키 저장") }
                HorizontalDivider()
            }

            // ---------------- 정류장 ----------------
            Text("1. 정류장", style = MaterialTheme.typography.titleMedium)

            val stop = selectedStop
            if (stop == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("정류장 이름") },
                        placeholder = { Text("예: 춘천역, 명동") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        enabled = apiKeySaved && query.isNotBlank() && !searching,
                        onClick = {
                            scope.launch {
                                searching = true
                                error = null
                                results = try {
                                    TagoApi.searchStops(
                                        WidgetStore.getApiKey(context),
                                        WidgetStore.getCityCode(context),
                                        query.trim()
                                    )
                                } catch (e: Exception) {
                                    error = e.message
                                    emptyList()
                                }
                                if (error == null && results.isEmpty()) {
                                    error = "검색 결과가 없습니다"
                                }
                                searching = false
                            }
                        }
                    ) { Text("검색") }
                }

                if (searching) CircularProgressIndicator()

                results.take(40).forEach { s ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedStop = s
                                selectedRoutes.clear()
                                results = emptyList()
                            }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(s.nodeName, fontWeight = FontWeight.Bold)
                            Text(
                                listOfNotNull(s.nodeNo?.let { "정류장번호 $it" }, s.nodeId)
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stop.nodeName, fontWeight = FontWeight.Bold)
                            Text(stop.nodeId, style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = {
                            selectedStop = null
                            routes = emptyList()
                            selectedRoutes.clear()
                        }) { Text("변경") }
                    }
                }

                // ---------------- 노선 ----------------
                Text("2. 표시할 노선", style = MaterialTheme.typography.titleMedium)
                if (loadingRoutes) {
                    CircularProgressIndicator()
                } else if (routes.isEmpty()) {
                    Text(
                        "노선 목록을 불러오지 못했습니다. 노선을 고르지 않으면 " +
                            "도착이 빠른 순서대로 표시됩니다.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        routes.forEach { r ->
                            val on = selectedRoutes.contains(r.routeNo)
                            FilterChip(
                                selected = on,
                                onClick = {
                                    if (on) selectedRoutes.remove(r.routeNo)
                                    else selectedRoutes.add(r.routeNo)
                                },
                                label = { Text(r.routeNo) }
                            )
                        }
                    }
                    Text(
                        if (selectedRoutes.isEmpty()) "선택 안 함 = 도착이 빠른 순서대로 표시"
                        else "선택한 순서대로 표시됩니다: ${selectedRoutes.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // ---------------- 모양 ----------------
                Text("3. 위젯 모양", style = MaterialTheme.typography.titleMedium)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("배경 블러 (One UI)")
                        Text(
                            if (WidgetStore.isSamsung)
                                "One UI 7.0 이상에서 런처가 위젯 뒤 배경화면을 블러 처리합니다"
                            else
                                "삼성 기기가 아니면 효과가 없습니다. 끄면 반투명 패널로 그립니다",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = oneUiBlur, onCheckedChange = { oneUiBlur = it })
                }

                Text("배경 불투명도 ${alpha.toInt()}%")
                Text(
                    if (oneUiBlur) "블러 위에 덮이는 색의 진하기입니다 (너무 낮거나 100%면 블러가 꺼집니다)"
                    else "0% 에 가까울수록 배경화면이 그대로 비칩니다",
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = alpha,
                    onValueChange = { alpha = it },
                    valueRange = if (oneUiBlur) 5f..95f else 0f..100f
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("어두운 배경", modifier = Modifier.weight(1f))
                    Switch(checked = darkStyle, onCheckedChange = { darkStyle = it })
                }

                Text("표시할 줄 수 ${maxRows.toInt()}")
                Slider(
                    value = maxRows,
                    onValueChange = { maxRows = it },
                    valueRange = 1f..6f,
                    steps = 4
                )

                Button(
                    modifier = Modifier.fillMaxWidth(),
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
                ) { Text("저장") }
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

