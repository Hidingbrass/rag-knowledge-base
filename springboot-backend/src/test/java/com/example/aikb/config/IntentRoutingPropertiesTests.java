package com.example.aikb.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntentRoutingPropertiesTests {

    @Test
    void shouldAcceptProbabilityThreshold() {
        assertThat(new IntentRoutingProperties(0.80).minConfidence()).isEqualTo(0.80);
    }

    @Test
    void shouldRejectOutOfRangeThreshold() {
        assertThatThrownBy(() -> new IntentRoutingProperties(1.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0 到 1");
    }
}
