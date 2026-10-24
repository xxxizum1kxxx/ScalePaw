package com.izum1k.scalepaw.feature.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.izum1k.scalepaw.data.model.ActivityLevel
import com.izum1k.scalepaw.data.model.AmputationPart
import com.izum1k.scalepaw.data.model.Gender
import com.izum1k.scalepaw.data.model.Limb
import com.izum1k.scalepaw.data.model.User
import com.izum1k.scalepaw.data.model.UserAvatar
import com.izum1k.scalepaw.data.repository.ScaleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class UserEditUiState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val name: String = "",
    val avatar: UserAvatar = UserAvatar.COOKIE,
    val birthDate: LocalDate = LocalDate.now().minusYears(30),
    val gender: Gender = Gender.FEMALE,
    val height: String = "",
    val activity: ActivityLevel = ActivityLevel.SEDENTARY,
    val assisted: Boolean = false,
    val amputations: Map<Limb, AmputationPart> = emptyMap(),
    val canDelete: Boolean = false,
    val done: Boolean = false,
) {
    val heightCm: Float? get() = height.replace(',', '.').toFloatOrNull()?.takeIf { it in 30f..280f }
    val canSave: Boolean get() = name.isNotBlank() && heightCm != null
}

class UserEditViewModel(private val id: Long, private val repository: ScaleRepository) : ViewModel() {
    private val _state = MutableStateFlow(UserEditUiState())
    val state: StateFlow<UserEditUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val users = repository.usersNow()
            val user = users.firstOrNull { it.id == id }
            _state.value = if (user == null) {
                UserEditUiState(loaded = true)
            } else {
                UserEditUiState(
                    loaded = true,
                    isNew = false,
                    name = user.name,
                    avatar = user.avatar,
                    birthDate = user.birthDate,
                    gender = user.gender,
                    height = user.heightCm.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() },
                    activity = user.activityLevel,
                    assisted = user.assistedWeighing,
                    amputations = user.amputations,
                    canDelete = users.size > 1,
                )
            }
        }
    }

    fun update(change: (UserEditUiState) -> UserEditUiState) = _state.update(change)

    fun setAmputation(limb: Limb, part: AmputationPart?) = _state.update {
        it.copy(amputations = if (part == null) it.amputations - limb else it.amputations + (limb to part))
    }

    fun save() {
        val s = _state.value
        val height = s.heightCm ?: return
        if (!s.canSave) return
        viewModelScope.launch {
            val saved = repository.saveUser(
                User(
                    id = id,
                    name = s.name.trim(),
                    avatar = s.avatar,
                    birthDate = s.birthDate,
                    gender = s.gender,
                    heightCm = height,
                    activityLevel = s.activity,
                    assistedWeighing = s.assisted,
                    amputations = s.amputations,
                ),
            )
            if (s.isNew) repository.selectUser(saved)
            _state.update { it.copy(done = true) }
        }
    }

    fun delete() {
        viewModelScope.launch {
            repository.deleteUser(id)
            _state.update { it.copy(done = true) }
        }
    }
}
