package com.example.bank.db;

import com.example.bank.core.Money;
import com.example.bank.core.Transaction;
import com.example.bank.core.TransactionType;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class TransactionMapper implements RowMapper<Transaction> {
    @Override
    public Transaction map(ResultSet rs, StatementContext ctx) throws SQLException {
        return new Transaction(rs.getObject("id", UUID.class), rs.getObject("account_id", UUID.class),
                TransactionType.valueOf(rs.getString("type")), new Money(rs.getLong("amount_cents")),
                rs.getString("note"), rs.getTimestamp("created_at").toInstant());
    }
}
