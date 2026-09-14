package com.github.toober77.fastpaste

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.* // For MaterialTheme, Card, Text, etc.
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class QuickPasteDialogActivity : ComponentActivity() {

    private lateinit var pasteDao: PasteDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pasteDao = AppDatabase.getDatabase(applicationContext).pasteDao()

        setContent {
            MaterialTheme {
                AlertDialog(
                    onDismissRequest = { finish() },
                    title = { Text("快速貼上") },
                    text = {
                        val itemsState by pasteDao.getAllItemsSortedByTime().collectAsState(initial = emptyList())
                        val recentItems = itemsState.take(5) // 只顯示最近5筆

                        if (recentItems.isEmpty()) {
                            Text("目前沒有快速貼上項目")
                        } else {
                            LazyColumn {
                                items(recentItems) { item ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable {
                                                copyToClipboard(item.content)
                                                finish() // 複製後關閉對話框
                                            }
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text(item.content, style = MaterialTheme.typography.bodyLarge)
                                            if (item.label.isNotEmpty()) {
                                                Text(item.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { finish() }) { Text("關閉") }
                    }
                )
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Paste", text))
        Toast.makeText(this, "已複製: $text", Toast.LENGTH_SHORT).show()
    }
}
