package com.cheon.ccbuswidget.alarm

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 승하차 알람 한 건. 한 번에 하나만 켤 수 있다.
 *
 * - 승차 알람: [stopNodeId] 정류장에서 [routeNo] 버스를 탄다 → 버스가 2정류장 전에 오면 알린다.
 * - 하차 알람: [stops] 의 첫 정류장(탄 곳)에서 마지막 정류장(내릴 곳)까지 → 휴대폰 GPS 로 따라가며
 *   2정류장 · 1정류장 전에 알린다.
 */
data class BusAlarm(
    val type: Type,
    val routeId: String,
    val routeNo: String,
    val routeType: String?,
    /** 승차: 타는 정류장 / 하차: 내릴 정류장 */
    val stopNodeId: String,
    val stopName: String,
    /** 하차 알람의 구간 (탄 정류장 ~ 내릴 정류장, 노선 순서대로). 승차 알람은 비어 있다 */
    val stops: List<Point> = emptyList(),
    /** 하차 알람의 출발 정류장 (노선 창에서 알람 표시를 가리는 데 쓴다) */
    val fromNodeId: String = ""
) {
    enum class Type { BOARD, ALIGHT }

    data class Point(val nodeId: String, val name: String, val lat: Double, val lng: Double)

    fun isBoardingAt(nodeId: String, routeId: String) =
        type == Type.BOARD && stopNodeId == nodeId && this.routeId == routeId

    fun isAlightingOn(routeId: String) = type == Type.ALIGHT && this.routeId == routeId

    internal fun toJson(): String = JSONObject()
        .put("type", type.name)
        .put("routeId", routeId).put("routeNo", routeNo).put("routeType", routeType ?: "")
        .put("stopNodeId", stopNodeId).put("stopName", stopName).put("fromNodeId", fromNodeId)
        .put("stops", JSONArray().apply {
            stops.forEach {
                put(JSONObject().put("id", it.nodeId).put("n", it.name).put("a", it.lat).put("o", it.lng))
            }
        })
        .toString()

    internal companion object {
        fun fromJson(text: String): BusAlarm? = runCatching {
            val o = JSONObject(text)
            val arr = o.optJSONArray("stops") ?: JSONArray()
            BusAlarm(
                type = Type.valueOf(o.getString("type")),
                routeId = o.getString("routeId"),
                routeNo = o.getString("routeNo"),
                routeType = o.optString("routeType").ifBlank { null },
                stopNodeId = o.getString("stopNodeId"),
                stopName = o.optString("stopName"),
                fromNodeId = o.optString("fromNodeId"),
                stops = (0 until arr.length()).map { i ->
                    val s = arr.getJSONObject(i)
                    Point(s.getString("id"), s.optString("n"), s.getDouble("a"), s.getDouble("o"))
                }
            )
        }.getOrNull()
    }
}

/** 지금 켜져 있는 알람과 진행 상황. 화면(배너 · 아이콘)과 알람 서비스가 같이 본다. */
object AlarmStore {
    private const val PREFS = "bus_alarm"
    private const val KEY = "active"

    private val _active = MutableStateFlow<BusAlarm?>(null)
    val active: StateFlow<BusAlarm?> = _active

    /** 남은 정류장 수 (모르면 null) */
    private val _remaining = MutableStateFlow<Int?>(null)
    val remaining: StateFlow<Int?> = _remaining

    @Volatile private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val text = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
        _active.value = text?.let { BusAlarm.fromJson(it) }
    }

    internal fun set(context: Context, alarm: BusAlarm?) {
        loaded = true
        _active.value = alarm
        _remaining.value = null
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            if (alarm == null) remove(KEY) else putString(KEY, alarm.toJson())
        }
    }

    /** 새로 켠 알람이라 시작 안내를 들려줘야 하는지 (한 번 꺼내면 false 가 된다) */
    @Volatile private var startSoundPending = false

    internal fun markStarted() { startSoundPending = true }

    internal fun consumeStartSound(): Boolean = startSoundPending.also { startSoundPending = false }

    internal fun setRemaining(value: Int?) {
        _remaining.value = value
    }
}
