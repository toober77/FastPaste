package com.github.toober77.fastpaste

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class PasteWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return PasteWidgetRemoteViewsFactory(this.applicationContext)
    }
}

class PasteWidgetRemoteViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private var items: List<PasteItem> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        // Run blocking is okay here because it's called on a background thread by the widget framework
        runBlocking {
            val db = AppDatabase.getDatabase(context)
            items = db.pasteDao().getAllItemsSortedByTime().first().take(10) // Show top 10
        }
    }

    override fun onDestroy() {}

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val item = items[position]
        val views = RemoteViews(context.packageName, R.layout.widget_item).apply {
            setTextViewText(R.id.widget_item_content, item.content)
            if (item.label.isNotEmpty()) {
                setTextViewText(R.id.widget_item_label, item.label)
                setViewVisibility(R.id.widget_item_label, android.view.View.VISIBLE)
            } else {
                setViewVisibility(R.id.widget_item_label, android.view.View.GONE)
            }

            // Fill-in intent for copying
            val fillInIntent = Intent().apply {
                putExtra("content", item.content)
            }
            setOnClickFillInIntent(R.id.widget_item_content, fillInIntent)
        }
        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = items[position].id.toLong()
    override fun hasStableIds(): Boolean = true
}
