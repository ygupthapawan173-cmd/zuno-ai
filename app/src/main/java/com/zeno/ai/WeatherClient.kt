package com.zeno.ai

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Weather from Open-Meteo (free, no API key). Call from a background thread. */
object WeatherClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun get(url: String): String {
        return http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            r.body?.string().orEmpty()
        }
    }

    fun fetch(city: String): String? {
        return try {
            val geo = JSONObject(
                get("https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + URLEncoder.encode(city, "UTF-8"))
            )
            val place = geo.optJSONArray("results")?.optJSONObject(0) ?: return null
            val lat = place.getDouble("latitude")
            val lon = place.getDouble("longitude")
            val name = place.optString("name", city)
            val w = JSONObject(
                get(
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,weather_code" +
                        "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                        "&timezone=auto&forecast_days=1"
                )
            )
            val cur = w.getJSONObject("current")
            val daily = w.getJSONObject("daily")
            val min = daily.getJSONArray("temperature_2m_min").optDouble(0)
            val max = daily.getJSONArray("temperature_2m_max").optDouble(0)
            val rain = daily.getJSONArray("precipitation_probability_max").optInt(0)
            "$name: now ${cur.getDouble("temperature_2m")}°C, ${describe(cur.getInt("weather_code"))}; " +
                "today min $min°C, max $max°C, rain chance $rain%"
        } catch (e: Exception) {
            null
        }
    }

    private fun describe(code: Int): String = when (code) {
        0 -> "clear sky"
        1, 2 -> "partly cloudy"
        3 -> "overcast"
        45, 48 -> "fog"
        in 51..57 -> "drizzle"
        in 61..67 -> "rain"
        in 71..77 -> "snow"
        in 80..82 -> "rain showers"
        in 95..99 -> "thunderstorm"
        else -> "mixed weather"
    }
}
