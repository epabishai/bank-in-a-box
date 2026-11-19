package com.example.bank.core;

import java.time.Instant;
import java.util.UUID;

public record Account(UUID id, String name, String email, Money balance, Instant createdAt) {
}
