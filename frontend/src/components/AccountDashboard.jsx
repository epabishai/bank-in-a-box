import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import apiClient from "../api/client.js";
import TransactionForm from "./TransactionForm.jsx";
import TransactionHistory from "./TransactionHistory.jsx";

const formatMoney = (amount) =>
  new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(Number(amount));

const toCents = (amount) => Math.round(Number(amount) * 100);
const fromCents = (cents) => (cents / 100).toFixed(2);
const accountKey = (id) => ["account", id];
const historyKey = (id, page = 1) => ["transactions", id, page];

export default function AccountDashboard({ accountId }) {
  const queryClient = useQueryClient();
  const account = useQuery({
    queryKey: accountKey(accountId),
    queryFn: async () => (await apiClient.get(`/accounts/${encodeURIComponent(accountId)}`)).data,
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

        <TransactionHistory accountId={accountId} />
      </section>
    </main>
  );
}
