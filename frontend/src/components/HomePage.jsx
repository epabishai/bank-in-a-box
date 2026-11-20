import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useRef, useState } from "react";
import apiClient from "../api/client.js";
import CreateAccountModal from "./CreateAccountModal.jsx";

export default function HomePage({ onNavigate }) {
  const [accountId, setAccountId] = useState("");
  const [searchTouched, setSearchTouched] = useState(false);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const createTriggerRef = useRef(null);
  const queryClient = useQueryClient();
  const [validationError, setValidationError] = useState("");
  const search = useMutation({
    mutationFn: async (id) => (await apiClient.get(`/accounts/${encodeURIComponent(id)}`)).data,
    onSuccess: (account) => {
      queryClient.setQueryData(["account", account.id], account);
      if (onNavigate) onNavigate(account.id);
      else window.location.assign(`/accounts/${encodeURIComponent(account.id)}`);
    },
  });
  const searchError = validationError || search.error?.error || search.error?.message || "";

  function openModal(event) {
    createTriggerRef.current = event.currentTarget;
    setIsModalOpen(true);
  }

  function submitSearch(event) {
    event.preventDefault();
    const value = accountId.trim();
    setSearchTouched(true);
    search.reset();
    setValidationError("");
    if (!value) {
      setValidationError("Enter your account ID.");
      return;
    }
    search.mutate(value);
  }

  return (
    <main className="relative min-h-screen bg-background px-4 py-6 text-text sm:px-6 sm:py-8">
      <header className="mx-auto flex max-w-6xl items-center justify-between">
        <p className="font-ui text-base font-bold tracking-wide">Bank in a Box</p>
      </header>

      <section className="mx-auto mt-16 max-w-xl sm:mt-24">
        <h1 className="text-center text-2xl font-bold sm:text-[28px]">Welcome back</h1>
        <p className="mt-2 text-center text-muted">Find your account</p>
        <form className="mt-8 space-y-4 sm:flex sm:items-end sm:gap-3 sm:space-y-0" onSubmit={submitSearch} noValidate>
          <div className="min-w-0 flex-1">
            <label htmlFor="account-id" className="mb-2 block text-sm font-medium">Account ID</label>
            <input
              id="account-id"
              name="accountId"
              value={accountId}
              onChange={(event) => {
                setAccountId(event.target.value);
                setValidationError("");
                search.reset();
              }}
              onBlur={() => setSearchTouched(true)}
              aria-invalid={Boolean(searchError && searchTouched)}
              aria-describedby={searchError ? "account-id-error" : undefined}
              className="w-full rounded-sm border border-border bg-surface px-3 py-3 font-mono text-text focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
            />
            {searchError && (
              <p id="account-id-error" className="mt-2 text-sm text-danger" role="status" aria-live="polite">
                {searchError}
              </p>
            )}
          </div>
          <button
            type="submit"
            disabled={search.isPending}
            className="w-full rounded-md bg-accent px-5 py-3 font-medium text-on-accent hover:underline disabled:cursor-not-allowed disabled:bg-surface disabled:text-muted sm:w-auto"
          >
            {search.isPending ? "Searching…" : "Search"}
          </button>
        </form>

        <button
          type="button"
          onClick={openModal}
          style={{ right: "max(24px, calc((100vw - 1152px) / 2))" }}
          className="absolute right-6 top-6 hidden rounded-md border border-border px-4 py-2 text-sm font-medium hover:border-accent focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent sm:inline-flex"
        >
          Create account
        </button>
        <div className="my-8 flex items-center gap-4 text-sm text-muted" aria-hidden="true">
          <span className="h-px flex-1 bg-border" /><span>or</span><span className="h-px flex-1 bg-border" />
        </div>
        <div className="flex flex-col items-start gap-4 sm:flex-row sm:items-center sm:justify-center">
          <p>New here?</p>
          <button
            type="button"
            onClick={openModal}
            className="w-full rounded-md border border-border px-5 py-3 font-medium hover:border-accent focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent sm:w-auto"
          >
            Create account
          </button>
        </div>
      </section>

      {isModalOpen && (
        <CreateAccountModal
          onClose={() => setIsModalOpen(false)}
          onCreated={(account) => {
            if (onNavigate) onNavigate(account.id);
            else window.location.assign(`/accounts/${encodeURIComponent(account.id)}`);
          }}
          returnFocusRef={createTriggerRef}
        />
      )}
    </main>
  );
}
