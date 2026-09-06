package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.localization.AppLanguage
import com.example.localization.Translations
import com.example.ui.theme.AppColors
import com.example.ui.theme.AppRadius
import com.example.ui.theme.AppSpacing

@Composable
fun ServerConfigDialog(
    currentUrl: String,
    language: AppLanguage,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var textValue by remember { mutableStateOf(currentUrl) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(AppRadius.lg),
            color = AppColors.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                Text(
                    text = Translations.tr("serverSettings", language),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.ink
                )

                Text(
                    text = "Configure the remote backend URL for /api/config and /api/diagnostic",
                    fontSize = 12.sp,
                    color = AppColors.inkMuted
                )

                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    label = { Text(Translations.tr("serverUrl", language)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.primary,
                        focusedLabelColor = AppColors.primary
                    )
                )

                // Quick presets
                Text(
                    text = "Quick Presets:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.inkMuted
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PresetChip(label = "10.26.8.122", onClick = { textValue = "http://10.26.8.122:3000" })
                    PresetChip(label = "10.0.2.2 (Emu)", onClick = { textValue = "http://10.0.2.2:3000" })
                    PresetChip(label = "192.168.1.1", onClick = { textValue = "http://192.168.1.1:3000" })
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = Translations.tr("cancel", language),
                            color = AppColors.inkSoft
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { onSave(textValue) },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.primary)
                    ) {
                        Text(
                            text = Translations.tr("save", language),
                            color = AppColors.white,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(AppColors.primarySoft)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.primary
        )
    }
}
