import { useState } from "react";

export default function TransactionForm({ type, mutation, busy }) {
  const [amount, setAmount] = useState("");
  const [note, setNote] = useState("");
  const [validationError, setValidationError] = useState("");
  const label = type === "deposit" ? "Deposit" : "Withdraw";
  const error = validationError || mutation.error?.message;

  function submit(event) {
    event.preventDefault();
    mutation.reset();
    setValidationError("");
    if (!/^\d+(\.\d{1,2})?$/.test(amount) || !Number.isFinite(Number(amount))) {
      setValidationError("Enter an amount greater than 0 with no more than 2 decimal places.");
      return;
    }
    const amountCents = Math.round(Number(amount) * 100);
    if (!Number.isSafeInteger(amountCents) || amountCents <= 0) {
      setValidationError("Enter an amount greater than 0 with no more than 2 decimal places.");
      return;
    }
    mutation.mutate({ amount: amountCents / 100, amountCents, note: note.trim() || undefined });
  }

  return (
    <form onSubmit={submit} noValidate className="rounded-md border border-border bg-surface p-5">
      <h2 className="text-lg font-bold">{label}</h2>
      <label htmlFor={`${type}-amount`} className="mt-4 block text-sm font-medium">Amount</label>
      <input
        id={`${type}-amount`}
        name="amount"
        type="text"
        inputMode="decimal"
        value={amount}
        onChange={(event) => {
          setAmount(event.target.value);
          setValidationError("");
          mutation.reset();
        }}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `${type}-error` : undefined}
        className="mt-2 w-full rounded-sm border border-border bg-background px-3 py-3 font-mono text-text focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
      />
      <label htmlFor={`${type}-note`} className="mt-4 block text-sm font-medium">Note (optional)</label>
      <input
        id={`${type}-note`}
        name="note"
        value={note}
        onChange={(event) => setNote(event.target.value)}
        className="mt-2 w-full rounded-sm border border-border bg-background px-3 py-3 text-text focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
      />
      {error && <p id={`${type}-error`} className="mt-3 text-sm text-danger" role="alert">{error}</p>}
      <button
        type="submit"
        disabled={busy}
        className="mt-5 w-full rounded-md bg-accent px-5 py-3 font-medium text-on-accent hover:underline disabled:cursor-not-allowed disabled:bg-background disabled:text-muted"
      >
        {mutation.isPending ? `${label}ing…` : label}
      </button>
    </form>
  );
}
