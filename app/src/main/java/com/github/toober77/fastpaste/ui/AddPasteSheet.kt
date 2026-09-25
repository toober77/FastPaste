package com.github.toober77.fastpaste.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.toober77.fastpaste.PasteLogic
import com.github.toober77.fastpaste.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPasteSheet(
    onDismiss: () -> Unit,
    onAdd: (content: String, label: String) -> Unit,
    isDuplicate: (String) -> Boolean = { false }
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var content by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    val canSave = content.isNotBlank()
    // Hint only; saving a duplicate is still allowed.
    val duplicate = remember(content) { isDuplicate(content) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.add_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            OutlinedTextField(
                value = content,
                onValueChange = { content = it.take(PasteLogic.MAX_CONTENT_LENGTH) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                label = { Text(stringResource(R.string.field_content)) },
                placeholder = { Text(stringResource(R.string.field_content_placeholder)) },
                supportingText = {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (duplicate) stringResource(R.string.duplicate_hint) else "",
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            stringResource(
                                R.string.char_count,
                                content.length,
                                PasteLogic.MAX_CONTENT_LENGTH
                            )
                        )
                    }
                },
                minLines = 2,
                maxLines = 5
            )
            OutlinedTextField(
                value = label,
                onValueChange = { label = it.replace('\n', ' ').take(PasteLogic.MAX_LABEL_LENGTH) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_label)) },
                placeholder = { Text(stringResource(R.string.field_label_placeholder)) },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (canSave) {
                            onAdd(
                                PasteLogic.normalizeContent(content),
                                PasteLogic.normalizeLabel(label)
                            )
                        }
                    },
                    enabled = canSave
                ) {
                    Text(stringResource(R.string.add_item))
                }
            }
        }
    }
}
