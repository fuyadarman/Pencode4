package com.example.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ModelFallbackManager
import com.example.ui.CustomModelConfig

/**
 * ModelFallbackSettingsCard
 * Renders the Model Auto-Switch / Fallback configuration card inside the Settings dialog.
 * Enables the user to toggle automatic model fallback and choose which configured models
 * should be attempted sequentially on execution failure.
 */
@Composable
fun ModelFallbackSettingsCard(
    customModels: List<CustomModelConfig>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isEnabled by remember { mutableStateOf(ModelFallbackManager.isAutoSwitchEnabled(context)) }
    var selectedModelIds by remember { mutableStateOf(ModelFallbackManager.getSelectedFallbackModelIds(context)) }

    Surface(
        color = Color(0xFF161B22),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF30363D)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.AltRoute,
                        contentDescription = "Model Fallback",
                        tint = Color(0xFF58A6FF),
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Auto-Switch Models on Error",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF0F6FC)
                        )
                        Text(
                            text = "Automatically switches to selected fallback models if the current model encounters errors",
                            fontSize = 11.sp,
                            color = Color(0xFF8B949E),
                            lineHeight = 14.sp
                        )
                    }
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { checked ->
                        isEnabled = checked
                        ModelFallbackManager.setAutoSwitchEnabled(context, checked)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF238636),
                        uncheckedThumbColor = Color(0xFF8B949E),
                        uncheckedTrackColor = Color(0xFF21262D)
                    )
                )
            }

            AnimatedVisibility(visible = isEnabled) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Divider(color = Color(0xFF30363D), thickness = 0.5.dp, modifier = Modifier.padding(bottom = 10.dp))

                    Text(
                        text = "Select Fallback Models (tried in order):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFC9D1D9),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (customModels.isEmpty()) {
                        Text(
                            text = "No custom models configured yet. Add models in the 'Configured Models' section above to select them as fallbacks.",
                            fontSize = 11.5.sp,
                            color = Color(0xFF8B949E),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            customModels.forEach { model ->
                                val isSelected = selectedModelIds.contains(model.id) || selectedModelIds.contains(model.modelId)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            color = if (isSelected) Color(0xFF1F242C) else Color(0xFF0D1117),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            val newSelected = !isSelected
                                            ModelFallbackManager.toggleFallbackModel(context, model.id, newSelected)
                                            selectedModelIds = ModelFallbackManager.getSelectedFallbackModelIds(context)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                                        contentDescription = null,
                                        tint = if (isSelected) Color(0xFF238636) else Color(0xFF484F58),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = model.alias.ifBlank { model.modelId },
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFFF0F6FC)
                                        )
                                        Text(
                                            text = "${model.provider.uppercase()} • ${model.modelId}",
                                            fontSize = 10.5.sp,
                                            color = Color(0xFF8B949E)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
