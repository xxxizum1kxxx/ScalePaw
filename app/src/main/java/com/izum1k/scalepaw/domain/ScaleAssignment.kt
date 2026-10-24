package com.izum1k.scalepaw.domain

import kotlin.math.abs

/** Who a scale reading belongs to. */
sealed interface Assignment {
    data class To(val userId: Long) : Assignment
    data object Ignored : Assignment
}

object ScaleAssignment {
    /**
     * Picks the reading's user: an explicit choice, then the scale's own user slot, then (with smart
     * assignment) the user whose last weight is closest within [tolerancePercent], then [currentUserId].
     * [lastWeightKg] holds each user's latest weight; users without one never match by weight.
     */
    fun choose(
        userIds: List<Long>,
        currentUserId: Long,
        forcedUserId: Long?,
        slotUserId: Long?,
        weightKg: Float?,
        lastWeightKg: Map<Long, Float>,
        smartAssignment: Boolean,
        tolerancePercent: Int,
        ignoreOutsideTolerance: Boolean,
    ): Assignment {
        forcedUserId?.takeIf { it in userIds }?.let { return Assignment.To(it) }
        slotUserId?.takeIf { it in userIds }?.let { return Assignment.To(it) }
        val fallback = currentUserId.takeIf { it in userIds } ?: userIds.first()
        if (!smartAssignment || weightKg == null) return Assignment.To(fallback)
        val best = userIds.mapNotNull { id ->
            val last = lastWeightKg[id]?.takeIf { it > 0f } ?: return@mapNotNull null
            id to abs(weightKg - last) / last * 100f
        }.minByOrNull { it.second } ?: return Assignment.To(fallback)
        return when {
            best.second <= tolerancePercent -> Assignment.To(best.first)
            ignoreOutsideTolerance -> Assignment.Ignored
            else -> Assignment.To(fallback)
        }
    }
}
