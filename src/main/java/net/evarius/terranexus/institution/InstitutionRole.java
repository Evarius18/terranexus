package net.evarius.terranexus.institution;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public enum InstitutionRole {
    OWNER("owner", "Owner", 100, EnumSet.allOf(InstitutionPermission.class)),
    DIRECTOR("director", "Director", 90, EnumSet.of(
            InstitutionPermission.VIEW_MEMBERS, InstitutionPermission.MANAGE_EMPLOYEES,
            InstitutionPermission.MANAGE_ROLES, InstitutionPermission.VIEW_FINANCES,
            InstitutionPermission.MANAGE_FINANCES, InstitutionPermission.VIEW_AUDIT,
            InstitutionPermission.MANAGE_SALARIES, InstitutionPermission.MANAGE_SETTINGS,
            InstitutionPermission.MANAGE_TASKS, InstitutionPermission.MANAGE_INVOICES,
            InstitutionPermission.MANAGE_PROPERTY,
            InstitutionPermission.VIEW_TIME_CLOCK, InstitutionPermission.MANAGE_TIME_CLOCK_SETTINGS,
            InstitutionPermission.BANK_VIEW_ACCOUNTS, InstitutionPermission.BANK_CASH_OPERATIONS,
            InstitutionPermission.BANK_FREEZE_ACCOUNTS, InstitutionPermission.CENTRAL_BANK_VIEW,
            InstitutionPermission.CENTRAL_BANK_MONETARY_POLICY)),
    MANAGER("manager", "Manager", 60, EnumSet.of(
            InstitutionPermission.VIEW_MEMBERS, InstitutionPermission.MANAGE_EMPLOYEES,
            InstitutionPermission.VIEW_FINANCES, InstitutionPermission.VIEW_AUDIT,
            InstitutionPermission.MANAGE_TASKS,
            InstitutionPermission.VIEW_TIME_CLOCK, InstitutionPermission.MANAGE_TIME_CLOCK_SETTINGS,
            InstitutionPermission.BANK_VIEW_ACCOUNTS, InstitutionPermission.BANK_FREEZE_ACCOUNTS)),
    AUDITOR("auditor", "Auditor", 50, EnumSet.of(
            InstitutionPermission.VIEW_MEMBERS, InstitutionPermission.VIEW_FINANCES,
            InstitutionPermission.VIEW_AUDIT, InstitutionPermission.BANK_VIEW_ACCOUNTS,
            InstitutionPermission.CENTRAL_BANK_VIEW, InstitutionPermission.VIEW_TIME_CLOCK)),
    ACCOUNTANT("accountant", "Accountant", 50, EnumSet.of(
            InstitutionPermission.VIEW_MEMBERS, InstitutionPermission.VIEW_FINANCES,
            InstitutionPermission.MANAGE_FINANCES, InstitutionPermission.VIEW_AUDIT,
            InstitutionPermission.MANAGE_SALARIES, InstitutionPermission.MANAGE_INVOICES, InstitutionPermission.BANK_VIEW_ACCOUNTS,
            InstitutionPermission.BANK_CASH_OPERATIONS, InstitutionPermission.CENTRAL_BANK_VIEW,
            InstitutionPermission.VIEW_TIME_CLOCK)),
    HR("hr", "HR", 50, EnumSet.of(
            InstitutionPermission.VIEW_MEMBERS, InstitutionPermission.MANAGE_EMPLOYEES,
            InstitutionPermission.MANAGE_ROLES, InstitutionPermission.VIEW_TIME_CLOCK)),
    EMPLOYEE("employee", "Employee", 10, EnumSet.of(InstitutionPermission.VIEW_MEMBERS));

    private final String id;
    private final String label;
    private final int level;
    private final Set<InstitutionPermission> permissions;

    InstitutionRole(String id, String label, int level, Set<InstitutionPermission> permissions) {
        this.id = id;
        this.label = label;
        this.level = level;
        this.permissions = Set.copyOf(permissions);
    }
    public String id() { return id; }
    public String label() { return label; }
    public int level() { return level; }
    public boolean permits(InstitutionPermission permission) { return permissions.contains(permission); }
    public static InstitutionRole fromId(String value) {
        if (value == null) return EMPLOYEE;
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.equals("member")) return EMPLOYEE;
        return Arrays.stream(values()).filter(role -> role.id.equals(normalized)).findFirst().orElse(EMPLOYEE);
    }
}
