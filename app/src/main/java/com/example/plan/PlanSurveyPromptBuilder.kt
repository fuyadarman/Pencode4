package com.example.plan

/**
 * PlanSurveyPromptBuilder
 *
 * Formats the user's prompt for AI planning mode and builds the execution prompt
 * once the user selects which features to include from the survey.
 */
object PlanSurveyPromptBuilder {

    fun buildPlanningRequestPrompt(userPrompt: String): String {
        return """
[PLAN_MODE_ACTIVE]
user's request: "$userPrompt"
You are operating in PLAN MODE (like Cursor / Claude Code). You must ONLY PLAN AND DESIGN. Do NOT write full application source code files yet.
1. Outline a clear architectural breakdown, UI structure, and step-by-step implementation plan for the user's request.
2. Outline the exact to-dos and features required for implementation.
3. At the end of your response, include this exact structured JSON block so the app can render the interactive Plan Card:

```json:feature_survey
{
  "title": "Implementation Plan",
  "overview": "Brief summary of the architectural strategy.",
  "steps": [
    "Step 1: Setup data models and state management",
    "Step 2: Implement UI screens and layout",
    "Step 3: Connect interaction logic and event handlers"
  ],
  "features": [
    {"id": "feat_1", "title": "Core Functionality", "description": "Essential primary workflow", "selected": true},
    {"id": "feat_2", "title": "Data Persistence", "description": "Store state locally or with cloud sync", "selected": true},
    {"id": "feat_3", "title": "Modern Polished UI", "description": "Responsive Material 3 design", "selected": true}
  ]
}
```

Wait for the user to review and click 'Build' before writing code.
[/PLAN_MODE_ACTIVE]
""".trimIndent()
    }

    fun buildExecutionPrompt(
        survey: PlanFeatureSurvey,
        selectedFeatures: List<SurveyFeatureItem>,
        customInstructions: String = ""
    ): String {
        val featuresList = if (selectedFeatures.isNotEmpty()) {
            selectedFeatures.joinToString("\n") { "- ${it.title}: ${it.description}" }
        } else {
            "Proceed with the base architecture without extra optional features."
        }

        val customText = if (customInstructions.isNotBlank()) "\nAdditional User Instructions: $customInstructions\n" else ""

        return """
[EXECUTE_PLAN_MODE]
The user reviewed the proposed plan "${survey.title}" and approved the following selected features from the Feature Survey:

$featuresList
$customText
Now execute this plan completely! Implement all selected features, create and update all necessary source files, UI composables, models, and dependencies, and ensure the app builds cleanly.
[/EXECUTE_PLAN_MODE]
""".trimIndent()
    }
}
