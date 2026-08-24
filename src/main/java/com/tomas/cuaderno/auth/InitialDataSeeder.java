package com.tomas.cuaderno.auth;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.tomas.cuaderno.configuration.*;
import com.tomas.cuaderno.finance.*;
import java.math.BigDecimal;
import java.time.Instant;

@Component
public class InitialDataSeeder implements CommandLineRunner {
    private final UserRepository users; private final ConfigItemRepository config; private final FinanceAccountRepository accounts;
    public InitialDataSeeder(UserRepository users, ConfigItemRepository config, FinanceAccountRepository accounts) { this.users = users; this.config = config; this.accounts = accounts; }
    @Transactional public void run(String... args) {
        users.findAll().stream().filter(user -> user.getAuthUserId() != null).forEach(this::seedFor);
    }
    @Transactional public void seedFor(User user) { if (user.getAuthUserId() != null) { seed(user.getId()); seedAccounts(user); } }
    private void seedAccounts(User user) {
        if (!"tomas".equalsIgnoreCase(user.getUsername())) return;
        account(user.getId(), "mercadopago", "MercadoPago / Caja de ahorro", FinanceAccountType.CASH, "58938.11", "18.5", FinanceAccountGrowthMode.DAILY_TNA);
        account(user.getId(), "inversiones_pesos", "Inversiones en pesos", FinanceAccountType.INVESTMENT, "800000", "0", FinanceAccountGrowthMode.MANUAL);
        account(user.getId(), "crypto", "Crypto", FinanceAccountType.CRYPTO, "6206454.61", "0", FinanceAccountGrowthMode.MANUAL);
    }
    private void account(java.util.UUID owner, String code, String label, FinanceAccountType type, String balance, String rate, FinanceAccountGrowthMode mode) {
        if (accounts.findByOwnerIdAndCodeIgnoreCaseAndDeletedAtIsNull(owner, code).isPresent()) return;
        FinanceAccount account = new FinanceAccount(); account.setOwnerId(owner); account.setCode(code); account.setLabel(label); account.setType(type); account.setBalanceArs(new BigDecimal(balance)); account.setAnnualRatePercent(new BigDecimal(rate)); account.setGrowthMode(mode); account.setBalanceAsOf(Instant.now()); account.setActive(true); accounts.save(account);
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
        option(owner, ConfigKind.DAY_FEELING, "triste", "Triste", null, 5, true);
        option(owner, ConfigKind.DAY_FEELING, "estresado", "Estresado", null, 6, true);
        option(owner, ConfigKind.DAY_FEELING, "agradecido", "Agradecido", null, 7, true);
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
        option(owner, ConfigKind.NOTE_CATEGORY, "projects", "Proyectos", null, 3, true);
        option(owner, ConfigKind.NOTE_CATEGORY, "goals", "Objetivos", null, 4, true);
        option(owner, ConfigKind.NOTE_CATEGORY, "health", "Salud", null, 5, true);
        option(owner, ConfigKind.NOTE_CATEGORY, "learning", "Aprendizajes", null, 6, true);
        option(owner, ConfigKind.NOTE_CATEGORY, "reminders", "Recordatorios", null, 7, true);
        option(owner, ConfigKind.NOTE_CATEGORY, "important", "Importante", null, 8, true);
        option(owner, ConfigKind.NOTE_CATEGORY, "inspiration", "Inspiración", null, 9, true);
    }
    private void option(java.util.UUID owner, ConfigKind kind, String code, String label, String emoji, int order, boolean active) {
        if (config.existsByOwnerIdAndKindAndCodeIgnoreCaseAndDeletedAtIsNull(owner, kind, code)) return;
        ConfigItem item = new ConfigItem(); item.setOwnerId(owner); item.setKind(kind); item.setCode(code); item.setLabel(label); item.setEmoji(emoji); item.setSortOrder(order); item.setActive(active); config.save(item);
    }
}
