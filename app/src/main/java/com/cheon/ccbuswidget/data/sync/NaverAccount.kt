package com.cheon.ccbuswidget.data.sync

import android.app.Activity
import android.content.Context
import androidx.core.content.edit
import com.cheon.ccbuswidget.BuildConfig
import com.navercorp.nid.NaverIdLoginSDK
import com.navercorp.nid.oauth.OAuthLoginCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * 네이버 아이디로 로그인.
 *
 * 로그인 화면은 네이버 SDK 가 띄우고, 받은 토큰은 여기서 따로 들고 있으면서
 * 만료되면 네이버 토큰 주소로 직접 갱신한다. (동기화 요청은 백그라운드에서 나가므로 화면 없이 갱신해야 한다)
 *
 * 키는 프로젝트 최상위 local.properties 의 naver.client.id / naver.client.secret 에서 빌드 때 들어온다.
 */
object NaverAccount {
    private const val PREF = "cc_account"
    private const val KEY_ACCESS = "naver_access_token"
    private const val KEY_REFRESH = "naver_refresh_token"
    /** 액세스 토큰 만료 시각 (epoch 초) */
    private const val KEY_EXPIRES = "naver_expires_at"
    private const val KEY_LAST_SYNC = "last_sync_at"

    private const val TOKEN_URL = "https://nid.naver.com/oauth2.0/token"

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** local.properties 에 네이버 키가 들어 있는지 */
    val isConfigured: Boolean get() = BuildConfig.NAVER_CLIENT_ID.isNotBlank() &&
        BuildConfig.NAVER_CLIENT_SECRET.isNotBlank()

    @Volatile
    private var initialized = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun init(context: Context) {
        if (initialized || !isConfigured) return
        synchronized(this) {
            if (initialized) return
            NaverIdLoginSDK.initialize(
                context.applicationContext,
                BuildConfig.NAVER_CLIENT_ID,
                BuildConfig.NAVER_CLIENT_SECRET,
                "춘천버스 위젯"
            )
            initialized = true
        }
    }

    fun isLoggedIn(context: Context): Boolean =
        !prefs(context).getString(KEY_REFRESH, null).isNullOrBlank()

    /** 마지막으로 서버와 맞춘 시각 (epoch ms, 없으면 0) */
    fun lastSyncAt(context: Context): Long = prefs(context).getLong(KEY_LAST_SYNC, 0L)

    internal fun markSynced(context: Context) {
        prefs(context).edit { putLong(KEY_LAST_SYNC, System.currentTimeMillis()) }
    }

    /** 네이버 로그인 화면을 띄운다. 성공하면 토큰을 저장한다. */
    suspend fun login(activity: Activity): Result<Unit> {
        if (!isConfigured) return Result.failure(IllegalStateException("네이버 로그인 키가 설정되지 않았습니다"))
        init(activity)
        return suspendCancellableCoroutine { cont ->
            NaverIdLoginSDK.authenticate(activity, object : OAuthLoginCallback {
                override fun onSuccess() {
                    val access = NaverIdLoginSDK.getAccessToken()
                    val refresh = NaverIdLoginSDK.getRefreshToken()
                    val result = if (access.isNullOrBlank() || refresh.isNullOrBlank()) {
                        Result.failure(IllegalStateException("토큰을 받지 못했습니다"))
                    } else {
                        saveTokens(activity, access, refresh, NaverIdLoginSDK.getExpiresAt())
                        Result.success(Unit)
                    }
                    if (cont.isActive) cont.resume(result)
                }

                override fun onFailure(httpStatus: Int, message: String) {
                    val desc = NaverIdLoginSDK.getLastErrorDescription()?.takeIf { it.isNotBlank() } ?: message
                    if (cont.isActive) cont.resume(Result.failure(IllegalStateException(desc)))
                }

                override fun onError(errorCode: Int, message: String) = onFailure(errorCode, message)
            })
        }
    }

    /** 이 기기에서만 로그아웃한다 (서버에 저장된 즐겨찾기는 남는다) */
    fun logout(context: Context) {
        prefs(context).edit {
            remove(KEY_ACCESS); remove(KEY_REFRESH); remove(KEY_EXPIRES); remove(KEY_LAST_SYNC)
        }
        if (initialized) runCatching { NaverIdLoginSDK.logout() }
    }

    /**
     * 쓸 수 있는 액세스 토큰. 만료가 1분 안쪽이면(또는 [forceRefresh]) 먼저 갱신한다.
     * 갱신 토큰까지 만료됐으면 로그아웃 처리하고 null.
     */
    suspend fun accessToken(context: Context, forceRefresh: Boolean = false): String? {
        val p = prefs(context)
        val access = p.getString(KEY_ACCESS, null)
        val expiresAt = p.getLong(KEY_EXPIRES, 0L)
        val nowSec = System.currentTimeMillis() / 1000
        if (!forceRefresh && !access.isNullOrBlank() && expiresAt - 60 > nowSec) return access
        return refresh(context)
    }

    private suspend fun refresh(context: Context): String? = withContext(Dispatchers.IO) {
        val refresh = prefs(context).getString(KEY_REFRESH, null) ?: return@withContext null
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", BuildConfig.NAVER_CLIENT_ID)
            .add("client_secret", BuildConfig.NAVER_CLIENT_SECRET)
            .add("refresh_token", refresh)
            .build()
        val json = runCatching {
            http.newCall(Request.Builder().url(TOKEN_URL).post(body).build()).execute().use { res ->
                JSONObject(res.body?.string().orEmpty())
            }
        }.getOrElse { return@withContext null } // 네트워크 문제: 다음에 다시 시도

        val newAccess = json.optString("access_token")
        if (newAccess.isBlank()) {
            // 갱신 토큰이 만료·취소됨 → 다시 로그인해야 한다
            if (json.has("error")) logout(context)
            return@withContext null
        }
        val expiresIn = json.optString("expires_in").toLongOrNull() ?: 3600L
        saveTokens(context, newAccess, refresh, System.currentTimeMillis() / 1000 + expiresIn)
        newAccess
    }

    private fun saveTokens(context: Context, access: String, refresh: String, expiresAtSec: Long) {
        prefs(context).edit {
            putString(KEY_ACCESS, access)
            putString(KEY_REFRESH, refresh)
            putLong(KEY_EXPIRES, expiresAtSec)
        }
    }
}
