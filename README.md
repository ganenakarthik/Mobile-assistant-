# NOVA - Next-Generation Interactive Voice Assistant

![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Room Database](https://img.shields.io/badge/Room%20DB-12100E?style=for-the-badge&logo=sqlite&logoColor=white)

**NOVA** is a high-performance, multimodally aligned Android voice assistant delivering ultra-low latency, personality-driven task execution, and intelligent hardware automation. Moving beyond standard assistant wrappers, NOVA integrates real-time hardware telemetry, on-device semantic memory, clipboard interception intelligence, and direct wireless control—all within a reactive, modern Android architecture.

## 🎯 Executive Summary

NOVA reimagines mobile voice assistance through a focus on **fluid, high-fidelity interaction**. Users communicate via natural voice or keyboard, toggle hardware peripherals instantly, record contextual thoughts to a secure database, inspect system parameters, and trigger intelligent actions through screen capture and background processing. The system emphasizes **low-latency responsiveness**, **data privacy**, and **personality-driven engagement**.

---

## ✨ Feature Suite

### 🧠 Multi-Personality Cognitive Core
- **Dynamic Personality Switching**: Instant toggle between specialized operational profiles
  - **JARVIS** — Polished military executive assistant
  - **SAMANTHA** — Empathetic, communicative workspace coordinator  
  - **GLADOS** — Sardonic, dark-themed diagnostic monitor
- **Long-Term Memory**: Local database caches store custom user profiles, preferences, and alignment instructions for personalized interactions

### 📋 Intelligent Clipboard Processing
- **Automatic Detection**: NOVA intercepts clipboard content whenever you open the dashboard
- **Dynamic HUD Actions**: Four instant processing options for any copied text:
  - **Summarize** — Compress logs and text into actionable insights
  - **Translate** — Convert content to your target language
  - **Explain** — Unpack complex definitions or code snippets
  - **Search** — Query across your personal data nodes

### 💭 Smart Notes & Brain Dumps
- **Instant Capture**: Voice commands like *"note this [thought]"* instantly save to the database with high-resolution timestamp logging
- **Interactive Thought Cabinet**: Retrieve stored notes vocally or browse an interactive dashboard with instant purging and categorization
- **Semantic Memory**: Full conversation history and context synthesis for long-term reasoning

### 📡 Complete Hardware Telemetry & Wireless Automation
- **Real-Time Power Metering**: Direct device battery status, charge level, and charging detection
- **Zero-Menu Wireless Control**: Toggle Wi-Fi and Bluetooth states directly without navigating system settings
- **Screenshot & Share**: Capture the active view, save securely, and invoke the system share sheet instantly
- **Hardware Status Inspection**: Monitor connected Bluetooth devices, Wi-Fi networks, and system parameters

### 🎤 Multimodal Communication
- **Voice Interaction**: Native Android SpeechRecognizer with real-time audio processing
- **Text-to-Speech Responses**: High-fidelity TTS with personality-aligned voice synthesis
- **Keyboard Input**: Full fallback support for silent or accessibility-focused interaction

### 🔗 Advanced AI Integration
- **Multi-Engine Architecture**: Fallback system including Gemini Flash REST API with advanced system prompting
- **Offline Fallbacks**: Keyword-based heuristics for zero-bill operation and offline scenarios
- **Long-Term Memory Synthesis**: Context preservation across sessions for coherent, ongoing conversations

### 🌐 Integrated Browser & Web Access
- **NOVA Core Browser**: Seamless web browsing with quick-link shortcuts (Google, GitHub, Reddit, V2EX)
- **Gateway Security**: Secure web portal with status indicators
- **Page Scraping**: Extract and process web content with voice commands
- **Context-Aware Browsing**: Browser state integrated into NOVA's memory and automation

### 🤖 Advanced Automation & App Integration
- **Contextual Action Hub**: Quick-launch buttons for Maps, YouTube, Tasks, and system functions
- **Voice Command Execution**: Trigger complex automations through natural language
- **App Ecosystem Integration**: Direct integration with popular applications for seamless task execution

---

## 🏗️ Architecture Overview

NOVA employs a **reactive, event-driven MVVM architecture** designed for maximum responsiveness and maintainability:

```
┌─────────────────────────────────────────────────────┐
│           User Interaction Layer                    │
│        (Voice, Keyboard, UI Gestures)              │
└────────────────┬────────────────────────────────────┘
                 │
        ┌────────▼────────┐
        │ VoiceAssistant  │
        │     Panel       │  ◄─ Jetpack Compose UI State
        │   (StateFlow)   │
        └────────┬────────┘
                 │
    ┌────────────┼────────────┐
    │            │            │
    ▼            ▼            ▼
┌────────┐  ┌────────┐  ┌────────┐
│Clipboard│ │Context │ │Vocal   │
│Monitor  │ │Sensors │ │Decoders│
└────────┘  └────────┘  └────────┘
    │            │            │
    └────────────┼────────────┘
                 │
        ┌────────▼────────────────┐
        │  AI Processing Layer    │
        │ (Personality Engine,    │
        │  Gemini, Heuristics)    │
        └────────┬────────────────┘
                 │
        ┌────────▼──────────────────┐
        │  Room Database            │
        │  (Neural Vault)           │
        │ • Chat Logs               │
        │ • Brain Dumps             │
        │ • User Preferences        │
        │ • System Telemetry        │
        │ • Browser History         │
        └───────────────────────────┘

┌─────────────────────────────────────┐
│  Hardware Layer (Android APIs)      │
│ • BatteryManager • WifiManager      │
│ • BluetoothAdapter • FileProvider   │
│ • SpeechRecognizer • TTS            │
│ • WebView • MediaProjection         │
└─────────────────────────────────────┘
```

### Core Architectural Principles

- **MVVM Pattern**: Clean separation between UI (Compose), state management (ViewModel), and business logic (Models)
- **Reactive State Flow**: StateFlow and SharedFlow ensure UI automatically syncs with data changes
- **Coroutine-Driven Concurrency**: Kotlin Coroutines eliminate callback hell and manage long-running operations gracefully
- **Database-Backed Persistence**: Room SQLite provides rich relational storage with type-safe queries
- **Hardware Abstraction**: Encapsulated manager interfaces shield business logic from low-level Android API changes

---

## 🛠️ Tech Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| **UI/Rendering** | Jetpack Compose + Material Design 3 | Modern declarative UI, Canvas rendering, particle effects |
| **Architecture** | MVVM | Clean separation of concerns |
| **State Management** | StateFlow, SharedFlow, Kotlin Coroutines | Reactive data binding and async operations |
| **Persistence** | Room Database (SQLite) | Type-safe relational storage, migrations, DAOs |
| **Voice I/O** | Android SpeechRecognizer, Text-to-Speech | Real-time voice input and high-fidelity audio output |
| **Hardware** | BatteryManager, WifiManager, BluetoothAdapter | System telemetry and wireless control |
| **File Management** | FileProvider | Secure sandbox file routing and system sharing |
| **Web Integration** | WebView, Network APIs | Browser functionality and REST API calls |
| **AI/NLP** | Gemini Flash REST API + Custom Fallbacks | Advanced reasoning with offline keyword heuristics |
| **Language** | Kotlin | Null-safe, expressive, fully interoperable with Android |
| **Build System** | Gradle | Modular dependency management |

---

## 📱 UI/UX Showcase

### Main Dashboard
The home screen presents a welcoming interface with:
- **Personalized greeting** with time and weather
- **Clipboard Intelligence panel** showing detected clipboard content with contextual action buttons
- **Automation Core status** indicator showing active automation systems
- **Animated voice orb** with listening state visualization
- **Bottom navigation** for quick access to Chat, Browser, APIs, and Memory

### Memory & Preferences Panel
Access personalized settings with:
- **Cognitive Quantum Core** personality selector (Nova, Jarvis AI, Samantha, GLADOS)
- **Synaptic Node Interactor** visualization showing neural network connections
- **About You** section with user profile and demographics
- **Frequently Used contacts** for quick communication (Mom, Rahul, Dad)
- **Brain Dump & Quick Notes** section with active thought tracking

### NOVA Core Browser
Integrated web browsing featuring:
- **Custom URL input** with gateway security status
- **Quick-link shortcuts** for popular sites (Google, GitHub, Reddit, V2EX)
- **Page scraping** capability for content extraction
- **Navigation controls** with refresh and home options
- **Multi-tab support** for seamless web exploration

### Voice Interaction Panel (Cognitive Bridge)
Advanced voice command interface showing:
- **Animated central orb** with listening state visualization
- **Real-time listening indicator** with pulsing feedback
- **Contextual quick-action buttons**:
  - Launch Maps
  - YouTube Stream
  - Add task/Milk
  - System commands
- **Text command input** fallback option
- **App integration ring** showing connected applications

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio** (latest version recommended)
- **Android SDK 24+** (API level 24 or higher)
- **Gradle 7.0+**
- **Java Development Kit (JDK) 11+**
- **API Key** for Gemini Flash integration

### Installation

1. **Clone the Repository**
   ```bash
   git clone https://github.com/ganenakarthik/Mobile-assistant-.git
   cd Mobile-assistant-
   ```

2. **Open in Android Studio**
   - Launch Android Studio
   - Select **Open an existing Android Studio project**
   - Navigate to the cloned repository directory
   - Allow Android Studio to auto-fix any incompatibilities

3. **Configure API Keys**
   - Create a `.env` file in the project root
   - Add your Gemini API key:
     ```
     GEMINI_API_KEY=your_api_key_here
     ```
   - Alternatively, configure via `build.gradle.kts` buildConfig fields

4. **Sync Gradle Dependencies**
   - Android Studio will prompt automatically
   - Allow all dependencies to download and compile

5. **Set Permissions**
   - Ensure `AndroidManifest.xml` includes required permissions:
     ```xml
     <uses-permission android:name="android.permission.RECORD_AUDIO" />
     <uses-permission android:name="android.permission.INTERNET" />
     <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
     <uses-permission android:name="android.permission.BLUETOOTH" />
     <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
     <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
     <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
     <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
     ```

6. **Build and Run**
   - Connect an Android device (API 24+) or launch an emulator
   - Click **Run** or press `Shift + F10`
   - Grant all permission prompts on first launch

---

## 📱 Usage Guide

### Starting a Conversation

1. **Open NOVA** on your device
2. **Tap the central orb** or say **"Hey NOVA"** to activate voice listening
3. **Speak your command** naturally
4. **Listen to NOVA's response** in your chosen personality
5. **Use follow-up queries** to maintain context

### Personality Switching

- Voice: *"Switch to JARVIS"*, *"Become SAMANTHA"*, or *"Enable GLADOS mode"*
- UI: Tap personality selector in Memory & Preferences panel

### Clipboard Processing

1. Copy any text to your clipboard
2. Open NOVA dashboard
3. NOVA auto-detects clipboard content
4. Choose: **Summarize** | **Translate** | **Explain** | **Search It**

### Recording Brain Dumps

- **Voice**: *"Note this: [your thought]"* or *"Brain dump: [thought]"*
- **UI**: Tap the Notes icon, type manually, and save
- **Retrieval**: Say *"Read my notes"* or tap the dashboard Notes tab

### Hardware Control

- *"What's my battery?"* — Real-time charge level and status
- *"Toggle Wi-Fi"* — Instant wireless state change
- *"Connect to [device name]"* — Bluetooth pairing
- *"Take a screenshot"* — Capture and auto-share

### Web Browsing

- *"Open Google"* — Quick navigation to preset sites
- *"Browse to [URL]"* — Open custom websites
- *"Scrape this page"* — Extract and process page content
- *"Show me [search term]"* — Voice-driven web search

### App Integration

- *"Launch Maps"* — Quick app access
- *"Stream YouTube [query]"* — Direct streaming control
- *"Add milk to my task"* — Task management integration
- *"Show system info"* — Hardware status dashboard

---

## 📁 Project Structure

```
Mobile-assistant-/
├── app/
│   ├── src/main/
│   │   ├── java/com/nova/
│   │   │   ├── ui/
│   │   │   │   ├── screens/          # Jetpack Compose screens
│   │   │   │   ├── components/       # Reusable UI components
│   │   │   │   └── theme/            # Material Design 3 theme
│   │   │   ├── viewmodel/
│   │   │   │   ├── VoiceAssistantViewModel.kt
│   │   │   │   ├── NotesViewModel.kt
│   │   │   │   └── BrowserViewModel.kt
│   │   │   ├── data/
│   │   │   │   ├── db/
│   │   │   │   │   ├── NovaDatabase.kt
│   │   │   │   │   ├── entities/
│   │   │   │   │   └── dao/
│   │   │   │   ├── api/
│   │   │   │   │   └── GeminiService.kt
│   │   │   │   └── repository/
│   │   │   ├── domain/
│   │   │   │   ├── model/
│   │   │   │   └── usecase/
│   │   │   ├── hardware/
│   │   │   │   ├── BatteryTelemetry.kt
│   │   │   │   ├── WirelessManager.kt
│   │   │   │   └── DeviceStateMonitor.kt
│   │   │   ├── ai/
│   │   │   │   ├── PersonalityEngine.kt
│   │   │   │   ├── ConversationSynthesis.kt
│   │   │   │   └── OfflineFallback.kt
│   │   │   ├── browser/
│   │   │   │   ├── BrowserController.kt
│   │   │   │   └── WebScraperService.kt
│   │   │   └── utils/
│   │   │       ├── ClipboardMonitor.kt
│   │   │       ├── ScreenCapture.kt
│   │   │       └── Extensions.kt
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## 🔧 Key Components Deep Dive

### Voice Assistant Panel (Main UI State)
Jetpack Compose-based reactive UI that binds directly to ViewModel's StateFlow. Handles voice input UI, response display, personality indicators, hardware status widgets, and browser integration.

### Personality Engine
Dynamically switches system prompts, response tone, and behavioral profiles. Each personality has dedicated prompt engineering and response formatting.

### Clipboard Monitor
Automatically intercepts clipboard changes when the dashboard is visible. Triggers contextual action suggestions via composables.

### Browser Integration
Built-in WebView with custom controls, URL routing, page scraping, and voice command support. Integrates browsing context into NOVA's memory.

### Hardware Telemetry Layer
Encapsulated wrappers around BatteryManager, WifiManager, and BluetoothAdapter. Exposes clean APIs for state queries and transitions.

### Room Database (Neural Vault)
Relational SQLite schema storing conversations, brain dumps, user profiles, system telemetry, and browser history.

### Gemini AI Integration
REST-based client with fallback to offline heuristic matching. Supports multi-turn conversations with long-context memory synthesis.

### Offline Fallback Engine
Keyword-matching and rule-based responses for zero-network scenarios. Enables basic task execution without cloud connectivity.

---

## ⚙️ System Constraints

| Constraint | Details | Why |
|-----------|---------|-----|
| **System Security** | Cannot toggle GPS, Airplane Mode, or reboot without `su` | Android SELinux policies |
| **Screen Recording** | Cannot capture third-party apps or run background grabbers | MediaProjection privacy restrictions |
| **Biometric Interfacing** | Cannot override lock screens or fingerprints | Hardware enclave protection |
| **Offline Advanced AI** | Complex reasoning requires cloud NLP | On-device models are computationally expensive |
| **Cross-App Automation** | Limited to Intent-based IPC | App sandboxing restrictions |

---

## 🎨 Roadmap

### ✅ Currently Implemented
- [x] Multi-personality voice assistant core
- [x] Real-time hardware telemetry (battery, Wi-Fi, Bluetooth)
- [x] Clipboard interception and contextual actions
- [x] Voice input/output with SpeechRecognizer and TTS
- [x] Brain dump note-taking with Room persistence
- [x] Gemini Flash integration with system prompting
- [x] MVVM architecture with Jetpack Compose
- [x] Screenshot capture and system sharing
- [x] Integrated web browser with quick-links
- [x] App integration and automation hub

### 📋 Future Enhancements

1. **On-Device Semantic Embedding** — Vector database with semantic search
2. **Offline Speech Pipeline** — Whisper integration for local voice-to-text
3. **Advanced MediaProjection** — System-wide screen capture and overlay support
4. **Semantic Context Synthesis** — Vector-based long-term memory
5. **Permission Management Dashboard** — Granular transparency controls
6. **Progressive Web App Companion** — Desktop dashboard with real-time sync

---

## 📸 Screenshots

| Feature | Description |
|---------|-------------|
| **Memory & Preferences** | Personality selection and brain dump management |
| **NOVA Core Browser** | Integrated web browsing with custom controls |
| **Voice Interaction Panel** | Main dashboard with animated orb |
| **Cognitive Bridge** | Voice command hub with app integration |
| **Clipboard Intelligence** | Auto-detected clipboard with contextual actions |
| **Conversation History** | Full chat log with personality-aligned responses |

---

## 🤝 Contributing

NOVA welcomes contributions! To get involved:

1. **Fork the Repository**
2. **Create a Feature Branch** — `git checkout -b feature/your-feature-name`
3. **Commit Changes** — `git commit -m "Add [feature]: description"`
4. **Push to Your Fork** — `git push origin feature/your-feature-name`
5. **Open a Pull Request** with clear description and test results

### Guidelines
- Follow Kotlin style guide (ktlint recommended)
- Write unit tests for new features
- Update documentation for API changes
- Test on multiple API levels (24, 29, 34+)

---

## 📄 License

This project is licensed under the **MIT License**. See [LICENSE](LICENSE) file for details.

You are free to use, modify, and distribute NOVA for personal or commercial purposes with proper attribution.

---

## 📞 Support & Contact

**Issues & Bug Reports**: [Open GitHub Issue](https://github.com/ganenakarthik/Mobile-assistant-/issues)

**Feature Requests**: [Start Discussion](https://github.com/ganenakarthik/Mobile-assistant-/discussions)

**Direct Contact**: Reach out via GitHub

---

## 🎓 Learning Resources

- [Android Jetpack Documentation](https://developer.android.com/jetpack)
- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-overview.html)
- [Room Database Persistence](https://developer.android.com/training/data-storage/room)
- [Material Design 3 on Compose](https://developer.android.com/design/material/get-started)
- [Android Hardware Integration](https://developer.android.com/guide/topics/sensors)
- [WebView Development](https://developer.android.com/develop/ui/views/layout/webapps/webview)

---

## 🏆 Acknowledgments

NOVA represents a deep exploration of modern Android architecture, reactive programming, and voice-driven UX design:
- Production-grade MVVM implementation
- Advanced state management with coroutines
- Hardware-aware application design
- Personality-driven AI alignment
- Secure data persistence patterns
- Integrated web and automation capabilities

---

Built with ❤️ by **REX**
