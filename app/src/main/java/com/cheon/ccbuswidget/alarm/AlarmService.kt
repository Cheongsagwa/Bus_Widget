package com.cheon.ccbuswidget.alarm

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.cheon.ccbuswidget.R
import com.cheon.ccbuswidget.alarm.AlarmService.Companion.BOARD_ALERT_STOPS
import com.cheon.ccbuswidget.alarm.AlarmService.Companion.NEAR_STOPS
import com.cheon.ccbuswidget.alarm.AlarmService.Companion.PROGRESS_PER_STOP
import com.cheon.ccbuswidget.data.api.TagoApi
import com.cheon.ccbuswidget.data.local.WidgetStore
import com.cheon.ccbuswidget.data.model.RouteKind
import com.cheon.ccbuswidget.feature.stopdetail.StopDetailActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * 승하차 알람을 지켜보는 포그라운드 서비스.
 *
 * - 승차: 30초마다 정류장 도착 정보를 물어, 버스가 [BOARD_ALERT_STOPS] 정류장 전에 오면 알리고 끝낸다.
 * - 하차: 휴대폰 GPS 로 지금 가장 가까운 노선 정류장을 따라가며 2정류장 · 1정류장 전에 알리고,
 *   내릴 정류장에 닿으면 알리고 끝낸다.
 *
 * 진행 상황은 알림 한 줄(안드로이드 16 이상은 Live Update → 상태바 칩 · 삼성 Now Bar)로 계속 보여 준다.
 */
