package com.example.data

object NovaKnowledgeSystem {

    // 1. World Knowledge Database
    data class WorldKnowledge(
        val topic: String,
        val category: String,
        val description: String,
        val relatedTopics: List<String>,
        val requiresLiveData: Boolean
    )

    // 2. App Knowledge Database
    data class AppKnowledge(
        val appName: String,
        val capabilities: List<String>,
        val purpose: String
    )

    // 3. Device Knowledge Database
    data class DeviceFeatureKnowledge(
        val featureName: String,
        val capabilities: List<String>,
        val limitations: List<String>,
        val permissionsRequired: List<String>
    )

    // 4. Tool Knowledge Database
    data class ToolKnowledge(
        val toolName: String,
        val description: String,
        val idealUseCases: List<String>
    )

    // 5. Behavior Rule Database
    data class BehaviorRule(
        val name: String,
        val instruction: String,
        val scope: String
    )

    // 6. Intent Training Examples
    data class TrainingExample(
        val input: String,
        val intent: String,
        val selectedTool: String,
        val expectedReason: String
    )

    // POPUTATED DATABASES

    val worldKnowledgeDb = listOf(
        WorldKnowledge("Java", "Programming", "A highly popular object-oriented programming language designed to have as few implementation dependencies as possible, operating on the Java Virtual Machine (JVM).", listOf("Kotlin", "OOP", "JVM", "Spring Boot"), false),
        WorldKnowledge("Kotlin", "Programming", "A modern, cross-platform, statically typed programming language with type inference. Fully interoperable with Java, and the preferred language for modern Android development.", listOf("Java", "Android Jetpack", "Coroutines"), false),
        WorldKnowledge("Python", "Programming", "An interpreted high-level general-purpose programming language. Its design philosophy emphasizes code readability with use of significant indentation.", listOf("AI Core", "Machine Learning", "Django"), false),
        WorldKnowledge("AI", "Technology", "Artificial Intelligence is intelligence demonstrated by machines, in contrast to natural intelligence displayed by humans and animals.", listOf("Neural Networks", "Deep Learning", "LLMs"), false),
        WorldKnowledge("Neural Networks", "Technology", "Computing systems inspired by the biological neural networks that constitute animal brains, forming the foundation of modern Deep Learning algorithms.", listOf("AI", "Deep Learning", "Backpropagation"), false),
        WorldKnowledge("Cricket Score", "Sports", "Real-time batting, bowling, wicket records, and run counts of active or historic cricket matches.", listOf("IPL Table", "Cricket Standings", "Live Matches"), true),
        WorldKnowledge("IPL Table", "Sports", "Current standings and point records of the Indian Premier League tournament teams.", listOf("Cricket Score", "IPL Standings"), true),
        WorldKnowledge("Bitcoin Price", "Finance", "The current market price of BTC cryptocurrency relative to fiat currencies (USD, INR). Highly volatile and requires real-time search.", listOf("Crypto", "Ethereum Price", "Stock Price"), true),
        WorldKnowledge("Ethereum Price", "Finance", "The current market price of Ether (ETH) cryptocurrency, powered by the decentralized Ethereum smart contract blockchain.", listOf("Crypto", "Bitcoin Price"), true),
        WorldKnowledge("Stock Price", "Finance", "The latest trading price of specific equities on exchanges like NASDAQ, NYSE, or NSE.", listOf("Market Indexes", "Finance"), true),
        WorldKnowledge("Weather Today", "Geography", "Real-time atmospheric parameters like temperature, rain, wind, and forecast conditions for any specific city.", listOf("Forecast", "Temperature", "Rain"), true),
        WorldKnowledge("Elon Musk", "Biography", "Business magnate and tech entrepreneur, founder/CEO of SpaceX, Tesla, Neuralink, and xAI.", listOf("SpaceX", "Tesla", "AI"), false),
        WorldKnowledge("Science", "Education", "The systematic study of the physical and natural world through observation, experimentation, and testing.", listOf("Physics", "Chemistry", "Biology"), false),
        WorldKnowledge("History", "Education", "The study and documentation of past events, human civilizations, development patterns, and epoch occurrences.", listOf("World War", "Archaeology"), false),
        WorldKnowledge("Geography", "Education", "Study of the lands, features, inhabitants, and climate phenomena of Earth.", listOf("Climatology", "Topography"), false)
    )

