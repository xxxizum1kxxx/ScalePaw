package com.izum1k.scalepaw.data.repository

import androidx.room.withTransaction
import com.izum1k.scalepaw.data.local.MeasurementEntity
import com.izum1k.scalepaw.data.local.ScaleDatabase
import com.izum1k.scalepaw.data.local.toEntities
import com.izum1k.scalepaw.data.local.toEntity
import com.izum1k.scalepaw.data.local.toModel
import com.izum1k.scalepaw.data.model.InputType
import com.izum1k.scalepaw.data.model.Measurement
import com.izum1k.scalepaw.data.model.MetricSpec
import com.izum1k.scalepaw.data.model.MetricType
import com.izum1k.scalepaw.data.model.MetricValue
import com.izum1k.scalepaw.data.model.Metrics
import com.izum1k.scalepaw.data.model.UnitConversion
import com.izum1k.scalepaw.data.model.UnitType
import com.izum1k.scalepaw.data.model.User
import com.izum1k.scalepaw.data.model.UserGoal
import com.izum1k.scalepaw.data.settings.SettingsRepository
import com.izum1k.scalepaw.domain.BodyFormulas
import com.izum1k.scalepaw.domain.MeasurementProcessor
import kotlin.math.abs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Values reported by a scale, keyed by metric identity, in each metric's wire unit. */
data class ScaleReading(
    val timestamp: Long,
    val values: Map<String, MetricValue>,
    /** The app user the scale identified (via its own user slots), when it did. */
    val userId: Long? = null,
    /** Device metrics this scale contributes beyond the builtin vocabulary. */
    val extraSpecs: List<MetricSpec> = emptyList(),
    /** Display names for [extraSpecs], resolved by the driver at reading time. */
    val extraNames: Map<String, String> = emptyMap(),
)

sealed interface SaveResult {
    data class Saved(val measurementId: Long, val userId: Long) : SaveResult
    data object Ignored : SaveResult
    data object NoUser : SaveResult
}

