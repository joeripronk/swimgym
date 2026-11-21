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
    val lastUpdated: Long = 0L,
    val newsPage: Int = 1,
    val isLoadingMoreNews: Boolean = false,
    val newsLoadMoreError: String? = null,
    val newsEndReached: Boolean = false,
    val selectedNews: ClubNews? = null,
    val isLoadingNewsDetail: Boolean = false
)

class ClubInfoViewModel(
    private val getClubInfoUseCase: GetClubInfoUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClubInfoUiState())
    val uiState: StateFlow<ClubInfoUiState> = _uiState.asStateFlow()

    // Invalidates in-flight page loads when a fresh load() starts, so a late
    // page-N response cannot overwrite the refreshed feed.
    private var loadGeneration = 0

    fun load() {
        val generation = ++loadGeneration
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            getClubInfoUseCase().fold(
                onSuccess = { info: ClubInfo ->
                    if (generation != loadGeneration) return@fold
                    _uiState.update {
                        it.copy(
                            news = info.news,
                            credits = info.credits,
                            isLoading = false,
                            lastUpdated = System.currentTimeMillis(),
                            error = null,
                            newsPage = 1,
                            isLoadingMoreNews = false,
                            newsLoadMoreError = null,
                            newsEndReached = info.news.isEmpty()
                        )
                    }
                },
                onFailure = { e ->
                    if (generation != loadGeneration) return@fold
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to load SwimGym info",
                            isLoadingMoreNews = false
                        )
                    }
                }
            )
        }
    }

    fun loadMoreNews() {
        val state = _uiState.value
        if (state.isLoadingMoreNews || state.newsEndReached || state.isLoading) return
        val nextPage = state.newsPage + 1
        val generation = loadGeneration
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoadingMoreNews = true, newsLoadMoreError = null)
            }
            getClubInfoUseCase.getNewsPage(nextPage).fold(
                onSuccess = { page ->
                    if (generation != loadGeneration) return@fold
                    _uiState.update { current ->
                        val knownIds = current.news.map { it.id }.toSet()
                        val fresh = page.items.filterNot { it.id in knownIds }
                        current.copy(
                            news = current.news + fresh,
                            newsPage = nextPage,
                            isLoadingMoreNews = false,
                            newsEndReached = page.isLast || fresh.isEmpty(),
                            newsLoadMoreError = null
                        )
                    }
                },
                onFailure = { e ->
                    if (generation != loadGeneration) return@fold
                    _uiState.update {
                        it.copy(
                            isLoadingMoreNews = false,
                            newsLoadMoreError = e.message ?: "Failed to load more news"
                        )
                    }
                }
            )
        }
    }

    fun openNews(item: ClubNews) {
        if (item.id.isBlank()) return
        val generation = ++loadGeneration
        _uiState.update {
            it.copy(selectedNews = item, isLoadingNewsDetail = true)
        }
        viewModelScope.launch {
            getClubInfoUseCase.getNewsDetail(item.id)
                .onSuccess { detail ->
                    if (generation != loadGeneration) return@onSuccess
                    if (detail != null && detail.message.length > item.message.length) {
                        _uiState.update { it.copy(selectedNews = detail) }
                    }
                }
                .onFailure { e ->
                    android.util.Log.w("ClubInfoVM", "Could not load full news detail", e)
                }
            if (generation == loadGeneration) {
                _uiState.update { it.copy(isLoadingNewsDetail = false) }
            }
        }
    }

    fun closeNews() {
        loadGeneration++
        _uiState.update { it.copy(selectedNews = null, isLoadingNewsDetail = false) }
    }
}
