package com.example.plan

import java.util.UUID

/**
 * PlanSurveyModels
 *
 * Data models representing an interactive AI architectural plan and feature survey checklist.
 */
data class PlanFeatureSurvey(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val overviewPlan: String,
    val steps: List<String> = emptyList(),
    val suggestedFeatures: List<SurveyFeatureItem> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

data class SurveyFeatureItem(
    val id: String,
    val title: String,
    val description: String,
    val isSelected: Boolean = true
)
