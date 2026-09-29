package com.cheon.ccbuswidget.wear

import android.content.ComponentName
import android.content.Context
import androidx.core.content.edit
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

/** 폰에서 받은 승하차 알람 상태 (폰 앱 WearSync.State 와 같은 내용) */
data class WearAlarm(
    /** BOARD(승차) / ALIGHT(하차) */
    val type: String,
    val routeNo: String,
    val color: Int,
    val title: String,
    val subtitle: String,
    val time: String,
    val chip: String,
    /** 진행 중 활동 칩 글 (예: 3정류장 전) */
    val status: String,
    /** 하차 진행률 0~1, 승차는 -1 */
    val progress: Float
)

/**
 * 워치 쪽 알람 상태 보관소.
 * 폰에서 오면 저장하고, 화면 · 진행 중 활동 · 타일 · 컴플리케이션을 모두 새로 그리게 한다.
 */
object WearAlarmStore {
    /** 폰 앱 WearSync 와 같은 경로 */
    const val PATH = "/bus_alarm"
    const val PATH_CANCEL = "/bus_alarm/cancel"
    const val PATH_ALERT = "/bus_alarm/alert"

    private const val PREFS = "wear_alarm"

    private val _state = MutableStateFlow<WearAlarm?>(null)
    val state: StateFlow<WearAlarm?> = _state

    @Volatile private var loaded = false

    /** 저장된 상태 (타일 · 컴플리케이션은 앱이 새로 뜬 상태에서 불리므로 저장소에서 읽는다) */
    fun current(context: Context): WearAlarm? {
        if (!loaded) {
            loaded = true
            _state.value = read(context)
        }
        return _state.value
    }

    fun fromDataMap(map: DataMap): WearAlarm? {
        if (!map.getBoolean("active", false)) return null
        return WearAlarm(
            type = map.getString("type", "ALIGHT"),
            routeNo = map.getString("routeNo", ""),
            color = map.getInt("color", 0xFF00B9BE.toInt()),
            title = map.getString("title", ""),
            subtitle = map.getString("subtitle", ""),
            time = map.getString("time", ""),
            chip = map.getString("chip", ""),
            status = map.getString("status", ""),
            progress = map.getFloat("progress", -1f)
        )
    }

    fun update(context: Context, alarm: WearAlarm?) {
        loaded = true
        _state.value = alarm
        save(context, alarm)
        AlarmOngoing.show(context, alarm)
        refreshSurfaces(context)
    }

    /** 워치에서 [해제] — 폰에 알람을 끄라고 보내고, 워치 화면은 바로 비운다 */
    suspend fun requestCancel(context: Context) {
        update(context, null)
        runCatching {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            val client = Wearable.getMessageClient(context)
            nodes.forEach { client.sendMessage(it.id, PATH_CANCEL, ByteArray(0)).await() }
        }
    }

    /** 타일 · 워치 페이스 컴플리케이션에 다시 그려 달라고 한다 */
    private fun refreshSurfaces(context: Context) {
        runCatching { TileService.getUpdater(context).requestUpdate(AlarmTileService::class.java) }
        runCatching {
            ComplicationDataSourceUpdateRequester.create(
                context, ComponentName(context, RemainingComplicationService::class.java)
            ).requestUpdateAll()
        }
    }

    private fun save(context: Context, a: WearAlarm?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            clear()
            if (a != null) {
                putString("type", a.type)
                putString("routeNo", a.routeNo)
                putInt("color", a.color)
                putString("title", a.title)
                putString("subtitle", a.subtitle)
                putString("time", a.time)
                putString("chip", a.chip)
                putString("status", a.status)
                putFloat("progress", a.progress)
            }
        }
    }

    private fun read(context: Context): WearAlarm? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val type = p.getString("type", null) ?: return null
        return WearAlarm(
            type = type,
            routeNo = p.getString("routeNo", "").orEmpty(),
            color = p.getInt("color", 0xFF00B9BE.toInt()),
            title = p.getString("title", "").orEmpty(),
            subtitle = p.getString("subtitle", "").orEmpty(),
            time = p.getString("time", "").orEmpty(),
            chip = p.getString("chip", "").orEmpty(),
            status = p.getString("status", "").orEmpty(),
            progress = p.getFloat("progress", -1f)
        )
    }
}
