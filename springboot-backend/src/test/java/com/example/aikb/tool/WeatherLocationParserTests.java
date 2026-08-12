package com.example.aikb.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherLocationParserTests {
    private final WeatherLocationParser parser = new WeatherLocationParser();

    @Test
    void shouldExtractExplicitChineseCity() {
        assertThat(parser.parse("今天合肥天气怎么样？")).contains("合肥");
        assertThat(parser.parse("查询杭州市天气")).contains("杭州");
    }

    @Test
    void shouldRequireClarificationWhenCityMissing() {
        assertThat(parser.parse("今天的天气怎么样？")).isEmpty();
        assertThat(parser.parse("帮我查天气")).isEmpty();
    }
}
