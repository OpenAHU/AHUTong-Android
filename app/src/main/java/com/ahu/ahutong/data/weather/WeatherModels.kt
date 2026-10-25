package com.ahu.ahutong.data.weather

import com.google.gson.annotations.SerializedName

data class WeatherResponse(
    val province: String? = null,
    val city: String? = null,
    val district: String? = null,
    val adcode: String? = null,
    val weather: String? = null,
    @SerializedName("weather_icon")
    val weatherIcon: String? = null,
    @SerializedName("weather_code")
    val weatherCode: String? = null,
    val temperature: Double? = null,
    @SerializedName("wind_direction")
    val windDirection: String? = null,
    @SerializedName("wind_power")
    val windPower: String? = null,
    val humidity: Int? = null,
    @SerializedName("report_time")
    val reportTime: String? = null,

    @SerializedName("feels_like")
    val feelsLike: Double? = null,
    val visibility: Double? = null,
    val pressure: Double? = null,
    val uv: Double? = null,
    val precipitation: Double? = null,
    val cloud: Int? = null,
    val aqi: Int? = null,
    @SerializedName("aqi_level")
    val aqiLevel: Int? = null,
    @SerializedName("aqi_category")
    val aqiCategory: String? = null,
    @SerializedName("aqi_primary")
    val aqiPrimary: String? = null,
    @SerializedName("air_pollutants")
    val airPollutants: AirPollutants? = null,
    val alerts: List<WeatherAlert>? = null,

    @SerializedName("temp_max")
    val tempMax: Double? = null,
    @SerializedName("temp_min")
    val tempMin: Double? = null,
    val forecast: List<ForecastDay>? = null,

    @SerializedName("hourly_forecast")
    val hourlyForecast: List<HourlyForecast>? = null,

    @SerializedName("life_indices")
    val lifeIndices: LifeIndices? = null,
    val source: String? = null,
    val isDaylight: Boolean? = null,
    val precipitationDescription: String? = null,
    @SerializedName("wind_speed")
    val windSpeed: Double? = null
)

data class AirPollutants(
    val pm25: Double? = null,
    val pm10: Double? = null,
    val o3: Double? = null,
    val no2: Double? = null,
    val so2: Double? = null,
    val co: Double? = null
)

data class WeatherAlert(
    val title: String? = null,
    val type: String? = null,
    val level: String? = null,
    val text: String? = null,
    @SerializedName("publish_time")
    val publishTime: String? = null,
    val publisher: String? = null,
    val guidance: List<String>? = null
)

data class ForecastDay(
    val date: String? = null,
    val week: String? = null,
    @SerializedName("temp_max")
    val tempMax: Double? = null,
    @SerializedName("temp_min")
    val tempMin: Double? = null,
    @SerializedName("weather_day")
    val weatherDay: String? = null,
    @SerializedName("weather_night")
    val weatherNight: String? = null,
    @SerializedName("wind_dir_day")
    val windDirDay: String? = null,
    @SerializedName("wind_dir_night")
    val windDirNight: String? = null,
    @SerializedName("wind_scale_day")
    val windScaleDay: String? = null,
    @SerializedName("wind_scale_night")
    val windScaleNight: String? = null,
    @SerializedName("wind_speed_day")
    val windSpeedDay: Double? = null,
    val humidity: Int? = null,
    val precip: Double? = null,
    val visibility: Double? = null,
    @SerializedName("uv_index")
    val uvIndex: Int? = null,
    val sunrise: String? = null,
    val sunset: String? = null,
    val aqi: Int? = null
)

data class HourlyForecast(
    val time: String? = null,
    val temperature: Double? = null,
    val weather: String? = null,
    @SerializedName("wind_direction")
    val windDirection: String? = null,
    @SerializedName("wind_speed")
    val windSpeed: Double? = null,
    @SerializedName("wind_scale")
    val windScale: String? = null,
    val humidity: Int? = null,
    val precip: Double? = null,
    @SerializedName("feels_like")
    val feelsLike: Double? = null,
    val visibility: Double? = null,
    val pop: Int? = null,
    @SerializedName("uv_index")
    val uvIndex: Int? = null,
    val aqi: Int? = null
)

data class LifeIndices(
    val clothing: LifeIndexItem? = null,
    val uv: LifeIndexItem? = null,
    @SerializedName("car_wash")
    val carWash: LifeIndexItem? = null,
    val drying: LifeIndexItem? = null,
    @SerializedName("air_conditioner")
    val airConditioner: LifeIndexItem? = null,
    @SerializedName("cold_risk")
    val coldRisk: LifeIndexItem? = null,
    val exercise: LifeIndexItem? = null,
    val comfort: LifeIndexItem? = null,
    val travel: LifeIndexItem? = null,
    val fishing: LifeIndexItem? = null,
    val allergy: LifeIndexItem? = null,
    val sunscreen: LifeIndexItem? = null,
    val mood: LifeIndexItem? = null,
    val beer: LifeIndexItem? = null,
    val umbrella: LifeIndexItem? = null,
    val traffic: LifeIndexItem? = null,
    @SerializedName("air_purifier")
    val airPurifier: LifeIndexItem? = null,
    val pollen: LifeIndexItem? = null
)

data class LifeIndexItem(
    val level: String? = null,
    val brief: String? = null,
    val advice: String? = null
)
