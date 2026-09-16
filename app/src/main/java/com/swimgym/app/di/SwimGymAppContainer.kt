package com.swimgym.app.di

import android.content.Context
import com.swimgym.app.SwimGymApp
import com.swimgym.app.data.api.VirtuagymApiClientImpl
import com.swimgym.app.data.local.SwimGymDao
import com.swimgym.app.data.local.SwimGymDatabase
import com.swimgym.app.data.parser.ScheduleParserImpl
import com.swimgym.app.data.repository.*
import com.swimgym.app.domain.repository.CalendarRepository
import com.swimgym.app.domain.repository.TrainingRepository
import com.swimgym.app.domain.usecase.*
import com.swimgym.app.util.AlarmScheduler
import com.swimgym.app.util.BookingNotificationManager

class SwimGymAppContainer private constructor() {
    private val context: Context by lazy { (SwimGymApp.instance as Context) }
    val dao: SwimGymDao by lazy { SwimGymDatabase.getDatabase(context) }
    val sessionRepository: SessionRepository by lazy { SessionRepository(context) }
    val bookingNotificationManager: BookingNotificationManager by lazy { BookingNotificationManager(context) }
    val scheduledBookingRepository: ScheduledBookingRepository by lazy { ScheduledBookingRepository(context) }
    
    val apiClient: VirtuagymApiClientImpl by lazy {
        VirtuagymApiClientImpl(
            sessionRepository = sessionRepository,
            notificationManager = bookingNotificationManager,
            context = context
        )
    }
    
    val parser: ScheduleParserImpl by lazy {
        ScheduleParserImpl(dao = dao)
    }
    
    val calendarService: CalendarServiceImpl by lazy {
        CalendarServiceImpl(
            context = context,
            sessionRepository = sessionRepository,
            dao = dao
        )
    }
    
    val bookingScheduler: BookingSchedulerImpl by lazy {
        BookingSchedulerImpl(
            dao = dao,
            scheduledBookingRepo = scheduledBookingRepository,
            notificationManager = bookingNotificationManager,
            alarmScheduler = alarmScheduler,
            apiClient = apiClient,
            context = context
        )
    }

    val trainingRepository: TrainingRepository by lazy {
        TrainingRepositoryImpl(
            context = context,
            apiClient = apiClient,
            dao = dao,
            parser = parser,
            calendarService = calendarService,
            notificationManager = bookingNotificationManager
        )
    }
    val calendarRepository: CalendarRepository by lazy { CalendarRepositoryImpl(context) }
    val trainingRepositoryInterface: TrainingRepository by lazy { trainingRepository }
    val trainerImageCache: TrainerImageCache by lazy { TrainerImageCache(context, dao) }
    val alarmScheduler: AlarmScheduler by lazy { AlarmScheduler(context) }
    
    // Use cases
    val getScheduleUseCase: GetScheduleUseCase by lazy { GetScheduleUseCase(trainingRepositoryInterface) }
    val getTrainingDetailsUseCase: GetTrainingDetailsUseCase by lazy { GetTrainingDetailsUseCase(trainingRepositoryInterface) }
    val bookTrainingUseCase: BookTrainingUseCase by lazy { BookTrainingUseCase(trainingRepositoryInterface) }
    val cancelBookingUseCase: CancelBookingUseCase by lazy { CancelBookingUseCase(trainingRepositoryInterface) }
    val getMyBookingsUseCase: GetMyBookingsUseCase by lazy { GetMyBookingsUseCase(trainingRepositoryInterface) }
    val refreshScheduleUseCase: RefreshScheduleUseCase by lazy { RefreshScheduleUseCase(trainingRepositoryInterface) }
    val getSyncStatusUseCase: GetSyncStatusUseCase by lazy { GetSyncStatusUseCase(trainingRepositoryInterface) }
    
    companion object {
        @Volatile
        private var instance: SwimGymAppContainer? = null
        
        fun getInstance(): SwimGymAppContainer {
            return instance ?: synchronized(this) {
                instance ?: SwimGymAppContainer()
            }
        }
        
        fun reset() {
            instance = null
        }
    }
}