class AlarmService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pollJob: Job? = null
    private var arrivalJob: Job? = null
    private var locationListener: LocationListener? = null
    private var running: BusAlarm? = null

    /** 하차: 지금까지 지난(도착한) 정류장 번호. 앞으로만 간다 */
    private var passedIndex = 0
    /** 이미 울린 알림 (같은 단계에서 여러 번 울리지 않도록) */
    private val alerted = mutableSetOf<Int>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AlarmStore.init(this)
        if (intent?.action == ACTION_CANCEL) {
            finish()
            return START_NOT_STICKY
        }
        val alarm = AlarmStore.active.value
        if (alarm == null) {
            finish()
            return START_NOT_STICKY
        }
        ensureChannels(this)
        val type = if (alarm.type == BusAlarm.Type.ALIGHT) {
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        } else {
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        }
        val started = runCatching {
            ServiceCompat.startForeground(this, STATUS_ID, statusNotification(alarm, AlarmStore.remaining.value), type)
        }.isSuccess
        if (!started) {
            // 권한이 없거나 백그라운드에서 시작할 수 없는 경우
            AlarmStore.set(this, null)
            stopSelf()
            return START_NOT_STICKY
        }
        if (running != alarm) begin(alarm)
        return START_NOT_STICKY
    }

    private fun begin(alarm: BusAlarm) {
        stopWatching()
        running = alarm
        shownRemaining = -1
        shownProgress = -1
        progressPos = 0
        alerted.clear()
        boardOrders = null
        // 켤 때 "승하차 알림을 시작합니다" 안내 (앱을 다시 열어 이어서 지켜볼 때는 다시 나오지 않는다)
        if (AlarmStore.consumeStartSound()) playVoice(R.raw.alarm_start)
        passedIndex = 0
        arrivedAt = null
        when (alarm.type) {
            BusAlarm.Type.BOARD -> pollJob = scope.launch { pollArrivals(alarm) }
            BusAlarm.Type.ALIGHT -> {
                update(alarm, alarm.stops.lastIndex.coerceAtLeast(0))
                watchLocation(alarm)
            }
        }
    }

    // ---- 승차 ----------------------------------------------------------------

    private suspend fun pollArrivals(alarm: BusAlarm) {
        val apiKey = WidgetStore.getApiKey(this)
        val city = WidgetStore.getCityCode(this)
        /** 지난번에 본 남은 정류장 수 (버스가 지나갔는지 가리는 데 쓴다) */
        var prev: Int? = null
        while (scope.isActive) {
            // 남은 정류장 수는 도착예정정보의 '몇 번째 전' 값이 아니라 실제 버스 위치로 센다.
            // (도착예정정보의 값은 버스 위치보다 늦게 바뀌어 1정류장씩 어긋날 때가 많았다)
            // null = 이번 조회 실패(잠깐 끊김 등) → 판단하지 않고 다음에 다시 본다
            val found: Result<Int?>? = stopsLeftByLocation(apiKey, city, alarm)
            if (found != null) {
                val left = found.getOrNull()
                // 버스가 정류장에 닿았다: 버스 위치가 이 정류장이 되었거나(0),
                // 1정류장 안까지 왔던 버스가 목록에서 사라졌거나 다음 차(더 먼 차)로 바뀌었다
                val arrived = left == 0 || (prev != null && prev <= 1 && (left == null || left > prev))
                if (arrived) {
                    alert(alarm, "${alarm.routeNo}번 버스 도착", "${alarm.stopName} 정류장에서 타세요")
                    finish()
                    return
                }
                update(alarm, left)
                if (left != null && left <= BOARD_ALERT_STOPS && alerted.add(BOARD_ALERT_STOPS)) {
                    alert(alarm, "${alarm.routeNo}번 버스가 ${left}정류장 전이에요",
                        "${alarm.stopName} 정류장에서 탈 준비를 하세요",
                        voice = if (left == 2) R.raw.alarm_board_2 else null)
                }
                if (left != null && left <= 1 && alerted.add(1)) {
                    alert(alarm, "${alarm.routeNo}번 버스가 곧 도착해요", "${alarm.stopName} 전 정류장에 있어요",
                        voice = R.raw.alarm_board_soon)
                }
                prev = left ?: prev
            }
            // 가까워질수록 자주, 멀면 천천히 묻는다 (배터리 · 데이터 절약)
            val near = prev
            delay(
                when {
                    near == null || near > NEAR_STOPS -> POLL_FAR_MS
                    near <= BOARD_ALERT_STOPS -> POLL_CLOSE_MS
                    else -> POLL_MS
                }.milliseconds
            )
        }
    }

    /** 이 노선에서 타는 정류장의 순번들 (순환 노선이면 여러 개일 수 있다) */
    private var boardOrders: List<Int>? = null

    /**
     * 버스 위치(노선 위 몇 번째 정류장에 있는지)로 타는 정류장까지 남은 정류장 수를 센다.
     * 0 = 버스가 이 정류장에 있음, 1 = 바로 전 정류장, … / 오는 버스가 없으면 성공(null).
     * 조회 실패면 null 을 돌려준다. 노선 경로를 못 받으면 도착예정정보 값으로 대신한다.
     */
    private suspend fun stopsLeftByLocation(apiKey: String, city: String, alarm: BusAlarm): Result<Int?>? {
        val orders = boardOrders ?: runCatching { TagoApi.routeStops(apiKey, city, alarm.routeId) }.getOrNull()
            ?.filter { it.nodeId == alarm.stopNodeId }?.map { it.order }
            ?.also { if (it.isNotEmpty()) boardOrders = it }
        if (orders.isNullOrEmpty()) {
            val arrivals = runCatching { TagoApi.arrivals(apiKey, city, alarm.stopNodeId) }.getOrNull() ?: return null
            return Result.success(
                arrivals.filter { it.routeId == alarm.routeId || it.routeNo == alarm.routeNo }
                    .minOfOrNull { it.prevStationCount.coerceAtLeast(0) }
            )
        }
        val buses = runCatching { TagoApi.busLocations(apiKey, city, alarm.routeId) }.getOrNull() ?: return null
        val left = buses.filter { it.order >= 0 }
            .flatMap { bus -> orders.map { it - bus.order } }
            .filter { it >= 0 }
            .minOrNull()
        return Result.success(left)
    }

    // ---- 하차 ----------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun watchLocation(alarm: BusAlarm) {
        val lm = getSystemService(LocationManager::class.java) ?: return
        if (!hasLocationPermission(this) || alarm.stops.size < 2) {
            finish()
            return
        }
        // 람다로 만들면 안드로이드 10 이하에서 onStatusChanged 등이 없어 죽는다 — 모두 구현한다
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) = onLocation(alarm, location)
            @Deprecated("API 29 이하 호환")
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        locationListener = listener
        // fused(구글 위치 · 안드로이드 12+) 가 있으면 그것도 같이 쓴다 — GPS 가 약한 버스 안에서도 위치가 잘 들어온다
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }
        for (provider in providers) {
            if (runCatching { lm.isProviderEnabled(provider) }.getOrDefault(false)) {
                runCatching {
                    lm.requestLocationUpdates(
                        provider, LOCATION_INTERVAL_MS, LOCATION_MIN_DISTANCE_M, listener, Looper.getMainLooper()
                    )
                }
            }
        }
        // 위 구독만으로는 기기(절전 · 백그라운드 제한)에 따라 위치가 안 들어올 때가 있다.
        // 그래서 일정 간격으로 '지금 위치'를 직접 한 번씩 받아 온다.
        pollJob = scope.launch {
            val enabled = providers.filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
            while (true) {
                for (provider in enabled) {
                    val loc = currentLocation(lm, provider)
                    if (loc != null) {
                        onLocation(alarm, loc)
                        break
                    }
                }
                delay(LOCATION_POLL_MS.milliseconds)
            }
        }
    }

    /** 한 번 위치를 받아 온다 (최대 8초) */
    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(lm: LocationManager, provider: String): Location? =
        kotlinx.coroutines.withTimeoutOrNull(8.seconds) {
            kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                val signal = android.os.CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                runCatching {
                    androidx.core.location.LocationManagerCompat.getCurrentLocation(
                        lm, provider, signal, ContextCompat.getMainExecutor(this@AlarmService)
                    ) { loc -> if (cont.isActive) cont.resumeWith(Result.success(loc)) }
                }.onFailure { if (cont.isActive) cont.resumeWith(Result.success(null)) }
            }
        }

    private fun onLocation(alarm: BusAlarm, loc: Location) {
        if (running != alarm) return
        if (loc.hasAccuracy() && loc.accuracy > MAX_ACCURACY_M) return
        val stops = alarm.stops
        val last = stops.lastIndex
        val target = stops[last]

        // 내릴 정류장에 이미 닿았으면: 정류장을 벗어나거나(내려서 걸어가거나 버스가 떠남) 잠시 뒤에 끝낸다
        arrivedAt?.let { at ->
            if (distance(loc, target) > LEAVE_M || android.os.SystemClock.elapsedRealtime() - at > ARRIVED_KEEP_MS) {
                finish()
            }
            return
        }

        // 정류장 점이 아니라 정류장 사이 '구간' 위 어디쯤인지로 따라간다.
        // (버스가 빨리 지나가면 5초 간격 위치로는 정류장 바로 옆을 한 번도 못 찍을 수 있다)
        // 지금 지난 정류장부터 몇 구간 앞까지만 본다 (순환 노선에서 반대편으로 튀지 않도록)
        var bestSeg = -1
        var bestDist = Double.MAX_VALUE
        var bestRemain = 0.0
        var bestLen = 0.0
        for (j in passedIndex until minOf(last, passedIndex + LOOKAHEAD)) {
            val (dist, remainToNext, len) = projectOnSegment(loc, stops[j], stops[j + 1])
            if (dist < bestDist) {
                bestDist = dist
                bestSeg = j
                bestRemain = remainToNext
                bestLen = len
            }
        }
        if (bestSeg >= 0 && bestDist <= ON_ROUTE_M) {
            // 구간 끝(다음 정류장)에 거의 닿았으면 그 정류장까지 지난 것으로 본다
            val passed = if (bestRemain <= REACHED_M) bestSeg + 1 else bestSeg
            if (passed > passedIndex) passedIndex = passed
            // 알림 막대의 버스 아이콘은 정류장 사이에서도 실제 위치를 따라 움직인다 (앞으로만)
            val frac = if (bestLen > 0.0) (1.0 - bestRemain / bestLen).coerceIn(0.0, 1.0) else 1.0
            val pos = ((bestSeg + frac) * PROGRESS_PER_STOP).toInt()
            if (pos > progressPos) progressPos = pos
        }
        progressPos = maxOf(progressPos, passedIndex * PROGRESS_PER_STOP)

        val left = last - passedIndex
        update(alarm, left)
        if (left <= 0) {
            arrivedAt = android.os.SystemClock.elapsedRealtime()
            alert(alarm, "${alarm.stopName} 도착", "지금 내리세요")
            // 멈춰 서 있으면 위치가 더 안 들어올 수 있으니, 시간이 지나면 알아서 끝낸다
            arrivalJob = scope.launch {
                delay(ARRIVED_KEEP_MS.milliseconds)
                if (running == alarm) finish()
            }
            return
        }
        // 한 번에 여러 정류장을 건너뛰어도 알림을 놓치지 않도록 '이하'로 본다
        if (left <= 2 && alerted.add(2) && left == 2) {
            alert(alarm, "2정류장 뒤에 내려요", "${alarm.routeNo}번 · ${alarm.stopName} 하차",
                voice = R.raw.alarm_alight_2)
        }
        if (left <= 1 && alerted.add(1)) {
            alert(alarm, "다음 정류장에서 내리세요", "${alarm.routeNo}번 · ${alarm.stopName} 하차",
                voice = R.raw.alarm_alight_1)
        }
    }

    /** 내릴 정류장에 닿은 시각 (닿기 전에는 null) */
    private var arrivedAt: Long? = null

    private fun distance(loc: Location, p: BusAlarm.Point): Float {
        val d = FloatArray(1)
        Location.distanceBetween(loc.latitude, loc.longitude, p.lat, p.lng, d)
        return d[0]
    }

    /**
     * 위치를 정류장 a→b 구간에 투영한다. (구간까지 거리 m, 투영점에서 b 까지 남은 거리 m)
     * 정류장 사이는 짧으니 평면으로 근사한다.
     */
    private fun projectOnSegment(loc: Location, a: BusAlarm.Point, b: BusAlarm.Point): Triple<Double, Double, Double> {
        val mPerLat = 111_320.0
        val mPerLng = 111_320.0 * kotlin.math.cos(Math.toRadians(a.lat))
        val bx = (b.lng - a.lng) * mPerLng
        val by = (b.lat - a.lat) * mPerLat
        val px = (loc.longitude - a.lng) * mPerLng
        val py = (loc.latitude - a.lat) * mPerLat
        val len2 = bx * bx + by * by
        val t = if (len2 <= 0.0) 1.0 else ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
        val dx = px - bx * t
        val dy = py - by * t
        val len = kotlin.math.sqrt(len2)
        return Triple(kotlin.math.sqrt(dx * dx + dy * dy), len * (1.0 - t), len)
    }

    // ---- 알림 ----------------------------------------------------------------

    /** 알림에 마지막으로 올린 남은 정류장 수 — 바뀔 때만 알림을 다시 올린다 */
    private var shownRemaining: Int? = -1
    /** 하차: 알림 막대 위치 (정류장 하나 = [PROGRESS_PER_STOP]) */
    private var progressPos = 0
    private var shownProgress = -1

    private fun update(alarm: BusAlarm, remaining: Int?) {
        // 남은 정류장 수가 바뀌거나, 막대 위 버스가 눈에 띄게(정류장 사이의 1/10) 움직였을 때만 다시 올린다
        if (remaining == shownRemaining && progressPos - shownProgress < PROGRESS_PER_STOP / 10) return
        shownRemaining = remaining
        shownProgress = progressPos
        AlarmStore.setRemaining(remaining)
        val nm = getSystemService(NotificationManager::class.java) ?: return
        runCatching { nm.notify(STATUS_ID, statusNotification(alarm, remaining)) }
    }

    private fun statusNotification(alarm: BusAlarm, remaining: Int?): Notification {
        val subtitle = AlarmText.subtitle(alarm, remaining)
        val time = AlarmText.timeLine(alarm, remaining)
        val b = Notification.Builder(this, CHANNEL_STATUS)
            // 아이콘은 정류장 화면의 '버스 위치' 아이콘으로 통일 (작은 아이콘은 흰 버스, 큰 아이콘은 노선 색 원 + 흰 버스)
            .setSmallIcon(R.drawable.ic_alarm_bus)
            .setLargeIcon(routeIcon(alarm))
            // 진행 막대 · 상태바 칩 · Now Bar 의 강조색 = 노선 유형 대표색 (지선 · 간선 · 마을)
            .setColor(routeColor(alarm))
            .setContentTitle(AlarmText.title(alarm, remaining))
            .setContentText(subtitle)
            // 펼치면 둘째 줄에 남은 시간 (예: 하차까지 12개 정류장 / 11분 후 하차)
            .setStyle(Notification.BigTextStyle().bigText(if (time != null) "$subtitle\n$time" else subtitle))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setContentIntent(openAppIntent())
            .addAction(
                Notification.Action.Builder(null, "해제", cancelIntent(this)).build()
            )
        if (alarm.type == BusAlarm.Type.ALIGHT && alarm.stops.size >= 2 && remaining != null) {
            val total = alarm.stops.lastIndex * PROGRESS_PER_STOP
            val done = maxOf(progressPos, (alarm.stops.lastIndex - remaining) * PROGRESS_PER_STOP).coerceIn(0, total)
            // 안드로이드 16: 진행 막대를 노선 색으로 칠하고, 막대 위(지금 위치)에 버스 아이콘을 올린다
            if (!applyProgressStyle(b, alarm, total, done, subtitle, time)) {
                b.setProgress(total, done, false)
            }
        }
        if (Build.VERSION.SDK_INT >= 36) {
            // 안드로이드 16 Live Update — 상태바 칩 · 잠금화면 위쪽 · 삼성 One UI 8 Now Bar 에 뜬다
            // 설치된 SDK 36 에 이 API 가 없을 수 있어(QPR 에서 추가) 리플렉션으로 부른다
            callBuilder(b, "setRequestPromotedOngoing", Boolean::class.javaPrimitiveType!!, true)
            // 상태바 칩은 남은 정류장 수만 (예: 12개) — 상태바 공간을 적게 차지하도록
            AlarmText.chip(alarm, remaining)?.let {
                // 실제 API 는 String 인자 — 기기마다 달라도 되도록 둘 다 시도한다
                if (!callBuilder(b, "setShortCriticalText", String::class.java, it)) {
                    callBuilder(b, "setShortCriticalText", CharSequence::class.java, it)
                }
            }
        }
        return b.build()
    }

    /**
     * Notification.ProgressStyle (API 36) — 막대 색 = 노선 대표색, 막대 위 추적 아이콘 = 노선 색 버스 아이콘.
     * SDK 에 따라 클래스가 없을 수 있어 리플렉션으로 만든다. 성공하면 true.
     */
    private fun applyProgressStyle(
        b: Notification.Builder, alarm: BusAlarm, total: Int, done: Int, subtitle: String, time: String?
    ): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        return runCatching {
            val styleCls = Class.forName("android.app.Notification\$ProgressStyle")
            val segCls = Class.forName("android.app.Notification\$ProgressStyle\$Segment")
            val intT = Int::class.javaPrimitiveType!!
            val color = routeColor(alarm)
            val seg = segCls.getConstructor(intT).newInstance(total)
            segCls.getMethod("setColor", intT).invoke(seg, color)
            val style = styleCls.getConstructor().newInstance()
            styleCls.getMethod("setProgressSegments", List::class.java).invoke(style, listOf(seg))
            styleCls.getMethod("setProgress", intT).invoke(style, done)
            styleCls.getMethod("setProgressTrackerIcon", android.graphics.drawable.Icon::class.java)
                .invoke(style, routeIcon(alarm))
            runCatching {
                styleCls.getMethod("setStyledByProgress", Boolean::class.javaPrimitiveType!!).invoke(style, false)
            }
            b.setStyle(style as Notification.Style)
            // 이 모양에서는 본문이 한 줄이라 남은 시간을 같은 줄에 붙이고, 오른쪽 큰 아이콘은 뺀다
            b.setContentText(if (time != null) "$subtitle · $time" else subtitle)
            b.setLargeIcon(null as android.graphics.drawable.Icon?)
            true
        }.getOrDefault(false)
    }

    /** 노선 유형 대표색 */
    private fun routeColor(alarm: BusAlarm): Int =
        ContextCompat.getColor(this, RouteKind.of(alarm.routeNo, alarm.routeType).colorRes)

    private var iconCache: Pair<String, android.graphics.drawable.Icon>? = null

    /** 노선 색 원 + 흰 테두리 + 흰 버스 (정류장 화면 · 배너의 버스 아이콘과 같은 모양, Figma 24 기준) */
    private fun routeIcon(alarm: BusAlarm): android.graphics.drawable.Icon {
        val key = "${alarm.routeNo}|${alarm.routeType}"
        iconCache?.takeIf { it.first == key }?.let { return it.second }
        val size = (48 * resources.displayMetrics.density).toInt()
        val unit = size / 24f
        val bmp = androidx.core.graphics.createBitmap(size, size)
        val canvas = android.graphics.Canvas(bmp)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.color = routeColor(alarm)
        canvas.drawCircle(size / 2f, size / 2f, 10f * unit, paint)
        ContextCompat.getDrawable(this, R.drawable.ic_alarm_bus)?.mutate()?.let { bus ->
            bus.setTint(android.graphics.Color.WHITE)
            val inset = (5f * unit).toInt()
            bus.setBounds(inset, inset, size - inset, size - inset)
            bus.draw(canvas)
        }
        return android.graphics.drawable.Icon.createWithBitmap(bmp).also { iconCache = key to it }
    }

    /** 있으면 부르고 성공 여부를 돌려준다 */
    private fun callBuilder(b: Notification.Builder, name: String, type: Class<*>, value: Any): Boolean =
        runCatching { Notification.Builder::class.java.getMethod(name, type).invoke(b, value) }.isSuccess

    /**
     * 알림을 띄운다. [voice] 가 있으면 알림 소리 대신 그 음성 안내를 튼다
     * (음성이 있는 알림은 소리 없는 채널로 보내 기본 알림음과 겹치지 않게 한다).
     */
    private fun alert(alarm: BusAlarm, title: String, text: String, voice: Int? = null) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val n = Notification.Builder(this, if (voice != null) CHANNEL_ALERT_VOICE else CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_alarm_bus)
            .setLargeIcon(routeIcon(alarm))
            .setColor(routeColor(alarm))
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        runCatching { nm.notify(ALERT_ID + alarm.routeNo.hashCode() % 1000, n) }
        voice?.let { playVoice(it) }
    }


    /** 이어폰 · 헤드셋이 연결돼 있는지 (유선 · 블루투스 · USB) */
    private fun headphonesConnected(am: android.media.AudioManager): Boolean {
        val types = buildSet {
            add(android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET)
            add(android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
            add(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            add(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
            add(android.media.AudioDeviceInfo.TYPE_USB_HEADSET)
            if (Build.VERSION.SDK_INT >= 31) {
                add(android.media.AudioDeviceInfo.TYPE_BLE_HEADSET)
                add(android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER)
            }
        }
        return am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).any { it.type in types }
    }

    /** 음성 안내 재생 (내비게이션 안내처럼 — 음악이 나오고 있으면 잠깐 줄였다가 되돌린다) */
    private fun playVoice(resId: Int) {
        val am0 = getSystemService(android.media.AudioManager::class.java)
        // 무음 · 진동 모드에서는 이어폰(유선 · 블루투스)이 연결돼 있을 때만 안내한다 — 이어폰으로만 들린다
        val silent = am0 != null && am0.ringerMode != android.media.AudioManager.RINGER_MODE_NORMAL
        if (silent && !headphonesConnected(am0)) return
        player?.release()
        val attrs = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val am = getSystemService(android.media.AudioManager::class.java)
        val focus = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs).build()
        player = runCatching {
            android.media.MediaPlayer.create(applicationContext, resId, attrs, am?.generateAudioSessionId() ?: 0)
        }.getOrNull()?.apply {
            am?.requestAudioFocus(focus)
            setOnCompletionListener {
                am?.abandonAudioFocusRequest(focus)
                it.release()
                if (player === it) player = null
            }
            start()
        }
    }

    /**
     * 알림을 누르면 앱 아이콘을 누른 것과 똑같이 연다.
     * 예전에는 화면을 직접 지정해 열어서, 앱이 이미 떠 있으면 그 위에 새 화면이 하나 더 겹쳐 뜨면서
     * 스플래시(다크 모드에서는 검은 화면)에서 넘어가지 못했다.
     * 런처 인텐트로 열면 떠 있던 앱은 그대로 앞으로 오고, 꺼져 있으면 처음부터 켜진다.
     */
    private fun openAppIntent(): PendingIntent {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, StopDetailActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
        launch.setPackage(null)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return PendingIntent.getActivity(
            this, 0, launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    // ---- 정리 ----------------------------------------------------------------

    private fun stopWatching() {
        pollJob?.cancel()
        pollJob = null
        arrivalJob?.cancel()
        arrivalJob = null
        locationListener?.let { l ->
            getSystemService(LocationManager::class.java)?.let { runCatching { it.removeUpdates(l) } }
        }
        locationListener = null
    }

    /** 알람을 끄고 서비스를 내린다 */
    private fun finish() {
        // "승하차 알림을 종료합니다" — 서비스가 내려가도 끝까지 나오도록 앱 전체에서 한 번 튼다
        playVoice(R.raw.alarm_end)
        stopWatching()
        running = null
        AlarmStore.set(this, null)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopWatching()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_STATUS = "alarm_status"
        private const val CHANNEL_ALERT = "alarm_alert"
        /** 음성 안내가 나가는 알림 (채널 자체 소리 없음 · 진동만) */
        private const val CHANNEL_ALERT_VOICE = "alarm_alert_voice"
        private const val STATUS_ID = 4100
        private const val ALERT_ID = 4200
        private const val ACTION_CANCEL = "com.cheon.ccbuswidget.alarm.CANCEL"

        /** 음성 안내 재생기 — 서비스가 끝난 뒤에도 종료 안내가 끊기지 않도록 서비스 밖(앱 전체)에 둔다 */
        private var player: android.media.MediaPlayer? = null

        /** 승차 알람: 버스가 이만큼 정류장 전에 오면 알린다 */
        const val BOARD_ALERT_STOPS = 2
        private const val POLL_MS = 30_000L
        /** 알림 막대에서 정류장 한 칸의 눈금 수 */
        private const val PROGRESS_PER_STOP = 100
        /** 버스가 [NEAR_STOPS] 정류장보다 멀면 이 간격으로 묻는다 */
        private const val POLL_FAR_MS = 60_000L
        /** 버스가 2정류장 안으로 들어오면 이 간격으로 묻는다 (도착을 놓치지 않도록) */
        private const val POLL_CLOSE_MS = 15_000L
        private const val NEAR_STOPS = 6
        /** 이만큼 움직였을 때만 위치를 받는다 (정류장 사이 간격보다 충분히 작게) */
        private const val LOCATION_MIN_DISTANCE_M = 15f
        private const val LOCATION_INTERVAL_MS = 5_000L
        /** 위치를 직접 받아 오는 간격 (구독이 멈췄을 때의 안전장치) */
        private const val LOCATION_POLL_MS = 10_000L
        /** 이 거리 안에 들어오면 그 정류장을 지난 것으로 본다 */
        private const val REACHED_M = 60.0
        /** 노선 구간에서 이만큼 안이면 버스 안에 있는 것으로 본다 */
        private const val ON_ROUTE_M = 80.0
        /** 내릴 정류장에 닿은 뒤 이만큼 멀어지면(내려서 걸어감 · 버스가 떠남) 알람을 끝낸다 */
        private const val LEAVE_M = 150f
        /** 내릴 정류장에 닿은 뒤 최대 이만큼 '이번정류장 하차'를 보여 준다 */
        private const val ARRIVED_KEEP_MS = 3 * 60_000L
        private const val MAX_ACCURACY_M = 150f
        private const val LOOKAHEAD = 6

        /** 알람을 켠다 (이미 켜진 알람은 바뀐다) */
        fun start(context: Context, alarm: BusAlarm) {
            AlarmStore.set(context, alarm)
            AlarmStore.markStarted()
            ContextCompat.startForegroundService(context, Intent(context, AlarmService::class.java))
        }

        /** 알람을 끈다 */
        fun cancel(context: Context) {
            AlarmStore.set(context, null)
            runCatching { context.startService(cancelIntentRaw(context)) }
        }

        /** 앱을 다시 열었을 때, 켜져 있던 알람이 있으면 이어서 지켜본다 */
        fun resume(context: Context) {
            AlarmStore.init(context)
            if (AlarmStore.active.value != null) {
                runCatching {
                    ContextCompat.startForegroundService(context, Intent(context, AlarmService::class.java))
                }.onFailure { AlarmStore.set(context, null) }
            }
        }

        fun hasLocationPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        private fun cancelIntentRaw(context: Context) =
            Intent(context, AlarmService::class.java).setAction(ACTION_CANCEL)

        private fun cancelIntent(context: Context): PendingIntent = PendingIntent.getService(
            context, 1, cancelIntentRaw(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        private fun ensureChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_STATUS, "승하차 알람 진행 상황", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply {
                        description = "남은 정류장 수를 계속 보여 줍니다"
                        setSound(null, null)
                        enableVibration(false)
                    }
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ALERT, "승하차 알림", NotificationManager.IMPORTANCE_HIGH)
                    .apply {
                        description = "탈 버스가 다가오거나 내릴 정류장이 가까워지면 울립니다"
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0, 400, 200, 400)
                    }
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ALERT_VOICE, "승하차 음성 안내", NotificationManager.IMPORTANCE_HIGH)
                    .apply {
                        description = "\"하차까지 두 정류장 남았습니다\" 같은 음성 안내와 함께 울립니다"
                        setSound(null, null)
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0, 400, 200, 400)
                    }
            )
        }
    }
}

