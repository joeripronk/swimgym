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
    MainActivity.kt
    SwimGymApp.kt
```

### Notes
- Use standard Android architecture: MVVM, Coroutines
- API endpoints in `data/api/VirtuagymApiClient.kt` - verify actual Virtuagym API via network inspection

### Versions & Conventions
- AGP `9.2.1`, Kotlin `2.2.10`, Compose (AndroidX)
- KSP `2.3.4` for Room `2.7.0`, DB version 11 (destructive-migration fallback)
- Retrofit + OkHttp `4.12.0` for Virtuagym REST API; Gson `2.10.1` for models
- Jsoup `1.17.2` parses Virtuagym HTML schedule pages
- Coil `2.5.0` (coil-compose) for image loading
- DataStore `1.0.0` for settings preferences
- UI is single-journey Compose: schedule → detail → booking info

## Architecture Analysis

### Domain: Virtuagym HTML Scraping Proxy
The app is a **frontend proxy** for the Virtuagym web portal (`swimgym.virtuagym.com`). It does not use a REST API — it scrapes HTML schedule pages with Jsoup and submits booking/cancellation via form POST requests (identical to the browser's form submission). This means:
- The app is fragile to Virtuagym HTML structure changes
- The `ScheduleParser` (Jsoup selectors) and `VirtuagymApiClient` (form fields) are the two most likely break points
- All state management revolves around cookie persistence (`virtuagym_u` cookie = user ID > 1)

### Application Flow
```
SwimGymApp.onCreate()
  → creates SwimGymAppContainer (singleton, no DI framework)
  → arms schedule sync alarm (09:00, 21:00 daily)
  → reschedules all pending booking alarms

NavHost
  → checks cookies → Login screen or Schedule screen
  → Login → saves cookies to DataStore → refresh schedule → Schedule
  → Schedule → TrainingDetail (with booking/cancel UI)
  → MyBookings → view + cancel bookings, manage scheduled (recurring) bookings
  → Settings → calendar integration, reminders, work-time filter, exact alarms
```

### Data Layer
**Room DB** (`swimgym_database`, 3 tables):
- `trainings` — cached schedule items (id, title, instructor, startTime, endTime, isJoined, isFull, eventId, calendarId...)
- `bookings` — confirmed bookings (trainingId, status, lastUpdated)
- `instructors` — instructor image cache (name, link, imageUrl)

**DataStore** (`session` + `scheduled_bookings`):
- `session` — auth tokens, cookies (serialized "key=value; key=value"), user prefs (level, calendar, reminders, work-time filter, exact alarm toggle)
- `scheduled_bookings` — **custom JSON** stored as a single string in DataStore, parsed with hand-rolled regex (no Gson for this; why Gson is included for other models is unclear)

### Scheduling System (Recurring Bookings)
The app supports **recurring weekly bookings** — the user selects a class and sets a max repeat count. The system:
1. Stores a `ScheduledBooking` with `startTime`, `maxRepeatCount`, `bookedCount`
2. Uses `AlarmManager` to fire a `PendingIntent` 5 minutes before the booking window opens (7 days before the class)
3. On alarm fire, `ScheduledBookingReceiver` calls `BookingScheduler.checkBookings()` / `processSingleBooking()`
4. The scheduler finds the next weekly occurrence of the class, fetches details from API, checks if full/joined, and calls `apiClient.bookTraining()`
5. On success, increments `bookedCount`, saves next week's booking, re-arms alarm
6. On failure, re-arms with a 15-minute retry delay
7. When `bookedCount >= maxRepeatCount`, marks booking as COMPLETED

**Sync alarm** fires twice daily (09:00, 21:00) to re-check all scheduled bookings.

### Calendar Integration
Uses Android `CalendarContract` to add training events to the user's phone calendar. Stores `eventId` and `calendarId` on the training entity for later removal. Supports configurable reminders (minutes before).

### Key Files
| File | Purpose |
|------|---------|
| `data/api/VirtuagymApiClient.kt` | OkHttp client, cookie management, form POST for booking/cancel |
| `data/parser/ScheduleParser.kt` | Jsoup HTML parsing of Virtuagym schedule pages |
| `data/repository/RepositoryImpl.kt` | Main repo: schedule fetch, caching, booking, cancellation |
| `data/repository/BookingScheduler.kt` | Recurring booking logic (weekly loop, retry) |
| `data/repository/ScheduledBookingRepository.kt` | Custom JSON persistence for scheduled bookings |
| `util/AlarmScheduler.kt` | AlarmManager wrapper for booking + sync alarms |
| `receiver/ScheduledBookingReceiver.kt` | BroadcastReceiver that triggers booking check |
| `receiver/ScheduleSyncReceiver.kt` | BroadcastReceiver for daily schedule sync |
| `receiver/LoginRequiredReceiver.kt` | BroadcastReceiver for cookie expiry → login screen |
| `di/SwimGymAppContainer.kt` | Manual singleton DI container (no Hilt/Dagger) |
| `ui/navigation/NavHost.kt` | Compose navigation, ViewModel wiring, state management |
| `ui/screens/ScheduleScreen.kt` | Main schedule grid view |
| `ui/screens/TrainingDetailScreen.kt` | Training detail + book/cancel/schedule UI |
| `ui/screens/MyBookingsScreen.kt` | Bookings list + scheduled booking management |
| `ui/screens/SettingsScreen.kt` | Calendar, reminders, work-time filter, alarms |
| `ui/viewmodel/ViewModels.kt` | ScheduleViewModel (main viewmodel) |
| `domain/model/DomainModels.kt` | Domain data classes (Training, Booking, User...) |

### Known Issues / Concerns
- **No DI framework** — `SwimGymAppContainer` is a manual singleton with `lazy` properties; hard to test
- **No Hilt/Dagger** — all dependencies wired manually in NavHost
- **No unit tests** — `testImplementation` only has JUnit; no instrumentation tests visible
- **Custom JSON parser** — `ScheduledBookingRepository` uses regex for JSON parsing instead of Gson/Moshi
- **No migration strategy** — DB uses `fallbackToDestructiveMigration()` (version 11, suggests frequent destructive migrations)
- **Magic strings in Jsoup selectors** — brittle to Virtuagym HTML changes
- **`runBlocking` in `SwimGymApp.onCreate()`** — blocks main thread during app startup
- **No error handling on booking success** — `onFailure {}` is empty in `BookingScheduler`
- **`Training.imageUrl` is `var`** — mutable property on a data class (set in NavHost after Coil loads image)
- **`LoginActivity` referenced but not in source** — likely in a different module or generated

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
