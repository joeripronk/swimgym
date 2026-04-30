# AGENTS.md

## SwimGym Android App

### Commands
- `./gradlew installDebug` - Build and install debug APK on connected device
- `./gradlew assembleDebug` - Build debug APK
- `./gradlew assembleRelease` - Build release APK
- `./gradlew test` - Run unit tests
- `./gradlew lint` - Run lint analysis
- `./gradlew ktlintCheck` - Run Kotlin linter

### Project Structure
```
app/src/main/java/com/swimgym/app/
    data/           # API, models, repositories
    di/             # Hilt modules
    domain/         # Use cases, repository interfaces
    ui/             # Compose screens, ViewModels, theme
```

### Notes
- Android SDK at `/home/joeri/android-sdk`
- Java 17 at `/home/joeri/java/jdk-17.0.19+10`
- Run with: `JAVA_HOME=/home/joeri/java/jdk-17.0.19+10 ANDROID_SDK_ROOT=/home/joeri/android-sdk ./gradlew ...`
- Use standard Android architecture: MVVM, Hilt DI, Coroutines
- API endpoints in `data/api/VirtuagymApi.kt` - verify actual Virtuagym API via network inspection

## HTML Analysis Tool

### Setup
```bash
pip install -r tools/requirements.txt
```

### Commands
- `python tools/analyze_swimgym_html.py login --email <email>` - Login and save cookies
- `python tools/analyze_swimgym_html.py analyze [path]` - Analyze page with saved cookies

### Examples
```bash
python tools/analyze_swimgym_html.py login --email swimgym@joeri.nu
python tools/analyze_swimgym_html.py analyze
python tools/analyze_swimgym_html.py analyze /schedule
```