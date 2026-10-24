package com.gpo.scalepaw.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [UserEntity::class, MetricTypeEntity::class, MeasurementEntity::class, MeasurementValueEntity::class, UserGoalEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ScaleDatabase : RoomDatabase() {
    abstract fun users(): UserDao
    abstract fun metricTypes(): MetricTypeDao
    abstract fun measurements(): MeasurementDao
    abstract fun goals(): UserGoalDao

    companion object {
        const val NAME = "scalepaw.db"

        fun build(context: Context): ScaleDatabase =
            Room.databaseBuilder(context, ScaleDatabase::class.java, NAME).build()

        fun inMemory(context: Context): ScaleDatabase =
            Room.inMemoryDatabaseBuilder(context, ScaleDatabase::class.java).allowMainThreadQueries().build()
    }
}
