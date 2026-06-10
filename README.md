---
**Developer**: REX | Instagram: [@THEDEVREX](https://instagram.com/THEDEVREX)

---

# NOVA - Next-Generation Interactive Voice Assistant

![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Room Database](https://img.shields.io/badge/Room%20DB-12100E?style=for-the-badge&logo=sqlite&logoColor=white)

**NOVA** is a high-performance, multimodally aligned Android voice assistant delivering ultra-low latency, personality-driven task execution, and intelligent hardware automation. Moving beyond standard voice assistants, NOVA adapts its operational personality to your communication style, manages hardware with zero-menu control, and preserves rich conversational context through local database persistence.

## 🎯 Executive Summary

NOVA reimagines mobile voice assistance through a focus on **fluid, high-fidelity interaction**. Users communicate via natural voice or keyboard, toggle hardware peripherals instantly, record contextual notes with semantic indexing, and harness advanced AI reasoning with complete offline fallback capabilities.

---

## ✨ Feature Suite

### 🧠 Multi-Personality Cognitive Core
- **Dynamic Personality Switching**: Instant toggle between specialized operational profiles
  - **NOVA** — Balanced, responsive default personality
  - **JARVIS** — Polished military executive assistant
  - **SAMANTHA** — Empathetic, highly communicative workspace coordinator  
  - **GLADOS** — Sardonic, dark-themed diagnostic monitor
- **Long-Term Memory**: Local database caches store custom user profiles, preferences, and alignment instructions for personalized interactions
- **Synaptic Node Visualizer**: Interactive neural network visualization showing conversation context and memory synthesis

### 📋 Intelligent Clipboard Processing
- **Automatic Detection**: NOVA intercepts clipboard content whenever you open the dashboard
- **Dynamic HUD Actions**: Four instant processing options for any copied text:
  - **Summarize** — Compress logs and text into actionable insights
  - **Translate** — Convert content to your target language
  - **Explain** — Unpack complex definitions or code snippets
  - **Search It** — Query across your personal data nodes

### 💭 Smart Notes & Brain Dumps
- **Instant Capture**: Voice commands like *"note this [thought]"* or *"brain dump [thought]"* instantly save to the database with high-resolution timestamp logging
- **Interactive Thought Cabinet**: Stored entries can be read back vocally or browsed in an interactive dashboard with instant purging and categorization
- **Semantic Memory**: Full conversation history with automatic context synthesis for coherent multi-turn reasoning

### 📡 Complete Hardware Telemetry & Wireless Automation
- **Real-Time Power Metering**: Direct device battery status, charge level, and active charging detection
- **Zero-Menu Wireless Control**: Toggle Wi-Fi and Bluetooth states directly without navigating system settings
- **Screenshot & Share**: Capture the active view, save securely, and invoke the system share sheet instantly
- **Hardware Status Inspection**: Monitor connected Bluetooth devices, Wi-Fi networks, and system parameters

### 🎤 Multimodal Communication
- **Voice Interaction**: Native Android SpeechRecognizer with real-time audio processing
- **Text-to-Speech Responses**: High-fidelity TTS with personality-aligned voice synthesis
- **Keyboard Input**: Full fallback support for silent or accessibility-focused interaction
- **Command Recognition**: Contextual intent parsing with automated task routing

### 🔗 Advanced AI Integration
- **Multi-Engine Architecture**: Fallback system including Gemini Flash REST API with advanced system prompting
- **Offline Fallbacks**: Keyword-based heuristics for zero-bill operation and offline scenarios
- **Long-Term Memory Synthesis**: Context preservation across sessions for coherent, ongoing conversations
- **Cognitive Bridge**: Real-time app context awareness with cross-platform action triggering

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
         └───────────────────────────┘

 ┌─────────────────────────────────────┐
 │  Hardware Layer (Android APIs)      │
 │ • BatteryManager • WifiManager      │
 │ • BluetoothAdapter • FileProvider   │
 │ • SpeechRecognizer • TTS            │
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
| **AI/NLP** | Gemini Flash REST API + Custom Fallbacks | Advanced reasoning with offline keyword heuristics |
| **Language** | Kotlin | Null-safe, expressive, fully interoperable with Android |
| **Build System** | Gradle | Modular dependency management |

---

## 🚀 Getting Started

### Prerequisites

- **Android SDK 24+** (API level 24 or higher)
- **Gradle 7.0+**
- **Java Development Kit (JDK) 11+**
- **Git** installed on your system
- **API Key** for Gemini Flash integration (see Configuration)

### Installation

1. **Clone the Repository**
   ```bash
   git clone https://github.com/ganenakarthik/Mobile-assistant-.git
   cd Mobile-assistant-
   ```

2. **Open in IDE (Android Studio, IntelliJ, or equivalent)**
   - Open your IDE
   - Select **File → Open** and navigate to the cloned repository directory
   - Allow Gradle to sync and auto-fix any incompatibilities

3. **Configure API Keys**
   - Create a `.env` file in the project root
   - Add your Gemini API key:
     ```
     GEMINI_API_KEY=your_api_key_here
     ```
   - Alternatively, configure via `build.gradle.kts` buildConfig fields

4. **Sync Gradle Dependencies**
   - Your IDE will prompt automatically for Gradle sync
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
2. **Tap the Orb** (central glowing interface) to activate voice listening
3. **Speak your command** naturally:
   - *"What's my battery level?"*
   - *"Create a note about my meeting"*
   - *"Toggle Wi-Fi"*
   - *"Take a screenshot"*
4. **Listen to NOVA's response** in your chosen personality
5. **Use follow-up queries** to maintain context

### Personality Switching

- **Voice Command**: *"Switch to JARVIS"*, *"Become SAMANTHA"*, or *"Enable GLADOS mode"*
- **UI Navigation**: Open Memory & Preferences, tap personality badge
- **Instant Switching**: Four-button personality selector with visual neural network feedback

### Clipboard Processing

1. Copy any text to your clipboard (code, logs, articles, etc.)
2. Open NOVA dashboard or main chat screen
3. NOVA auto-detects clipboard content and displays HUD with options
4. Choose action: **Summarize** | **Translate** | **Explain** | **Search It**

### Recording Brain Dumps

- **Voice**: *"Note this: [your thought]"* or *"Brain dump: [thought]"*
- **UI**: Tap the Brain Dump panel, type manually, save
- **Retrieval**: Say *"Read my notes"* or browse the interactive Notes dashboard
- **Organization**: Auto-tag and categorize using semantic memory

### Hardware Control

- *"What's my battery?"* — Real-time charge level and charging status
- *"Toggle Wi-Fi"* — Instant wireless state change without menu navigation
- *"Connect to [device name]"* — Bluetooth pairing and device management
- *"Take a screenshot"* — Capture active view and instantly share
- *"Show system status"* — Display battery, connectivity, and device info

### Core Browser & Web Integration

- Open **Core Browser** tab for integrated web browsing
- Pre-configured shortcuts: Google, GitHub, Reddit, V2EX
- NOVA can scrape page content for contextual processing
- "Gateway Secured" status indicates safe browsing mode

---

## 📁 Project Structure

```
Mobile-assistant-/
├── app/
│   ├── src/main/
│   │   ├── java/com/nova/
│   │   │   ├── ui/
│   │   │   │   ├── screens/          # Jetpack Compose screens
│   │   │   │   │   ├── HomeScreen.kt
│   │   │   │   │   ├── ChatScreen.kt
│   │   │   │   │   ├── BrowserScreen.kt
│   │   │   │   │   ├── MemoryScreen.kt
│   │   │   │   │   └── APIsScreen.kt
│   │   │   │   ├── components/       # Reusable UI components
│   │   │   │   │   ├── VoiceOrb.kt
│   │   │   │   │   ├── ClipboardHUD.kt
│   │   │   │   │   ├── PersonalitySelector.kt
│   │   │   │   │   ├── BrainDumpPanel.kt
│   │   │   │   │   └── SynapticVisualizer.kt
│   │   │   │   └── theme/            # Material Design 3 theme config
│   │   │   ├── viewmodel/
│   │   │   │   ├── VoiceAssistantViewModel.kt
│   │   │   │   ├── NotesViewModel.kt
│   │   │   │   ├── BrowserViewModel.kt
│   │   │   │   └── HardwareViewModel.kt
│   │   │   ├── data/
│   │   │   │   ├── db/
│   │   │   │   │   ├── NovaDatabase.kt
│   │   │   │   │   ├── entities/     # Room entity classes
│   │   │   │   │   │   ├── ConversationEntity.kt
│   │   │   │   │   │   ├── BrainDumpEntity.kt
│   │   │   │   │   │   └── UserProfileEntity.kt
│   │   │   │   │   └── dao/          # Data Access Objects
│   │   │   │   ├── api/
│   │   │   │   │   ├── GeminiService.kt
│   │   │   │   │   ├── GeminiRepository.kt
│   │   │   │   │   └── models/
│   │   │   │   └── repository/       # Business logic layer
│   │   │   ├── domain/
│   │   │   │   ├── model/            # Domain models
│   │   │   │   │   ├── Conversation.kt
│   │   │   │   │   ├── BrainDump.kt
│   │   │   │   │   └── UserProfile.kt
│   │   │   │   └── usecase/          # Use case classes
│   │   │   ├── hardware/
│   │   │   │   ├── BatteryTelemetry.kt
│   │   │   │   ├── WirelessManager.kt
│   │   │   │   └── DeviceStateMonitor.kt
│   │   │   ├── ai/
│   │   │   │   ├── PersonalityEngine.kt
│   │   │   │   ├── ConversationSynthesis.kt
│   │   │   │   ├── OfflineFallback.kt
│   │   │   │   └── models/
│   │   │   │       ├── JARVIS.kt
│   │   │   │       ├── SAMANTHA.kt
│   │   │   │       └── GLADOS.kt
│   │   │   └── utils/
│   │   │       ├── ClipboardMonitor.kt
│   │   │       ├── ScreenCapture.kt
│   │   │       ├── VoiceProcessor.kt
│   │   │       └── Extensions.kt
│   │   ├── res/
│   │   │   ├── drawable/
│   │   │   ├── values/                # Strings, colors, dimens
│   │   │   └── raw/                   # Audio assets
│   │   └── AndroidManifest.xml
│   ├── build.gradle.kts               # App-level dependencies
│   └── proguard-rules.pro
├── build.gradle.kts                   # Project-level config
├── settings.gradle.kts
├── gradle/wrapper/
├── .env.example
└── README.md
```

---

## 🔧 Key Components Deep Dive

### Voice Orb & Main Interface (HomeScreen)
Jetpack Compose-based reactive UI featuring the central Voice Orb visualization. Binds directly to ViewModel's StateFlow. Displays:
- Real-time voice activity indicator
- Personality indicator
- Quick access buttons for common commands
- Clipboard HUD integration
- System status widgets

### Personality Engine
Dynamically switches system prompts, response tone, and behavioral profiles. Each personality (NOVA, JARVIS, SAMANTHA, GLADOS) has:
- Dedicated system prompting
- Unique response voice characteristics
- Behavioral profile templates
- Memory synthesis preferences

### Clipboard Monitor
Runs on UI thread, automatically intercepts clipboard changes when dashboard is visible. Triggers contextual action suggestions via composables with four instant processing options.

### Synaptic Node Interactor
Interactive neural network visualization showing conversation context and memory nodes. Touch to warp synapses, representing memory synthesis and context mapping. Visual feedback for cognitive processing stages.

### Hardware Telemetry Layer
Encapsulated wrappers around BatteryManager, WifiManager, and BluetoothAdapter. Exposes clean APIs for state queries and state transitions with real-time event broadcasting.

### Room Database (Neural Vault)
Relational SQLite schema storing:
- **Conversations**: User queries, NOVA responses, timestamp, context, personality used
- **Brain Dumps**: User notes with tagging, semantic indexing, and custom categorization
- **User Profile**: Stored name, age, profession, preferences, personality selection
- **System Telemetry**: Historical battery, connectivity, and device data
- **Hardware Events**: Logged state changes and user interactions

### Gemini AI Integration
REST-based client with fallback to offline heuristic matching. Supports:
- Multi-turn conversations with context preservation
- Long-context memory synthesis
- Personality-aligned system prompting
- Streaming responses for real-time feedback
- Error recovery and graceful degradation

### Offline Fallback Engine
Keyword-matching and rule-based responses for zero-network scenarios. Enables basic task execution (battery status, note-taking, command routing) without cloud connectivity.

### Core Browser Integration
Embedded web browser with NOVA-specific features:
- URL command parsing
- Page scraping for content processing
- Integrated search shortcuts
- Gateway security layer

---

## 📸 Screenshots & Visual Guide

### Screen 1: Memory & Preferences Dashboard
Features:
- **Cognitive Quantum Core** - Personality selector with synaptic visualization
- **Personality Toggle** - NOVA, JARVIS, SAMANTHA, GLADOS options
- **About You** - User profile with name, age, language, location
- **Frequently Used** - Quick access contacts (Mom, Rahul, Dad)
- **Brain Dump Cabinet** - Quick notes section with note count

### Screen 2: Nova Core Browser
Features:
- **Navigation Bar** - Back, forward, refresh, home buttons
- **URL Input** - Direct web address entry with GO button
- **Quick Shortcuts** - Google, GitHub, Reddit, V2EX
- **Scrape Page** - Extract content for processing
- **Gateway Security** - Status indicator for safe browsing

### Screen 3: Voice Assistant Home Screen
Features:
- **Greeting** - Personalized welcome message with time and weather
- **Clipboard Intelligence** - Auto-detected copied text with action buttons
  - Summarize, Translate, Explain, Search It
- **Automation Core** - Active status indicator
- **Central Voice Orb** - Interactive visualization with reactive glow
- **Listening Indicator** - Visual feedback during voice processing
- **Command Buttons** - Quick shortcuts: Launch Maps, YouTube Stream, Add Tasks

### Screen 4: Cognitive Bridge Voice Interface
Features:
- **Listening State** - Red/pink visualization during audio capture
- **App Context Ring** - Surrounding app icons showing detected applications
- **Command Input** - Text fallback for typed commands
- **Voice Indicator** - Top-right green microphone when active
- **Cross-App Awareness** - Intelligent detection of running applications

---

## ⚙️ System Constraints & Non-Capabilities

Understanding NOVA's boundaries is essential for setting realistic expectations:

| Constraint Domain | Details | Why It Exists |
|-------------------|---------|---------------|
| **System Security & Device Control** | Cannot toggle GPS, Airplane Mode, or reboot without `su` permissions | Android enforces strict SELinux policies for device integrity |
| **Active Screen Recording** | Cannot capture third-party apps or run background frame grabbers | MediaProjection APIs restrict privacy; OS prevents unauthorized recording |
| **Biometric Interfacing** | Cannot override, record, or modify lock screens or fingerprint templates | Hardware enclaves protect biometric data; SELinux prevents access |
| **Offline Advanced AI** | Complex multi-role reasoning requires cloud NLP services | On-device models are computationally expensive; current design favors responsiveness |
| **Cross-App Automation** | Limited to NOVA's own context and Android Intent-based IPC | App sandboxing prevents unrestricted cross-process control |

---

## 🎨 Current State & Future Roadmap

### ✅ Currently Implemented
- [x] Multi-personality voice assistant core with NOVA, JARVIS, SAMANTHA, GLADOS
- [x] Real-time hardware telemetry (battery, Wi-Fi, Bluetooth)
- [x] Clipboard interception and contextual actions (Summarize, Translate, Explain, Search)
- [x] Voice input/output with SpeechRecognizer and TTS
- [x] Brain dump note-taking with Room persistence and categorization
- [x] Gemini Flash integration with personality system prompting
- [x] MVVM architecture with Jetpack Compose and StateFlow
- [x] Screenshot capture and system sharing
- [x] Synaptic node visualization for memory synthesis
- [x] Core browser with page scraping integration
- [x] User profile management (name, age, preferences)
- [x] Frequently used contacts integration

### 📋 Recommended Future Enhancements

#### 1. **On-Device Semantic Embedding (Vector Database)**
Replace keyword scanning with a local tokenizing vector embedding model. Enable semantic search—e.g., retrieving a "groceries" note when querying "food".
- **Technology**: Sentence-BERT or lightweight embedding model
- **Storage**: SQLite with vector extension (sqlite-vec)
- **Benefit**: Dramatically improved note retrieval accuracy and memory synthesis

#### 2. **Continuous Offline Speech Pipeline**
Integrate compact Whisper (OpenAI) for local voice-to-text decoding, eliminating network latency and dependency on Google Speech API.
- **Technology**: Whisper Tiny/Base model quantized for mobile (ONNX)
- **Integration**: MediaStore + local audio processing pipeline
- **Benefit**: Ultra-low latency, works completely offline, 100% user data privacy

#### 3. **Advanced MediaProjection Callbacks**
Transition from DecorView snapshots to proper MediaProjection API for system-wide screen capture, overlay support, and background frame grabbing.
- **Technology**: Android MediaProjection API with service callbacks
- **Use Cases**: Persistent on-screen HUD, screen-aware context, third-party app monitoring
- **Benefit**: Professional-grade screen capture and real-time visual context awareness

#### 4. **Semantic Context Synthesis**
Implement vector-based long-term memory that stores embeddings of past interactions, enabling NOVA to reference similar past contexts automatically.
- **Technology**: Vector database, similarity search with semantic matching
- **Benefit**: Richer, more coherent multi-turn conversations with enhanced contextual understanding

#### 5. **Advanced Permission Management UI**
Build a permission dashboard showing what permissions NOVA has requested, used, and denied. Offer granular revocation controls.
- **Technology**: PermissionState tracking with reactive UI
- **Benefit**: Enhanced transparency and user control over data access

#### 6. **Real-Time Multi-Modal Processing**
Integrate vision models for visual question answering, OCR, and screen content understanding.
- **Technology**: TensorFlow Lite with lightweight vision models
- **Use Cases**: Screenshot analysis, document processing, visual context extraction
- **Benefit**: True multimodal assistant with visual understanding

---

## 🤝 Contributing

NOVA welcomes community contributions! To get involved:

1. **Fork the Repository**
   ```bash
   git clone https://github.com/ganenakarthik/Mobile-assistant-.git
   cd Mobile-assistant-
   ```

2. **Create a Feature Branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```

3. **Commit Changes**
   ```bash
   git commit -m "Add [feature]: clear description of changes"
   ```

4. **Push to Your Fork**
   ```bash
   git push origin feature/your-feature-name
   ```

5. **Open a Pull Request**
   - Provide clear description of changes
   - Reference any related issues
   - Ensure all tests pass locally

### Contribution Guidelines
- Follow Kotlin style guide (ktlint recommended)
- Write unit tests for new features
- Update documentation for API changes
- Test on multiple API levels (24, 29, 34+)
- Ensure compatibility with existing personality engines

---

## 📄 License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.

You are free to use, modify, and distribute NOVA for personal or commercial purposes with proper attribution.

---

## 📞 Support & Contact

**Issues & Bug Reports**: Open a [GitHub Issue](https://github.com/ganenakarthik/Mobile-assistant-/issues)

**Feature Requests**: Start a [Discussion](https://github.com/ganenakarthik/Mobile-assistant-/discussions)

**Direct Contact**: Reach out via GitHub or the project repository

---

## 🎓 Learning Resources

- [Android Jetpack Documentation](https://developer.android.com/jetpack)
- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-overview.html)
- [Room Database Persistence](https://developer.android.com/training/data-storage/room)
- [Material Design 3 on Compose](https://developer.android.com/design/material/get-started)
- [Android Hardware Integration](https://developer.android.com/guide/topics/sensors)
- [Speech Recognition & TTS](https://developer.android.com/reference/android/speech/SpeechRecognizer)

---

## 🏆 Acknowledgments

NOVA represents a deep exploration of modern Android architecture, reactive programming, and voice-driven UX design. It demonstrates:
- Production-grade MVVM implementation with StateFlow
- Advanced state management with Kotlin Coroutines
- Hardware-aware application design with real-time telemetry
- Personality-driven AI alignment and system prompting
- Secure data persistence patterns with Room Database
- Reactive UI architecture with Jetpack Compose
- Multi-engine AI integration with graceful fallbacks

---

Built with ❤️ by **REX**

---

**Developer**: REX | Instagram: [@THEDEVREX](https://instagram.com/THEDEVREX)
