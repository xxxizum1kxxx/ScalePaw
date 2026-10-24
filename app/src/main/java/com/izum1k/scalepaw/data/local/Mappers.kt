package com.izum1k.scalepaw.data.local

import com.izum1k.scalepaw.data.model.ActivityLevel
import com.izum1k.scalepaw.data.model.AmputationPart
import com.izum1k.scalepaw.data.model.Gender
import com.izum1k.scalepaw.data.model.InputType
import com.izum1k.scalepaw.data.model.Limb
import com.izum1k.scalepaw.data.model.Measurement
import com.izum1k.scalepaw.data.model.MetricIcon
import com.izum1k.scalepaw.data.model.MetricType
import com.izum1k.scalepaw.data.model.MetricValue
import com.izum1k.scalepaw.data.model.UnitType
import com.izum1k.scalepaw.data.model.User
import com.izum1k.scalepaw.data.model.UserAvatar
import com.izum1k.scalepaw.data.model.UserGoal
import java.time.LocalDate

private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: fallback

fun UserEntity.toModel() = User(
    id = id,
    name = name,
    avatar = enumOr(avatar, UserAvatar.COOKIE),
    birthDate = LocalDate.ofEpochDay(birthDate),
    gender = enumOr(gender, Gender.MALE),
    heightCm = heightCm,
    activityLevel = enumOr(activityLevel, ActivityLevel.SEDENTARY),
    assistedWeighing = assistedWeighing,
    amputations = decodeAmputations(amputations),
)

fun User.toEntity() = UserEntity(
    id = id,
    name = name,
    avatar = avatar.name,
    birthDate = birthDate.toEpochDay(),
    gender = gender.name,
    heightCm = heightCm,
    activityLevel = activityLevel.name,
    assistedWeighing = assistedWeighing,
    amputations = amputations.entries.joinToString(";") { "${it.key.name}=${it.value.name}" },
)

internal fun decodeAmputations(raw: String): Map<Limb, AmputationPart> = raw.split(';')
    .mapNotNull { pair ->
        val (limb, part) = pair.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
        val l = Limb.entries.firstOrNull { it.name == limb } ?: return@mapNotNull null
        val p = AmputationPart.entries.firstOrNull { it.name == part } ?: return@mapNotNull null
        l to p
    }.toMap()

fun MetricTypeEntity.toModel() = MetricType(
    id = id,
    identity = identity,
    customName = customName,
    color = color,
    icon = enumOr(icon, MetricIcon.DEFAULT),
    unit = enumOr(unit, UnitType.NONE),
    inputType = enumOr(inputType, InputType.FLOAT),
    displayOrder = displayOrder,
    isDerived = isDerived,
    isEnabled = isEnabled,
    isPinned = isPinned,
    isOnRightAxis = isOnRightAxis,
    isInternal = isInternal,
)

fun MetricType.toEntity() = MetricTypeEntity(
    id = id,
    identity = identity,
    customName = customName,
    color = color,
    icon = icon.name,
    unit = unit.name,
    inputType = inputType.name,
    displayOrder = displayOrder,
    isDerived = isDerived,
    isEnabled = isEnabled,
    isPinned = isPinned,
    isOnRightAxis = isOnRightAxis,
    isInternal = isInternal,
)

fun MeasurementWithValues.toModel() = Measurement(
    id = measurement.id,
    userId = measurement.userId,
    timestamp = measurement.timestamp,
    values = values.associate { it.typeId to MetricValue(it.floatValue, it.intValue, it.textValue) },
)

fun Map<Long, MetricValue>.toEntities(measurementId: Long) = map { (typeId, v) ->
    MeasurementValueEntity(measurementId = measurementId, typeId = typeId, floatValue = v.float, intValue = v.int, textValue = v.text)
}

fun UserGoalEntity.toModel() = UserGoal(userId, typeId, goalValue, startDate, targetDate)

fun UserGoal.toEntity() = UserGoalEntity(userId, typeId, goalValue, startDate, targetDate)
