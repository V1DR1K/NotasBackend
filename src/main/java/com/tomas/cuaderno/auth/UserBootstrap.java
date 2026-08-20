package com.tomas.cuaderno.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.tomas.cuaderno.configuration.*;

@Component
public class UserBootstrap implements CommandLineRunner {
    private final UserRepository users; private final PasswordEncoder encoder; private final ConfigItemRepository config;
    @Value("${cuaderno.initial-user.username}") private String username;
    @Value("${cuaderno.initial-user.password}") private String password;
    public UserBootstrap(UserRepository users, PasswordEncoder encoder, ConfigItemRepository config) { this.users = users; this.encoder = encoder; this.config = config; }
    @Transactional public void run(String... args) {
        User user = users.findByUsername(username).orElseGet(() -> { User created = new User(); created.setUsername(username); created.setPasswordHash(encoder.encode(password)); created.setRole("ADMIN"); return users.save(created); });
        seed(user.getId());
    }
    private void seed(java.util.UUID owner) {
        option(owner, ConfigKind.DAY_STATUS, "green", "Verde", "🟢", 0, true);
        option(owner, ConfigKind.DAY_STATUS, "yellow", "Amarillo", "🟡", 1, true);
        option(owner, ConfigKind.DAY_STATUS, "red", "Rojo", "🔴", 2, true);
        option(owner, ConfigKind.FINANCE_CONCEPT, "monthly_payment", "Pago mensual", null, 0, true);
        option(owner, ConfigKind.FINANCE_CONCEPT, "freelance", "Trabajo freelance", null, 1, true);
        option(owner, ConfigKind.FINANCE_CONCEPT, "weekly_purchase", "Compra semanal", null, 2, true);
        option(owner, ConfigKind.FINANCE_CONCEPT, "fuel", "Combustible", null, 3, true);
        option(owner, ConfigKind.FINANCE_CONCEPT, "usd_purchase", "Compra de dólares", null, 4, true);
        option(owner, ConfigKind.FINANCE_CONCEPT, "investment_fund", "Fondo de inversión", null, 5, true);
        option(owner, ConfigKind.FINANCE_CONCEPT, "transfer", "Transferencia", null, 6, true);
        option(owner, ConfigKind.FINANCE_CONCEPT, "other", "Otro", null, 7, true);
        option(owner, ConfigKind.FINANCE_CATEGORY, "work", "Trabajo", null, 0, true);
        option(owner, ConfigKind.FINANCE_CATEGORY, "extra", "Extra", null, 1, true);
        option(owner, ConfigKind.FINANCE_CATEGORY, "food", "Comida", null, 2, true);
        option(owner, ConfigKind.FINANCE_CATEGORY, "mobility", "Movilidad", null, 3, true);
        option(owner, ConfigKind.FINANCE_CATEGORY, "dollars", "Dólares", null, 4, true);
        option(owner, ConfigKind.FINANCE_CATEGORY, "market", "Mercado", null, 5, true);
        option(owner, ConfigKind.FINANCE_CATEGORY, "home", "Hogar", null, 6, true);
        option(owner, ConfigKind.FINANCE_CATEGORY, "leisure", "Ocio", null, 7, true);
        option(owner, ConfigKind.FINANCE_CATEGORY, "health", "Salud", null, 8, true);
        option(owner, ConfigKind.FINANCE_CATEGORY, "other", "Otro", null, 9, true);
        option(owner, ConfigKind.NOTE_CATEGORY, "ideas", "Ideas", null, 0, true);
        option(owner, ConfigKind.NOTE_CATEGORY, "personal", "Personal", null, 1, true);
        option(owner, ConfigKind.NOTE_CATEGORY, "work", "Trabajo", null, 2, true);
    }
    private void option(java.util.UUID owner, ConfigKind kind, String code, String label, String emoji, int order, boolean active) {
        if (config.existsByOwnerIdAndKindAndCodeIgnoreCaseAndDeletedAtIsNull(owner, kind, code)) return;
        ConfigItem item = new ConfigItem(); item.setOwnerId(owner); item.setKind(kind); item.setCode(code); item.setLabel(label); item.setEmoji(emoji); item.setSortOrder(order); item.setActive(active); config.save(item);
    }
}
