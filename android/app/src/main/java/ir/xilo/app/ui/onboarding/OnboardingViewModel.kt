package ir.xilo.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.xilo.app.R
import ir.xilo.app.data.contacts.ContactsReader
import ir.xilo.app.data.remote.api.XiloApiService
import ir.xilo.app.data.remote.dto.ContactMatchRequest
import ir.xilo.app.data.remote.dto.ContactMatchUserDto
import ir.xilo.app.data.remote.dto.InterestDto
import ir.xilo.app.data.remote.dto.UpdateInterestsRequest
import ir.xilo.app.data.repository.BrandRepository
import ir.xilo.app.util.ErrorMessageResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SuggestedMatchUi(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
    val isFollowing: Boolean,
    val isFollowBusy: Boolean = false,
)

enum class ContactsPhase {
    Idle,
    NeedsPermission,
    Loading,
    Ready,
    Skipped,
    Error,
}

data class OnboardingUiState(
    val step: Int = 1,
    val interests: List<InterestDto> = emptyList(),
    val selectedInterestIds: Set<String> = emptySet(),
    val interestsLoading: Boolean = true,
    val interestsError: String? = null,
    val savingInterests: Boolean = false,
    val contactsPhase: ContactsPhase = ContactsPhase.Idle,
    val suggestions: List<SuggestedMatchUi> = emptyList(),
    val contactsError: String? = null,
    val completing: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    brandRepository: BrandRepository,
    private val apiService: XiloApiService,
    private val contactsReader: ContactsReader,
    private val errorMessageResolver: ErrorMessageResolver,
) : ViewModel() {

    val brand = brandRepository.brand

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        loadInterests()
    }

    fun loadInterests() {
        viewModelScope.launch {
            _uiState.update { it.copy(interestsLoading = true, interestsError = null) }
            try {
                val catalog = apiService.listInterests().interests.sortedBy { it.sortOrder }
                val mine = runCatching { apiService.getMyInterests() }.getOrNull()
                val selected = mine?.interestIds?.toSet().orEmpty()
                _uiState.update {
                    it.copy(
                        interests = catalog,
                        selectedInterestIds = selected,
                        interestsLoading = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        interestsLoading = false,
                        interestsError = errorMessageResolver.fromThrowable(e, R.string.error_unknown),
                    )
                }
            }
        }
    }

    fun toggleInterest(id: String) {
        _uiState.update { state ->
            val next = state.selectedInterestIds.toMutableSet()
            if (!next.add(id)) next.remove(id)
            state.copy(selectedInterestIds = next)
        }
    }

    fun onSkipPressed(onComplete: () -> Unit) {
        completeOnboarding(onComplete)
    }

    fun onPrimaryAction(onComplete: () -> Unit) {
        val step = _uiState.value.step
        when (step) {
            1 -> _uiState.update { it.copy(step = 2) }
            2 -> saveInterestsAndAdvance()
            else -> completeOnboarding(onComplete)
        }
    }

    fun requestContactsPermission() {
        _uiState.update {
            it.copy(contactsPhase = ContactsPhase.NeedsPermission, contactsError = null)
        }
    }

    fun onContactsPermissionResult(granted: Boolean) {
        if (granted) {
            matchContacts()
        } else {
            _uiState.update {
                it.copy(contactsPhase = ContactsPhase.Skipped, suggestions = emptyList())
            }
        }
    }

    fun skipContacts() {
        _uiState.update {
            it.copy(contactsPhase = ContactsPhase.Skipped, suggestions = emptyList(), contactsError = null)
        }
    }

    fun matchContacts() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(contactsPhase = ContactsPhase.Loading, contactsError = null)
            }
            try {
                val payload = withContext(Dispatchers.IO) { contactsReader.collectHashes() }
                val response = apiService.matchContacts(
                    ContactMatchRequest(
                        phoneHashes = payload.phoneHashes,
                        emailHashes = payload.emailHashes,
                    )
                )
                _uiState.update {
                    it.copy(
                        contactsPhase = ContactsPhase.Ready,
                        suggestions = response.matches.map { m -> m.toUi() },
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        contactsPhase = ContactsPhase.Error,
                        contactsError = errorMessageResolver.fromThrowable(e, R.string.error_unknown),
                    )
                }
            }
        }
    }

    fun toggleFollow(username: String) {
        if (username.isBlank()) return
        val current = _uiState.value.suggestions.find { it.username == username } ?: return
        if (current.isFollowBusy) return
        val becomingFollow = !current.isFollowing
        _uiState.update { state ->
            state.copy(
                suggestions = state.suggestions.map {
                    if (it.username == username) {
                        it.copy(isFollowing = becomingFollow, isFollowBusy = true)
                    } else {
                        it
                    }
                },
            )
        }
        viewModelScope.launch {
            try {
                val response = if (becomingFollow) {
                    apiService.followUser(username)
                } else {
                    apiService.unfollowUser(username)
                }
                _uiState.update { state ->
                    state.copy(
                        suggestions = state.suggestions.map {
                            if (it.username == username) {
                                it.copy(isFollowing = response.following, isFollowBusy = false)
                            } else {
                                it
                            }
                        },
                    )
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        suggestions = state.suggestions.map {
                            if (it.username == username) {
                                it.copy(isFollowing = !becomingFollow, isFollowBusy = false)
                            } else {
                                it
                            }
                        },
                        contactsError = errorMessageResolver.fromThrowable(e, R.string.error_follow_failed),
                    )
                }
            }
        }
    }

    fun clearContactsError() {
        _uiState.update { it.copy(contactsError = null) }
    }

    private fun saveInterestsAndAdvance() {
        viewModelScope.launch {
            _uiState.update { it.copy(savingInterests = true, interestsError = null) }
            try {
                val ids = _uiState.value.selectedInterestIds.toList()
                apiService.updateMyInterests(UpdateInterestsRequest(interestIds = ids))
                _uiState.update {
                    it.copy(
                        savingInterests = false,
                        step = 3,
                        contactsPhase = ContactsPhase.Idle,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        savingInterests = false,
                        interestsError = errorMessageResolver.fromThrowable(e, R.string.error_unknown),
                    )
                }
            }
        }
    }

    private fun completeOnboarding(onComplete: () -> Unit) {
        if (_uiState.value.completing) return
        _uiState.update { it.copy(completing = true) }
        // MainScreenViewModel.completeOnboarding → AuthRepository.completeOnboarding
        onComplete()
    }

    private fun ContactMatchUserDto.toUi() = SuggestedMatchUi(
        id = id,
        username = username,
        displayName = displayName.ifBlank { username },
        avatarUrl = avatarUrl,
        isFollowing = alreadyFollowing,
    )
}
