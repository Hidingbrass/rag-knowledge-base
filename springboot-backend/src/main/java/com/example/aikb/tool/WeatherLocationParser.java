package com.example.aikb.tool;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;

/** 只提取高置信中文城市名；缺少城市时交给用户澄清。 */
@Component
public class WeatherLocationParser {
    public Optional<String> parse(String question) {
        String normalized = Normalizer.normalize(question == null ? "" : question, Normalizer.Form.NFKC)
                .strip()
                .toLowerCase(Locale.ROOT);
        int weatherIndex = normalized.indexOf("天气");
        int temperatureIndex = normalized.indexOf("气温");
        int boundary = weatherIndex >= 0 ? weatherIndex : temperatureIndex;
        if (boundary < 0) {
            return Optional.empty();
        }
        String city = normalized.substring(0, boundary)
                .replaceAll("请|帮我|麻烦|告诉我|查询|查|看看|今天|现在|当前|实时|一下|这个|当地|的", "")
                .replaceAll("[^\\p{IsHan}]", "")
                .replaceFirst("市$", "")
                .strip();
        return city.length() >= 2 && city.length() <= 10
                ? Optional.of(city)
                : Optional.empty();
    }
}
