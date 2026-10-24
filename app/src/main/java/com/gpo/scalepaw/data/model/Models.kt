package com.gpo.scalepaw.data.model

import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

enum class UserAvatar { CIRCLE, COOKIE, CLOVER, FLOWER, SUNNY, HEART }

data class User(
    val id: Long = 0,
    val name: String,
    val avatar: UserAvatar = UserAvatar.COOKIE,
    val birthDate: LocalDate,
    val gender: Gender,
    val heightCm: Float,
    val activityLevel: ActivityLevel = ActivityLevel.SEDENTARY,
    val assistedWeighing: Boolean = false,
    val amputations: Map<Limb, AmputationPart> = emptyMap(),
) {
    fun ageOn(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        val on = java.time.Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        return Period.between(birthDate, on).years.coerceAtLeast(0)
    }
}

data class MetricType(
    val id: Long = 0,
    val identity: String,
    /** Only user and device types carry a stored name; builtins resolve [spec]'s string. */
    val customName: String? = null,
    val color: Int,
    val icon: MetricIcon,
    val unit: UnitType,
    val inputType: InputType,
    val displayOrder: Int,
    val isDerived: Boolean,
    val isEnabled: Boolean,
    val isPinned: Boolean,
    val isOnRightAxis: Boolean,
    val isInternal: Boolean,
) {
    val spec: MetricSpec? get() = Metrics.specOf(identity)
    val isBuiltin: Boolean get() = identity.startsWith(Metrics.BUILTIN)
    val isUserOwned: Boolean get() = identity.startsWith(Metrics.USER)
    val columnKey: String get() = MetricSpec.columnKeyOf(identity)
    val isNumeric: Boolean get() = inputType != InputType.TEXT

    /** Visible in lists and charts: enabled and not an internal raw value. */
    val isVisible: Boolean get() = isEnabled && !isInternal

    companion object {
        fun seed(spec: MetricSpec, order: Int) = MetricType(
            identity = spec.identity,
            color = spec.defaultColor,
            icon = spec.icon,
            unit = spec.defaultUnit,
            inputType = spec.inputType,
            displayOrder = order,
            isDerived = spec.isDerived,
            isEnabled = spec.enabledByDefault,
            isPinned = spec.pinnedByDefault,
            isOnRightAxis = spec.rightAxisByDefault,
            isInternal = spec.isInternal,
        )
    }
}

/** A single value; exactly one field is set, matching the type's [InputType]. */
data class MetricValue(
    val float: Float? = null,
    val int: Int? = null,
    val text: String? = null,
) {
    val number: Float? get() = float ?: int?.toFloat()
    val isEmpty: Boolean get() = float == null && int == null && text.isNullOrBlank()

    companion object {
        fun of(value: Float) = MetricValue(float = value)
        fun of(value: Int) = MetricValue(int = value)
        fun of(value: String) = MetricValue(text = value)
    }
}

data class Measurement(
    val id: Long = 0,
    val userId: Long,
    val timestamp: Long,
    /** Keyed by [MetricType.id]. */
    val values: Map<Long, MetricValue>,
) {
    fun number(type: MetricType?): Float? = type?.let { values[it.id]?.number }
}

data class UserGoal(
    val userId: Long,
    val typeId: Long,
    val goalValue: Float,
    val startDate: Long,
    val targetDate: Long? = null,
)
