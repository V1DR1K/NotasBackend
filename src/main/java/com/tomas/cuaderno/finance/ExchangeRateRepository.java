package com.tomas.cuaderno.finance;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID> { Optional<ExchangeRate> findByOwnerIdAndCurrency(UUID ownerId, String currency); }
