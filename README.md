# NOVA - Android Voice Assistant

![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Room Database](https://img.shields.io/badge/Room%20DB-12100E?style=for-the-badge&logo=sqlite&logoColor=white)

NOVA is a sophisticated Android voice assistant built with modern Android development practices. It combines natural language processing with device intelligence to provide a seamless conversational experience, with real-time voice interaction and contextual device awareness.

## ✨ Features

- **Voice-Activated Interactions** - Speak naturally to communicate with NOVA
- **Real-Time Text-to-Speech** - Hear responses with Android TTS integration
- **Device Intelligence** - Access battery status, WiFi connectivity, and Bluetooth information
- **Persistent Conversation History** - Room DB stores and retrieves past interactions
- **Responsive UI** - Built with Jetpack Compose for modern, declarative interfaces
- **Reactive Architecture** - StateFlow and Kotlin Coroutines for efficient state management
- **REST API Integration** - Seamless backend communication for AI capabilities

## 🏗️ Architecture

NOVA follows the **MVVM (Model-View-ViewModel)** architectural pattern:

- **Model** - Room DB entities managing conversation history and app state
- **ViewModel** - StateFlow-powered business logic with coroutine management
- **View** - Jetpack Compose UI components with reactive data binding

This separation ensures maintainability, testability, and scalability across the application.

## 🛠️ Tech Stack

| Component | Technology |
|-----------|-----------|
| **Language** | Kotlin |
| **UI Framework** | Jetpack Compose |
| **Architecture** | MVVM |
| **Database** | Room Database |
| **Async Programming** | Kotlin Coroutines |
| **State Management** | StateFlow |
| **Voice Input** | Android SpeechRecognizer |
| **Voice Output** | Android Text-to-Speech (TTS) |
| **Device APIs** | BatteryManager, WifiManager, BluetoothAdapter |
| **Network** | REST API |

## 🚀 Getting Started

### Prerequisites

- Android Studio (latest version)
- Android SDK 24 or higher
- Gradle 7.0+
- Java Development Kit (JDK) 11+

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/ganenakarthik/Mobile-assistant-.git
   cd Mobile-assistant-
   ```

2. **Open in Android Studio**
   - Launch Android Studio
   - Select "Open an existing Android Studio project"
   - Navigate to the cloned repository directory

3. **Sync Gradle**
   - Android Studio will automatically prompt you to sync Gradle files
   - Allow all dependencies to download and build

4. **Configure API Endpoints**
   - Update the REST API base URL in your configuration
   - Add necessary API keys or credentials as required

5. **Build and Run**
   - Connect an Android device (API 24+) or start an emulator
   - Click "Run" or press `Shift + F10`

## 📱 Usage

1. **Launch NOVA** on your Android device
2. **Grant Permissions** for microphone and device access when prompted
3. **Tap the Microphone Button** to start listening
4. **Speak Your Query** naturally to interact with the assistant
5. **View Responses** in real-time with voice feedback

## 📁 Project Structure

```
Mobile-assistant-/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/nova/
│   │   │   │   ├── ui/              # Jetpack Compose screens
│   │   │   │   ├── viewmodel/       # ViewModels with StateFlow
│   │   │   │   ├── data/
│   │   │   │   │   ├── db/          # Room entities & DAOs
│   │   │   │   │   └── api/         # REST API service
│   │   │   │   └── utils/           # Helper utilities
│   │   │   └── res/                 # Resources
│   │   └── test/                    # Unit tests
│   └── build.gradle.kts             # Dependencies & build config
└── README.md
```

## 🔧 Key Components

### Room Database
- Stores conversation history for persistence
- Enables fast retrieval and querying of past interactions

### StateFlow & Coroutines
- Manages reactive state updates across the app
- Handles asynchronous operations without blocking the UI

### Speech Recognition & TTS
- `SpeechRecognizer` captures voice input
- Android TTS synthesizes voice responses

### Device Managers
- `BatteryManager` - Battery status and health monitoring
- `WifiManager` - Network connectivity information
- `BluetoothAdapter` - Bluetooth device discovery and pairing status

## 📸 Screenshots

| Feature | Preview |
|---------|---------|
| Home Screen | [Screenshot Placeholder] |
| Voice Interaction | [Screenshot Placeholder] |
| Conversation History | [Screenshot Placeholder] |
| Device Status | [Screenshot Placeholder] |

## 🤝 Contributing

Contributions are welcome! To contribute:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 📞 Support

For issues, questions, or suggestions, please open an issue on GitHub or reach out directly.

---

Built with ❤️ by **REX**