    val appKnowledgeDb = listOf(
        AppKnowledge("WhatsApp", listOf("send message", "voice call", "video call", "search chat", "share media"), "Encrypted instant messaging application used worldwide for personal and business contacts communication."),
        AppKnowledge("YouTube", listOf("search videos", "play video", "pause execution", "subscribe to channels", "view Shorts"), "Video sharing and streaming media platform housing educational, entertainment, and live content."),
        AppKnowledge("Chrome", listOf("search indices", "open custom website", "download files", "browse historical pages"), "High-performance web browser used to load, test, and render modern web capabilities and downloads."),
        AppKnowledge("Blinkit", listOf("search groceries", "add to shopping cart", "view cart contents", "checkout instant delivery"), "Instant-delivery platform for quick commerce, grocers, and home products."),
        AppKnowledge("Maps", listOf("get navigation routes", "search local business", "check traffic congestion", "locate coordinate parameters"), "Interactive geographic orientation routing map database.")
    )

    val deviceKnowledgeDb = listOf(
        DeviceFeatureKnowledge("Bluetooth", listOf("Scan for adjacent wireless hardware peripherals", "Connect to sound players or speakers", "Transfer bytes locally"), listOf("Requires hardware transceiver activated", "Limited to ~10 meters range"), listOf("android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN")),
        DeviceFeatureKnowledge("WiFi", listOf("Establish internet connections through local access points", "Measure network signal quality metadata", "Scan hot spots"), listOf("Range restricted to router coverage", "Interfered with by thick walls"), listOf("android.permission.CHANGE_WIFI_STATE", "android.permission.ACCESS_FINE_LOCATION")),
        DeviceFeatureKnowledge("Alarm", listOf("Coordinate clock alerts at exact chronological moments", "Vibrate system upon timeout", "Define repeats"), listOf("Will not ring if system power is completely drained"), listOf("android.permission.SET_ALARM")),
        DeviceFeatureKnowledge("Brightness", listOf("Regulate display backlighting density percentages", "Optimize visual legibility under sunlight"), listOf("Requires user write configuration authorization"), listOf("android.permission.WRITE_SETTINGS")),
        DeviceFeatureKnowledge("Volume", listOf("Configure audio gain thresholds for media, ringers, and alarms"), listOf("Cannot surpass safety bounds to prevent auditory damage"), emptyList()),
        DeviceFeatureKnowledge("Battery", listOf("Determine system power reserve metric percentages", "Evaluate charging stream parameters"), listOf("Passive state; cannot be programmatically charged from empty"), emptyList()),
        DeviceFeatureKnowledge("Screenshot", listOf("Acquire high-fidelity layout snapshot images of the display", "Save viewport captures directly"), listOf("Requires dynamic display capture permissions", "Restricted inside secure system fields"), listOf("android.permission.READ_MEDIA_IMAGES")),
        DeviceFeatureKnowledge("Camera", listOf("Capture digital photographs", "Record high-resolution video streams"), listOf("Saves heavy files; blocked if device memory is fully exhausted", "Subject to physical occlusion"), listOf("android.permission.CAMERA")),
        DeviceFeatureKnowledge("Flashlight", listOf("Activate the device LED emitter as an emergency illumination ray"), listOf("Drains charge rapidly", "Generates heat if activated persistently"), emptyList())
    )

    val toolKnowledgeDb = listOf(
         ToolKnowledge("Groq AI", "Ultra-fast inference AI execution engine powered by LPU architecture.", listOf("Factual linguistic explanation requests", "Programming syntax generation", "Logical reasoning, math problem-solving", "Conversational learning tasks")),
         ToolKnowledge("Gemini AI", "Google's deep multimodal generative engine designed for complex cross-context reasoning.", listOf("Deep planning tasks", "Large-context document reviews", "Complex multi-step analytical reasoning")),
         ToolKnowledge("Browser Search Agent", "Autonomous live scraping/search engine that browses live indexes in background.", listOf("Real-time weather reports", "Live sports score verification", "Active crypto and financial stock prices", "Up-to-the-minute news headlines")),
         ToolKnowledge("Contacts & Communication", "Local programmatic connection to dialers, SMS, and WhatsApp intent protocols.", listOf("Calling Nazeer or Hemanth", "Messaging family details", "Dialing urgent numbers")),
         ToolKnowledge("Notes SQLite Notepad", "Local SQLite card recorder designed for permanent preference and note persistence.", listOf("Saving ideas or preferences offline", "Retrieving historical checklists", "Recalling user self-descriptions"))
    )

