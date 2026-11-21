package com.swimgym.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swimgym.app.domain.model.ClubInfo
import com.swimgym.app.domain.model.ClubNews
import com.swimgym.app.domain.model.CreditItem
import com.swimgym.app.domain.usecase.GetClubInfoUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ClubInfoUiState(
    val news: List<ClubNews> = emptyList(),
    val credits: List<CreditItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastUpdated: Long = 0L
)

class ClubInfoViewModel(
    private val getClubInfoUseCase: GetClubInfoUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClubInfoUiState())
    val uiState: StateFlow<ClubInfoUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            getClubInfoUseCase().fold(
                onSuccess = { info: ClubInfo ->
                    _uiState.update {
                        it.copy(
                            news = info.news,
                            credits = info.credits,
                            isLoading = false,
                            lastUpdated = System.currentTimeMillis(),
                            error = null
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to load SwimGym info"
                        )
                    }
                }
            )
        }
    }
}
