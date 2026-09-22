package com.cheon.ccbuswidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.TypefaceSpan
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.cheon.ccbuswidget.R
import com.cheon.ccbuswidget.data.api.TagoApi
import com.cheon.ccbuswidget.data.local.WidgetStore
import com.cheon.ccbuswidget.data.model.BusArrival
import com.cheon.ccbuswidget.data.model.RouteKind
import com.cheon.ccbuswidget.data.model.RouteLabel
import com.cheon.ccbuswidget.data.model.WidgetConfig
import com.cheon.ccbuswidget.feature.settings.SettingsActivity
import com.cheon.ccbuswidget.feature.stopdetail.StopDetailActivity
import com.cheon.ccbuswidget.ui.theme.OneUiFont
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 위젯 화면을 구성/갱신하는 곳. */
object WidgetRenderer {

    /**
     * 위젯 글자에 시스템 글꼴(One UI Sans)을 입힌다.
     *
     * 위젯은 런처 프로세스가 그리기 때문에 Typeface 객체는 건너가지 못한다.
     * TypefaceSpan 은 '패밀리 이름'만 실어 보내고 런처가 거기서 다시 찾아 쓴다.
     * 이름을 못 찾은 기기(삼성이 아닌 경우)에서는 그대로 둔다.
     */
    private fun CharSequence.withSystemFont(): CharSequence {
        OneUiFont.ensureLoaded()
        val family = OneUiFont.familyName ?: return this
        val out = if (this is SpannableStringBuilder) this else SpannableString(this)
        out.setSpan(TypefaceSpan(family), 0, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return out
    }

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.KOREA)

    /** 이 시간이 지나면 '오래된 정보' 로 보고 가림막 + 새로고침 아이콘을 띄운다 */
    private const val STALE_AFTER_MS = 5 * 60 * 1000L

    /** 가림막 투명도 (20%) */
    private const val OVERLAY_ALPHA = 15

    /** 위젯 위에 씌우는 가림막 상태 */
    private enum class Overlay { NONE, REFRESHING, STALE }

    /** 위젯 하나를 새로 불러와 그린다. (IO 를 타므로 suspend) */
    suspend fun refresh(context: Context, appWidgetId: Int) {
        val manager = AppWidgetManager.getInstance(context)
        val config = WidgetStore.load(context, appWidgetId)

        // 눌렀는지 알 수 있게, 불러오기 전에 먼저 가림막부터 씌운다
        showOverlay(context, appWidgetId, config, Overlay.REFRESHING)

        if (config == null || !config.isValid) {
            manager.updateAppWidget(
                appWidgetId,
                buildBase(context, appWidgetId, null).also {
                    setMessage(it, "위젯을 길게 눌러 정류장을 설정해 주세요")
                }
            )
            return
        }

        val apiKey = WidgetStore.getApiKey(context)
        if (apiKey.isBlank()) {
            manager.updateAppWidget(
                appWidgetId,
                buildBase(context, appWidgetId, config).also {
                    setMessage(it, "앱을 열어 공공데이터 서비스키를 등록해 주세요")
                }
            )
            return
        }

        val views = buildBase(context, appWidgetId, config)
        try {
            val arrivals = TagoApi.arrivals(
                serviceKey = apiKey,
                cityCode = WidgetStore.getCityCode(context),
                nodeId = config.nodeId
            )
            fillRows(context, views, config, arrivals, columnsFor(context, manager, appWidgetId))
            WidgetStore.setLastUpdated(context, appWidgetId, System.currentTimeMillis())
            views.setTextViewText(R.id.updated_at, timeFormat.format(Date()).withSystemFont())
            // 5분 뒤 '오래됨' 표시 예약은 RefreshWorker 가 모든 위젯을 한 번에 건다
        } catch (e: Exception) {
            setMessage(views, e.message ?: "도착 정보를 불러오지 못했습니다")
        }

        manager.updateAppWidget(appWidgetId, views)
    }

    /** 새로고침한 지 오래됐음을 표시한다 (네트워크 없이 가림막만 덧씌운다) */
    fun markStale(context: Context, appWidgetId: Int) {
        val config = WidgetStore.load(context, appWidgetId) ?: return
        val last = WidgetStore.getLastUpdated(context, appWidgetId)
        if (last <= 0L || System.currentTimeMillis() - last < STALE_AFTER_MS) return
        showOverlay(context, appWidgetId, config, Overlay.STALE)
    }

