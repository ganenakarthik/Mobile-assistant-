package com.example.data

import com.example.AutomationStep

data class TaskPlan(
    val goal: String,
    val steps: List<AutomationStep>,
    var status: String = "PENDING", // PENDING, EXECUTING, SUCCESS, FAILED
    var currentStep: Int = 0,
    var completedSteps: List<AutomationStep> = emptyList()
)
