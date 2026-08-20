package com.tomas.cuaderno;

import com.tomas.cuaderno.finance.ExchangeRateProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration @EnableConfigurationProperties(ExchangeRateProperties.class)
public class NotasExchangeRateConfig {}
