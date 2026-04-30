# SwimGym App Spec

## Project Overview
- **Name**: SwimGym
- **Type**: Android Native App (Kotlin + Jetpack Compose)
- **Core Functionality**: Login to Virtuagym, view scheduled trainings, book trainings, sync to device calendar

## Tech Stack
- Language: Kotlin
- UI: Jetpack Compose + Material 3
- Architecture: Clean Architecture (MVVM)
- DI: Hilt
- Networking: Retrofit + OkHttp
- Async: Kotlin Coroutines + Flow
- Local Storage: DataStore (session)
- Calendar Integration: Android CalendarProvider API

## API Integration (Virtuagym)
Base URL: `https://swimgym.virtuagym.com/`

### Auth Endpoints
- POST `/api/v1/login` - Login with email/password
- POST `/api/v1/logout` - Logout

### Training Schedule
- GET `/api/v1/schedule` - Get trainings (requires auth token)
- POST `/api/v1/schedule/{id}/book` - Book training session
- DELETE `/api/v1/schedule/{id}/book` - Cancel booking

### User Info
- GET `/api/v1/user` - Get current user profile

**Note**: API details to be verified via network inspection. May require scraping if no public API.

## Screen Flow
1. **LoginScreen** - Email/password fields, login button
2. **ScheduleScreen** - List of upcoming trainings (calendar view)
3. **TrainingDetailScreen** - Training info + book button
4. **MyBookingsScreen** - List of user's booked trainings

## Data Models
- `User`: id, name, email
- `Training`: id, title, instructor, startTime, endTime, location, spotsAvailable
- `Booking`: id, trainingId, userId, status

## Key Flows
1. Login → Store auth token in DataStore → Navigate to Schedule
2. Schedule → Tap training → Book → Add to device calendar → Show confirmation
3. View bookings → Cancel booking → Remove from calendar

## Build Commands
- `./gradlew assembleDebug` - Debug APK
- `./gradlew installDebug` - Install on device