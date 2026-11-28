-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Gson and the hand-written JSON persistence rely on field names and enum names.
-keep class com.swimgym.app.data.model.** { *; }
-keep class com.swimgym.app.domain.model.** { *; }
-keep class com.swimgym.app.data.repository.ScheduledBooking { *; }
-keep class com.swimgym.app.data.repository.ScheduledBookingStatus { *; }
-keep class com.swimgym.app.data.repository.RetryFailureReason { *; }
-keep class com.swimgym.app.domain.repository.SwodLevel { *; }

# Room generates code that reflects over entity fields.
-keep class com.swimgym.app.data.local.entity.** { *; }

# Jsoup discovers node implementations by name.
-keep class org.jsoup.** { *; }

-dontwarn org.jsoup.**
