package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.repository.BusinessCalendarRepository;
import com.alignedcardio.itsm.repository.SlaInstanceRepository;
import com.alignedcardio.itsm.repository.SlaPolicyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SlaEngineWiringTest.TestConfig.class)
class SlaEngineWiringTest {

    @Autowired
    private SlaEngine slaEngine;

    @Autowired
    private BusinessHoursCalculator businessHoursCalculator;

    @Test
    void slaEngineIsWiredWithBusinessHoursCalculator() {
        assertThat(slaEngine).isNotNull();
        assertThat(businessHoursCalculator).isNotNull();
        assertThat(ReflectionTestUtils.getField(slaEngine, "businessHoursCalculator"))
                .isSameAs(businessHoursCalculator);
    }

    @Configuration
    @ComponentScan(
            basePackages = "com.alignedcardio.itsm.service",
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = {BusinessHoursCalculator.class, SlaEngine.class}
            ),
            useDefaultFilters = false
    )
    static class TestConfig {

        @Bean
        SlaPolicyRepository slaPolicyRepository() {
            return mock(SlaPolicyRepository.class);
        }

        @Bean
        SlaInstanceRepository slaInstanceRepository() {
            return mock(SlaInstanceRepository.class);
        }

        @Bean
        BusinessCalendarRepository businessCalendarRepository() {
            return mock(BusinessCalendarRepository.class);
        }
    }
}
