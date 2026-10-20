package app.aiplanner.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import android.content.Context
import app.aiplanner.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore by preferencesDataStore(name = "ai_planner_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val WAKE_HOUR = intPreferencesKey("wake_hour")
        val WAKE_MINUTE = intPreferencesKey("wake_minute")
        val SLEEP_HOUR = intPreferencesKey("sleep_hour")
        val SLEEP_MINUTE = intPreferencesKey("sleep_minute")
        val PEAK_START = intPreferencesKey("peak_start")
        val PEAK_END = intPreferencesKey("peak_end")
        val FOCUS_BLOCK = intPreferencesKey("focus_block")
        val BREAK_MINUTES = intPreferencesKey("break_minutes")
        val DAILY_CAPACITY = doublePreferencesKey("daily_capacity")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val ONBOARDED = booleanPreferencesKey("onboarding_completed")
    }

    val profile: Flow<UserProfile> = context.settingsStore.data.map { p ->
        UserProfile(
            wakeHour = p[Keys.WAKE_HOUR] ?: 7,
            wakeMinute = p[Keys.WAKE_MINUTE] ?: 0,
            sleepHour = p[Keys.SLEEP_HOUR] ?: 23,
            sleepMinute = p[Keys.SLEEP_MINUTE] ?: 0,
            peakWindowStart = p[Keys.PEAK_START] ?: 9,
            peakWindowEnd = p[Keys.PEAK_END] ?: 12,
            focusBlockMinutes = p[Keys.FOCUS_BLOCK] ?: 50,
            breakMinutes = p[Keys.BREAK_MINUTES] ?: 10,
            dailyCapacityHours = p[Keys.DAILY_CAPACITY] ?: 6.0,
            notificationsEnabled = p[Keys.NOTIFICATIONS] ?: true,
            onboardingCompleted = p[Keys.ONBOARDED] ?: false,
        )
    }

    suspend fun saveProfile(profile: UserProfile) {
        context.settingsStore.edit { p ->
            p[Keys.WAKE_HOUR] = profile.wakeHour
            p[Keys.WAKE_MINUTE] = profile.wakeMinute
            p[Keys.SLEEP_HOUR] = profile.sleepHour
            p[Keys.SLEEP_MINUTE] = profile.sleepMinute
            p[Keys.PEAK_START] = profile.peakWindowStart
            p[Keys.PEAK_END] = profile.peakWindowEnd
            p[Keys.FOCUS_BLOCK] = profile.focusBlockMinutes
            p[Keys.BREAK_MINUTES] = profile.breakMinutes
            p[Keys.DAILY_CAPACITY] = profile.dailyCapacityHours
            p[Keys.NOTIFICATIONS] = profile.notificationsEnabled
            p[Keys.ONBOARDED] = profile.onboardingCompleted
        }
    }
}
