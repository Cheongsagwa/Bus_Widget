package com.cheon.ccbuswidget.wear

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture

/**
 * 타일 — 워치에서 옆으로 넘기면 나오는 카드.
 * "강원대학병원으로 이동 중 / 하차까지 3개 정류장 / 4분 후 하차". 누르면 알람 화면이 열린다.
 */
class AlarmTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val alarm = WearAlarmStore.current(this)
        val lines = if (alarm == null) listOf("승하차 알람 없음" to 0xB3FFFFFF.toInt())
        else listOfNotNull(
            alarm.title to 0xFFFFFFFF.toInt(),
            alarm.subtitle to 0xCCFFFFFF.toInt(),
            alarm.time.takeIf { it.isNotBlank() }?.let { it to alarm.color }
        )
        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        lines.forEachIndexed { i, (text, color) ->
            column.addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(text)
                    .setMaxLines(2)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(if (i == 0) 16f else 13f))
                            .setColor(argb(color))
                            .build()
                    )
                    .build()
            )
        }
        val root = LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setPadding(ModifiersBuilders.Padding.Builder().setAll(dp(16f)).build())
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setId("open")
                            .setOnClick(
                                ActionBuilders.LaunchAction.Builder()
                                    .setAndroidActivity(
                                        ActionBuilders.AndroidActivity.Builder()
                                            .setPackageName(packageName)
                                            .setClassName(AlarmActivity::class.java.name)
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .addContent(column.build())
            .build()
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            // 폰에서 상태가 바뀔 때마다 직접 갱신을 요청하므로 주기 갱신은 끈다
            .setFreshnessIntervalMillis(0)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
            .build()
        return CallbackToFutureAdapter.getFuture { it.set(tile); "tile" }
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> {
        val res = ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build()
        return CallbackToFutureAdapter.getFuture { it.set(res); "resources" }
    }

    private companion object {
        const val RESOURCES_VERSION = "1"
    }
}
