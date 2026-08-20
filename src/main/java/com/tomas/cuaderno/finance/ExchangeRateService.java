package com.tomas.cuaderno.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tomas.cuaderno.common.errors.BadRequestException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Service public class ExchangeRateService {
    private final ExchangeRateRepository repository; private final ExchangeRateProperties properties; private final ObjectMapper mapper; private final RestClient client;
    public ExchangeRateService(ExchangeRateRepository repository, ExchangeRateProperties properties, ObjectMapper mapper) { this.repository = repository; this.properties = properties; this.mapper = mapper; SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory(); factory.setConnectTimeout(properties.getTimeoutMs()); factory.setReadTimeout(properties.getTimeoutMs()); this.client = RestClient.builder().requestFactory(factory).build(); }
    public FinanceDtos.ExchangeRateResponse usd(UUID owner) {
        if (properties.getProviderUrl() != null && !properties.getProviderUrl().isBlank()) { try { return parse(client.get().uri(properties.getProviderUrl()).retrieve().body(String.class), "provider"); } catch (RuntimeException ignored) { /* fallback is intentional */ } }
        ExchangeRate persisted = repository.findByOwnerIdAndCurrency(owner, "USD").orElse(null);
        if (persisted != null) return response(persisted);
        return new FinanceDtos.ExchangeRateResponse("USD", properties.getFallback(), properties.getFallback(), properties.getFallback(), Instant.now(), "configured-fallback");
    }
    @Transactional public FinanceDtos.ExchangeRateResponse setFallback(UUID owner, FinanceDtos.FallbackRequest request) {
        ExchangeRate item = repository.findByOwnerIdAndCurrency(owner, "USD").orElseGet(ExchangeRate::new); item.setOwnerId(owner); item.setBuy(request.buy()); item.setSell(request.sell()); item.setFetchedAt(Instant.now()); item.setSource("manual-fallback"); return response(repository.save(item));
    }
    public BigDecimal average(UUID owner) { return usd(owner).average(); }
    private FinanceDtos.ExchangeRateResponse parse(String body, String source) {
        try { JsonNode root = mapper.readTree(body); BigDecimal numeric = root != null && root.isNumber() ? root.decimalValue() : null; BigDecimal buy = numeric != null ? numeric : number(root, "buy"); BigDecimal sell = numeric != null ? numeric : number(root, "sell"); if (buy == null) buy = number(root, "rate"); if (buy == null) buy = number(root, "usd"); if (sell == null) sell = buy; if (buy == null || sell == null) throw new IllegalArgumentException(); BigDecimal average = buy.add(sell).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP); return new FinanceDtos.ExchangeRateResponse("USD", buy, sell, average, Instant.now(), source); } catch (Exception ex) { throw new BadRequestException("Exchange rate provider returned invalid data"); }
    }
    private BigDecimal number(JsonNode root, String name) { return root.path(name).isNumber() ? root.path(name).decimalValue() : null; }
    private FinanceDtos.ExchangeRateResponse response(ExchangeRate item) { return new FinanceDtos.ExchangeRateResponse(item.getCurrency(), item.getBuy(), item.getSell(), item.getBuy().add(item.getSell()).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP), item.getFetchedAt(), item.getSource()); }
}
