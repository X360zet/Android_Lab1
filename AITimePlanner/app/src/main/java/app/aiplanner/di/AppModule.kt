package app.aiplanner.di

import android.content.Context
import androidx.room.Room
import app.aiplanner.data.local.AppDatabase
import app.aiplanner.data.local.dao.GoalDao
import app.aiplanner.data.local.dao.ScheduleDao
import app.aiplanner.data.local.dao.StatsDao
import app.aiplanner.data.local.dao.TaskDao
import app.aiplanner.domain.planner.GoalDecomposer
import app.aiplanner.domain.planner.HeuristicSchedulePlanner
import app.aiplanner.domain.planner.PlanMerger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDb(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()
    @Provides fun provideScheduleDao(db: AppDatabase): ScheduleDao = db.scheduleDao()
    @Provides fun provideGoalDao(db: AppDatabase): GoalDao = db.goalDao()
    @Provides fun provideStatsDao(db: AppDatabase): StatsDao = db.statsDao()

    /** Локальный ИИ-движок планирования (полностью оффлайн). */
    @Provides
    @Singleton
    fun providePlanner(): HeuristicSchedulePlanner = HeuristicSchedulePlanner()

    /** Оффлайн-декомпозиция целей на задачи. */
    @Provides
    @Singleton
    fun provideDecomposer(): GoalDecomposer = GoalDecomposer()

    @Provides
    @Singleton
    fun providePlanMerger(planner: HeuristicSchedulePlanner): PlanMerger = PlanMerger(planner)
}
