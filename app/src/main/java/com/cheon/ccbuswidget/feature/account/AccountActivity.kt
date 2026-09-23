package com.cheon.ccbuswidget.feature.account

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cheon.ccbuswidget.data.sync.FavoriteSync
import com.cheon.ccbuswidget.data.sync.NaverAccount
import com.cheon.ccbuswidget.ui.theme.CcBusTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 계정 설정 — 네이버로 로그인해서 즐겨찾기를 서버에 보관한다 */
class AccountActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FavoriteSync.init(this)
        setContent { CcBusTheme { AccountScreen(onBack = { finish() }) } }
    }
}

/** 네이버 로그인 버튼 색 (네이버 브랜드 가이드) */
private val NaverGreen = Color(0xFF03C75A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val timeFormat = remember { SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA) }

    var loggedIn by remember { mutableStateOf(NaverAccount.isLoggedIn(context)) }
    var lastSync by remember { mutableLongStateOf(NaverAccount.lastSyncAt(context)) }
    var busy by remember { mutableStateOf(false) }

    fun runSync(successMessage: (Int) -> String) {
        busy = true
        scope.launch {
            FavoriteSync.syncNow(context)
                .onSuccess { snackbar.showSnackbar(successMessage(it)) }
                .onFailure { snackbar.showSnackbar(it.message ?: "동기화하지 못했습니다") }
            loggedIn = NaverAccount.isLoggedIn(context)
            lastSync = NaverAccount.lastSyncAt(context)
            busy = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("계정 설정") },
                navigationIcon = { TextButton(onClick = onBack) { Text("닫기") } }
            )
        },
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
            if (!FavoriteSync.isAvailable) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("아직 설정되지 않았습니다", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "local.properties 에 naver.client.id · naver.client.secret · sync.url 을 넣고 " +
                                "앱을 다시 빌드해 주세요. (sync-server/README.md 참고)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                return@Column
            }

            Text(
                if (loggedIn) "네이버 계정이 연결되어 있습니다" else "네이버로 로그인하면 즐겨찾기가 계정에 보관됩니다",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                if (loggedIn) {
                    if (lastSync > 0) "마지막 동기화: ${timeFormat.format(Date(lastSync))}"
                    else "아직 동기화하지 않았습니다"
                } else {
                    "앱을 지웠다 다시 깔거나 휴대폰을 바꿔도, 같은 네이버 계정으로 로그인하면 즐겨찾기가 돌아옵니다."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!loggedIn) {
                Button(
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
                                    snackbar.showSnackbar(it.message ?: "로그인하지 못했습니다")
                                }
                        }
                    },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = NaverGreen, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("네이버로 로그인", fontWeight = FontWeight.Bold) }
            } else {
                Button(
                    onClick = { runSync { n -> "동기화했습니다 (즐겨찾기 ${n}개)" } },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "동기화 중…" else "지금 동기화") }

                OutlinedButton(
                    onClick = {
                        NaverAccount.logout(context)
                        loggedIn = false
                        lastSync = 0L
                        scope.launch { snackbar.showSnackbar("로그아웃했습니다. 즐겨찾기는 계정에 남아 있습니다") }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("로그아웃") }

                TextButton(
                    onClick = {
                        busy = true
                        scope.launch {
                            FavoriteSync.deleteRemote(context)
                                .onSuccess {
                                    NaverAccount.logout(context)
                                    loggedIn = false
                                    lastSync = 0L
                                    snackbar.showSnackbar("계정에 보관된 즐겨찾기를 지우고 연결을 끊었습니다")
                                }
                                .onFailure { snackbar.showSnackbar(it.message ?: "지우지 못했습니다") }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("계정 연결 해제 (보관된 즐겨찾기 삭제)", color = MaterialTheme.colorScheme.error)
                }
            }

            Text(
                "이 기기의 즐겨찾기는 로그아웃해도 지워지지 않습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
