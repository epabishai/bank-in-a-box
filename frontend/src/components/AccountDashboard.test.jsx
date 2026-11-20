import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import apiClient from "../api/client.js";
import AccountDashboard from "./AccountDashboard.jsx";

const accountId = "account-123";
const originalAdapter = apiClient.defaults.adapter;

afterEach(() => {
  cleanup();
  apiClient.defaults.adapter = originalAdapter;
});

function renderDashboard() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={queryClient}>
      <AccountDashboard accountId={accountId} />
    </QueryClientProvider>,
  );
  return queryClient;
}

function response(config, data, status = 200) {
  return { config, data, status, statusText: "OK", headers: {} };
}

function account() {
  return { id: accountId, name: "Ada Lovelace", email: "ada@example.com", balance: 100, createdAt: "2026-01-01T00:00:00Z" };
}

describe("AccountDashboard", () => {
  it("optimistically updates the balance and rolls it back with the server message on failure", async () => {
    let rejectWithdrawal;
    apiClient.defaults.adapter = async (config) => {
      if (config.method === "get" && config.url.endsWith("/transactions")) {
        return response(config, { items: [], page: 1, limit: 20, total: 0 });
      }
      if (config.method === "get") return response(config, account());
      if (config.url.endsWith("/withdraw")) {
        return new Promise((_resolve, reject) => {
          rejectWithdrawal = () => reject({
            config,
            response: { status: 422, data: { error: "Insufficient funds for this withdrawal.", code: "INSUFFICIENT_FUNDS" } },
          });
        });
      }
      return response(config, {});
    };
    const queryClient = renderDashboard();
    await screen.findByText("$100.00");

    fireEvent.change(screen.getByLabelText("Amount", { selector: "#withdraw-amount" }), { target: { value: "25.00" } });
    fireEvent.click(screen.getByRole("button", { name: "Withdraw" }));
    await waitFor(() => expect(screen.getByText("$75.00")).toBeInTheDocument());
    expect(screen.getByRole("button", { name: "Withdrawing…" })).toBeDisabled();
    rejectWithdrawal();

    expect(await screen.findByText("Insufficient funds for this withdrawal.")).toBeInTheDocument();
    expect(screen.getByText("$100.00")).toBeInTheDocument();
    await waitFor(() => {
      expect(queryClient.getQueryData(["account", accountId]).balance).toBe(100);
      expect(screen.getByRole("button", { name: "Withdraw" })).toBeEnabled();
    });
    expect(queryClient.getQueryData(["transactions", accountId]).items).toHaveLength(0);
  });

  it("shows 422 insufficient funds inline on the withdraw form and refetches queries", async () => {
    let withdrawalAttempted = false;
    let accountRequests = 0;
    let historyRequests = 0;
    apiClient.defaults.adapter = async (config) => {
      if (config.method === "get" && config.url.endsWith("/transactions")) {
        historyRequests += 1;
        return response(config, { items: [], page: 1, limit: 20, total: 0 });
      }
      if (config.method === "get") {
        accountRequests += 1;
        return response(config, account());
      }
      if (config.url.endsWith("/withdraw")) {
        withdrawalAttempted = true;
        return Promise.reject({
          config,
          response: { status: 422, data: { error: "Not enough money.", code: "INSUFFICIENT_FUNDS" } },
        });
      }
      return response(config, {});
    };
    renderDashboard();
    await screen.findByText("$100.00");
    fireEvent.change(screen.getByLabelText("Amount", { selector: "#withdraw-amount" }), { target: { value: "150" } });
    fireEvent.click(screen.getByRole("button", { name: "Withdraw" }));

    const error = await screen.findByRole("alert");
    expect(error).toHaveTextContent("Not enough money.");
    expect(screen.getByLabelText("Amount", { selector: "#withdraw-amount" })).toHaveAttribute("aria-describedby", "withdraw-error");
    await waitFor(() => expect(withdrawalAttempted).toBe(true));
    await waitFor(() => {
      expect(accountRequests).toBeGreaterThan(1);
      expect(historyRequests).toBeGreaterThan(1);
    });
  });

  it("validates an amount before submitting a deposit", async () => {
    const adapter = vi.fn(async (config) => {
      if (config.method === "get" && config.url.endsWith("/transactions")) {
        return response(config, { items: [], page: 1, limit: 20, total: 0 });
      }
      return response(config, account());
    });
    apiClient.defaults.adapter = adapter;
    renderDashboard();
    await screen.findByText("$100.00");
    fireEvent.change(screen.getByLabelText("Amount", { selector: "#deposit-amount" }), { target: { value: "1.001" } });
    fireEvent.click(screen.getByRole("button", { name: "Deposit" }));

    expect(screen.getByText(/no more than 2 decimal places/)).toBeInTheDocument();
    expect(adapter).toHaveBeenCalledTimes(2);
  });
});
