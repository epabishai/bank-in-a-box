import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import apiClient from "../api/client.js";

const LIMIT = 20;

const formatMoney = (amount) =>
  new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(Number(amount));

const transactionLabel = (type) =>
  ({
    DEPOSIT: "Deposit",
    WITHDRAWAL: "Withdrawal",
    TRANSFER_IN: "Transfer in",
    TRANSFER_OUT: "Transfer out",
  })[type] || type.replace("_", " ");

export default function TransactionHistory({ accountId }) {
  const [page, setPage] = useState(1);
  const history = useQuery({
    queryKey: ["transactions", accountId, page],
    queryFn: async () =>
      (await apiClient.get(`/accounts/${encodeURIComponent(accountId)}/transactions`, {
        params: { page, limit: LIMIT },
      })).data,
  });

  const totalPages = history.data ? Math.max(1, Math.ceil(history.data.total / history.data.limit)) : 1;

  return (
    <section className="mt-10" aria-labelledby="history-heading">
      <h2 id="history-heading" className="text-xl font-bold">Recent activity</h2>
      {history.isLoading ? (
        <div className="mt-4 space-y-2" aria-busy="true" aria-label="Loading transactions">
          {[0, 1, 2].map((item) => (
            <div key={item} className="h-20 rounded-md border border-border bg-surface" />
          ))}
        </div>
      ) : null}
      {history.isError && (
        <div className="mt-4 rounded-md border border-border bg-surface p-5" role="alert">
          <p className="text-danger">{history.error.message}</p>
          <button
            type="button"
            onClick={() => history.refetch()}
            className="mt-3 rounded-md border border-accent px-4 py-2 text-accent hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
          >
            Retry
          </button>
          {page > 1 && !history.data && (
            <nav className="mt-3" aria-label="Transaction history pagination">
              <button
                type="button"
                onClick={() => setPage((current) => Math.max(1, current - 1))}
                className="rounded-md border border-border px-4 py-2 text-text hover:border-accent hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
              >
                Previous
              </button>
            </nav>
          )}
        </div>
      )}
      {history.isLoading || !history.data ? null : history.data.items.length === 0 ? (
        <div className="mt-4 rounded-md border border-border bg-surface p-5">
          <p className="text-muted">
            {history.data.total === 0
              ? "No transactions yet — make your first deposit"
              : "No transactions on this page."}
          </p>
          {page > 1 && (
            <button
              type="button"
              onClick={() => setPage(1)}
              className="mt-3 rounded-md border border-accent px-4 py-2 text-accent hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
            >
              Back to first page
            </button>
          )}
        </div>
      ) : (
        <>
          <ul
            aria-label="Transaction history"
            className="mt-4 max-h-96 divide-y divide-border overflow-y-auto rounded-md border border-border bg-surface"
          >
            {history.data.items.map((transaction) => {
              const isIncoming = transaction.type === "DEPOSIT" || transaction.type === "TRANSFER_IN";
              const sign = isIncoming ? "+" : "-";
              return (
                <li key={transaction.id} className="flex items-center justify-between gap-4 p-4">
                  <div className="flex min-w-0 items-center gap-3">
                    <span
                      aria-hidden="true"
                      className={`text-lg ${isIncoming ? "text-success" : "text-danger"}`}
                    >
                      {isIncoming ? "↓" : "↑"}
                    </span>
                    <div className="min-w-0">
                      <p className="font-medium">{transactionLabel(transaction.type)}</p>
                      {transaction.note && <p className="truncate text-sm text-muted">{transaction.note}</p>}
                      <time className="text-xs text-muted" dateTime={transaction.createdAt}>
                        {new Date(transaction.createdAt).toLocaleString()}
                      </time>
                    </div>
                  </div>
                  <span className={`shrink-0 font-mono tabular-nums ${isIncoming ? "text-success" : "text-danger"}`}>
                    {sign}{formatMoney(transaction.amount)}
                  </span>
                </li>
              );
            })}
          </ul>
          <nav className="mt-4 flex items-center justify-between" aria-label="Transaction history pagination">
            <button
              type="button"
              disabled={page === 1}
              onClick={() => setPage((current) => Math.max(1, current - 1))}
              className="rounded-md border border-border px-4 py-2 text-text hover:border-accent hover:underline disabled:cursor-not-allowed disabled:text-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
            >
              Previous
            </button>
            <span className="text-sm text-muted">Page {page} of {totalPages}</span>
            <button
              type="button"
              disabled={page >= totalPages}
              onClick={() => setPage((current) => current + 1)}
              className="rounded-md border border-border px-4 py-2 text-text hover:border-accent hover:underline disabled:cursor-not-allowed disabled:text-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
            >
              Next
            </button>
          </nav>
        </>
      )}
    </section>
  );
}
