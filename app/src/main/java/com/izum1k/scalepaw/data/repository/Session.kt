package com.izum1k.scalepaw.data.repository

import com.izum1k.scalepaw.data.model.Measurement
import com.izum1k.scalepaw.data.model.MetricSpec
import com.izum1k.scalepaw.data.model.MetricType
import com.izum1k.scalepaw.data.model.User
import com.izum1k.scalepaw.data.model.UserGoal
import com.izum1k.scalepaw.data.settings.AppSettings
import com.izum1k.scalepaw.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Everything the screens read, combined once for the whole app. */
data class SessionState(
    val loaded: Boolean = false,
    val users: List<User> = emptyList(),
    val user: User? = null,
    val types: List<MetricType> = emptyList(),
    /** Newest first. */
    val measurements: List<Measurement> = emptyList(),
    val goals: List<UserGoal> = emptyList(),
    val settings: AppSettings = AppSettings(),
) {
    val visibleTypes: List<MetricType> get() = types.filter { it.isVisible }
    val numericTypes: List<MetricType> get() = visibleTypes.filter { it.isNumeric }

    fun type(spec: MetricSpec): MetricType? = types.firstOrNull { it.identity == spec.identity }
    fun type(id: Long): MetricType? = types.firstOrNull { it.id == id }
    fun goal(typeId: Long): UserGoal? = goals.firstOrNull { it.typeId == typeId }
}

@OptIn(ExperimentalCoroutinesApi::class)
class Session(repository: ScaleRepository, settings: SettingsRepository, scope: CoroutineScope) {
    private val userData = repository.currentUser.flatMapLatest { user ->
        if (user == null) {
            flowOf(Triple<User?, List<Measurement>, List<UserGoal>>(null, emptyList(), emptyList()))
        } else {
            combine(repository.measurements(user.id), repository.goals(user.id)) { m, g -> Triple<User?, List<Measurement>, List<UserGoal>>(user, m, g) }
        }
    }

    val state: StateFlow<SessionState> = combine(
        repository.allUsers,
        repository.metricTypes,
        userData,
        settings.settings,
    ) { users, types, (user, measurements, goals), s ->
        SessionState(true, users, user, types, measurements, goals, s)
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, SessionState())

    val user = state.map { it.user }.distinctUntilChanged()
}
