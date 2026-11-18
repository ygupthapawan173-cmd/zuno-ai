# Zeno AI v2

Dark futuristic Jarvis-style voice assistant (Kotlin + Jetpack Compose).

## Build cheyadam
1. Android Studio (Ladybug leda kotha) lo **Open** -> ee folder select cheyandi, Gradle sync avvanivvandi.
2. Phone connect chesi **Run**, leda **Build > Build Bundle(s)/APK(s) > Build APK(s)**.
3. App open chesi **Settings** tab lo Gemini API key paste cheyandi (free: https://aistudio.google.com/apikey).

## Features
- Glowing animated orb (Idle / Listening / Thinking / Speaking), mic loudness reacts
- Voice input Telugu / English / Hindi, replies spoken back (auto Telugu or English voice)
- Streaming AI replies (Gemini), Tanglish friendly
- Chat tab with history saved on device
- Local phone commands: "open youtube", "call 98xxxxxxxx", "search ...", "play ... on youtube", "torch on/off", "what time"
- Continuous conversation mode, quick-action chips
- Telugu commands also work: ఓపెన్ యూట్యూబ్, కాల్ ..., సెర్చ్ ..., టార్చ్ ఆన్

## Android Studio lekunda APK (GitHub Actions)
1. github.com lo kotha repo create cheyandi, ee folder lo files anni upload cheyandi (.github folder tho kalipi).
2. **Actions** tab > **Build APK** > **Run workflow** (leda upload avagane auto start avthundi).
3. 3-5 nimishala tarvata run open chesi, kinda **Artifacts** lo `zeno-ai-debug-apk` download cheyandi, zip lo app-debug.apk untundi.
