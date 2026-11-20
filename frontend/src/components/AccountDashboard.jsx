import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import apiClient from "../api/client.js";
import TransactionForm from "./TransactionForm.jsx";

const formatMoney = (amount) =>
  new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(Number(amount));

const toCents = (amount) => Math.round(Number(amount) * 100);
const fromCents = (cents) => (cents / 100).toFixed(2);
const accountKey = (id) => ["account", id];
const historyKey = (id) => ["transactions", id];

export default function AccountDashboard({ accountId }) {
  const queryClient = useQueryClient();
  const account = useQuery({
    queryKey: accountKey(accountId),
    queryFn: async () => (await apiClient.get(`/accounts/${encodeURIComponent(accountId)}`)).data,
  });
  const history = useQuery({
    queryKey: historyKey(accountId),
    queryFn: async () =>
      (await apiClient.get(`/accounts/${encodeURIComponent(accountId)}/transactions`, {
        params: { page: 1, limit: 20 },
      })).data,
  });
  const mutateTransaction = (type) => ({
    mutationFn: async ({ amount, note }) =>
      (await apiClient.post(`/accounts/${encodeURIComponent(accountId)}/${type}`, { amount, note })).data,
    onMutate: async ({ amountCents, note }) => {
      await Promise.all([
        queryClient.cancelQueries({ queryKey: accountKey(accountId) }),
        queryClient.cancelQueries({ queryKey: historyKey(accountId) }),
      ]);
      const previousAccount = queryClient.getQueryData(accountKey(accountId));
      const previousHistory = queryClient.getQueryData(historyKey(accountId));
      if (previousAccount) {
        const change = type === "deposit" ? amountCents : -amountCents;
        queryClient.setQueryData(accountKey(accountId), {
          ...previousAccount,
          balance: fromCents(toCents(previousAccount.balance) + change),
        });
      }
      if (previousHistory) {
        queryClient.setQueryData(historyKey(accountId), {
          ...previousHistory,
          items: [{
            id: `pending-${Date.now()}`,
            accountId,
            type: type === "deposit" ? "DEPOSIT" : "WITHDRAWAL",
            amount: fromCents(amountCents),
            note,
            createdAt: new Date().toISOString(),
          }, ...previousHistory.items],
          total: previousHistory.total + 1,
        });
      }
      return { previousAccount, previousHistory };
    },
    onError: (_error, _variables, context) => {
      if (context?.previousAccount) queryClient.setQueryData(accountKey(accountId), context.previousAccount);
      if (context?.previousHistory) queryClient.setQueryData(historyKey(accountId), context.previousHistory);
    },
    onSettled: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: accountKey(accountId) }),
        queryClient.invalidateQueries({ queryKey: historyKey(accountId) }),
      ]);
    },
  });
  const deposit = useMutation(mutateTransaction("deposit"));
  const withdraw = useMutation(mutateTransaction("withdraw"));
  const busy = deposit.isPending || withdraw.isPending;

  if (account.isLoading) {
    return <main className="mx-auto min-h-screen max-w-6xl px-4 py-8 text-text" aria-busy="true">
      <div className="balance-shimmer h-32 rounded-md bg-surface" />
    </main>;
  }
  if (account.isError) {
    return <main className="mx-auto max-w-6xl px-4 py-8 text-text" role="alert">
      {account.error.message}
    </main>;
  }

  return (
    <main className="min-h-screen bg-background px-4 py-6 text-text sm:px-6 sm:py-8">
      <header className="mx-auto flex max-w-6xl items-center justify-between">
        <a href="/" className="font-bold tracking-wide hover:text-accent">Bank in a Box</a>
        <span className="font-mono text-xs text-muted sm:text-sm">{account.data.id}</span>
      </header>
      <section className="mx-auto mt-10 max-w-6xl">
        <p className="text-sm font-medium text-muted">Available balance</p>
        <h1 className={`balance-amount mt-2 text-4xl font-extrabold tabular-nums sm:text-6xl ${account.isFetching ? "balance-shimmer" : ""}`}>
          {formatMoney(account.data.balance)}
        </h1>
        <p className="mt-2 text-muted">{account.data.name}</p>

        <section className="mt-10 grid grid-cols-1 gap-5 md:grid-cols-2" aria-label="Account transactions">
          <TransactionForm type="deposit" mutation={deposit} busy={busy} />
          <TransactionForm type="withdraw" mutation={withdraw} busy={busy} />
        </section>

        <section className="mt-10" aria-labelledby="history-heading">
          <h2 id="history-heading" className="text-xl font-bold">Recent activity</h2>
          {history.isLoading ? (
            <p className="mt-4 text-muted" aria-busy="true">Loading transactions…</p>
          ) : history.isError ? (
            <p className="mt-4 text-danger" role="alert">{history.error.message}</p>
          ) : history.data.items.length === 0 ? (
            <p className="mt-4 rounded-md border border-border bg-surface p-5 text-muted">
              No transactions yet — make your first deposit
            </p>
          ) : (
            <ul className="mt-4 max-h-96 divide-y divide-border overflow-y-auto rounded-md border border-border bg-surface">
              {history.data.items.map((transaction) => (
                <li key={transaction.id} className="flex items-center justify-between gap-4 p-4">
                  <div className="flex min-w-0 items-center gap-3">
                    <span aria-hidden="true" className="text-lg text-accent">
                      {transaction.type === "DEPOSIT" || transaction.type === "TRANSFER_IN" ? "↓" : "↑"}
                    </span>
                    <div className="min-w-0">
                      <p className="font-medium">{transaction.type.replace("_", " ")}</p>
                      {transaction.note && <p className="truncate text-sm text-muted">{transaction.note}</p>}
                      <time className="text-xs text-muted" dateTime={transaction.createdAt}>
                        {new Date(transaction.createdAt).toLocaleString()}
                      </time>
                    </div>
                  </div>
                  <span className="shrink-0 font-mono tabular-nums">{formatMoney(transaction.amount)}</span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </section>
    </main>
  );
}
