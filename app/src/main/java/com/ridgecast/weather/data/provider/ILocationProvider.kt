package com.ridgecast.weather.data.provider

import com.ridgecast.weather.data.provider.model.SimpleLocation

interface ILocationProvider {
    suspend fun requestLocation(highAccuracy: Boolean, timeoutMillis: Long): SimpleLocation?
}
