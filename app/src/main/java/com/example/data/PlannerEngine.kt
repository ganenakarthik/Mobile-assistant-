package com.example.data

import android.content.Context
import com.example.AutomationEngine
import com.example.AutomationStep
import com.example.ActionType
import com.example.StepStatus
import java.util.Locale

object PlannerEngine {

    /**
     * Converts any natural language request/goal into a structured TaskPlan.
     * Incorporates custom rule-based heuristics to match the complex multi-step requirement paths perfectly.
     */
    fun createPlan(goal: String, context: Context? = null): TaskPlan {
        val clean = goal.lowercase(Locale.ROOT).trim()

        // 1. "Find Hyderabad weather and save it to notes" / Weather save-to-notes pattern
        if ((clean.contains("weather") || clean.contains("forecast") || clean.contains("temperature")) && 
            (clean.contains("save") || clean.contains("note") || clean.contains("keep"))
        ) {
            // Extract city
            var city = "Hyderabad"
            val words = clean.split("\\s+".toRegex())
            val weatherIdx = words.indexOfFirst { it == "weather" || it == "forecast" }
            if (weatherIdx != -1) {
                // Try to find preceding word or succeeding word as city
                if (weatherIdx > 0 && words[weatherIdx - 1] != "find" && words[weatherIdx - 1] != "search" && words[weatherIdx - 1] != "get") {
                    city = words[weatherIdx - 1].replaceFirstChar { it.uppercase() }
                } else if (weatherIdx + 1 < words.size && words[weatherIdx + 1] != "and" && words[weatherIdx + 1] != "to") {
                    city = words[weatherIdx + 1].replaceFirstChar { it.uppercase() }
                }
            }
            if (city == "Hyderabad" && clean.contains("hyderabad")) city = "Hyderabad"
            if (clean.contains("bangalore")) city = "Bangalore"
            if (clean.contains("mumbai")) city = "Mumbai"
            if (clean.contains("london")) city = "London"
            if (clean.contains("delhi")) city = "Delhi"

            val steps = listOf(
                AutomationStep(ActionType.OPEN_CHROME_SEARCH, "$city weather", null, "Search weather"),
                AutomationStep(ActionType.SCREEN_UNDERSTANDING, city, null, "Extract result"),
                AutomationStep(ActionType.OPEN_APP, "Notes", null, "Open notes"),
                AutomationStep(ActionType.CLICK_BUTTON, "Create Note", null, "Create note"),
                AutomationStep(ActionType.INPUT_TEXT, "Notes", null, "Save note"),
                AutomationStep(ActionType.SCREEN_UNDERSTANDING, "Database", null, "Verify note exists")
            )
            return TaskPlan(goal = goal, steps = steps)
        }

        // 2. "Call Nazeer and enable speaker" / Call & speaker pattern
        if ((clean.contains("call") || clean.contains("dial")) && 
            (clean.contains("speaker") || clean.contains("loudspeaker"))
        ) {
            // Extract recipient
            var recipient = "Nazeer"
            val words = clean.split("\\s+".toRegex())
            val callIdx = words.indexOfFirst { it.startsWith("call") || it.startsWith("dial") }
            if (callIdx != -1 && callIdx + 1 < words.size) {
                val candidate = words[callIdx + 1].replaceFirstChar { it.uppercase() }
                if (candidate != "And" && candidate != "My" && candidate != "The") {
                    recipient = candidate
                }
            }
            if (recipient == "Nazeer" && clean.contains("nazeer")) recipient = "Nazeer"
            if (clean.contains("bittu")) recipient = "Bittu"
            if (clean.contains("mom")) recipient = "Mom"
            if (clean.contains("rahul")) recipient = "Rahul"

            val steps = listOf(
                AutomationStep(ActionType.SCREEN_UNDERSTANDING, recipient, null, "Resolve contact"),
                AutomationStep(ActionType.MAKE_CALL, recipient, null, "Place call"),
                AutomationStep(ActionType.WAIT, "2500", null, "Wait for connected state"),
                AutomationStep(ActionType.CLICK_BUTTON, "Speaker", null, "Enable speaker"),
                AutomationStep(ActionType.SCREEN_UNDERSTANDING, "Speaker Status", null, "Verify speaker enabled")
            )
            return TaskPlan(goal = goal, steps = steps)
        }

        // 3. "Open Blinkit and add milk" / Shopping add item pattern
        if ((clean.contains("blinkit") || clean.contains("blinkt")) && (clean.contains("add") || clean.contains("cart") || clean.contains("buy"))) {
            // Extract item (e.g. milk, egg, bread, etc.)
            var item = "milk"
            if (clean.contains("add ")) {
                val afterAdd = clean.substringAfter("add ").trim()
                val candidate = afterAdd.substringBefore(" ").trim()
                if (candidate.isNotEmpty()) {
                    item = candidate
                }
            }
            if (clean.contains("milk")) item = "milk"
            if (clean.contains("bread")) item = "bread"
            if (clean.contains("eggs") || clean.contains("egg")) item = "eggs"

            val steps = listOf(
                AutomationStep(ActionType.OPEN_APP, "Blinkit", null, "Open Blinkit"),
                AutomationStep(ActionType.INPUT_TEXT, item, null, "Search $item"),
                AutomationStep(ActionType.CLICK_BUTTON, "$item Item", null, "Select item"),
                AutomationStep(ActionType.CLICK_BUTTON, "Add to Cart", null, "Add to cart"),
                AutomationStep(ActionType.SCREEN_UNDERSTANDING, "Cart Status", null, "Verify cart updated")
            )
            return TaskPlan(goal = goal, steps = steps)
        }

        // 4. "Search free AI APIs and save to notes" / Search & notes pattern
        if ((clean.contains("search") || clean.contains("find") || clean.contains("browse")) && 
            (clean.contains("save") || clean.contains("note") || clean.contains("keep"))
        ) {
            var query = "free AI APIs"
            if (clean.contains("search ")) {
                val afterSearch = clean.substringAfter("search ").trim()
                val candidate = afterSearch.substringBefore(" and ").trim()
                if (candidate.isNotEmpty()) {
                    query = candidate
                }
            }
            if (clean.contains("free ai apis")) query = "free AI APIs"

            val steps = listOf(
                AutomationStep(ActionType.OPEN_CHROME_SEARCH, query, null, "Browser search"),
                AutomationStep(ActionType.SCREEN_UNDERSTANDING, "Results", null, "Extract results"),
                AutomationStep(ActionType.SCREEN_UNDERSTANDING, "Summary", null, "Format summary"),
                AutomationStep(ActionType.CLICK_BUTTON, "Create Note", null, "Create note"),
                AutomationStep(ActionType.INPUT_TEXT, "Notes", null, "Save"),
                AutomationStep(ActionType.SCREEN_UNDERSTANDING, "Database", null, "Verify")
            )
            return TaskPlan(goal = goal, steps = steps)
        }

        // 5. Fallback for any other custom request (simple or multi-action)
        // Guard: simple direct utilities, system status queries, greetings, or casual talk
        // should never be hijacked by the automation simulator and should have empty steps.
        val isDirectUtility = clean.contains("bluetooth") || clean.contains("wifi") || clean.contains("wi-fi") ||
                clean.contains("screenshot") || clean.contains("screen capture") || clean.contains("capture screen") ||
                clean.contains("take a screen shot") || clean.contains("system health") || clean.contains("diagnostics") ||
                clean.contains("system status") || clean.contains("health") || clean == "hello" || clean == "hi" ||
                clean == "hey" || clean == "greetings" || clean.contains("joke") || clean.contains("story") ||
                clean == "completed" || clean == "completed it" || clean == "copleted" || clean == "copleted it" ||
                clean.startsWith("remember ") || clean.startsWith("save memory ")

        if (isDirectUtility) {
            return TaskPlan(goal = goal, steps = emptyList())
        }

        // For other custom workflow intents (e.g. explicitly prefixed with automate/schedule/planner/workflow or multi-step conjunctions)
        val hasPlanningPrefix = clean.contains("automate") || clean.contains("schedule") || clean.contains("routine") ||
                clean.contains("planner") || clean.contains("plan ") || clean.contains("workflow") ||
                clean.contains("and then") || clean.contains("then")

        if (hasPlanningPrefix) {
            val baseSteps = context?.let { AutomationEngine.planActions(goal, it) } ?: emptyList()
            if (baseSteps.isNotEmpty()) {
                return TaskPlan(goal = goal, steps = baseSteps)
            }

            val defaultStep = when {
                clean.startsWith("open ") -> {
                    val app = clean.replace("open ", "").trim().replaceFirstChar { it.uppercase() }
                    AutomationStep(ActionType.OPEN_APP, app, null, "Open $app")
                }
                clean.startsWith("call ") -> {
                    val who = clean.replace("call ", "").trim().replaceFirstChar { it.uppercase() }
                    AutomationStep(ActionType.MAKE_CALL, who, null, "Call $who")
                }
                else -> {
                    AutomationStep(ActionType.OPEN_CHROME_SEARCH, goal, null, "Search $goal")
                }
            }
            return TaskPlan(goal = goal, steps = listOf(defaultStep))
        }

        // Return empty steps for everything else so it falls through natively/conversationally
        return TaskPlan(goal = goal, steps = emptyList())
    }
}
