package com.example.bank.db;

import com.example.bank.core.Transaction;
import com.example.bank.core.TransactionType;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RegisterRowMapper(TransactionMapper.class)
public interface TransactionDao {
    @SqlUpdate("""
            INSERT INTO transactions (id, account_id, type, amount_cents, note)
            VALUES (:id, :accountId, :type, :amountCents, :note)
            """)
    int insert(@Bind("id") UUID id, @Bind("accountId") UUID accountId,
               @Bind("type") TransactionType type, @Bind("amountCents") long amountCents,
               @Bind("note") String note);

    @SqlQuery("""
            SELECT id, account_id, type, amount_cents, note, created_at
            FROM transactions WHERE id = :id
            """)
    Optional<Transaction> findById(@Bind("id") UUID id);

    @SqlQuery("""
            SELECT id, account_id, type, amount_cents, note, created_at
            FROM transactions WHERE account_id = :accountId
            ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset
            """)
    List<Transaction> listByAccount(@Bind("accountId") UUID accountId,
                                    @Bind("limit") int limit, @Bind("offset") long offset);

    @SqlQuery("SELECT count(*) FROM transactions WHERE account_id = :accountId")
    long countByAccount(@Bind("accountId") UUID accountId);
}
