package com.orabank.backend.dto;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 72;
    public static final String PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$";
    public static final String MESSAGE =
            "Le mot de passe doit contenir au moins une minuscule, une majuscule et un chiffre.";
    public static final String SIZE_MESSAGE = "Le mot de passe doit contenir entre 8 et 72 caractères.";
    public static final int MAX_BYTES = 72;
    public static final String BYTES_MESSAGE =
            "Le mot de passe est trop long : chaque lettre accentuée compte pour deux caractères (72 au plus).";

    private PasswordPolicy() {
    }
}
