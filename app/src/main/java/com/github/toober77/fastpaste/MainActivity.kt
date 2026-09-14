package com.github.toober77.fastpaste

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PasteApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PasteApp(viewModel: PasteViewModel = viewModel()) {
    val items by viewModel.items.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Int>()) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fast Paste") },
                actions = {
                    if (selectedIds.isNotEmpty()) {
                        IconButton(onClick = {
                            viewModel.deleteItems(selectedIds.toList())
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                    TextButton(onClick = {
                        viewModel.setSortOrder(if (sortOrder == SortOrder.TIME) SortOrder.ALPHA else SortOrder.TIME)
                    }) {
                        Text(if (sortOrder == SortOrder.TIME) "Time" else "Alpha")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                placeholder = { Text("Search...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )

            LazyColumn {
                items(items, key = { it.id }) { item ->
                    val isSelected = selectedIds.contains(item.id)
                    ListItem(
                        headlineContent = { Text(item.content) },
                        supportingContent = { if (item.label.isNotEmpty()) Text(item.label) },
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                if (selectedIds.isEmpty()) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Paste", item.content))
                                    Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                                } else {
                                    selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                                }
                            },
                            onLongClick = {
                                selectedIds = selectedIds + item.id
                            }
                        ),
                        colors = if (isSelected) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer) 
                                 else ListItemDefaults.colors()
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        var content by remember { mutableStateOf("") }
        var label by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add New Item") },
            text = {
                Column {
                    TextField(value = content, onValueChange = { content = it }, label = { Text("Content") })
                    TextField(value = label, onValueChange = { label = it }, label = { Text("Label (Optional)") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (content.isNotBlank()) {
                        viewModel.addItem(content, label)
                        showAddDialog = false
                    }
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}
