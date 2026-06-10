package com.example.data

data class TaskContext(
    val originalCommand: String,
    var extractedData: String = "",
    var searchResult: String = "",
    var noteTitle: String = "",
    var noteBody: String = "",
    var targetApp: String = "Notes"
)

object TaskContextHolder {
    var activeContext: TaskContext? = null
}
