package com.cheon.ccbuswidget.feature.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cheon.ccbuswidget.data.local.WidgetStore
import com.cheon.ccbuswidget.ui.theme.CcBusTheme
import com.cheon.ccbuswidget.widget.BusWidgetProvider
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CcBusTheme { HomeScreen() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var apiKey by remember { mutableStateOf(WidgetStore.getApiKey(context)) }
    var cityCode by remember { mutableStateOf(WidgetStore.getCityCode(context)) }
    var naverKey by remember { mutableStateOf(WidgetStore.getNaverKeyId(context)) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("춘천버스 위젯") }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("공공데이터 서비스키", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("serviceKey") },
                placeholder = { Text("공공데이터포털에서 발급받은 키") },
                singleLine = false,
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = cityCode,
                onValueChange = { cityCode = it },
                label = { Text("도시코드 (춘천시 = 32010)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("네이버 지도 Key ID", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = naverKey,
                onValueChange = { naverKey = it },
                label = { Text("NCP Key ID") },
                placeholder = { Text("정류장 위치 지도를 보려면 필요합니다") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    WidgetStore.setApiKey(context, apiKey)
                    WidgetStore.setNaverKeyId(context, naverKey)
                    WidgetStore.setCityCode(context, cityCode.trim().ifBlank { WidgetStore.DEFAULT_CITY_CODE })
                    BusWidgetProvider.requestRefresh(context)
                    scope.launch { snackbar.showSnackbar("저장했습니다") }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("저장") }

            OutlinedButton(
                onClick = { BusWidgetProvider.requestRefresh(context) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("모든 위젯 새로고침") }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("서비스키 발급 방법", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "1. data.go.kr 회원가입 후 로그인\n" +
                            "2. '국토교통부(TAGO)_버스도착정보' 활용신청\n" +
                            "3. '국토교통부(TAGO)_버스정류소정보' 활용신청\n" +
                            "4. 마이페이지 > 개발계정에서 일반 인증키(Encoding) 복사\n" +
                            "5. 위 칸에 붙여넣고 저장\n\n" +
                            "※ 승인까지 보통 수 분 정도 걸립니다.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("네이버 지도 Key ID 발급", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "1. 네이버 클라우드 플랫폼(ncloud.com) 가입\n" +
                            "2. Services > Application Service > Maps 이용 신청\n" +
                            "3. Application 등록 시 Android 앱 패키지 이름에\n" +
                            "   com.cheon.ccbuswidget 을 입력\n" +
                            "4. 발급된 Key ID 를 위 칸에 붙여넣고 저장\n\n" +
                            "※ 지도는 정류장 상세 화면에서만 씁니다. 키가 없어도 도착 정보는 정상 동작합니다.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("위젯 추가하기", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "홈 화면 빈 곳을 길게 누르고 '위젯' > '춘천버스 위젯'을 홈에 놓으면 " +
                            "설정 화면이 열립니다. 정류장을 검색해 고르고, 표시할 노선을 선택하세요.\n\n" +
                            "위젯을 누르면 정류장 위치와 그 정류장에 오는 모든 버스의 도착 시간을 볼 수 있습니다. " +
                            "설정을 바꾸려면 위젯을 길게 눌러 수정하거나, 상세 화면의 설정 버튼을 누르세요.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

