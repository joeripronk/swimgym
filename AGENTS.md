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

## Curl Debug Tooling

### Scripts
- `tools/curl_schedule.sh` - Fetch daily schedule page (`/classes/day/{date}`)
- `tools/booking.sh` - Reserve a class (`action=reserve_class`)
- `tools/cancel_booking.sh` - Cancel a booking (`action=cancel_reserve_class`)
- `tools/book_info.sh` - Fetch booking info for a specific class

### HTML Dumps
- `tools/schedule.html` - Saved schedule page response
- `tools/book_info.html` - Saved booking info response

### Usage
Scripts contain hardcoded cookies/tokens from a prior session. Update the `Cookie:` header with fresh tokens from the browser to use.