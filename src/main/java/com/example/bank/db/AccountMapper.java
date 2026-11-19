package com.example.bank.db;

import com.example.bank.core.Account;
import com.example.bank.core.Money;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class AccountMapper implements RowMapper<Account> {
    @Override
    public Account map(ResultSet rs, StatementContext ctx) throws SQLException {
        return new Account(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("email"),
                new Money(rs.getLong("balance_cents")), rs.getTimestamp("created_at").toInstant());
    }
}