/** 배너와 알림에 같이 쓰는 문구 */
object AlarmText {
    /** 정류장 하나에 걸리는 시간(분) 어림값 — 하차알람 설정 화면과 같은 값 */
    private const val MINUTES_PER_STOP = 1.5

    fun title(alarm: BusAlarm, remaining: Int?): String = when (alarm.type) {
        BusAlarm.Type.BOARD ->
            when (remaining) {
                null -> "${alarm.routeNo} 승차 대기중"
                0 -> "${alarm.routeNo} 도착"
                1 -> "${alarm.routeNo} 곧 도착 (전 정류장)"
                else -> "${alarm.routeNo} ${remaining}정류장 전"
            }
        // 하차: 지금 가고 있는 정류장이 제목 (예: 강원대학병원으로 이동 중)
        BusAlarm.Type.ALIGHT ->
            if (remaining == 0) "${alarm.stopName} 도착"
            else nextStopName(alarm, remaining)?.let { withRo(it) + " 이동 중" } ?: "${alarm.routeNo} 탑승중"
    }

    fun subtitle(alarm: BusAlarm, remaining: Int?): String = when (alarm.type) {
        BusAlarm.Type.BOARD -> "${alarm.stopName} 승차"
        BusAlarm.Type.ALIGHT -> when (remaining) {
            null -> "${alarm.stopName} 하차"
            0 -> "지금 내리세요"
            1 -> "다음 정류장 하차"
            else -> "하차까지 ${remaining}개 정류장"
        }
    }

