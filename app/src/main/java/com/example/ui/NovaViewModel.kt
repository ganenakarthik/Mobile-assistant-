package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.InventoryItem
import com.example.data.InventoryRepository
import com.example.data.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NovaViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    val repository = InventoryRepository(database.taskDao, database.inventoryDao)

    val tasksStream: StateFlow<List<Task>> = repository.allTasks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val inventoryStream: StateFlow<List<InventoryItem>> = repository.allItems
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _ruleLogs = MutableStateFlow<List<String>>(emptyList())
    val ruleLogs: StateFlow<List<String>> = _ruleLogs.asStateFlow()

    private val _currentTab = MutableStateFlow("TASKS")
    val currentTab: StateFlow<String> = _currentTab.asStateFlow()

    init {
        refreshRuleLogs()
        viewModelScope.launch {
            // Trigger rule logic on start
            repository.runRulesForAll()
            refreshRuleLogs()
            // Automate dynamic launcher app discovery for on-device app matching and aliases
            com.example.AutomationEngine.discoverApps(application)
        }
    }

    fun selectTab(tab: String) {
        _currentTab.value = tab
    }

    fun refreshRuleLogs() {
        _ruleLogs.value = repository.ruleActivityLog.toList()
    }

    fun addTask(title: String, description: String, priority: String, category: String, dueDate: Long? = null) {
        viewModelScope.launch {
            val task = Task(
                title = title,
                description = description,
                priority = priority,
                category = category,
                dueDate = dueDate,
                status = "PENDING"
            )
            repository.insertTask(task)
            refreshRuleLogs()
        }
    }

    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            val newStatus = if (task.status == "COMPLETED") "PENDING" else "COMPLETED"
            repository.updateTaskStatus(task.id, newStatus)
            refreshRuleLogs()
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
            refreshRuleLogs()
        }
    }

    fun addInventoryItem(
        name: String,
        description: String,
        quantity: Int,
        minThreshold: Int,
        location: String,
        category: String,
        expiryDays: Int? = null
    ) {
        viewModelScope.launch {
            val expiryTime = expiryDays?.let {
                System.currentTimeMillis() + (it.toLong() * 24 * 60 * 60 * 1000)
            }
            val item = InventoryItem(
                name = name,
                description = description,
                quantity = quantity,
                minThreshold = minThreshold,
                location = location,
                category = category,
                expiryDate = expiryTime
            )
            repository.insertItem(item)
            refreshRuleLogs()
        }
    }

    fun updateStockLevel(item: InventoryItem, delta: Int) {
        viewModelScope.launch {
            val newQty = (item.quantity + delta).coerceAtLeast(0)
            repository.updateStock(item.id, newQty)
            refreshRuleLogs()
        }
    }

    fun deleteInventoryItem(item: InventoryItem) {
        viewModelScope.launch {
            repository.deleteItem(item)
            refreshRuleLogs()
        }
    }

    fun forceEvaluateRules() {
        viewModelScope.launch {
            repository.runRulesForAll()
            refreshRuleLogs()
        }
    }
}
