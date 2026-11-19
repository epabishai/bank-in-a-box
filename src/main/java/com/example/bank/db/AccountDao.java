package com.example.bank.db;

import com.example.bank.core.Account;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.Optional;
import java.util.UUID;

@RegisterRowMapper(AccountMapper.class)
public interface AccountDao {
    @SqlUpdate("""
            INSERT INTO accounts (id, name, email, balance_cents)
            VALUES (:id, :name, :email, :balanceCents)
            """)
    int insert(@Bind("id") UUID id, @Bind("name") String name, @Bind("email") String email,
               @Bind("balanceCents") long balanceCents);

    @SqlQuery("SELECT id, name, email, balance_cents, created_at FROM accounts WHERE id = :id")
    Optional<Account> findById(@Bind("id") UUID id);

    @SqlUpdate("""
            UPDATE accounts SET balance_cents = balance_cents + :amount
            WHERE id = :id AND :amount > 0
            """)
    int credit(@Bind("id") UUID id, @Bind("amount") long amountCents);

    @SqlUpdate("""
            UPDATE accounts SET balance_cents = balance_cents - :amount
            WHERE id = :id AND :amount > 0 AND balance_cents >= :amount
            """)
    int debit(@Bind("id") UUID id, @Bind("amount") long amountCents);
}
