package com.orabank.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordSetupRequest(
        @NotBlank(message = "Le lien est incomplet.")
        @Size(max = 1000, message = "Le lien est invalide.")
        String token,

        @NotBlank(message = "Le mot de passe est obligatoire.")
        @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH,
                message = PasswordPolicy.SIZE_MESSAGE)
        @Pattern(regexp = PasswordPolicy.PATTERN, message = PasswordPolicy.MESSAGE)
        String password) {

    @Override
    public String toString() {
        return "PasswordSetupRequest(****)";
    }
}
