package com.ridgecast.weather.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.ridgecast.weather.data.provider.impl.OfflineGeocodingProvider
import com.ridgecast.weather.data.provider.impl.SystemGeocoderProvider
import com.ridgecast.weather.data.provider.impl.SystemLocationProvider
import com.ridgecast.weather.data.provider.model.SimpleLocation
import com.ridgecast.weather.util.FileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationManager @Inject constructor(
    @ApplicationContext val context: Context,
    private val systemLocationProvider: SystemLocationProvider,
    private val systemGeocoderProvider: SystemGeocoderProvider,
    private val offlineGeocodingProvider: OfflineGeocodingProvider
) {
    data class CachedLocation(
        val latitude: Double,
        val longitude: Double,
        val name: String,
        val time: Long = 0L,
        val accuracy: Float = 0f,
        val isReliableName: Boolean = true
    )

    private data class PendingLocation(
        val latitude: Double,
        val longitude: Double,
        val name: String,
        val time: Long,
        val accuracy: Float
    )

    private data class TimedNullableResult<T>(
        val value: T?
    )

    companion object {
        private const val TAG = "LocationManager"
        const val UNKNOWN_LOCATION = "未知位置"
        const val LOCATING_NAME = "定位中..."
        const val DEFAULT_LONGITUDE = 116.4074
        const val DEFAULT_LATITUDE = 39.9042
        private const val PREFS_NAME = "location_cache"
        private const val KEY_CACHED_LAT = "cached_lat"
        private const val KEY_CACHED_LON = "cached_lon"
        private const val KEY_CACHED_NAME = "cached_name"
        private const val KEY_CACHED_TIME = "cached_time"
        private const val KEY_CACHED_ACCURACY = "cached_accuracy"
        private const val KEY_PENDING_LAT = "pending_lat"
        private const val KEY_PENDING_LON = "pending_lon"
        private const val KEY_PENDING_NAME = "pending_name"
        private const val KEY_PENDING_TIME = "pending_time"
        private const val KEY_PENDING_ACCURACY = "pending_accuracy"
        private const val KEY_LAST_GOOD_NAME = "last_good_name"

        private const val REGULAR_LOCATION_TOTAL_TIMEOUT_MS = 6_000L
        private const val HIGH_ACCURACY_LOCATION_TOTAL_TIMEOUT_MS = 5_000L
        private const val ACCEPTABLE_ACCURACY_METERS = 120f
    }

    private val cachePrefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun hasBackgroundLocationPermission(): Boolean {
        val hasForeground = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!hasForeground) return false
        return hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    fun hasLocationPermission(): Boolean {
        return hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    fun getCachedLocation(): CachedLocation? {
        val lat = cachePrefs.getFloat(KEY_CACHED_LAT, 0f).toDouble()
        val lon = cachePrefs.getFloat(KEY_CACHED_LON, 0f).toDouble()
        val name = cachePrefs.getString(KEY_CACHED_NAME, null)?.takeIf { it.isNotBlank() }
        val time = cachePrefs.getLong(KEY_CACHED_TIME, 0L)
        val accuracy = cachePrefs.getFloat(KEY_CACHED_ACCURACY, 0f)
        if (lat == 0.0 || lon == 0.0 || name == null) return null
        return CachedLocation(latitude = lat, longitude = lon, name = name, time = time, accuracy = accuracy)
    }

    fun saveCachedLocation(
        name: String,
        longitude: Double,
        latitude: Double,
        time: Long = System.currentTimeMillis(),
        accuracy: Float = 0f,
        isReliableName: Boolean = true
    ) {
        val normalizedName = name.takeIf { it.isNotBlank() } ?: return
        if (latitude == 0.0 || longitude == 0.0) return

        val isGenericOrEmpty = normalizedName == UNKNOWN_LOCATION ||
            normalizedName == LOCATING_NAME ||
            normalizedName == "当前位置" ||
            normalizedName.isBlank()

        val displayName: String = if (!isGenericOrEmpty) {
            saveLastGoodName(normalizedName)
            locI("save_cached_location: valid_name, display=${normalizedName.safeLogValue()}")
            normalizedName
        } else {
            val lastGood = getLastGoodName()
            val resolved = lastGood ?: normalizedName
            locI("save_cached_location: generic_name, reused_last_good=${resolved.safeLogValue()}")
            resolved
        }

        cachePrefs.edit()
            .putFloat(KEY_CACHED_LAT, latitude.toFloat())
            .putFloat(KEY_CACHED_LON, longitude.toFloat())
            .putString(KEY_CACHED_NAME, displayName)
            .putLong(KEY_CACHED_TIME, time)
            .putFloat(KEY_CACHED_ACCURACY, accuracy)
            .apply()
    }

    private fun saveLastGoodName(name: String) {
        cachePrefs.edit().putString(KEY_LAST_GOOD_NAME, name).apply()
    }

    private fun getLastGoodName(): String? {
        return cachePrefs.getString(KEY_LAST_GOOD_NAME, null)?.takeIf { it.isNotBlank() }
    }

    private fun getPendingLocation(): PendingLocation? {
        val lat = cachePrefs.getFloat(KEY_PENDING_LAT, 0f).toDouble()
        val lon = cachePrefs.getFloat(KEY_PENDING_LON, 0f).toDouble()
        val name = cachePrefs.getString(KEY_PENDING_NAME, null)?.takeIf { it.isNotBlank() }
        val time = cachePrefs.getLong(KEY_PENDING_TIME, 0L)
        val accuracy = cachePrefs.getFloat(KEY_PENDING_ACCURACY, 0f)
        if (lat == 0.0 || lon == 0.0 || name == null || time <= 0L) return null
        return PendingLocation(latitude = lat, longitude = lon, name = name, time = time, accuracy = accuracy)
    }

    private fun savePendingLocation(
        name: String,
        longitude: Double,
        latitude: Double,
        time: Long,
        accuracy: Float
    ) {
        val normalizedName = name.takeIf { it.isNotBlank() } ?: return
        if (latitude == 0.0 || longitude == 0.0) return
        cachePrefs.edit()
            .putFloat(KEY_PENDING_LAT, latitude.toFloat())
            .putFloat(KEY_PENDING_LON, longitude.toFloat())
            .putString(KEY_PENDING_NAME, normalizedName)
            .putLong(KEY_PENDING_TIME, time)
            .putFloat(KEY_PENDING_ACCURACY, accuracy)
            .apply()
    }

    private fun clearPendingLocation() {
        cachePrefs.edit()
            .remove(KEY_PENDING_LAT)
            .remove(KEY_PENDING_LON)
            .remove(KEY_PENDING_NAME)
            .remove(KEY_PENDING_TIME)
            .remove(KEY_PENDING_ACCURACY)
            .apply()
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    private fun locI(message: String) = FileLogger.locI(TAG, message)
    private fun locW(message: String) = FileLogger.locW(TAG, message)

    private fun elapsedSince(startMs: Long): Long = android.os.SystemClock.elapsedRealtime() - startMs

    private fun Double.fullCoord(): String = String.format(Locale.US, "%.6f", this)

    private fun SimpleLocation.locSummary(): String {
        return "lat=${latitude.fullCoord()}, lon=${longitude.fullCoord()}, accuracy=${accuracy}m, provider=$provider"
    }

    private fun applyAntiJitter(
        lat: Double,
        lon: Double,
        name: String,
        accuracy: Float = 0f,
        time: Long = System.currentTimeMillis(),
        highAccuracy: Boolean = false,
        isReliableName: Boolean = true
    ): CachedLocation {
        val cached = getCachedLocation()
        if (cached != null && cached.name.isNotBlank() && cached.name != UNKNOWN_LOCATION) {
            val now = System.currentTimeMillis()
            val dist = distanceBetween(lat, lon, cached.latitude, cached.longitude)
            val timeDiff = now - cached.time
            val isCacheExpired = timeDiff > 5 * 60 * 1000L

            if (LocationJumpGuard.shouldHoldForConfirmation(dist, accuracy, timeDiff, highAccuracy)) {
                val pending = getPendingLocation()
                if (pending != null) {
                    val pendingAge = now - pending.time
                    val distanceToPending = distanceBetween(lat, lon, pending.latitude, pending.longitude)
                    if (LocationJumpGuard.isConfirmedByPending(distanceToPending, pendingAge)) {
                        clearPendingLocation()
                        return CachedLocation(latitude = lat, longitude = lon, name = name, time = time, accuracy = accuracy, isReliableName = isReliableName)
                    }
                    if (LocationJumpGuard.isPendingExpired(pendingAge)) {
                        clearPendingLocation()
                    }
                }
                savePendingLocation(name, lon, lat, now, accuracy)
                return cached
            }

            val isOutlierJump = !isCacheExpired &&
                accuracy > 300f &&
                cached.accuracy > 0f &&
                cached.accuracy < accuracy &&
                dist < accuracy

            if (isOutlierJump) {
                return cached
            }

            val isReliableFineUpdate = highAccuracy &&
                accuracy > 0f &&
                accuracy <= ACCEPTABLE_ACCURACY_METERS &&
                (dist >= 35f || (name != cached.name && dist >= 20f))
            if (isReliableFineUpdate) {
                clearPendingLocation()
                return CachedLocation(latitude = lat, longitude = lon, name = name, time = time, accuracy = accuracy, isReliableName = isReliableName)
            }

            if (!isCacheExpired && dist < 200f && name == cached.name && (accuracy >= cached.accuracy || accuracy > 100f)) {
                return cached
            }
        }
        clearPendingLocation()
        return CachedLocation(latitude = lat, longitude = lon, name = name, time = time, accuracy = accuracy, isReliableName = isReliableName)
    }

    suspend fun requestBestLocation(
        highAccuracy: Boolean = false,
        totalTimeoutMillis: Long = if (highAccuracy) {
            HIGH_ACCURACY_LOCATION_TOTAL_TIMEOUT_MS
        } else {
            REGULAR_LOCATION_TOTAL_TIMEOUT_MS
        }
    ): CachedLocation? {
        val startMs = android.os.SystemClock.elapsedRealtime()
        locI("location_flow_start: provider=system_plus_offline_geo, highAccuracy=$highAccuracy, totalTimeout=${totalTimeoutMillis}ms")
        val result = withTimeoutOrNull(totalTimeoutMillis) {
            TimedNullableResult(requestBestLocationInternal(highAccuracy, totalTimeoutMillis))
        }
        if (result == null) {
            Log.w(TAG, "定位总流程超时: ${totalTimeoutMillis}ms, highAccuracy=$highAccuracy")
            locW("location_flow_timeout: elapsed=${elapsedSince(startMs)}ms, highAccuracy=$highAccuracy")
        } else {
            locI("location_flow_complete: elapsed=${elapsedSince(startMs)}ms, highAccuracy=$highAccuracy")
        }
        return result?.value
    }

    private suspend fun requestBestLocationInternal(
        highAccuracy: Boolean = false,
        totalTimeoutMillis: Long
    ): CachedLocation? {
        val startMs = android.os.SystemClock.elapsedRealtime()
        if (!hasLocationPermission()) {
            locW("location_flow_internal_no_permission")
            return null
        }

        var sysLoc: SimpleLocation? = null
        val maxAttempts = if (highAccuracy) 1 else 2
        for (attempt in 1..maxAttempts) {
            val remainingMillis = totalTimeoutMillis - elapsedSince(startMs)
            if (remainingMillis < 2_000L) break
            val attemptTimeoutMillis = (remainingMillis - 300L).coerceAtLeast(1_500L)
            sysLoc = systemLocationProvider.requestLocation(highAccuracy, attemptTimeoutMillis)
            if (sysLoc != null) break
            if (attempt < maxAttempts) {
                kotlinx.coroutines.delay(600L)
            }
        }

        if (sysLoc != null) {
            val name = reverseGeocode(
                sysLoc.latitude,
                sysLoc.longitude,
                forceRefresh = highAccuracy,
                accuracy = sysLoc.accuracy
            )
            val isReliable = name != UNKNOWN_LOCATION && name.isNotBlank()
            locI("system_location_used: elapsed=${elapsedSince(startMs)}ms, name=${name.safeLogValue()}, ${sysLoc.locSummary()}")
            return applyAntiJitter(
                sysLoc.latitude,
                sysLoc.longitude,
                name,
                sysLoc.accuracy,
                sysLoc.time,
                highAccuracy,
                isReliableName = isReliable
            )
        }

        locW("location_flow_internal_failed: elapsed=${elapsedSince(startMs)}ms")
        return null
    }

    suspend fun reverseGeocode(
        lat: Double,
        lon: Double,
        forceRefresh: Boolean = false,
        accuracy: Float = 0f
    ): String = withContext(Dispatchers.IO) {
        val startMs = android.os.SystemClock.elapsedRealtime()
        val cached = getCachedLocation()
        if (!forceRefresh && cached != null && cached.name.isNotBlank() && cached.name != UNKNOWN_LOCATION) {
            val dist = distanceBetween(lat, lon, cached.latitude, cached.longitude)
            if (dist < 1000f) {
                locI("reverse_geocode_cache_hit_1km: elapsed=${elapsedSince(startMs)}ms, dist=${dist}m, result=${cached.name.safeLogValue()}")
                return@withContext cached.name
            }
        }

        // 1. 本地全离线两阶段逆地理引擎 (微秒级响应，0网络请求，100%对齐民政部最新法定乡镇街道)
        val offlineResult = offlineGeocodingProvider.reverseGeocode(lat, lon)
        if (!offlineResult.isNullOrBlank() && offlineResult != UNKNOWN_LOCATION) {
            locI("reverse_geocode_selected: source=offline_engine, total=${elapsedSince(startMs)}ms, result=${offlineResult.safeLogValue()}")
            return@withContext offlineResult
        }

        // 2. 境外坐标兜底：使用系统原生 Geocoder
        val systemResult = systemGeocoderProvider.reverseGeocode(lat, lon)
        if (!systemResult.isNullOrBlank() && systemResult != UNKNOWN_LOCATION) {
            locI("reverse_geocode_selected: source=system_geocoder, total=${elapsedSince(startMs)}ms, result=${systemResult.safeLogValue()}")
            return@withContext systemResult
        }

        UNKNOWN_LOCATION
    }

    private fun String?.safeLogValue(maxLength: Int = 80): String {
        val value = this?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotBlank() } ?: "null"
        return if (value.length <= maxLength) value else value.take(maxLength) + "..."
    }
}