    /**
     * 위젯 전체를 다시 그리지 않고 가림막만 바꾼다.
     * (다시 그리면 노선 목록을 또 불러와야 해서 partiallyUpdate 를 쓴다)
     */
    private fun showOverlay(
        context: Context,
        appWidgetId: Int,
        config: WidgetConfig?,
        state: Overlay
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_bus)
        applyOverlay(context, views, appWidgetId, config?.darkStyle ?: true, state)
        runCatching {
            AppWidgetManager.getInstance(context).partiallyUpdateAppWidget(appWidgetId, views)
        }
    }

    /** 모든 위젯 ID */
    fun allWidgetIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(
            ComponentName(context, BusWidgetProvider::class.java)
        )

    // ------------------------------------------------------------- 내부 구현

    private fun buildBase(context: Context, appWidgetId: Int, config: WidgetConfig?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_bus)
        val dark = config?.darkStyle ?: true
        val alphaPercent = config?.backgroundAlpha ?: 55

        // 색은 res/values/colors.xml 에서 가져온다
        val textColor = ContextCompat.getColor(
            context, if (dark) R.color.widget_text_dark else R.color.widget_text_light
        )
        val subColor = ContextCompat.getColor(
            context, if (dark) R.color.widget_subtext_dark else R.color.widget_subtext_light
        )
        val panelColor = ContextCompat.getColor(
            context, if (dark) R.color.widget_panel_dark else R.color.widget_panel_light
        )

        applyBackground(
            views = views,
            oneUiBlur = config?.oneUiBlur ?: WidgetStore.isSamsung,
            panelColor = panelColor,
            alphaPercent = alphaPercent
        )

        views.setInt(R.id.btn_refresh, "setColorFilter", subColor)
        views.setTextColor(R.id.stop_name, textColor)
        views.setTextColor(R.id.updated_at, subColor)
        views.setTextColor(R.id.message, subColor)

        views.setTextViewText(
            R.id.stop_name,
            (config?.nodeName?.ifBlank { "정류장 미설정" } ?: "정류장 미설정").withSystemFont()
        )
        views.setViewVisibility(R.id.message, android.view.View.GONE)
        views.removeAllViews(R.id.rows_container)

        // 새로고침 버튼
        val refreshIntent = Intent(context, BusWidgetProvider::class.java).apply {
            action = BusWidgetProvider.ACTION_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val refreshPending = PendingIntent.getBroadcast(
            context, appWidgetId, refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_refresh, refreshPending)
        views.setOnClickPendingIntent(R.id.refresh_overlay, refreshPending)

        // 마지막으로 불러온 지 오래됐으면 가림막을 띄워 둔다
        val last = WidgetStore.getLastUpdated(context, appWidgetId)
        val stale = last > 0L && System.currentTimeMillis() - last >= STALE_AFTER_MS
        applyOverlay(context, views, appWidgetId, dark, if (stale) Overlay.STALE else Overlay.NONE)

        // 본문 탭 → 정류장 상세 화면(지도 + 전체 노선 도착 정보)
        // 설정을 바꾸려면 위젯을 길게 눌러 재구성하거나 상세 화면의 설정 버튼을 쓴다.
        val openIntent = if (config == null) {
            Intent(context, SettingsActivity::class.java)
        } else {
            StopDetailActivity.intent(
                context = context,
                nodeId = config.nodeId,
                nodeName = config.nodeName,
                lat = config.gpsLat,
                lng = config.gpsLng,
                appWidgetId = appWidgetId
            )
        }
        openIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        val openPending = PendingIntent.getActivity(
            context, 100_000 + appWidgetId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.stop_name, openPending)
        views.setOnClickPendingIntent(R.id.rows_container, openPending)
        views.setOnClickPendingIntent(R.id.message, openPending)
        return views
    }

    /**
     * 가림막.
     *   새로고침 중  : 20% 가림막 + 도는 표시
     *   오래됨      : 20% 가림막 + 새로고침 아이콘 (누르면 새로고침)
     * 밝은 위젯에는 흰색, 어두운 위젯에는 검은색을 씌운다.
     */
    private fun applyOverlay(
        context: Context,
        views: RemoteViews,
        appWidgetId: Int,
        dark: Boolean,
        state: Overlay
    ) {
        val show = state != Overlay.NONE
        views.setViewVisibility(
            R.id.refresh_overlay,
            if (show) android.view.View.VISIBLE else android.view.View.GONE
        )
        if (!show) return

        views.setInt(R.id.overlay_bg, "setColorFilter", if (dark) Color.WHITE else Color.BLACK)
        views.setInt(R.id.overlay_bg, "setImageAlpha", OVERLAY_ALPHA)

        // 아이콘과 도는 표시는 같은 색 (글자색과 동일)
        views.setInt(
            R.id.overlay_refresh, "setColorFilter",
            ContextCompat.getColor(
                context, if (dark) R.color.widget_text_dark else R.color.widget_text_light
            )
        )

        val refreshing = state == Overlay.REFRESHING
        views.setViewVisibility(
            R.id.overlay_refresh,
            if (refreshing) android.view.View.GONE else android.view.View.VISIBLE
        )
        views.setViewVisibility(
            R.id.overlay_spinner_dark,
            if (refreshing && dark) android.view.View.VISIBLE else android.view.View.GONE
        )
        views.setViewVisibility(
            R.id.overlay_spinner_light,
            if (refreshing && !dark) android.view.View.VISIBLE else android.view.View.GONE
        )

        if (!refreshing) {
            val intent = Intent(context, BusWidgetProvider::class.java).apply {
                action = BusWidgetProvider.ACTION_REFRESH
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            views.setOnClickPendingIntent(
                R.id.refresh_overlay,
                PendingIntent.getBroadcast(
                    context, appWidgetId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }
    }

    /**
     * 위젯 배경 처리.
     *
     * One UI Home 7.0+ 는 루트 뷰 ID 가 `@android:id/background` 이고 배경색 알파가
     * 1~254 인 위젯을 만나면, 그 영역의 배경화면을 캡처해 블러 처리한 뒤
     * 지정한 배경색으로 틴팅해서 깔아 준다. (알파 0 또는 255 면 블러가 꺼진다)
     *
     * 그 외 런처에서는 런처가 아무것도 해주지 않으므로, 앱이 직접 반투명 패널
     * (`R.id.widget_bg`)을 그려서 비슷한 느낌을 낸다.
     */
    private fun applyBackground(
        views: RemoteViews,
        oneUiBlur: Boolean,
        panelColor: Int,
        alphaPercent: Int
    ) {
        if (oneUiBlur) {
            // 런처에게 맡기는 모드: 루트에만 반투명 색을 칠한다
            val alpha = ((alphaPercent.coerceIn(0, 100) * 255) / 100).coerceIn(1, 254)
            val tinted = Color.argb(
                alpha,
                Color.red(panelColor),
                Color.green(panelColor),
                Color.blue(panelColor)
            )
            views.setInt(android.R.id.background, "setBackgroundColor", tinted)
            views.setViewVisibility(R.id.widget_bg, android.view.View.GONE)
        } else {
            // 직접 그리는 모드: 루트는 완전 투명, 라운드 패널로 처리
            views.setInt(android.R.id.background, "setBackgroundColor", Color.TRANSPARENT)
            views.setViewVisibility(R.id.widget_bg, android.view.View.VISIBLE)
            views.setInt(R.id.widget_bg, "setColorFilter", panelColor)
            views.setInt(
                R.id.widget_bg,
                "setImageAlpha",
                (alphaPercent.coerceIn(0, 100) * 255) / 100
            )
        }
    }

    /**
     * 위젯 폭에 따라 노선을 몇 열로 보여줄지 정한다.
     * 홈 화면 4칸 너비(약 250dp 이상)면 2열, 2칸이면 1열.
     */
    private fun columnsFor(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int
    ): Int {
        val minWidthDp = runCatching {
            manager.getAppWidgetOptions(appWidgetId)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        }.getOrDefault(0)

        val threshold = context.resources
            .getDimension(R.dimen.widget_two_column_min_width) / context.resources.displayMetrics.density

        return if (minWidthDp >= threshold) 2 else 1
    }

    private fun fillRows(
        context: Context,
        views: RemoteViews,
        config: WidgetConfig,
        arrivals: List<BusArrival>,
        columns: Int
    ) {
        val dark = config.darkStyle
        val textColor = ContextCompat.getColor(
            context, if (dark) R.color.widget_text_dark else R.color.widget_text_light
        )
        val subColor = ContextCompat.getColor(
            context, if (dark) R.color.widget_subtext_dark else R.color.widget_subtext_light
        )

        val byRoute = arrivals.groupBy { it.routeNo }

        // 지정한 노선이 있으면 그 순서대로, 없으면 빨리 오는 순서대로
        val routeOrder: List<String> =
            if (config.routeNumbers.isNotEmpty()) config.routeNumbers
            else arrivals.map { it.routeNo }.distinct()

        if (routeOrder.isEmpty()) {
            setMessage(views, "표시할 노선이 없습니다. 위젯을 길게 눌러 설정해 주세요")
            return
        }

        // maxRows 는 '줄 수'. 2열이면 한 줄에 두 노선이 들어간다.
        val limit = config.maxRows * columns
        val rowViews = routeOrder.take(limit).map { routeNo ->
            buildRow(
                context = context,
                routeNo = routeNo,
                arrivals = byRoute[routeNo].orEmpty().sortedBy { it.arrTimeSec },
                textColor = textColor,
                subColor = subColor
            )
        }

        if (columns >= 2) {
            var i = 0
            while (i < rowViews.size) {
                val pair = RemoteViews(context.packageName, R.layout.widget_bus_row_pair)
                pair.addView(R.id.col_left, rowViews[i])
                if (i + 1 < rowViews.size) {
                    pair.addView(R.id.col_right, rowViews[i + 1])
                } else {
                    // 홀수 개면 오른쪽 칸을 비워 두되 폭은 유지한다
                    pair.setViewVisibility(R.id.col_right, android.view.View.INVISIBLE)
                }
                views.addView(R.id.rows_container, pair)
                i += 2
            }
        } else {
            rowViews.forEach { views.addView(R.id.rows_container, it) }
        }

        val shown = rowViews.size

        if (shown == 0) {
            setMessage(views, "지금은 도착 예정 정보가 없습니다")
        }
    }

    /** 노선 한 줄(원형 뱃지 + 2줄 텍스트)을 만든다 */
    private fun buildRow(
        context: Context,
        routeNo: String,
        arrivals: List<BusArrival>,
        textColor: Int,
        subColor: Int
    ): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_bus_row)

        // 뱃지 색은 노선 유형(간선/지선/마을)을 따른다
        val kind = RouteKind.of(routeNo, arrivals.firstOrNull()?.routeType)
        row.setInt(
            R.id.route_no, "setBackgroundResource",
            when (kind) {
                RouteKind.BRANCH -> R.drawable.badge_bg2
                RouteKind.TRUNK -> R.drawable.badge_bg3
                RouteKind.VILLAGE -> R.drawable.badge_bg4
            }
        )

        // "서면5(서면100)" 처럼 괄호가 붙으면 주번호를 크게, 괄호를 작게 두 줄로
        val label = RouteLabel.of(routeNo)
        if (label.isTwoLine) {
            val text = SpannableStringBuilder(label.main).apply {
                append("\n")
                val from = length
                append(label.sub)
                setSpan(
                    RelativeSizeSpan(0.46f), from, length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            row.setTextViewText(R.id.route_no, text.withSystemFont())
        } else {
            row.setTextViewText(R.id.route_no, label.main.withSystemFont())
        }

        // 원형 뱃지 안에 들어가도록 자릿수에 따라 글자 크기를 줄인다
        // (크기 값은 res/values/dimens.xml)
        val badgeLen = label.main.length
        val badgeTextRes = when {
            badgeLen >= 5 -> R.dimen.widget_badge_text_5digit
            badgeLen == 4 -> R.dimen.widget_badge_text_4digit
            badgeLen == 3 -> R.dimen.widget_badge_text_3digit
            else -> R.dimen.widget_badge_text
        }
        row.setTextViewTextSize(
            R.id.route_no,
            TypedValue.COMPLEX_UNIT_PX,
            context.resources.getDimension(badgeTextRes)
        )
        row.setTextColor(R.id.arr_time, textColor)
        row.setTextColor(R.id.arr_sub, subColor)

        if (arrivals.isEmpty()) {
            row.setTextViewText(R.id.arr_time, "-".withSystemFont())
            row.setTextViewText(R.id.arr_sub, "운행 정보 없음".withSystemFont())
        } else {
            val first = arrivals[0]
            val second = arrivals.getOrNull(1)
            row.setTextViewText(R.id.arr_time, first.displayTime.withSystemFont())
            // 2줄: 남은 정류장 수 (+ 여유가 있으면 다음 차 시간)
            row.setTextViewText(
                R.id.arr_sub,
                buildString {
                    append(first.displayStations)
                    second?.let { append(" · 다음 ${it.arrTimeSec / 60}분") }
                }.withSystemFont()
            )
        }
        return row
    }

    private fun setMessage(views: RemoteViews, text: String) {
        views.setTextViewText(R.id.message, text.withSystemFont())
        views.setViewVisibility(R.id.message, android.view.View.VISIBLE)
    }
}
