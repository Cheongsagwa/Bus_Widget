package com.cheon.ccbuswidget.wear

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.launch

/**
 * 워치의 알람 화면.
 * 둥근 화면 가장자리를 따라 하차 진행 막대(노선 색), 가운데 버스 아이콘과 문구, 아래 [해제].
 */
class AlarmActivity : ComponentActivity() {
    private val askNotification =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // 허용하면 지금 상태로 진행 중 활동을 바로 띄운다
            AlarmOngoing.show(this, WearAlarmStore.current(this))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WearAlarmStore.current(this)
        // 진행 중 활동(알림)을 띄우려면 알림 권한이 필요하다 (Wear OS 4 이상)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) askNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { MaterialTheme { AlarmScreen() } }
    }
}

@Composable
private fun AlarmScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val alarm by WearAlarmStore.state.collectAsState()

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val a = alarm
        if (a == null) {
            Text(
                "켜진 승하차 알람이 없어요\n폰에서 알람을 켜 주세요",
                modifier = Modifier.padding(24.dp),
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
            return@Box
        }
        val routeColor = Color(a.color)

        // 가장자리 진행 막대 (하차만)
        if (a.progress >= 0f) {
            Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                val stroke = 6.dp.toPx()
                drawArc(routeColor.copy(alpha = 0.25f), -90f, 360f, false, style = Stroke(stroke))
                drawArc(routeColor, -90f, 360f * a.progress, false, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 노선 색 원 + 흰 버스
            Box(Modifier.size(34.dp).background(routeColor, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_bus), null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                a.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(a.subtitle, fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center)
            if (a.time.isNotBlank()) {
                Text(a.time, fontSize = 13.sp, color = routeColor, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { scope.launch { WearAlarmStore.requestCancel(context) } }) {
                Text("해제", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
    }
}
