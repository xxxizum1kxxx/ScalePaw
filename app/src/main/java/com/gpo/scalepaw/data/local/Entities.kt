package com.gpo.scalepaw.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val avatar: String,
    /** Epoch day. */
    val birthDate: Long,
    val gender: String,
    val heightCm: Float,
    val activityLevel: String,
    val assistedWeighing: Boolean,
    /** `LIMB=PART` pairs joined by `;`. */
    val amputations: String,
)

@Entity(tableName = "metric_types", indices = [Index(value = ["identity"], unique = true)])
data class MetricTypeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val identity: String,
    val customName: String?,
    val color: Int,
    val icon: String,
    val unit: String,
    val inputType: String,
    val displayOrder: Int,
    val isDerived: Boolean,
    val isEnabled: Boolean,
    val isPinned: Boolean,
    val isOnRightAxis: Boolean,
    val isInternal: Boolean,
)

@Entity(
    tableName = "measurements",
    foreignKeys = [ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("userId"), Index(value = ["userId", "timestamp"], unique = true)],
)
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val timestamp: Long,
)

@Entity(
    tableName = "measurement_values",
    foreignKeys = [
        ForeignKey(entity = MeasurementEntity::class, parentColumns = ["id"], childColumns = ["measurementId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MetricTypeEntity::class, parentColumns = ["id"], childColumns = ["typeId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["measurementId", "typeId"], unique = true), Index("typeId")],
)
data class MeasurementValueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val measurementId: Long,
    val typeId: Long,
    val floatValue: Float?,
    val intValue: Int?,
    val textValue: String?,
)

@Entity(
    tableName = "user_goals",
    primaryKeys = ["userId", "typeId"],
    foreignKeys = [
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MetricTypeEntity::class, parentColumns = ["id"], childColumns = ["typeId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("typeId")],
)
data class UserGoalEntity(
    val userId: Long,
    val typeId: Long,
    val goalValue: Float,
    val startDate: Long,
    val targetDate: Long?,
)

data class MeasurementWithValues(
    @Embedded val measurement: MeasurementEntity,
    @Relation(parentColumn = "id", entityColumn = "measurementId")
    val values: List<MeasurementValueEntity>,
)
