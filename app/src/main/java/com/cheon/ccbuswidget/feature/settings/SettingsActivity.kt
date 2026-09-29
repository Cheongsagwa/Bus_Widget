package com.cheon.ccbuswidget.feature.settings

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cheon.ccbuswidget.R
import com.cheon.ccbuswidget.data.local.WidgetStore
import com.cheon.ccbuswidget.feature.account.AccountActivity
import com.cheon.ccbuswidget.ui.oneui.OneUiButton
import com.cheon.ccbuswidget.ui.oneui.OneUiCard
import com.cheon.ccbuswidget.ui.oneui.OneUiDivider
import com.cheon.ccbuswidget.ui.oneui.OneUiGroupLabel
import com.cheon.ccbuswidget.ui.oneui.OneUiRow
import com.cheon.ccbuswidget.ui.oneui.OneUiScreen
import com.cheon.ccbuswidget.ui.oneui.OneUiText
import com.cheon.ccbuswidget.ui.oneui.OneUiTextField
import com.cheon.ccbuswidget.ui.theme.CcBusTheme
import com.cheon.ccbuswidget.widget.BusWidgetProvider

/** 설정 — One UI 모양 (큰 제목 · 둥근 카드 묶음) */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CcBusTheme { SettingsScreen(onBack = { finish() }) }
        }
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var apiKey by remember { mutableStateOf(WidgetStore.getApiKey(context)) }
    var cityCode by remember { mutableStateOf(WidgetStore.getCityCode(context)) }
    var naverKey by remember { mutableStateOf(WidgetStore.getNaverKeyId(context)) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    OneUiScreen(title = "설정", onBack = onBack) {
        OneUiGroupLabel("계정")
        OneUiCard {
            OneUiRow(
                title = "계정 설정",
                summary = "네이버로 로그인해 즐겨찾기를 보관합니다",
                iconRes = R.drawable.ic_nav_account,
                onClick = { context.startActivity(Intent(context, AccountActivity::class.java)) }
            )
        }

        OneUiGroupLabel("키")
        OneUiCard {
            OneUiTextField(
                label = "공공데이터 서비스키",
                value = apiKey,
                onValueChange = { apiKey = it },
                placeholder = "공공데이터포털에서 발급받은 키",
                singleLine = false
            )
            OneUiTextField(
                label = "도시코드 (춘천시 = 32010)",
                value = cityCode,
                onValueChange = { cityCode = it }
            )
            OneUiTextField(
                label = "네이버 지도 Key ID",
                value = naverKey,
                onValueChange = { naverKey = it },
                placeholder = "정류장 위치 지도를 보려면 필요합니다"
            )
            Spacer(Modifier.height(8.dp))
        }
        OneUiButton(
            text = "저장",
            onClick = {
                WidgetStore.setApiKey(context, apiKey)
                WidgetStore.setNaverKeyId(context, naverKey)
                WidgetStore.setCityCode(context, cityCode.trim().ifBlank { WidgetStore.DEFAULT_CITY_CODE })
                BusWidgetProvider.requestRefresh(context)
                toast("저장했습니다")
            }
        )

        OneUiGroupLabel("위젯")
        OneUiCard {
            OneUiRow(
                title = "모든 위젯 새로고침",
                summary = "홈 화면의 위젯 도착 정보를 지금 다시 불러옵니다",
                iconRes = R.drawable.ic_refresh,
                onClick = {
                    BusWidgetProvider.requestRefresh(context)
                    toast("위젯을 새로고침했습니다")
                }
            )
        }

        OneUiGroupLabel("도움말")
        OneUiCard {
            OneUiText(
                "서비스키 발급 방법",
                "1. data.go.kr 회원가입 후 로그인\n" +
                    "2. '국토교통부(TAGO)_버스도착정보' 활용신청\n" +
                    "3. '국토교통부(TAGO)_버스정류소정보' 활용신청\n" +
                    "4. 마이페이지 > 개발계정에서 일반 인증키(Encoding) 복사\n" +
                    "5. 위 칸에 붙여넣고 저장\n\n" +
                    "※ 승인까지 보통 수 분 정도 걸립니다."
            )
            OneUiDivider()
            OneUiText(
                "네이버 지도 Key ID 발급",
                "1. 네이버 클라우드 플랫폼(ncloud.com) 가입\n" +
                    "2. Services > Application Service > Maps 이용 신청\n" +
                    "3. Application 등록 시 Android 앱 패키지 이름에\n" +
                    "   com.cheon.ccbuswidget 을 입력\n" +
                    "4. 발급된 Key ID 를 위 칸에 붙여넣고 저장\n\n" +
                    "※ 키가 없어도 도착 정보는 정상 동작합니다."
            )
            OneUiDivider()
            OneUiText(
                "위젯 추가하기",
                "홈 화면 빈 곳을 길게 누르고 '위젯' > '춘천버스 위젯'을 홈에 놓으면 " +
                    "설정 화면이 열립니다. 정류장을 검색해 고르고, 표시할 노선을 선택하세요.\n\n" +
                    "위젯을 누르면 정류장 위치와 그 정류장에 오는 모든 버스의 도착 시간을 볼 수 있습니다. " +
                    "설정을 바꾸려면 위젯을 길게 눌러 수정하세요."
            )
        }
    }
}
