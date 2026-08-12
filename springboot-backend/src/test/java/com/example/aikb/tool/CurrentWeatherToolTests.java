package com.example.aikb.tool;

import com.example.aikb.config.ToolExecutionProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;

class CurrentWeatherToolTests {
    @Test
    void shouldReturnAttributedCurrentWeatherFromFixedProviders() throws Exception {
        RestClient.Builder geocodingBuilder = RestClient.builder().baseUrl("https://geo.test");
        RestClient.Builder forecastBuilder = RestClient.builder().baseUrl("https://weather.test");
        MockRestServiceServer geocodingServer = MockRestServiceServer.bindTo(geocodingBuilder).build();
        MockRestServiceServer forecastServer = MockRestServiceServer.bindTo(forecastBuilder).build();
        geocodingServer.expect(requestTo(containsString("/v1/search")))
                .andRespond(withSuccess("""
                        {"results":[{"name":"合肥","admin1":"安徽","latitude":31.82,"longitude":117.22}]}
                        """, APPLICATION_JSON));
        forecastServer.expect(requestTo(containsString("/v1/forecast")))
                .andRespond(withSuccess("""
                        {"current":{"time":"2026-08-12T16:45","temperature_2m":31.2,
                        "apparent_temperature":34.0,"relative_humidity_2m":64,
                        "precipitation":0.0,"weather_code":1,"wind_speed_10m":9.5}}
                        """, APPLICATION_JSON));
        ToolExecutionProperties properties = new ToolExecutionProperties(
                300,
                new ToolExecutionProperties.Weather(true, "https://geo.test", "https://weather.test", 1, 1)
        );
        CurrentWeatherTool tool = new CurrentWeatherTool(
                geocodingBuilder.build(), forecastBuilder.build(), properties
        );
        var arguments = new ObjectMapper().readTree("{\"city\":\"合肥\"}");

        ToolExecutionResult result = tool.execute(
                new ToolExecutionContext("user-1", "dev", UUID.randomUUID()), arguments
        );

        assertThat(result.answer()).contains("安徽 合肥", "31.2°C", "Open-Meteo", "2026-08-12T16:45");
        assertThat(result.auditData()).containsEntry("provider", "Open-Meteo");
        geocodingServer.verify();
        forecastServer.verify();
    }
}
