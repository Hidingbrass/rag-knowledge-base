package com.example.aikb.tool;

import com.example.aikb.config.ToolExecutionProperties;
import com.example.aikb.enums.ToolOperation;
import com.example.aikb.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

/** 使用固定 Open-Meteo 域名读取当前天气，不接受模型提供任意 URL。 */
@Component
public class CurrentWeatherTool implements AuthorizedTool {
    public static final String NAME = "get_current_weather";

    private final RestClient geocodingClient;
    private final RestClient forecastClient;
    private final ToolExecutionProperties properties;

    public CurrentWeatherTool(
            @Qualifier("weatherGeocodingRestClient") RestClient geocodingClient,
            @Qualifier("weatherForecastRestClient") RestClient forecastClient,
            ToolExecutionProperties properties
    ) {
        this.geocodingClient = geocodingClient;
        this.forecastClient = forecastClient;
        this.properties = properties;
    }

    @Override
    public String name() { return NAME; }

    @Override
    public ToolOperation operation() { return ToolOperation.READ; }

    @Override
    public ToolExecutionResult execute(ToolExecutionContext context, JsonNode arguments) {
        if (!properties.weather().enabled()) {
            throw new BusinessException("当前天气工具未启用");
        }
        String city = arguments.path("city").asText("").strip();
        if (city.isBlank()) {
            throw new BusinessException("查询天气前需要提供城市");
        }
        try {
            JsonNode geocoding = geocodingClient.get()
                    .uri(uri -> uri.path("/v1/search")
                            .queryParam("name", city)
                            .queryParam("count", 1)
                            .queryParam("language", "zh")
                            .queryParam("format", "json")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode location = geocoding == null ? null : geocoding.path("results").path(0);
            if (location == null || location.isMissingNode()) {
                throw new BusinessException("没有找到城市“" + city + "”，请提供更完整的城市名");
            }
            double latitude = location.path("latitude").asDouble(Double.NaN);
            double longitude = location.path("longitude").asDouble(Double.NaN);
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) {
                throw new BusinessException("天气服务未返回有效坐标");
            }

            JsonNode forecast = forecastClient.get()
                    .uri(uri -> uri.path("/v1/forecast")
                            .queryParam("latitude", latitude)
                            .queryParam("longitude", longitude)
                            .queryParam("current", "temperature_2m,apparent_temperature,relative_humidity_2m,precipitation,weather_code,wind_speed_10m")
                            .queryParam("timezone", "auto")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode current = forecast == null ? null : forecast.path("current");
            if (current == null || current.isMissingNode()) {
                throw new BusinessException("天气服务暂未返回当前观测数据");
            }

            String locationName = location.path("name").asText(city);
            String admin1 = location.path("admin1").asText("");
            String displayName = admin1.isBlank() || admin1.equals(locationName)
                    ? locationName : admin1 + " " + locationName;
            String updatedAt = current.path("time").asText("未知时间");
            double temperature = current.path("temperature_2m").asDouble();
            double apparent = current.path("apparent_temperature").asDouble();
            int humidity = current.path("relative_humidity_2m").asInt();
            double precipitation = current.path("precipitation").asDouble();
            double wind = current.path("wind_speed_10m").asDouble();
            String condition = weatherCodeDescription(current.path("weather_code").asInt(-1));
            String answer = "%s当前天气：%s，%.1f°C，体感 %.1f°C，湿度 %d%%，降水 %.1f mm，风速 %.1f km/h。数据时间：%s；来源：Open-Meteo。"
                    .formatted(displayName, condition, temperature, apparent, humidity, precipitation, wind, updatedAt);

            Map<String, Object> audit = new LinkedHashMap<>();
            audit.put("provider", "Open-Meteo");
            audit.put("city", displayName);
            audit.put("observed_at", updatedAt);
            return new ToolExecutionResult(answer, audit);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new BusinessException("实时天气服务调用失败，请稍后重试", exception);
        }
    }

    private String weatherCodeDescription(int code) {
        return switch (code) {
            case 0 -> "晴";
            case 1, 2, 3 -> "多云";
            case 45, 48 -> "有雾";
            case 51, 53, 55, 56, 57 -> "毛毛雨";
            case 61, 63, 65, 66, 67, 80, 81, 82 -> "有雨";
            case 71, 73, 75, 77, 85, 86 -> "有雪";
            case 95, 96, 99 -> "雷暴";
            default -> "天气代码 " + code;
        };
    }
}
