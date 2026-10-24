package com.izum1k.scalepaw.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<UserEntity>

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun get(id: Long): UserEntity?

    @Insert
    suspend fun insert(user: UserEntity): Long

    @Update
    suspend fun update(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface MetricTypeDao {
    @Query("SELECT * FROM metric_types ORDER BY displayOrder, id")
    fun observeAll(): Flow<List<MetricTypeEntity>>

    @Query("SELECT * FROM metric_types ORDER BY displayOrder, id")
    suspend fun getAll(): List<MetricTypeEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(types: List<MetricTypeEntity>)

    @Insert
    suspend fun insert(type: MetricTypeEntity): Long

    @Update
    suspend fun update(type: MetricTypeEntity)

    @Update
    suspend fun updateAll(types: List<MetricTypeEntity>)

    @Query("DELETE FROM metric_types WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface MeasurementDao {
    @Transaction
    @Query("SELECT * FROM measurements WHERE userId = :userId ORDER BY timestamp DESC")
    fun observeForUser(userId: Long): Flow<List<MeasurementWithValues>>

    @Transaction
    @Query("SELECT * FROM measurements WHERE userId = :userId ORDER BY timestamp DESC")
    suspend fun getForUser(userId: Long): List<MeasurementWithValues>

    @Transaction
    @Query("SELECT * FROM measurements WHERE id = :id")
    suspend fun get(id: Long): MeasurementWithValues?

    @Transaction
    @Query("SELECT * FROM measurements WHERE userId = :userId AND timestamp < :before ORDER BY timestamp DESC LIMIT 1")
    suspend fun previous(userId: Long, before: Long): MeasurementWithValues?

    @Query("SELECT * FROM measurements WHERE userId = :userId AND timestamp = :timestamp")
    suspend fun findAt(userId: Long, timestamp: Long): MeasurementEntity?

    @Query("SELECT COUNT(*) FROM measurements")
    suspend fun count(): Int

    @Insert
    suspend fun insert(measurement: MeasurementEntity): Long

    @Update
    suspend fun update(measurement: MeasurementEntity)

    @Query("DELETE FROM measurements WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM measurements WHERE userId = :userId")
    suspend fun deleteForUser(userId: Long)

    @Query("DELETE FROM measurement_values WHERE measurementId = :measurementId")
    suspend fun clearValues(measurementId: Long)

    @Insert
    suspend fun insertValues(values: List<MeasurementValueEntity>)

    @Transaction
    suspend fun replaceValues(measurementId: Long, values: List<MeasurementValueEntity>) {
        clearValues(measurementId)
        insertValues(values)
    }
}

@Dao
interface UserGoalDao {
    @Query("SELECT * FROM user_goals WHERE userId = :userId")
    fun observeForUser(userId: Long): Flow<List<UserGoalEntity>>

    @Query("SELECT * FROM user_goals")
    suspend fun getAll(): List<UserGoalEntity>

    @Upsert
    suspend fun upsert(goal: UserGoalEntity)

    @Query("DELETE FROM user_goals WHERE userId = :userId AND typeId = :typeId")
    suspend fun delete(userId: Long, typeId: Long)
}
