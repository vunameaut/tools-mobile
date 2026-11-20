package com.vunameaut.powertimer.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 8 trạng thái khí quyển dùng để đổi Background động của cả Trang Thời Tiết lẫn Widget ngoài Màn hình chính.
 */
enum class WeatherAtmosphere(
    val key: String,
    val label: String,
    val emoji: String
) {
    CLEAR_DAY("clear_day", "Trời quang nắng đẹp", "☀️"),
    HOT_SUNNY("hot_sunny", "Nắng nóng rực rỡ", "🌞"),
    CLEAR_NIGHT("clear_night", "Đêm quang nhiều sao", "🌙"),
    PARTLY_CLOUDY("partly_cloudy", "Có mây dịu mát", "⛅"),
    CLOUDY("cloudy", "Nhiều mây âm u", "☁️"),
    FOG("fog", "Sương mù nhẹ", "🌫️"),
    RAIN("rain", "Trời đang mưa", "🌧️"),
    STORM("storm", "Dông bão có sấm sét", "⛈️");

    companion object {
        fun fromKey(key: String?): WeatherAtmosphere =
            entries.firstOrNull { it.key == key } ?: PARTLY_CLOUDY
    }
}

data class WeatherCityPreset(
    val name: String,
    val lat: Double,
    val lon: Double
)

data class HourlyWeatherItem(
    val timeLabel: String,
    val tempC: Int,
    val weatherCode: Int,
    val emoji: String
)

data class DailyWeatherItem(
    val dateStr: String,
    val dayLabel: String,
    val minTempC: Int,
    val maxTempC: Int,
    val weatherCode: Int,
    val conditionText: String,
    val emoji: String,
    val rainChancePct: Int
)

data class WeatherSnapshot(
    val cityName: String,
    val latitude: Double,
    val longitude: Double,
    val tempC: Int,
    val apparentTempC: Int,
    val humidityPct: Int,
    val windKmh: Int,
    val minTodayC: Int,
    val maxTodayC: Int,
    val isDay: Boolean,
    val weatherCode: Int,
    val conditionText: String,
    val emoji: String,
    val atmosphere: WeatherAtmosphere,
    val updatedAtMillis: Long,
    val hourly: List<HourlyWeatherItem>,
    val daily: List<DailyWeatherItem>
)

object WeatherManager {
    private const val PREF_NAME = "tools_hub_weather_prefs"
    private const val KEY_CITY_NAME = "city_name"
    private const val KEY_LAT = "lat"
    private const val KEY_LON = "lon"
    private const val KEY_USE_AUTO_LOCATION = "use_auto_location"
    private const val KEY_CACHED_JSON = "cached_snapshot_json"

