package com.ridgecast.weather.data.provider

interface IGeocodingProvider {
    suspend fun reverseGeocode(latitude: Double, longitude: Double): String?
}
