package com.github.toober77.fastpaste

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

class QuickPasteTileService : TileService() {
    override fun onClick() {
        super.onClick()
        
        val intent = Intent(this, QuickPasteDialogActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        // 使用 unlockAndRun 確保在設備鎖定時能引導解鎖並啟動 Activity
        unlockAndRun {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ (API 34+) 必須使用 PendingIntent
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                // 舊版本使用直接 Intent
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }
}
