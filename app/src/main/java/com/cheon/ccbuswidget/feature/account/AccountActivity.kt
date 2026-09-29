package com.cheon.ccbuswidget.feature.account

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.cheon.ccbuswidget.R
import com.cheon.ccbuswidget.data.sync.FavoriteSync
import com.cheon.ccbuswidget.data.sync.NaverAccount
import com.cheon.ccbuswidget.ui.oneui.OneUiButton
import com.cheon.ccbuswidget.ui.oneui.OneUiCard
import com.cheon.ccbuswidget.ui.oneui.OneUiDivider
import com.cheon.ccbuswidget.ui.oneui.OneUiFootnote
import com.cheon.ccbuswidget.ui.oneui.OneUiGroupLabel
import com.cheon.ccbuswidget.ui.oneui.OneUiRow
import com.cheon.ccbuswidget.ui.oneui.OneUiScreen
import com.cheon.ccbuswidget.ui.oneui.OneUiText
import com.cheon.ccbuswidget.ui.theme.CcBusTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 계정 설정 — 네이버로 로그인해서 즐겨찾기를 서버에 보관한다 (One UI 모양) */
class AccountActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        FavoriteSync.init(this)
        setContent { CcBusTheme { AccountScreen(onBack = { finish() }) } }
    }
}

/** 네이버 로그인 버튼 색 (네이버 브랜드 가이드) */
private val NaverGreen = Color(0xFF03C75A)
/** One UI 의 위험한 동작 글자색 */
private val DangerRed = Color(0xFFE5484D)

@Composable
private fun AccountScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val timeFormat = remember { SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA) }

    var loggedIn by remember { mutableStateOf(NaverAccount.isLoggedIn(context)) }
    var lastSync by remember { mutableLongStateOf(NaverAccount.lastSyncAt(context)) }
    var busy by remember { mutableStateOf(false) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    fun runSync(successMessage: (Int) -> String) {
        busy = true
        scope.launch {
            FavoriteSync.syncNow(context)
                .onSuccess { toast(successMessage(it)) }
                .onFailure { toast(it.message ?: "동기화하지 못했습니다") }
            loggedIn = NaverAccount.isLoggedIn(context)
            lastSync = NaverAccount.lastSyncAt(context)
            busy = false
        }
    }

    OneUiScreen(title = "계정", onBack = onBack) {
        if (!FavoriteSync.isAvailable) {
            OneUiCard {
                OneUiText(
                    "아직 설정되지 않았습니다",
                    "local.properties 에 naver.client.id · naver.client.secret · sync.url 을 넣고 " +
                        "앱을 다시 빌드해 주세요. (sync-server/README.md 참고)"
                )
            }
            return@OneUiScreen
        }

        // 계정 상태
        OneUiCard {
            OneUiRow(
                title = if (loggedIn) "네이버 계정 연결됨" else "로그인하지 않음",
                summary = if (loggedIn) {
                    if (busy) "동기화 중…"
                    else if (lastSync > 0) "마지막 동기화 ${timeFormat.format(Date(lastSync))}"
                    else "아직 동기화하지 않았습니다"
                } else {
                    "앱을 다시 깔거나 휴대폰을 바꿔도 같은 네이버 계정으로 로그인하면 즐겨찾기가 돌아옵니다"
                },
                iconRes = R.drawable.ic_nav_account,
                iconTint = if (loggedIn) NaverGreen else androidx.compose.ui.res.colorResource(R.color.glass_on_surface)
            )
        }

        if (!loggedIn) {
            OneUiButton(
                text = "네이버로 로그인",
                color = NaverGreen,
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        NaverAccount.login(activity)
                            .onSuccess {
                                loggedIn = true
                                runSync { n -> "로그인했습니다. 즐겨찾기 ${n}개를 맞췄습니다" }
                            }
                            .onFailure {
                                busy = false
                                toast(it.message ?: "로그인하지 못했습니다")
                            }
                    }
                }
            )
        } else {
            OneUiGroupLabel("즐겨찾기 동기화")
            OneUiCard {
                OneUiRow(
                    title = "지금 동기화",
                    summary = "이 기기와 계정의 즐겨찾기 · 검색 기록을 맞춥니다",
                    iconRes = R.drawable.ic_refresh,
                    enabled = !busy,
                    onClick = { runSync { n -> "동기화했습니다 (즐겨찾기 ${n}개)" } }
                )
                OneUiDivider(inset = true)
                OneUiRow(
                    title = "로그아웃",
                    summary = "즐겨찾기는 계정에 남아 있습니다",
                    iconRes = R.drawable.ic_back,
                    enabled = !busy,
                    onClick = {
                        NaverAccount.logout(context)
                        loggedIn = false
                        lastSync = 0L
                        toast("로그아웃했습니다. 즐겨찾기는 계정에 남아 있습니다")
                    }
                )
            }
            OneUiCard {
                OneUiRow(
                    title = "계정 연결 해제",
                    summary = "계정에 보관된 즐겨찾기를 지우고 연결을 끊습니다",
                    iconRes = R.drawable.ic_delete_outline,
                    iconTint = DangerRed,
                    titleColor = DangerRed,
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            FavoriteSync.deleteRemote(context)
                                .onSuccess {
                                    NaverAccount.logout(context)
                                    loggedIn = false
                                    lastSync = 0L
                                    toast("계정에 보관된 즐겨찾기를 지우고 연결을 끊었습니다")
                                }
                                .onFailure { toast(it.message ?: "지우지 못했습니다") }
                            busy = false
                        }
                    }
                )
            }
        }

        OneUiFootnote("이 기기의 즐겨찾기는 로그아웃해도 지워지지 않습니다.")
    }
}
