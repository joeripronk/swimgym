# AGENTS.md

## SwimGym Android App

### Commands
- `./gradlew installDebug` - Build and install debug APK on connected device
- `./gradlew assembleDebug` - Build debug APK
- `./gradlew assembleRelease` - Build release APK
- `./gradlew test` - Run unit tests
- `./gradlew lint` - Run lint analysis

### Project Structure
```
app/src/main/java/com/swimgym/app/
    data/           # API, models, repositories
        api/
        model/
        repository/
        parser/
        local/          # Room entities and DAO
    di/
    domain/             # Use cases, repository interfaces
        model/
        repository/
        usecase/
    ui/                 # Compose screens, ViewModels, theme
        screens/
        navigation/
        viewmodel/
        theme/
    receiver/           # Broadcast receivers
    util/               # Utilities (permissions, notifications, alarms)
    worker/             # WorkManager workers
    MainActivity.kt
    SwimGymApp.kt
```

### Notes
- Use standard Android architecture: MVVM, Coroutines
- API endpoints in `data/api/VirtuagymApiClient.kt` - verify actual Virtuagym API via network inspection

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