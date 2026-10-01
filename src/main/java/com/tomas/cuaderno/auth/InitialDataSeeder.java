package com.tomas.cuaderno.auth;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.tomas.cuaderno.configuration.*;
import com.tomas.cuaderno.finance.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Component
public class InitialDataSeeder implements CommandLineRunner {
    private final UserRepository users; private final ConfigItemRepository config; private final FinanceAccountRepository accounts;
    public InitialDataSeeder(UserRepository users, ConfigItemRepository config, FinanceAccountRepository accounts) { this.users = users; this.config = config; this.accounts = accounts; }
    @Transactional public void run(String... args) {
        users.findByAuthUserIdIsNotNull().forEach(this::seedFor);
    }
    @Transactional public void seedFor(User user) { if (user.getAuthUserId() != null) { seed(user.getId()); seedAccounts(user); } }
    private void seedAccounts(User user) {
        if (!"tomas".equalsIgnoreCase(user.getUsername())) return;
        Set<String> existing = accounts.findByOwnerIdAndDeletedAtIsNull(user.getId()).stream().map(account -> account.getCode().toLowerCase()).collect(java.util.stream.Collectors.toCollection(HashSet::new));
        account(user.getId(), existing, "mercadopago", "MercadoPago / Caja de ahorro", FinanceAccountType.CASH, "58938.11", "18.5", FinanceAccountGrowthMode.DAILY_TNA);
        account(user.getId(), existing, "inversiones_pesos", "Inversión en Pesos", FinanceAccountType.INVESTMENT, "800000", "0", FinanceAccountGrowthMode.MANUAL);
        account(user.getId(), existing, "crypto", "Inversión Cripto", FinanceAccountType.CRYPTO, "6206454.61", "0", FinanceAccountGrowthMode.MANUAL);
    }
    private void account(java.util.UUID owner, Set<String> existing, String code, String label, FinanceAccountType type, String balance, String rate, FinanceAccountGrowthMode mode) {
        if (!existing.add(code.toLowerCase())) return;
        FinanceAccount account = new FinanceAccount(); account.setOwnerId(owner); account.setCode(code); account.setLabel(label); account.setType(type); account.setBalanceArs(new BigDecimal(balance)); account.setAnnualRatePercent(new BigDecimal(rate)); account.setGrowthMode(mode); account.setBalanceAsOf(Instant.now()); account.setActive(true); accounts.save(account);
    }
    private void seed(java.util.UUID owner) {
        Set<String> existing = config.findByOwnerIdAndDeletedAtIsNull(owner).stream().map(item -> item.getKind() + ":" + item.getCode().toLowerCase()).collect(java.util.stream.Collectors.toCollection(HashSet::new));
        config.findByOwnerIdAndKindOrderBySortOrderAscCodeAsc(owner, ConfigKind.PROJECT).forEach(item -> existing.add(ConfigKind.PROJECT + ":" + item.getCode().toLowerCase()));
        config.findByOwnerIdAndKindOrderBySortOrderAscCodeAsc(owner, ConfigKind.CATEGORY).forEach(item -> existing.add(categoryKey(item.getProjectCode(), item.getCode())));
        option(owner, existing, ConfigKind.DAY_STATUS, "green", "Verde", "🟢", 0, true);
        option(owner, existing, ConfigKind.DAY_STATUS, "yellow", "Amarillo", "🟡", 1, true);
        option(owner, existing, ConfigKind.DAY_STATUS, "red", "Rojo", "🔴", 2, true);
        option(owner, existing, ConfigKind.DAY_FEELING, "tranquilo", "Tranquilo", null, 0, true);
        option(owner, existing, ConfigKind.DAY_FEELING, "con_energia", "Con energía", null, 1, true);
        option(owner, existing, ConfigKind.DAY_FEELING, "cansado", "Cansado", null, 2, true);
        option(owner, existing, ConfigKind.DAY_FEELING, "ansioso", "Ansioso", null, 3, true);
        option(owner, existing, ConfigKind.DAY_FEELING, "contento", "Contento", null, 4, true);
        option(owner, existing, ConfigKind.DAY_FEELING, "triste", "Triste", null, 5, true);
        option(owner, existing, ConfigKind.DAY_FEELING, "estresado", "Estresado", null, 6, true);
        option(owner, existing, ConfigKind.DAY_FEELING, "agradecido", "Agradecido", null, 7, true);
        option(owner, existing, ConfigKind.FINANCE_ITEM, "sueldo", "Sueldo", null, 0, true, FinanceItemType.INCOME);
        option(owner, existing, ConfigKind.FINANCE_ITEM, "otro", "Otro", null, 1, true, FinanceItemType.INCOME);
        option(owner, existing, ConfigKind.FINANCE_ITEM, "pedidos_ya", "Pedidos Ya", null, 2, true, FinanceItemType.EXPENSE);
        option(owner, existing, ConfigKind.FINANCE_ITEM, "comida_afuera", "Comida Afuera", null, 3, true, FinanceItemType.EXPENSE);
        option(owner, existing, ConfigKind.FINANCE_ITEM, "supermercado", "Supermercado", null, 4, true, FinanceItemType.EXPENSE);
        option(owner, existing, ConfigKind.FINANCE_ITEM, "nafta", "Nafta", null, 5, true, FinanceItemType.EXPENSE);
        option(owner, existing, ConfigKind.FINANCE_ITEM, "uber_didi", "Uber/Didi", null, 6, true, FinanceItemType.EXPENSE);
        option(owner, existing, ConfigKind.FINANCE_ITEM, "transferencia", "Transferencia", null, 7, true, FinanceItemType.TRANSFER);
        option(owner, existing, ConfigKind.PROJECT, "personal", "Personal", null, 0, true);
        option(owner, existing, ConfigKind.PROJECT, "facultad", "Facultad", null, 1, true);
        option(owner, existing, ConfigKind.PROJECT, "laburo", "Laburo", null, 2, true);
        category(owner, existing, "personal", "ideas", "Ideas", 0);
        category(owner, existing, "personal", "personal", "Personal", 1);
        category(owner, existing, "personal", "projects", "Proyectos", 2);
        category(owner, existing, "personal", "goals", "Objetivos", 3);
        category(owner, existing, "personal", "health", "Salud", 4);
        category(owner, existing, "personal", "learning", "Aprendizajes", 5);
        category(owner, existing, "personal", "reminders", "Recordatorios", 6);
        category(owner, existing, "personal", "important", "Importante", 7);
        category(owner, existing, "personal", "inspiration", "Inspiración", 8);
        category(owner, existing, "personal", "casa", "Casa", 9);
        category(owner, existing, "personal", "medico", "Médico", 10);
        category(owner, existing, "personal", "tramites", "Trámites", 11);
        category(owner, existing, "personal", "recordatorios", "Recordatorios", 12);
        category(owner, existing, "personal", "supermercado", "Supermercado", 13);
        category(owner, existing, "personal", "viajes", "Viajes", 14);
        category(owner, existing, "facultad", "facultad", "Facultad", 0);
        category(owner, existing, "laburo", "laburo", "Laburo", 0);
        category(owner, existing, "laburo", "work", "Trabajo", 1);
    }
    private void option(java.util.UUID owner, Set<String> existing, ConfigKind kind, String code, String label, String emoji, int order, boolean active) {
        option(owner, existing, kind, code, label, emoji, order, active, null);
    }
    private void option(java.util.UUID owner, Set<String> existing, ConfigKind kind, String code, String label, String emoji, int order, boolean active, FinanceItemType financeType) {
        if (!existing.add(kind + ":" + code.toLowerCase())) return;
        ConfigItem item = new ConfigItem(); item.setOwnerId(owner); item.setKind(kind); item.setCode(code); item.setLabel(label); item.setEmoji(emoji); item.setSortOrder(order); item.setActive(active); item.setFinanceType(financeType); config.save(item);
    }
    private void category(java.util.UUID owner, Set<String> existing, String projectCode, String code, String label, int order) {
        if (!existing.add(categoryKey(projectCode, code))) return;
        ConfigItem item = new ConfigItem(); item.setOwnerId(owner); item.setKind(ConfigKind.CATEGORY); item.setCode(code); item.setLabel(label); item.setProjectCode(projectCode); item.setSortOrder(order); item.setActive(true); config.save(item);
    }
    private String categoryKey(String projectCode, String code) { return ConfigKind.CATEGORY + ":" + projectCode.toLowerCase() + ":" + code.toLowerCase(); }
}
