package com.github.toober77.fastpaste

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import kotlinx.coroutines.launch

class PasteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Loading items hits the database, so finish asynchronously off the main thread.
        val pending = goAsync()
        PasteWidgetUpdater.scope.launch {
            try {
                PasteWidgetUpdater.update(context, appWidgetManager, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }
}
