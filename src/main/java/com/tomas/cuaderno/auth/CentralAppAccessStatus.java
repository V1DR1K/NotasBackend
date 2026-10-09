package com.tomas.cuaderno.auth;

public enum CentralAppAccessStatus {
    NONE,
    PENDING,
    APPROVED,
    REJECTED;

    public boolean grantsAccess() {
        return this == APPROVED;
    }
}
