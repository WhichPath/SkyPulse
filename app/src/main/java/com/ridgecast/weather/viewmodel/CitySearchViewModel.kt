package com.ridgecast.weather.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ridgecast.weather.data.CityEntry
import com.ridgecast.weather.data.GeocodingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CitySearchResult(
    val name: String,
    val district: String,
    val longitude: Double,
    val latitude: Double,
    val savedName: String = name,
    val isApproximateCenter: Boolean = false,
    val parentCountyName: String = "",
    val drillDownQuery: String? = null
)

@HiltViewModel
class CitySearchViewModel @Inject constructor(
    private val geocodingService: GeocodingService
) : ViewModel() {

    private val _searchResults = MutableStateFlow<List<CitySearchResult>>(emptyList())
    val searchResults: StateFlow<List<CitySearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    private var searchJob: Job? = null

    fun searchCities(query: String) {
        if (query.isBlank()) {
            clearSearchResults()
            return
        }
        _isSearchActive.value = true
        _isSearching.value = true
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            // 离线内存检索毫秒级完成，仅保留极短防抖避免输入法高频合成帧抖动
            delay(60)
            try {
                val entries: List<CityEntry> = geocodingService.search(query)
                _searchResults.value = entries.map { entry ->
                    CitySearchResult(
                        name = entry.name,
                        district = entry.province,
                        longitude = entry.lon,
                        latitude = entry.lat,
                        savedName = entry.savedName,
                        isApproximateCenter = entry.isApproximateCenter,
                        parentCountyName = entry.parentCountyName,
                        drillDownQuery = entry.drillDownQuery
                    )
                }
            } catch (e: Exception) {
                Log.w("SearchVM", "offline search failed for '$query'", e)
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearSearchResults() {
        searchJob?.cancel()
        _searchResults.value = emptyList()
        _isSearching.value = false
        _isSearchActive.value = false
    }
}
