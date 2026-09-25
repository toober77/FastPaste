package com.github.toober77.fastpaste

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Builds the home-screen widget using the API 31+ [RemoteViews.RemoteCollectionItems] path,
 * which replaces the deprecated RemoteViewsService/setRemoteAdapter(Intent) combination and
 * lets the app push fresh data directly after every add/delete.
 */
object PasteWidgetUpdater {
    private const val TAG = "PasteWidget"
    private const val MAX_ITEMS = 10
    private const val MAX_PREVIEW_CHARS = 200

    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Fire-and-forget refresh of every placed widget; safe to call from any thread. */
    fun requestUpdate(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                val manager = AppWidgetManager.getInstance(appContext)
                val ids = manager.getAppWidgetIds(ComponentName(appContext, PasteWidgetProvider::class.java))
                if (ids.isNotEmpty()) update(appContext, manager, ids)
            } catch (e: Exception) {
                Log.w(TAG, "Widget refresh failed", e)
            }
        }
    }

    suspend fun update(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        val appContext = context.applicationContext
        val items = AppDatabase.getDatabase(appContext).pasteDao().getRecentItemsOnce(MAX_ITEMS)
        for (id in appWidgetIds) {
            manager.updateAppWidget(id, buildViews(appContext, items))
        }
    }

    private fun buildViews(context: Context, items: List<PasteItem>): RemoteViews {
        val pkg = context.packageName
        val collection = RemoteViews.RemoteCollectionItems.Builder()
            .setHasStableIds(true)
            .setViewTypeCount(1)
        for (item in items) {
            val row = RemoteViews(pkg, R.layout.widget_item).apply {
                setTextViewText(R.id.widget_item_content, item.content.take(MAX_PREVIEW_CHARS))
                if (item.label.isNotEmpty()) {
                    setTextViewText(R.id.widget_item_label, item.label)
                    setViewVisibility(R.id.widget_item_label, View.VISIBLE)
                } else {
                    setViewVisibility(R.id.widget_item_label, View.GONE)
                }
                // Only the id travels in the intent; the receiver re-reads the content from the DB.
                setOnClickFillInIntent(
                    R.id.widget_item_root,
                    Intent().putExtra(WidgetCopyReceiver.EXTRA_ITEM_ID, item.id)
                )
            }
            collection.addItem(item.id.toLong(), row)
        }

        return RemoteViews(pkg, R.layout.widget_layout).apply {
            setRemoteAdapter(R.id.widget_list, collection.build())
            setEmptyView(R.id.widget_list, R.id.widget_empty)

            val openApp = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setOnClickPendingIntent(R.id.widget_title, openApp)

            // Must be mutable so the per-row fill-in intent can add the item id.
            val copyTemplate = PendingIntent.getBroadcast(
                context, 0, Intent(context, WidgetCopyReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            setPendingIntentTemplate(R.id.widget_list, copyTemplate)
        }
    }
}