    val VIETNAM_CITY_PRESETS = listOf(
        WeatherCityPreset("Hà Nội", 21.0285, 105.8542),
        WeatherCityPreset("TP. Hồ Chí Minh", 10.8231, 106.6297),
        WeatherCityPreset("Đà Nẵng", 16.0544, 108.2022),
        WeatherCityPreset("Hải Phòng", 20.8449, 106.6881),
        WeatherCityPreset("Cần Thơ", 10.0452, 105.7469),
        WeatherCityPreset("Nha Trang", 12.2388, 109.1967),
        WeatherCityPreset("Đà Lạt", 11.9404, 108.4583),
        WeatherCityPreset("Huế", 16.4637, 107.5909),
        WeatherCityPreset("Vũng Tàu", 10.4114, 107.1362),
        WeatherCityPreset("Phú Quốc", 10.2899, 103.9840),
        WeatherCityPreset("Quy Nhơn", 13.7829, 109.2197),
        WeatherCityPreset("Sa Pa", 22.3364, 103.8438)
    )

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun isAutoLocationEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_USE_AUTO_LOCATION, true)
    }

    fun setManualCity(context: Context, cityName: String, lat: Double, lon: Double) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_USE_AUTO_LOCATION, false)
            .putString(KEY_CITY_NAME, cityName)
            .putString(KEY_LAT, lat.toString())
            .putString(KEY_LON, lon.toString())
            .apply()
    }

    fun enableAutoLocation(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_USE_AUTO_LOCATION, true)
            .apply()
    }

    fun getCachedSnapshot(context: Context): WeatherSnapshot? {
        val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CACHED_JSON, null) ?: return null
        return runCatching { parseCachedSnapshot(JSONObject(raw)) }.getOrNull()
    }

    fun getCachedOrDefaultSnapshot(context: Context): WeatherSnapshot {
        return getCachedSnapshot(context) ?: WeatherSnapshot(
            cityName = "Hà Nội",
            latitude = 21.0285,
            longitude = 105.8542,
            tempC = 26,
            apparentTempC = 27,
            humidityPct = 72,
            windKmh = 10,
            minTodayC = 22,
            maxTodayC = 29,
            isDay = true,
            weatherCode = 2,
            conditionText = "Có mây dịu mát",
            emoji = "⛅",
            atmosphere = WeatherAtmosphere.PARTLY_CLOUDY,
            updatedAtMillis = 0L,
            hourly = emptyList(),
            daily = emptyList()
        )
    }

    suspend fun refreshWeather(context: Context, forceNetwork: Boolean = true): Result<WeatherSnapshot> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cached = getCachedSnapshot(context)
                val now = System.currentTimeMillis()
                if (!forceNetwork && cached != null && now - cached.updatedAtMillis < 15 * 60_000L) {
                    return@runCatching cached
                }

                val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                val useAuto = prefs.getBoolean(KEY_USE_AUTO_LOCATION, true)

                var targetName = prefs.getString(KEY_CITY_NAME, "Hà Nội") ?: "Hà Nội"
                var targetLat = prefs.getString(KEY_LAT, null)?.toDoubleOrNull() ?: 21.0285
                var targetLon = prefs.getString(KEY_LON, null)?.toDoubleOrNull() ?: 105.8542

                if (useAuto) {
                    val gps = resolveDeviceGpsLocation(context)
                    if (gps != null) {
                        targetName = gps.name
                        targetLat = gps.lat
                        targetLon = gps.lon
                    } else {
                        val ipLoc = resolveIpLocation()
                        if (ipLoc != null) {
                            targetName = ipLoc.name
                            targetLat = ipLoc.lat
                            targetLon = ipLoc.lon
                        }
                    }
                    prefs.edit()
                        .putString(KEY_CITY_NAME, targetName)
                        .putString(KEY_LAT, targetLat.toString())
                        .putString(KEY_LON, targetLon.toString())
                        .apply()
                }

                val snapshot = fetchOpenMeteoForecast(targetName, targetLat, targetLon)
                saveCachedSnapshot(context, snapshot)
                snapshot
            }
        }

    suspend fun searchCities(query: String): List<WeatherCityPreset> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()
        runCatching {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=8&language=vi&format=json"
            val body = httpGet(url)
            val root = JSONObject(body)
            val arr = root.optJSONArray("results") ?: return@runCatching emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val name = obj.optString("name")
                    val admin = obj.optString("admin1")
                    val country = obj.optString("country")
                    val label = listOf(name, admin, country).filter { it.isNotBlank() }.distinct().joinToString(", ")
                    add(
                        WeatherCityPreset(
                            name = label.ifBlank { name },
                            lat = obj.getDouble("latitude"),
                            lon = obj.getDouble("longitude")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    @SuppressLint("MissingPermission")
    private fun resolveDeviceGpsLocation(context: Context): WeatherCityPreset? {
        if (!hasLocationPermission(context)) return null
        return runCatching {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
            val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            var bestLoc: android.location.Location? = null
            for (p in providers) {
                val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull() ?: continue
                if (bestLoc == null || loc.time > bestLoc.time) {
                    bestLoc = loc
                }
            }
            val loc = bestLoc ?: return null
            val cityName = runCatching {
                @Suppress("DEPRECATION")
                val list = Geocoder(context, Locale("vi", "VN")).getFromLocation(loc.latitude, loc.longitude, 1)
                val addr = list?.firstOrNull()
                addr?.subAdminArea ?: addr?.locality ?: addr?.adminArea
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Vị trí hiện tại"
            WeatherCityPreset(cityName, loc.latitude, loc.longitude)
        }.getOrNull()
    }

    private fun resolveIpLocation(): WeatherCityPreset? {
        return runCatching {
            val json = JSONObject(httpGet("https://ipwho.is/"))
            if (!json.optBoolean("success", false)) return null
            val lat = json.optDouble("latitude", Double.NaN)
            val lon = json.optDouble("longitude", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) return null
            val city = json.optString("city").ifBlank { json.optString("region") }.ifBlank { "Vị trí hiện tại" }
            WeatherCityPreset(city, lat, lon)
        }.getOrNull()
    }

    private fun fetchOpenMeteoForecast(cityName: String, lat: Double, lon: Double): WeatherSnapshot {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,wind_speed_10m" +
                "&hourly=temperature_2m,weather_code,is_day" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                "&timezone=auto&forecast_days=7",
            lat,
            lon
        )
        val root = JSONObject(httpGet(url))
        val cur = root.getJSONObject("current")
        val tempC = cur.getDouble("temperature_2m").roundToInt()
        val apparentC = cur.getDouble("apparent_temperature").roundToInt()
        val humidity = cur.getInt("relative_humidity_2m")
        val windKmh = cur.getDouble("wind_speed_10m").roundToInt()
        val isDay = cur.optInt("is_day", 1) == 1
        val code = cur.getInt("weather_code")
        val curTimeStr = cur.optString("time", "")

        val (conditionText, emoji, atmosphere) = mapWeatherCode(code, isDay, tempC)

        // Hourly (lấy tối đa 12 giờ kế tiếp kể từ giờ hiện tại)
        val hourlyList = mutableListOf<HourlyWeatherItem>()
        val hourlyObj = root.optJSONObject("hourly")
        if (hourlyObj != null) {
            val times = hourlyObj.getJSONArray("time")
            val temps = hourlyObj.getJSONArray("temperature_2m")
            val codes = hourlyObj.getJSONArray("weather_code")
            val isDays = hourlyObj.optJSONArray("is_day")
            val curHourPrefix = curTimeStr.take(13)
            var startIdx = 0
            for (i in 0 until times.length()) {
                if (times.getString(i).startsWith(curHourPrefix)) {
                    startIdx = i
                    break
                }
            }
            val endIdx = minOf(startIdx + 12, times.length())
            for (i in startIdx until endIdx) {
                val tStr = times.getString(i)
                val hhmm = if (tStr.length >= 16) tStr.substring(11, 16) else tStr
                val hTemp = temps.getDouble(i).roundToInt()
                val hCode = codes.getInt(i)
                val hDay = isDays?.optInt(i, 1) != 0
                val (_, hEmoji, _) = mapWeatherCode(hCode, hDay, hTemp)
                hourlyList.add(HourlyWeatherItem(hhmm, hTemp, hCode, hEmoji))
            }
        }

        // Daily (7 ngày)
        val dailyList = mutableListOf<DailyWeatherItem>()
        val dailyObj = root.optJSONObject("daily")
        if (dailyObj != null) {
            val dates = dailyObj.getJSONArray("time")
            val codes = dailyObj.getJSONArray("weather_code")
            val maxTemps = dailyObj.getJSONArray("temperature_2m_max")
            val minTemps = dailyObj.getJSONArray("temperature_2m_min")
            val rainProbs = dailyObj.optJSONArray("precipitation_probability_max")
            for (i in 0 until dates.length()) {
                val dStr = dates.getString(i) // yyyy-MM-dd
                val dCode = codes.getInt(i)
                val dMax = maxTemps.getDouble(i).roundToInt()
                val dMin = minTemps.getDouble(i).roundToInt()
                val dRain = rainProbs?.optInt(i, 0) ?: 0
                val (dText, dEmoji, _) = mapWeatherCode(dCode, true, dMax)
                val dayLbl = if (i == 0) "Hôm nay" else if (i == 1) "Ngày mai" else formatShortDate(dStr)
                dailyList.add(
                    DailyWeatherItem(
                        dateStr = dStr,
                        dayLabel = dayLbl,
                        minTempC = dMin,
                        maxTempC = dMax,
                        weatherCode = dCode,
                        conditionText = dText,
                        emoji = dEmoji,
                        rainChancePct = dRain
                    )
                )
            }
        }

        val minToday = dailyList.firstOrNull()?.minTempC ?: (tempC - 3)
        val maxToday = dailyList.firstOrNull()?.maxTempC ?: (tempC + 3)

        return WeatherSnapshot(
            cityName = cityName,
            latitude = lat,
            longitude = lon,
            tempC = tempC,
            apparentTempC = apparentC,
            humidityPct = humidity,
            windKmh = windKmh,
            minTodayC = minToday,
            maxTodayC = maxToday,
            isDay = isDay,
            weatherCode = code,
            conditionText = conditionText,
            emoji = emoji,
            atmosphere = atmosphere,
            updatedAtMillis = System.currentTimeMillis(),
            hourly = hourlyList,
            daily = dailyList
        )
    }

    private fun formatShortDate(isoDate: String): String {
        val parts = isoDate.split("-")
        return if (parts.size == 3) "${parts[2]}/${parts[1]}" else isoDate
    }

    fun mapWeatherCode(code: Int, isDay: Boolean, tempC: Int): Triple<String, String, WeatherAtmosphere> {
        return when (code) {
            0 -> {
                if (!isDay) Triple("Đêm quang đãng", "🌙", WeatherAtmosphere.CLEAR_NIGHT)
                else if (tempC >= 34) Triple("Nắng nóng rực rỡ", "🌞", WeatherAtmosphere.HOT_SUNNY)
                else Triple("Trời nắng quang", "☀️", WeatherAtmosphere.CLEAR_DAY)
            }
            1, 2 -> {
                if (!isDay) Triple("Đêm ít mây", "🌙", WeatherAtmosphere.CLEAR_NIGHT)
                else Triple("Ít mây, nắng nhẹ", "⛅", WeatherAtmosphere.PARTLY_CLOUDY)
            }
            3 -> Triple("Nhiều mây âm u", "☁️", WeatherAtmosphere.CLOUDY)
            45, 48 -> Triple("Có sương mù", "🌫️", WeatherAtmosphere.FOG)
            51, 53, 55, 56, 57 -> Triple("Mưa phùn nhẹ", "🌦️", WeatherAtmosphere.RAIN)
            61, 63, 65, 66, 67, 80, 81, 82 -> Triple("Trời đang mưa", "🌧️", WeatherAtmosphere.RAIN)
            71, 73, 75, 77, 85, 86 -> Triple("Mưa lạnh / Tuyết", "❄️", WeatherAtmosphere.CLOUDY)
            95, 96, 99 -> Triple("Dông bão có sấm sét", "⛈️", WeatherAtmosphere.STORM)
            else -> {
                if (!isDay) Triple("Đêm dịu mát", "🌙", WeatherAtmosphere.CLEAR_NIGHT)
                else Triple("Có mây", "⛅", WeatherAtmosphere.PARTLY_CLOUDY)
            }
        }
    }

    private fun saveCachedSnapshot(context: Context, s: WeatherSnapshot) {
        val json = JSONObject().apply {
            put("cityName", s.cityName)
            put("latitude", s.latitude)
            put("longitude", s.longitude)
            put("tempC", s.tempC)
            put("apparentTempC", s.apparentTempC)
            put("humidityPct", s.humidityPct)
            put("windKmh", s.windKmh)
            put("minTodayC", s.minTodayC)
            put("maxTodayC", s.maxTodayC)
            put("isDay", s.isDay)
            put("weatherCode", s.weatherCode)
            put("conditionText", s.conditionText)
            put("emoji", s.emoji)
            put("atmosphere", s.atmosphere.key)
            put("updatedAtMillis", s.updatedAtMillis)
            put("hourly", JSONArray().apply {
                s.hourly.forEach { h ->
                    put(JSONObject().apply {
                        put("timeLabel", h.timeLabel)
                        put("tempC", h.tempC)
                        put("weatherCode", h.weatherCode)
                        put("emoji", h.emoji)
                    })
                }
            })
            put("daily", JSONArray().apply {
                s.daily.forEach { d ->
                    put(JSONObject().apply {
                        put("dateStr", d.dateStr)
                        put("dayLabel", d.dayLabel)
                        put("minTempC", d.minTempC)
                        put("maxTempC", d.maxTempC)
                        put("weatherCode", d.weatherCode)
                        put("conditionText", d.conditionText)
                        put("emoji", d.emoji)
                        put("rainChancePct", d.rainChancePct)
                    })
                }
            })
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CACHED_JSON, json.toString())
            .apply()
    }

    private fun parseCachedSnapshot(j: JSONObject): WeatherSnapshot {
        val hourlyArr = j.optJSONArray("hourly") ?: JSONArray()
        val hourly = buildList {
            for (i in 0 until hourlyArr.length()) {
                val h = hourlyArr.getJSONObject(i)
                add(
                    HourlyWeatherItem(
                        timeLabel = h.getString("timeLabel"),
                        tempC = h.getInt("tempC"),
                        weatherCode = h.getInt("weatherCode"),
                        emoji = h.getString("emoji")
                    )
                )
            }
        }
        val dailyArr = j.optJSONArray("daily") ?: JSONArray()
        val daily = buildList {
            for (i in 0 until dailyArr.length()) {
                val d = dailyArr.getJSONObject(i)
                add(
                    DailyWeatherItem(
                        dateStr = d.getString("dateStr"),
                        dayLabel = d.getString("dayLabel"),
                        minTempC = d.getInt("minTempC"),
                        maxTempC = d.getInt("maxTempC"),
                        weatherCode = d.getInt("weatherCode"),
                        conditionText = d.getString("conditionText"),
                        emoji = d.getString("emoji"),
                        rainChancePct = d.optInt("rainChancePct", 0)
                    )
                )
            }
        }
        return WeatherSnapshot(
            cityName = j.getString("cityName"),
            latitude = j.getDouble("latitude"),
            longitude = j.getDouble("longitude"),
            tempC = j.getInt("tempC"),
            apparentTempC = j.getInt("apparentTempC"),
            humidityPct = j.getInt("humidityPct"),
            windKmh = j.getInt("windKmh"),
            minTodayC = j.getInt("minTodayC"),
            maxTodayC = j.getInt("maxTodayC"),
            isDay = j.getBoolean("isDay"),
            weatherCode = j.getInt("weatherCode"),
            conditionText = j.getString("conditionText"),
            emoji = j.getString("emoji"),
            atmosphere = WeatherAtmosphere.fromKey(j.optString("atmosphere")),
            updatedAtMillis = j.getLong("updatedAtMillis"),
            hourly = hourly,
            daily = daily
        )
    }

    private fun httpGet(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 9000
            readTimeout = 9000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "ToolsHub-Android/1.0")
        }
        try {
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            return stream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

