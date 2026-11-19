package com.example.bank.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record CreateAccountRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        @JsonProperty("initial_deposit") BigDecimal initialDeposit) {
}