    val behaviorRulesDb = listOf(
        BehaviorRule("No Hallucination", "You are strictly forbidden from fabricating facts, scores, or prices of elements that require live parameters. If the data is live, you MUST query the Browser engine first.", "Universal Truthfulness"),
        BehaviorRule("Real-time Sourcing", "Always include the updated time, context, or verified source if returning live data scraped from browser agents.", "Information Delivery"),
        BehaviorRule("Execution Verification", "Never declare success on an action until verification of completion state is guaranteed (e.g. check system return indicators).", "Task Completion"),
        BehaviorRule("Active Query Interrogation", "If an input is highly ambiguous or information constraints prevent reliable decisions, gracefully ask the user for clarity.", "Operational Safety"),
        BehaviorRule("Central Router Validation", "The Brain Router must intercept all inputs first before any hardware toggle is completed, ensuring cognitive compliance.", "System Gatekeeping")
    )

    val trainingDataset = listOf(
        TrainingExample("What is Java?", "GENERAL_KNOWLEDGE", "Groq AI", "Linguistic knowledge query, requires no live metrics."),
        TrainingExample("Who is Elon Musk?", "GENERAL_KNOWLEDGE", "Groq AI", "Linguistic historical definition, requires no live metrics."),
        TrainingExample("Explain how a CPU works.", "GENERAL_KNOWLEDGE", "Groq AI", "Complex functional conceptual explanation, requires no live metrics."),
        TrainingExample("What is the current Bitcoin price?", "LIVE_INFORMATION", "Browser Search Agent", "Dynamic cryptochemical market value, absolutely requires live data."),
        TrainingExample("Weather today in Delhi", "LIVE_INFORMATION", "Browser Search Agent", "Perishable atmospheric variables, absolutely requires live data."),
        TrainingExample("Who won yesterday's cricket match?", "LIVE_INFORMATION", "Browser Search Agent", "Recent calendar sports score event, absolutely requires live data."),
        TrainingExample("Open YouTube and look up Kotlin tutorials", "DEVICE_ACTION", "Automation Engine", "Launches specific software applications with search parameters."),
        TrainingExample("Turn on Bluetooth right now", "DEVICE_ACTION", "Automation Engine", "Hardware transceiver toggle."),
        TrainingExample("Set an alarm for 7:30 AM", "DEVICE_ACTION", "Reminder Local Scheduler", "Chronological system alert schedule."),
        TrainingExample("Message Bittu on WhatsApp", "COMMUNICATION", "Safety Guard Dispatcher", "Communication message routing."),
        TrainingExample("Call Hemanth", "COMMUNICATION", "Safety Guard Dispatcher", "Telephonic voice call routing."),
        TrainingExample("Remember my birthday is June 20th", "MEMORY_NOTE", "Local SQLite Database", "Stores permanent relative user record parameters."),
        TrainingExample("Recall what I said about my preferences", "MEMORY_NOTE", "Local SQLite Database", "Retrieves preference checklist parameters from database."),
        TrainingExample("Search free machine learning APIs and save to notes", "BROWSER_RESEARCH", "Research Agent Scraper", "Browses live code asset options and saves results in notepad.")
    )

    /**
     * Looks up if the query contains topics listed in the World Knowledge DB.
     */
    fun findWorldKnowledgeMatch(input: String): WorldKnowledge? {
        val clean = input.lowercase().trim()
        val splitted = clean.split(Regex("\\s+"))
        // Check exact match first
        worldKnowledgeDb.firstOrNull { clean.contains(it.topic.lowercase()) }?.let { return it }
        // check if any of the splitted words are an EXACT match for a word in the topic
        return worldKnowledgeDb.firstOrNull { item ->
            val topicWords = item.topic.lowercase().split(Regex("\\s+"))
            splitted.any { word -> word.length > 2 && topicWords.contains(word) }
        }
    }

    /**
     * Looks up if the query mentions any specific apps.
     */
    fun findAppKnowledgeMatch(input: String): AppKnowledge? {
        val clean = input.lowercase().trim()
        val words = clean.split(Regex("\\s+"))
        return appKnowledgeDb.firstOrNull { app ->
            val appNameLower = app.appName.lowercase()
            appNameLower == clean || words.contains(appNameLower)
        }
    }

    /**
     * Looks up if the query mentions device features.
     */
    fun findDeviceKnowledgeMatch(input: String): DeviceFeatureKnowledge? {
        val clean = input.lowercase().trim()
        val words = clean.split(Regex("\\s+"))
        return deviceKnowledgeDb.firstOrNull { dev ->
            val featureLower = dev.featureName.lowercase()
            featureLower == clean || words.contains(featureLower)
        }
    }
}
