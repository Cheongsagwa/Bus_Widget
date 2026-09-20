package com.cheon.ccbuswidget.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.cheon.ccbuswidget.data.local.WidgetStore

class BusWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.cheon.ccbuswidget.ACTION_REFRESH"

        /** 외부에서 특정 위젯(또는 전체)을 즉시 갱신시키고 싶을 때 */
        fun requestRefresh(context: Context, appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID) {
            val ids = if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                WidgetRenderer.allWidgetIds(context)
            } else {
                intArrayOf(appWidgetId)
            }
            RefreshWorker.enqueueNow(context, ids)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        RefreshWorker.enqueueNow(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val id = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            requestRefresh(context, id)
        }
    }

    /** 사용자가 위젯 크기를 바꾸면 열 수가 달라질 수 있으므로 다시 그린다 */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        requestRefresh(context, appWidgetId)
    }

    override fun onEnabled(context: Context) {
        RefreshWorker.schedulePeriodic(context)
    }

    override fun onDisabled(context: Context) {
        RefreshWorker.cancelPeriodic(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetStore.delete(context, it) }
    }
}
