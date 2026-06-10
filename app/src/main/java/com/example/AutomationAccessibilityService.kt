package com.example

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.util.Locale

class AutomationAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AutomationAccessibilityService? = null
            private set

        val isServiceRunning: Boolean
            get() = instance != null
    }

    override fun onServiceConnected() {
        try {
            super.onServiceConnected()
            instance = this
            android.util.Log.d("AutomationService", "Nova Cognitive Bridge connected and active.")
            
            // Safely post Toast to Main Looper via Application Context to prevent WindowManager$BadTokenException
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    Toast.makeText(applicationContext, "Nova Cognitive Bridge connected", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    android.util.Log.e("AutomationService", "Safe Toast fail: ${e.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AutomationService", "Error in onServiceConnected: ${e.message}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        try {
            // Real-time tracking of active package context
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val pkg = event.packageName?.toString()
                if (!pkg.isNullOrBlank()) {
                    AutomationEngine.updateForegroundPackage(pkg)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AutomationService", "Error in onAccessibilityEvent: ${e.message}")
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    private fun getActiveRootWindow(): AccessibilityNodeInfo? {
        var root = rootInActiveWindow
        if (root == null) {
            try { Thread.sleep(200) } catch (e: java.lang.Exception) {}
            root = rootInActiveWindow
        }
        return root
    }

    // --- Core Automation Actions ---

    fun performBackAction(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun performHomeAction(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun performRecentsAction(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    fun scrollHorizontallyAction(right: Boolean): Boolean {
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        val startX = if (right) width * 0.8f else width * 0.2f
        val endX = if (right) width * 0.2f else width * 0.8f
        val y = height / 2.0f

        val path = Path().apply {
            moveTo(startX, y)
            lineTo(endX, y)
        }

        val gestureBuilder = GestureDescription.Builder()
        val stroke = GestureDescription.StrokeDescription(path, 0, 400)
        gestureBuilder.addStroke(stroke)

        return dispatchGesture(gestureBuilder.build(), null, null)
    }

    // App launching with discovery / package match
    fun launchApplication(query: String): Boolean {
        val pm = packageManager
        val list = pm.getInstalledApplications(0)
        
        // Find best matching app name/label
        var targetPackage: String? = null
        val lowerQuery = query.lowercase(Locale.ROOT)
        
        // Try exact package name or package tail first (e.g. "youtube")
        for (app in list) {
            val pkg = app.packageName.lowercase(Locale.ROOT)
            val label = pm.getApplicationLabel(app).toString().lowercase(Locale.ROOT)
            if (pkg == lowerQuery || label == lowerQuery) {
                targetPackage = app.packageName
                break
            }
        }

        // Secondary fuzzy match (e.g., "insta" -> "instagram" or "tube" -> "youtube")
        if (targetPackage == null) {
            for (app in list) {
                val pkg = app.packageName.lowercase(Locale.ROOT)
                val label = pm.getApplicationLabel(app).toString().lowercase(Locale.ROOT)
                if (label.contains(lowerQuery) || pkg.contains(lowerQuery) || 
                    (lowerQuery == "insta" && label.contains("instagram")) ||
                    (lowerQuery == "fb" && label.contains("facebook")) ||
                    (lowerQuery == "yt" && label.contains("youtube"))) {
                    targetPackage = app.packageName
                    break
                }
            }
        }

        if (targetPackage != null) {
            val launchIntent = pm.getLaunchIntentForPackage(targetPackage)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                return true
            }
        }
        return false
    }

    // Scroll operations
    fun scrollDownAction(): Boolean {
        val root = getActiveRootWindow() ?: return false
        val scrollables = findScrollableNodes(root)
        if (scrollables.isNotEmpty()) {
            return scrollables.first().performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        }
        // Fallback: Simulate gesture
        return simulateScrollGesture(down = true)
    }

    fun scrollUpAction(): Boolean {
        val root = getActiveRootWindow() ?: return false
        val scrollables = findScrollableNodes(root)
        if (scrollables.isNotEmpty()) {
            return scrollables.first().performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        }
        // Fallback: Simulate gesture
        return simulateScrollGesture(down = false)
    }

    private fun findScrollableNodes(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val scrollableNodes = mutableListOf<AccessibilityNodeInfo>()
        if (node.isScrollable) {
            scrollableNodes.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                scrollableNodes.addAll(findScrollableNodes(child))
            }
        }
        return scrollableNodes
    }

    private fun simulateScrollGesture(down: Boolean): Boolean {
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        val startY = if (down) height * 0.75f else height * 0.25f
        val endY = if (down) height * 0.25f else height * 0.75f
        val x = width / 2.0f

        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }

        val gestureBuilder = GestureDescription.Builder()
        val stroke = GestureDescription.StrokeDescription(path, 0, 400)
        gestureBuilder.addStroke(stroke)

        return dispatchGesture(gestureBuilder.build(), null, null)
    }

    // Find and click buttons matching text (or containing text)
    fun clickButtonByText(targetText: String): Boolean {
        var root = getActiveRootWindow() ?: return false
        var nodes = findNodesWithTextRecursive(root, targetText)
        if (nodes.isNotEmpty()) {
            for (node in nodes) {
                var current: AccessibilityNodeInfo? = node
                while (current != null) {
                    if (current.isClickable) {
                        if (current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            return true
                        }
                    }
                    current = current.parent
                }
            }
            val bounds = android.graphics.Rect()
            nodes.first().getBoundsInScreen(bounds)
            if (clickCoordinates(bounds.centerX().toFloat(), bounds.centerY().toFloat())) {
                return true
            }
        }
        
        var secondaryNodes = findNodesWithDescriptionRecursive(root, targetText)
        if (secondaryNodes.isNotEmpty()) {
            for (node in secondaryNodes) {
                var current: AccessibilityNodeInfo? = node
                while (current != null) {
                    if (current.isClickable) {
                        if (current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            return true
                        }
                    }
                    current = current.parent
                }
            }
        }

        // Retry Gate: If click fails, wait 800ms, scan nodes again, and retry once
        android.util.Log.d("AutomationService", "Primary click failed for '$targetText'. Retrying in 800ms...")
        try { Thread.sleep(800) } catch (e: Exception) {}

        root = getActiveRootWindow() ?: return false
        nodes = findNodesWithTextRecursive(root, targetText)
        if (nodes.isNotEmpty()) {
            for (node in nodes) {
                var current: AccessibilityNodeInfo? = node
                while (current != null) {
                    if (current.isClickable) {
                        if (current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            android.util.Log.d("AutomationService", "Retry click success for text target '$targetText'")
                            return true
                        }
                    }
                    current = current.parent
                }
            }
        }

        secondaryNodes = findNodesWithDescriptionRecursive(root, targetText)
        if (secondaryNodes.isNotEmpty()) {
            for (node in secondaryNodes) {
                var current: AccessibilityNodeInfo? = node
                while (current != null) {
                    if (current.isClickable) {
                        if (current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            android.util.Log.d("AutomationService", "Retry click success for description target '$targetText'")
                            return true
                        }
                    }
                    current = current.parent
                }
            }
        }

        // Fallback to coordinate-based tap only if node exists but performs nothing
        val combined = nodes + secondaryNodes
        if (combined.isNotEmpty()) {
            val bounds = android.graphics.Rect()
            combined.first().getBoundsInScreen(bounds)
            android.util.Log.d("AutomationService", "Coordinate fallback tap triggered at (${bounds.centerX()}, ${bounds.centerY()}) for target '$targetText'")
            return clickCoordinates(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        }
        
        return false
    }

    // Simulate direct coordinates tap gesture
    private fun clickCoordinates(x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        val gestureBuilder = GestureDescription.Builder()
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        gestureBuilder.addStroke(stroke)
        return dispatchGesture(gestureBuilder.build(), null, null)
    }

    // Search and fill edit/text fields
    fun enterTextIntoAnyField(textToEnter: String, hintText: String? = null): Boolean {
        val root = getActiveRootWindow() ?: return false
        val fields = findEditableNodes(root)
        
        if (fields.isNotEmpty()) {
            var targetField = fields.first()
            if (hintText != null) {
                // Look for closest label or hint matches
                for (field in fields) {
                    val textStr = field.text?.toString() ?: ""
                    val hintStr = field.hintText?.toString() ?: ""
                    val descStr = field.contentDescription?.toString() ?: ""
                    if (textStr.contains(hintText, true) || hintStr.contains(hintText, true) || descStr.contains(hintText, true)) {
                        targetField = field
                        break
                    }
                }
            }
            val arguments = Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToEnter)
            val success = targetField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            if (!success) {
                // Try focusing and pasting
                targetField.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val clipArgs = Bundle()
                clipArgs.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToEnter)
                return targetField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clipArgs)
            }
            return success
        }
        return false
    }

    private fun findEditableNodes(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val editableNodes = mutableListOf<AccessibilityNodeInfo>()
        if (node.isEditable || node.className?.toString()?.contains("EditText", true) == true) {
            editableNodes.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                editableNodes.addAll(findEditableNodes(child))
            }
        }
        return editableNodes
    }

    fun findNodesByText(text: String): List<AccessibilityNodeInfo> {
        val root = getActiveRootWindow() ?: return emptyList()
        return findNodesWithTextRecursive(root, text)
    }

    private fun findNodesWithTextRecursive(node: AccessibilityNodeInfo, text: String): List<AccessibilityNodeInfo> {
        val matches = mutableListOf<AccessibilityNodeInfo>()
        val nodeText = node.text?.toString() ?: ""
        if (nodeText.contains(text, true)) {
            matches.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                matches.addAll(findNodesWithTextRecursive(child, text))
            }
        }
        return matches
    }

    private fun findNodesWithDescriptionRecursive(node: AccessibilityNodeInfo, text: String): List<AccessibilityNodeInfo> {
        val matches = mutableListOf<AccessibilityNodeInfo>()
        val descText = node.contentDescription?.toString() ?: ""
        if (descText.contains(text, true)) {
            matches.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                matches.addAll(findNodesWithDescriptionRecursive(child, text))
            }
        }
        return matches
    }

    // Walk screen hierarchy and read out text snippets
    fun getScreenReadableContent(): String {
        val root = getActiveRootWindow() ?: return "System secure window or empty screen layout."
        val builder = StringBuilder()
        collectTexts(root, builder)
        return builder.toString()
    }

    private fun collectTexts(node: AccessibilityNodeInfo, builder: StringBuilder) {
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        
        if (text.isNotBlank()) {
            builder.append("[Text: $nodeTextFilter] ").append(text).append("\n")
        } else if (desc.isNotBlank()) {
            builder.append("[Desc: $nodeTextFilter] ").append(desc).append("\n")
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectTexts(child, builder)
            }
        }
    }

    private val nodeTextFilter: String
        get() = "LayoutElement"

    // Gather and format all clickable elements
    fun getClickableElementsList(): List<String> {
        val root = getActiveRootWindow() ?: return emptyList()
        val clickables = mutableListOf<String>()
        collectClickables(root, clickables)
        return clickables.distinct()
    }

    private fun collectClickables(node: AccessibilityNodeInfo, list: MutableList<String>) {
        if (node.isClickable) {
            val text = node.text?.toString() ?: node.contentDescription?.toString() ?: node.viewIdResourceName ?: "ActionButton"
            if (text.isNotBlank()) {
                list.add(text)
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectClickables(child, list)
            }
        }
    }

    fun findNodeByFuzzyMatch(phrase: String): AccessibilityNodeInfo? {
        val root = getActiveRootWindow() ?: return null
        val matches = findNodesWithTextRecursive(root, phrase)
        if (matches.isNotEmpty()) return matches.first()
        val matchDesc = findNodesWithDescriptionRecursive(root, phrase)
        if (matchDesc.isNotEmpty()) return matchDesc.first()
        return null
    }

    fun performClick(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        return performTapAtCoordinates(bounds.centerX(), bounds.centerY())
    }

    fun performTapAtCoordinates(x: Int, y: Int): Boolean {
        return clickCoordinates(x.toFloat(), y.toFloat())
    }

    // --- High-Grade Custom Media & Social Controllers ---

    fun youtubeSkip30Sec(): Boolean {
        android.util.Log.d("AutomationService", "Executing Skip 30 Seconds gestural trigger...")
        val metrics = resources.displayMetrics
        val w = metrics.widthPixels
        val h = metrics.heightPixels
        // Double-tap right side/top quarter of screens typically targets active YouTube video window
        val tapX = (w * 0.78f).toInt()
        val tapY = (h * 0.22f).toInt()
        
        // Simulates three consecutive fast double-taps to skip exactly 30 seconds (6 taps)
        for (i in 1..6) {
            performTapAtCoordinates(tapX, tapY)
            try { Thread.sleep(70) } catch (e: Exception) {}
        }
        return true
    }

    fun youtubeGoToComments(): Boolean {
        android.util.Log.d("AutomationService", "Locating comments node...")
        // 1. Traverse screen nodes looking for "comment" section matching
        val commentsBtn = findNodeByFuzzyMatch("comments") ?: findNodeByFuzzyMatch("Comments")
        if (commentsBtn != null) {
            return performClick(commentsBtn)
        }
        // 2. Perform scroll down in case Comments button is just off viewport margins
        scrollDownAction()
        try { Thread.sleep(900) } catch (e: Exception) {}
        val commentsBtnRetry = findNodeByFuzzyMatch("comments") ?: findNodeByFuzzyMatch("Comments")
        if (commentsBtnRetry != null) {
            return performClick(commentsBtnRetry)
        }
        return false
    }

    fun youtubeReadTopComment(speakNotification: (String) -> Unit): Boolean {
        val root = getActiveRootWindow() ?: return false
        val foundTexts = mutableListOf<String>()
        collectYouTubeCommentTexts(root, foundTexts)
        
        val filtered = foundTexts.filter { 
            it.length > 5 && 
            !it.lowercase(Locale.ROOT).contains("comment") && 
            !it.lowercase(Locale.ROOT).contains("reply") &&
            !it.lowercase(Locale.ROOT).contains("subscribe")
        }
        
        if (filtered.isNotEmpty()) {
            val topText = filtered.first()
            speakNotification("The top YouTube comment says: \"$topText\"")
            return true
        } else {
            speakNotification("I couldn't find any visible comment content on screen. Try loading comments first.")
            return false
        }
    }

    private fun collectYouTubeCommentTexts(node: AccessibilityNodeInfo, list: MutableList<String>) {
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        
        if (text.isNotBlank() && (viewId.contains("comment") || viewId.contains("text") || text.length > 10)) {
            list.add(text)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectYouTubeCommentTexts(child, list)
            }
        }
    }

    fun instagramReadDMs(speakNotification: (String) -> Unit): Boolean {
        val root = getActiveRootWindow() ?: return false
        val dmsParsed = mutableListOf<String>()
        collectInstagramDMs(root, dmsParsed)
        
        if (dmsParsed.isNotEmpty()) {
            val readout = dmsParsed.take(3).joinToString(". Next: ")
            speakNotification("Read out of active conversations: $readout")
            return true
        } else {
            // Direct messages navigation fallback trigger
            val dmsBtn = findNodeByFuzzyMatch("Direct") ?: findNodeByFuzzyMatch("Messages") ?: findNodeByFuzzyMatch("Inbox")
            if (dmsBtn != null) {
                performClick(dmsBtn)
                speakNotification("Entering Instagram DMs inbox panel.")
                return true
            }
            speakNotification("No direct messages found on current viewport. Try scrolling or opening inbox.")
            return false
        }
    }

    private fun collectInstagramDMs(node: AccessibilityNodeInfo, list: MutableList<String>) {
        val text = node.text?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        if (text.isNotBlank() && (viewId.contains("message") || viewId.contains("row_inbox") || viewId.contains("dm"))) {
            list.add(text)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectInstagramDMs(child, list)
            }
        }
    }

    fun instagramLikeLatestPosts(speakNotification: (String) -> Unit): Boolean {
        val root = getActiveRootWindow() ?: return false
        val buttonsList = mutableListOf<AccessibilityNodeInfo>()
        findInstagramLikeButtons(root, buttonsList)
        
        if (buttonsList.isNotEmpty()) {
            var count = 0
            for (btn in buttonsList.take(3)) {
                if (performClick(btn)) {
                    count++
                    try { Thread.sleep(600) } catch (e: Exception) {}
                }
            }
            speakNotification("Hands-free success: Liked $count active posts.")
            return true
        } else {
            // Coordinate tapping fallback to mimic double tap like
            val metrics = resources.displayMetrics
            val cx = metrics.widthPixels / 2
            val cy = metrics.heightPixels / 2
            
            speakNotification("Executing coordinate-based double-taps to apply post likes...")
            // Post 1
            performTapAtCoordinates(cx, cy)
            try { Thread.sleep(80) } catch (e: Exception) {}
            performTapAtCoordinates(cx, cy)
            
            scrollDownAction()
            try { Thread.sleep(1000) } catch (e: Exception) {}
            
            // Post 2
            performTapAtCoordinates(cx, cy)
            try { Thread.sleep(80) } catch (e: Exception) {}
            performTapAtCoordinates(cx, cy)
            
            speakNotification("Visual feed liked successfully.")
            return true
        }
    }

    private fun findInstagramLikeButtons(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        val desc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        if (desc.contains("Like", ignoreCase = true) || viewId.contains("like", ignoreCase = true)) {
            list.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                findInstagramLikeButtons(child, list)
            }
        }
    }

    fun takeSystemScreenshot(callback: (android.graphics.Bitmap?) -> Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
            try {
                takeScreenshot(
                    android.view.Display.DEFAULT_DISPLAY,
                    executor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshotResult: ScreenshotResult) {
                            val hardwareBuffer = screenshotResult.hardwareBuffer
                            val bitmap = android.graphics.Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshotResult.colorSpace)
                            if (bitmap != null) {
                                val copy = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
                                callback(copy)
                            } else {
                                callback(null)
                            }
                            hardwareBuffer.close()
                        }

                        override fun onFailure(errorCode: Int) {
                            callback(null)
                        }
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
                callback(null)
            }
        } else {
            callback(null)
        }
    }
}
