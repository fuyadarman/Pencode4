package com.example.plan

import org.json.JSONObject
import java.util.UUID

/**
 * PlanSurveyParser
 *
 * Robust parser that extracts structured Feature Survey and Plan details
 * from AI responses (JSON block or markdown list heuristics).
 */
object PlanSurveyParser {

    fun parse(aiResponseText: String): PlanFeatureSurvey? {
        if (aiResponseText.isBlank()) return null

        // 1. Try finding JSON either in a markdown code fence or as a raw JSON object
        val jsonStringCandidates = mutableListOf<String>()

        // Look for code fences: ```json ... ``` or ```json:plan ... ``` or ``` ... ```
        val codeBlockRegex = Regex("""```(?:json[a-zA-Z0-9_:-]*)?\s*([\s\S]*?)```""")
        for (match in codeBlockRegex.findAll(aiResponseText)) {
            val candidate = match.groupValues[1].trim()
            if (candidate.startsWith("{") && candidate.endsWith("}")) {
                jsonStringCandidates.add(candidate)
            }
        }

        // Also look for bare JSON object in the text if no code fence matched or text is JSON
        val firstBrace = aiResponseText.indexOf('{')
        val lastBrace = aiResponseText.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace > firstBrace) {
            val bare = aiResponseText.substring(firstBrace, lastBrace + 1).trim()
            if (!jsonStringCandidates.contains(bare)) {
                jsonStringCandidates.add(bare)
            }
        }

        for (jsonStr in jsonStringCandidates) {
            try {
                val obj = JSONObject(jsonStr)
                val title = obj.optString("plan_title").ifBlank {
                    obj.optString("title").ifBlank { "Application Implementation Plan" }
                }
                val overview = obj.optString("overview").ifBlank {
                    obj.optString("description").ifBlank {
                        obj.optString("summary").ifBlank {
                            "Implementation steps and architecture are ready."
                        }
                    }
                }

                val featureList = mutableListOf<SurveyFeatureItem>()
                val stepsList = mutableListOf<String>()

                // Check "steps" array (can contain objects with title/description or strings)
                val stepsArray = obj.optJSONArray("steps")
                if (stepsArray != null) {
                    for (i in 0 until stepsArray.length()) {
                        val stepItem = stepsArray.opt(i)
                        if (stepItem is JSONObject) {
                            val sTitle = stepItem.optString("title").ifBlank { stepItem.optString("name", "Step ${i + 1}") }
                            val sDesc = stepItem.optString("description", "")
                            val isDone = stepItem.optBoolean("done", false)
                            featureList.add(SurveyFeatureItem("step_$i", sTitle, sDesc, !isDone))
                            stepsList.add(sTitle)
                        } else if (stepItem != null) {
                            val str = stepItem.toString().trim()
                            if (str.isNotBlank()) {
                                stepsList.add(str)
                                featureList.add(SurveyFeatureItem("step_$i", str, "", true))
                            }
                        }
                    }
                }

                // Check "features" array
                val featuresArray = obj.optJSONArray("features")
                if (featuresArray != null) {
                    for (i in 0 until featuresArray.length()) {
                        val itemObj = featuresArray.optJSONObject(i)
                        if (itemObj != null) {
                            val id = itemObj.optString("id", "feat_" + UUID.randomUUID().toString().take(4))
                            val fTitle = itemObj.optString("title", "Feature ${i + 1}")
                            val desc = itemObj.optString("description", "")
                            val selected = itemObj.optBoolean("selected", true)
                            featureList.add(SurveyFeatureItem(id, fTitle, desc, selected))
                        }
                    }
                }

                if (featureList.isNotEmpty() || stepsList.isNotEmpty() || obj.has("plan_title")) {
                    return PlanFeatureSurvey(
                        title = title,
                        overviewPlan = overview,
                        steps = stepsList,
                        suggestedFeatures = featureList
                    )
                }
            } catch (e: Exception) {
                // Try next candidate
            }
        }

        // 2. Markdown heuristics: Only when explicitly marked as a feature survey
        if (aiResponseText.contains("[FEATURE_SURVEY]", ignoreCase = true) || aiResponseText.contains("Feature Survey", ignoreCase = true)) {
            val features = mutableListOf<SurveyFeatureItem>()
            val featureRegex = Regex("""(?:^|\n)[-*•]\s*(?:\[[ xX]?\]\s*)?([A-Za-z0-9\s-_/()]+):\s*(.+)""")
            var index = 1

            for (m in featureRegex.findAll(aiResponseText)) {
                val fTitle = m.groupValues[1].trim().take(50)
                val desc = m.groupValues[2].trim().take(120)
                if (fTitle.length in 3..40 && !fTitle.startsWith("http")) {
                    features.add(SurveyFeatureItem("feat_$index", fTitle, desc, true))
                    index++
                    if (features.size >= 8) break
                }
            }

            if (features.size >= 2) {
                return PlanFeatureSurvey(
                    title = "Suggested Feature Survey",
                    overviewPlan = aiResponseText.take(280).trim() + "...",
                    suggestedFeatures = features
                )
            }
        }

        return null
    }

    /**
     * Extracts and removes the plan JSON block (whether in markdown fence or bare JSON)
     * so that raw JSON never leaks into the conversational chat stream.
     */
    fun extractCleanText(fullText: String): String {
        val codeFenceRegex = Regex("""```(?:json[a-zA-Z0-9_:-]*)?\s*\{[\s\S]*?(?:plan_title|steps|features)[\s\S]*?\}\s*```""")
        var cleaned = fullText.replace(codeFenceRegex, "").trim()

        val bareJsonRegex = Regex("""\{[\s\S]*?(?:\"plan_title\"|\"steps\"|\"features\")[\s\S]*?\}""")
        cleaned = cleaned.replace(bareJsonRegex, "").trim()

        return cleaned
    }
}