/**
 * The single source of truth for users, metric types, measurements and goals. Every write that
 * changes values runs them through [MeasurementProcessor] so derived values are always current.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScaleRepository(
    private val db: ScaleDatabase,
    private val settings: SettingsRepository,
) {
    private val users = db.users()
    private val types = db.metricTypes()
    private val measurementDao = db.measurements()
    private val goalDao = db.goals()

    val allUsers: Flow<List<User>> = users.observeAll().map { list -> list.map { it.toModel() } }

    val metricTypes: Flow<List<MetricType>> = types.observeAll().map { list -> list.map { it.toModel() } }

    /** The selected user, falling back to the first one when the selection is gone. */
    val currentUser: Flow<User?> = combine(allUsers, settings.settings.map { it.currentUserId }.distinctUntilChanged()) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.distinctUntilChanged()

    val currentMeasurements: Flow<List<Measurement>> = currentUser.map { it?.id }.distinctUntilChanged().flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else measurements(id)
    }

    fun measurements(userId: Long): Flow<List<Measurement>> =
        measurementDao.observeForUser(userId).map { list -> list.map { it.toModel() } }

    fun goals(userId: Long): Flow<List<UserGoal>> = goalDao.observeForUser(userId).map { list -> list.map { it.toModel() } }

    suspend fun ensureSeeded() {
        val existing = types.getAll()
        val known = existing.map { it.identity }.toSet()
        val start = (existing.maxOfOrNull { it.displayOrder } ?: -1) + 1
        val missing = Metrics.builtins.filter { it.identity !in known }
        if (missing.isNotEmpty()) {
            types.insertAll(missing.mapIndexed { i, spec -> MetricType.seed(spec, start + i).toEntity() })
        }
    }

    suspend fun typesNow(): List<MetricType> = types.getAll().map { it.toModel() }

    suspend fun usersNow(): List<User> = users.getAll().map { it.toModel() }

    suspend fun measurementsNow(userId: Long): List<Measurement> = measurementDao.getForUser(userId).map { it.toModel() }

    suspend fun measurement(id: Long): Measurement? = measurementDao.get(id)?.toModel()

    suspend fun hasAnyMeasurement(): Boolean = measurementDao.count() > 0

    // ── Users ──────────────────────────────────────────────────────────────

    suspend fun saveUser(user: User): Long {
        val id = if (user.id == 0L) {
            users.insert(user.toEntity())
        } else {
            users.update(user.toEntity())
            recalculate(user.id)
            user.id
        }
        if (settings.current().currentUserId == 0L) settings.setCurrentUser(id)
        return id
    }

    suspend fun deleteUser(id: Long) {
        users.delete(id)
        if (settings.current().currentUserId == id) settings.setCurrentUser(users.getAll().firstOrNull()?.id ?: 0L)
    }

    suspend fun selectUser(id: Long) = settings.setCurrentUser(id)

    // ── Metric types ───────────────────────────────────────────────────────

    suspend fun updateType(type: MetricType) {
        val old = types.getAll().firstOrNull { it.id == type.id }?.toModel()
        db.withTransaction {
            if (old != null && old.unit != type.unit) convertStoredValues(type.id, old, type)
            types.update(type.toEntity())
        }
    }

    /** Stored values follow a unit change, so history never mixes units. */
    private suspend fun convertStoredValues(typeId: Long, old: MetricType, new: MetricType) {
        for (user in users.getAll()) {
            for (m in measurementDao.getForUser(user.id)) {
                val v = m.values.firstOrNull { it.typeId == typeId }?.floatValue ?: continue
                val model = m.toModel()
                val converted = model.values + (typeId to MetricValue.of(UnitConversion.convert(v, old.unit, new.unit)))
                measurementDao.replaceValues(m.measurement.id, converted.toEntities(m.measurement.id))
            }
        }
    }

    suspend fun reorderTypes(ordered: List<MetricType>) {
        types.updateAll(ordered.mapIndexed { i, t -> t.copy(displayOrder = i).toEntity() })
    }

    suspend fun addCustomType(type: MetricType): Long {
        val all = types.getAll()
        val identity = type.identity.ifBlank { Metrics.userIdentityFor(type.customName.orEmpty(), all.map { it.identity }.toSet()) }
        return types.insert(type.copy(id = 0, identity = identity, displayOrder = (all.maxOfOrNull { it.displayOrder } ?: 0) + 1).toEntity())
    }

    suspend fun deleteType(id: Long) = types.delete(id)

    /** Creates rows for device metrics a scale reports, returning identity → type. */
    private suspend fun ensureDeviceTypes(specs: List<MetricSpec>, names: Map<String, String>): Map<String, MetricType> {
        val all = types.getAll().map { it.toModel() }
        val known = all.map { it.identity }.toSet()
        var order = (all.maxOfOrNull { it.displayOrder } ?: 0) + 1
        val missing = specs.filter { it.identity !in known }
        if (missing.isNotEmpty()) types.insertAll(missing.map { MetricType.seed(it, order++).copy(customName = names[it.identity]).toEntity() })
        return types.getAll().map { it.toModel() }.associateBy { it.identity }
    }

    // ── Measurements ───────────────────────────────────────────────────────

    /**
     * Saves a manual entry (new when [id] is null). An entry at the exact timestamp of another
     * one of the same user is merged into it, as the database allows one per instant.
     */
    suspend fun saveMeasurement(userId: Long, timestamp: Long, values: Map<Long, MetricValue>, id: Long? = null): Long {
        val user = users.get(userId)?.toModel() ?: error("Unknown user $userId")
        val all = typesNow()
        val previous = measurementDao.previous(userId, timestamp)?.toModel()
        val processed = MeasurementProcessor(all).process(
            user = user,
            timestamp = timestamp,
            input = values,
            settings = settings.current().formulas,
            previous = previous.takeIf { id == null },
            applyAmputation = false,
        )
        return write(userId, timestamp, processed, id)
    }

    private suspend fun write(userId: Long, timestamp: Long, values: Map<Long, MetricValue>, id: Long?): Long = db.withTransaction {
        val clash = measurementDao.findAt(userId, timestamp)
        val targetId = when {
            id != null && clash != null && clash.id != id -> {
                measurementDao.delete(id)
                clash.id
            }
            id != null -> {
                measurementDao.update(MeasurementEntity(id, userId, timestamp))
                id
            }
            clash != null -> clash.id
            else -> measurementDao.insert(MeasurementEntity(userId = userId, timestamp = timestamp))
        }
        measurementDao.replaceValues(targetId, values.toEntities(targetId))
        targetId
    }

    suspend fun deleteMeasurement(id: Long) = measurementDao.delete(id)

    suspend fun deleteAllMeasurements(userId: Long) = measurementDao.deleteForUser(userId)

    /** Re-runs formulas and derived values over a user's history (after profile or formula changes). */
    suspend fun recalculate(userId: Long) {
        val user = users.get(userId)?.toModel() ?: return
        val all = typesNow()
        val processor = MeasurementProcessor(all)
        val derivedIds = all.filter { it.isDerived }.map { it.id }.toSet()
        val formulas = settings.current().formulas.copy(carryOverValues = false)
        db.withTransaction {
            for (m in measurementDao.getForUser(userId).map { it.toModel() }) {
                val input = m.values.filterKeys { it !in derivedIds }
                val processed = processor.process(user, m.timestamp, input, formulas, applyAmputation = false)
                if (processed != m.values) measurementDao.replaceValues(m.id, processed.toEntities(m.id))
            }
        }
    }

    suspend fun recalculateAll() = users.getAll().forEach { recalculate(it.id) }

    /**
     * Stores a scale reading: picks the user (scale slot, smart assignment or the current user),
     * applies assisted weighing and amputation correction, converts wire units to each type's unit.
     */
    suspend fun saveScaleReading(reading: ScaleReading): SaveResult {
        val s = settings.current()
        val allUsers = usersNow()
        if (allUsers.isEmpty()) return SaveResult.NoUser
        val byIdentity = ensureDeviceTypes(reading.extraSpecs, reading.extraNames)
        val weightType = byIdentity[Metrics.WEIGHT.identity]

        val readingWeightKg = reading.values[Metrics.WEIGHT.identity]?.float
        val wireValues = reading.values.mapNotNull { (identity, value) ->
            val type = byIdentity[identity] ?: return@mapNotNull null
            val wire = Metrics.specOf(identity)?.wireUnit ?: reading.extraSpecs.firstOrNull { it.identity == identity }?.wireUnit ?: type.unit
            val v = value.float
            type.id to when {
                v == null || type.inputType != InputType.FLOAT -> value
                wire == UnitType.PERCENT && type.unit.isWeight -> {
                    // A percentage stored as a mass needs the weight it is a share of.
                    val kg = readingWeightKg ?: return@mapNotNull null
                    MetricValue.of(BodyFormulas.round2(UnitConversion.fromKg(kg * v / 100f, type.unit)))
                }
                else -> MetricValue.of(BodyFormulas.round2(UnitConversion.convert(v, wire, type.unit)))
            }
        }.toMap()

        val slotUser = reading.userId?.takeIf { id -> allUsers.any { it.id == id } }
        var user = allUsers.firstOrNull { it.id == slotUser } ?: allUsers.firstOrNull { it.id == s.currentUserId } ?: allUsers.first()
        val weightKg = weightType?.let { t -> wireValues[t.id]?.float?.let { UnitConversion.toKg(it, t.unit) } }

        if (slotUser == null && s.assignment.smartAssignment && weightKg != null && weightType != null) {
            val candidates = allUsers.mapNotNull { u ->
                val last = measurementDao.getForUser(u.id).firstNotNullOfOrNull { m ->
                    m.values.firstOrNull { it.typeId == weightType.id }?.floatValue
                }?.let { UnitConversion.toKg(it, weightType.unit) } ?: return@mapNotNull null
                if (last <= 0f) null else u to abs(weightKg - last) / last * 100f
            }
            val best = candidates.minByOrNull { it.second }
            if (best != null) {
                if (best.second <= s.assignment.tolerancePercent) {
                    user = best.first
                } else if (s.assignment.ignoreOutsideTolerance) {
                    return SaveResult.Ignored
                }
            }
        }

        var values = wireValues
        if (user.assistedWeighing && weightType != null) {
            // The scale weighed the user holding someone; store only the difference.
            val reference = allUsers.filter { it.id != user.id && !it.assistedWeighing }.firstNotNullOfOrNull { ref ->
                measurementDao.getForUser(ref.id).firstNotNullOfOrNull { m -> m.values.firstOrNull { it.typeId == weightType.id }?.floatValue }
            }
            val total = values[weightType.id]?.float
            if (reference != null && total != null) values = mapOf(weightType.id to MetricValue.of(BodyFormulas.round2(total - reference)))
        }

        val all = byIdentity.values.toList()
        val processed = MeasurementProcessor(all).process(
            user = user,
            timestamp = reading.timestamp,
            input = values,
            settings = s.formulas.copy(carryOverValues = false),
            applyAmputation = true,
        )
        val id = write(user.id, reading.timestamp, processed, null)
        return SaveResult.Saved(id, user.id)
    }

    // ── Goals ──────────────────────────────────────────────────────────────

    suspend fun saveGoal(goal: UserGoal) = goalDao.upsert(goal.toEntity())

    suspend fun deleteGoal(userId: Long, typeId: Long) = goalDao.delete(userId, typeId)

    suspend fun allGoals(): List<UserGoal> = goalDao.getAll().map { it.toModel() }

    // ── Bulk import ────────────────────────────────────────────────────────

    /** Writes imported rows as they are (values already in each type's unit), then recalculates. */
    suspend fun importMeasurements(userId: Long, rows: List<Pair<Long, Map<Long, MetricValue>>>, replace: Boolean): Int {
        db.withTransaction {
            if (replace) measurementDao.deleteForUser(userId)
            for ((timestamp, values) in rows) write(userId, timestamp, values, null)
        }
        recalculate(userId)
        return rows.size
    }
}