    /** 알림 둘째 줄 — 남은 시간 (예: 11분 후 하차). 하차 알람만 */
    fun timeLine(alarm: BusAlarm, remaining: Int?): String? {
        if (alarm.type != BusAlarm.Type.ALIGHT || remaining == null || remaining <= 0) return null
        val min = kotlin.math.round(remaining * MINUTES_PER_STOP).toInt().coerceAtLeast(1)
        return "${min}분 후 하차"
    }

    /** 상태바 칩에 들어갈 짧은 글 (공간을 적게 차지하도록 숫자 위주) */
    fun chip(alarm: BusAlarm, remaining: Int?): String? = when {
        remaining == null -> null
        alarm.type == BusAlarm.Type.ALIGHT -> if (remaining <= 0) "도착" else "${remaining}개"
        else -> if (remaining <= 0) "도착" else "${remaining}전"
    }

    /** 지금 향하고 있는 정류장 (지난 정류장의 다음) */
    private fun nextStopName(alarm: BusAlarm, remaining: Int?): String? {
        val stops = alarm.stops
        if (stops.size < 2) return null
        val last = stops.lastIndex
        val passed = last - (remaining ?: last)
        return stops[(passed + 1).coerceIn(1, last)].name
    }

    /** 받침에 맞춰 '로' / '으로' 를 붙인다 (강원대학병원으로 · 춘천역으로 · 명동입구로) */
    private fun withRo(name: String): String {
        val c = name.trimEnd().lastOrNull { it.isLetterOrDigit() } ?: return name + "로"
        val eu = when (c) {
            in '가'..'힣' -> {
                val jong = (c - '가') % 28
                jong != 0 && jong != 8 // 받침 없음 · ㄹ받침은 '로'
            }
            '0', '3', '6' -> true // 영 · 삼 · 육
            else -> false
        }
        return name + if (eu) "으로" else "로"
    }
}
