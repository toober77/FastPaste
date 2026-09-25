package com.github.toober77.fastpaste

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Non-exported receiver for widget row taps, so other apps cannot drive our clipboard.
 */
class WidgetCopyReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_ITEM_ID = "com.github.toober77.fastpaste.extra.ITEM_ID"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ITEM_ID, -1)
        if (id < 0) return
        val appContext = context.applicationContext
        val pending = goAsync()
        PasteWidgetUpdater.scope.launch {
            try {
                val item = AppDatabase.getDatabase(appContext).pasteDao().getById(id)
                if (item == null) {
                    // Stale row (item deleted elsewhere): just refresh the widget.
                    PasteWidgetUpdater.requestUpdate(appContext)
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    val clipboard = appContext.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText(item.label.ifEmpty { "Paste" }, item.content))
                    Toast.makeText(appContext, R.string.widget_copied, Toast.LENGTH_SHORT).show()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
