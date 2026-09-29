package com.cheon.ccbuswidget.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/** 폰에서 알람 상태가 바뀔 때마다 불린다 */
class AlarmDataListener : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        // 버퍼는 이 함수가 끝나면 풀리므로 여기서 바로 꺼내 쓴다
        events.forEach { event ->
            if (event.dataItem.uri.path != WearAlarmStore.PATH) return@forEach
            val alarm = if (event.type == DataEvent.TYPE_DELETED) null
            else WearAlarmStore.fromDataMap(DataMapItem.fromDataItem(event.dataItem).dataMap)
            WearAlarmStore.update(this, alarm)
        }
    }

    /** 폰에서 온 승하차 알림 — 워치에 알림을 띄우고 길고 강하게 2번 진동한다 */
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WearAlarmStore.PATH_ALERT) return
        // "제목\n본문\n노선색"
        val lines = String(event.data).split("\n", limit = 3)
        AlarmOngoing.alert(
            this,
            lines.getOrElse(0) { "" },
            lines.getOrElse(1) { "" },
            lines.getOrNull(2)?.toIntOrNull() ?: 0xFF00B9BE.toInt()
        )
    }
}
