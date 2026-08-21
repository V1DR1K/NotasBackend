package com.tomas.cuaderno.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.tomas.cuaderno.configuration.*;

@Component
public class InitialDataSeeder implements CommandLineRunner {
    private final UserRepository users; private final PasswordEncoder encoder; private final ConfigItemRepository config;
    @Value("${cuaderno.initial-user.username}") private String username;
    @Value("${cuaderno.initial-user.password}") private String password;
    public InitialDataSeeder(UserRepository users, PasswordEncoder encoder, ConfigItemRepository config) { this.users = users; this.encoder = encoder; this.config = config; }
    @Transactional public void run(String... args) {
        User user = users.findByUsername(username).orElseGet(() -> {
            if ("tomas tomas".equals(username)) return users.findByUsername("tomas").map(legacy -> { legacy.setUsername(username); return users.save(legacy); }).orElse(null);
            return null;
        });
        if (user == null) { user = new User(); user.setUsername(username); user.setPasswordHash(encoder.encode(password)); user.setRole("ADMIN"); user = users.save(user); }
        seed(user.getId());
    }
    private void seed(java.util.UUID owner) {
        option(owner, ConfigKind.DAY_STATUS, "green", "Verde", "🟢", 0, true);
        option(owner, ConfigKind.DAY_STATUS, "yellow", "Amarillo", "🟡", 1, true);
        option(owner, ConfigKind.DAY_STATUS, "red", "Rojo", "🔴", 2, true);
        option(owner, ConfigKind.DAY_FEELING, "tranquilo", "Tranquilo", null, 0, true);
        option(owner, ConfigKind.DAY_FEELING, "con_energia", "Con energía", null, 1, true);
        option(owner, ConfigKind.DAY_FEELING, "cansado", "Cansado", null, 2, true);
        option(owner, ConfigKind.DAY_FEELING, "ansioso", "Ansioso", null, 3, true);
        option(owner, ConfigKind.DAY_FEELING, "contento", "Contento", null, 4, true);
        option(owner, ConfigKind.DAY_FEELING, "enfocado", "Enfocado", null, 5, true);
        option(owner, ConfigKind.DAY_FEELING, "abrumado", "Abrumado", null, 6, true);
        option(owner, ConfigKind.DAY_FEELING, "motivado", "Motivado", null, 7, true);
        option(owner, ConfigKind.FINANCE_ITEM, "sueldo", "Sueldo", null, 0, true);
        option(owner, ConfigKind.FINANCE_ITEM, "inversion_pesos", "Inversion Pesos", null, 1, true);
        option(owner, ConfigKind.FINANCE_ITEM, "inversion_cripto", "Inversion Cripto", null, 2, true);
        option(owner, ConfigKind.FINANCE_ITEM, "pedidos_ya", "Pedidos Ya", null, 3, true);
        option(owner, ConfigKind.FINANCE_ITEM, "comida_afuera", "Comida Afuera", null, 4, true);
        option(owner, ConfigKind.FINANCE_ITEM, "supermercado_golosineria", "Supermercado / Golosineria", null, 5, true);
        option(owner, ConfigKind.FINANCE_ITEM, "nafta", "Nafta", null, 6, true);
        option(owner, ConfigKind.FINANCE_ITEM, "uber_didi", "Uber/Didi", null, 7, true);
        option(owner, ConfigKind.NOTE_CATEGORY, "ideas", "Ideas", null, 0, true);
        option(owner, ConfigKind.NOTE_CATEGORY, "personal", "Personal", null, 1, true);
        option(owner, ConfigKind.NOTE_CATEGORY, "work", "Trabajo", null, 2, true);
    }
    private void option(java.util.UUID owner, ConfigKind kind, String code, String label, String emoji, int order, boolean active) {
        if (config.existsByOwnerIdAndKindAndCodeIgnoreCaseAndDeletedAtIsNull(owner, kind, code)) return;
        ConfigItem item = new ConfigItem(); item.setOwnerId(owner); item.setKind(kind); item.setCode(code); item.setLabel(label); item.setEmoji(emoji); item.setSortOrder(order); item.setActive(active); config.save(item);
    }
}
