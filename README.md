# RoboPet 🤖🐾

RoboPet is an autonomous, on-device AI living companion for Android featuring expressive real-time animations, computer vision gesture perception, Pomodoro focus mode, interactive mini-games, and dynamic personality dialogue.

## Getting Started

### 1. Prerequisites
- Android Studio Ladybug / Meerkat or later
- Android device with API 34+ (tested on Motorola Moto G 2025)
- Android SDK 36 (compileSdk 36)

### 2. Firebase Configuration Setup
For security, `google-services.json` is excluded from git tracking.

To configure Firebase for your own instance:
1. Create a Firebase project in the [Firebase Console](https://console.firebase.google.com/).
2. Add an Android app with package name `com.example.robopet`.
3. Download `google-services.json` and place it in the `app/` directory:
   ```text
   app/google-services.json
   ```
   *(A reference template is provided at [`app/google-services.json.example`](app/google-services.json.example)).*

### 3. Build & Run
```bash
./gradlew assembleDebug
```
Or open the project in Android Studio and click **Run 'app'**.
