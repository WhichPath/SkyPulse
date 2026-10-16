package com.ridgecast.weather.data

import com.ridgecast.weather.data.provider.impl.OfflineGeocodingProvider
import javax.inject.Inject
import javax.inject.Singleton

data class CityEntry(
    val name: String,
    val province: String,
    val lat: Double,
    val lon: Double,
    val savedName: String = name,
    val isApproximateCenter: Boolean = false,
    val parentCountyName: String = "",
    val drillDownQuery: String? = null
)

/**
 * 纯离线行政区划与位置搜索服务。
 *
 * 国内查询 100% 走本地民政部四级行政区划与空间坐标引擎 (OfflineGeocodingProvider)，
 * 不发起任何外部网络请求。
 */
@Singleton
class GeocodingService @Inject constructor(
    private val offlineGeocodingProvider: OfflineGeocodingProvider
) {

    suspend fun search(query: String): List<CityEntry> {
        if (query.isBlank()) return emptyList()
        return offlineGeocodingProvider.search(query)
    }
}
