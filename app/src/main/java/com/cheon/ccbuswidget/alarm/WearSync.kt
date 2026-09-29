package com.cheon.ccbuswidget.alarm

import android.content.Context
import androidx.core.content.edit
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

/**
 * 폰 ↔ 갤럭시 워치(Wear OS 앱) 연결.
 * 폰이 승하차 알람 상태를 계산해서 워치로 보내고, 워치는 보여 주기만 한다.
 * 워치에서 [해제] 를 누르면 메시지가 와서 폰의 알람을 끈다.
 *
 * 경로 · 키 이름은 wear 모듈의 WearAlarm.kt 와 같아야 한다.
 */
object WearSync {
    const val PATH = "/bus_alarm"
    const val PATH_CANCEL = "/bus_alarm/cancel"
    /** 폰 → 워치: 알림을 띄우고 길게 진동 (내용: "제목\n본문") */
    const val PATH_ALERT = "/bus_alarm/alert"
    /** 워치 앱이 알리는 기능 이름 (wear 모듈 res/values/wear.xml) */
    private const val CAPABILITY = "bus_alarm_wear"

    private const val PREFS = "wear_sync"
    private const val KEY_WATCH_APP = "watch_app"

    /**
     * 이 알람 앱이 깔린 워치가 있는지.
     * 확인(비동기)이 끝나기 전에 알림이 나가도 워치에 알림이 두 번 뜨지 않도록 마지막으로 확인한 값을 저장해 두고 쓴다.
     */
    fun watchAppReachable(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_WATCH_APP, false)

    private fun setWatchApp(context: Context, value: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_WATCH_APP, value)
        }
    }

    /** 워치 앱이 깔린 워치가 연결돼 있는지 확인해 둔다 */
    fun checkWatchApp(context: Context) {
        val app = context.applicationContext
        runCatching {
            Wearable.getCapabilityClient(app)
                .getCapability(CAPABILITY, com.google.android.gms.wearable.CapabilityClient.FILTER_REACHABLE)
                .addOnSuccessListener { setWatchApp(app, it.nodes.isNotEmpty()) }
        }
    }

    /** 워치에 알림을 띄우고 길게 진동하라고 보낸다 */
    fun alert(context: Context, title: String, text: String, color: Int) {
        val app = context.applicationContext
        runCatching {
            Wearable.getCapabilityClient(app)
                .getCapability(CAPABILITY, com.google.android.gms.wearable.CapabilityClient.FILTER_REACHABLE)
                .addOnSuccessListener { info ->
                    setWatchApp(app, info.nodes.isNotEmpty())
                    // "제목\n본문\n노선색" — 워치가 노선 색 버스 아이콘을 그린다
                    val payload = "$title\n$text\n$color".toByteArray()
                    info.nodes.forEach { Wearable.getMessageClient(app).sendMessage(it.id, PATH_ALERT, payload) }
                }
        }
    }

    /** 워치에 보여 줄 한 장면 */
    data class State(
        /** BOARD / ALIGHT */
        val type: String,
        val routeNo: String,
        /** 노선 대표색 (ARGB) */
        val color: Int,
        val title: String,
        val subtitle: String,
        /** 남은 시간 (하차만, 없으면 빈 글) */
        val time: String,
        /** 짧은 표시 (예: 12개) — 워치 페이스 컴플리케이션 */
        val chip: String,
        /** 워치 진행 중 활동(화면 아래 버스 칩)에 쓰는 글 (예: 3정류장 전) */
        val status: String,
        /** 하차 진행률 0~1 (승차는 -1) */
        val progress: Float
    )

    /** 상태를 워치로 보낸다. null = 알람 꺼짐. 워치가 없거나 연결이 끊겨 있으면 조용히 넘어간다 */
    fun push(context: Context, state: State?) {
        runCatching {
            val req = PutDataMapRequest.create(PATH).apply {
                dataMap.putBoolean("active", state != null)
                if (state != null) {
                    dataMap.putString("type", state.type)
                    dataMap.putString("routeNo", state.routeNo)
                    dataMap.putInt("color", state.color)
                    dataMap.putString("title", state.title)
                    dataMap.putString("subtitle", state.subtitle)
                    dataMap.putString("time", state.time)
                    dataMap.putString("chip", state.chip)
                    dataMap.putString("status", state.status)
                    dataMap.putFloat("progress", state.progress)
                }
                // 같은 내용이어도 매번 전달되도록 (데이터가 같으면 워치에 변경 알림이 가지 않는다)
                dataMap.putLong("at", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context.applicationContext).putDataItem(req)
        }
    }
}

/** 워치에서 보낸 메시지를 받는다 (지금은 [해제] 하나) */
class WearMessageListener : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == WearSync.PATH_CANCEL) AlarmService.cancel(this)
    }
}
