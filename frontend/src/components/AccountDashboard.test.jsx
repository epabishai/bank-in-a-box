import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
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
    expect(queryClient.getQueryData(["transactions", accountId, 1]).items).toHaveLength(0);
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

  it("renders signed, labeled transactions and paginates the history", async () => {
    apiClient.defaults.adapter = async (config) => {
      if (config.method === "get" && config.url.endsWith("/transactions")) {
        const page = config.params.page;
        return response(config, {
          items: page === 1
            ? [
                { id: "tx-1", accountId, type: "DEPOSIT", amount: "25.50", note: "Paycheck", createdAt: "2026-01-02T12:00:00Z" },
                { id: "tx-2", accountId, type: "WITHDRAWAL", amount: "10.00", note: "Coffee", createdAt: "2026-01-01T12:00:00Z" },
              ]
            : [{ id: "tx-3", accountId, type: "TRANSFER_IN", amount: "5.25", note: "Refund", createdAt: "2025-12-31T12:00:00Z" }],
          page,
          limit: 20,
          total: 21,
        });
      }
      if (config.method === "get") return response(config, account());
      return response(config, {});
    };
    renderDashboard();

    expect(await screen.findByText("Paycheck")).toBeInTheDocument();
    const history = screen.getByRole("list", { name: "Transaction history" });
    expect(within(history).getByText("Deposit")).toBeInTheDocument();
    expect(within(history).getByText("Withdrawal")).toBeInTheDocument();
    expect(within(history).getByText("+$25.50")).toBeInTheDocument();
    expect(within(history).getByText("-$10.00")).toBeInTheDocument();
    expect(screen.getByText("Page 1 of 2")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Next" }));
    expect(await screen.findByText("Refund")).toBeInTheDocument();
    expect(screen.getByText("+$5.25")).toBeInTheDocument();
    expect(screen.getByText("Page 2 of 2")).toBeInTheDocument();
  });

  it("shows the exact empty state", async () => {
    apiClient.defaults.adapter = async (config) => {
      if (config.method === "get" && config.url.endsWith("/transactions")) {
        return response(config, { items: [], page: 1, limit: 20, total: 0 });
      }
      return response(config, account());
    };
    renderDashboard();

    expect(await screen.findByText("No transactions yet — make your first deposit")).toBeInTheDocument();
  });

  it("shows a loading skeleton and a history error with retry", async () => {
    let rejectHistory;
    apiClient.defaults.adapter = async (config) => {
      if (config.method === "get" && config.url.endsWith("/transactions")) {
        return new Promise((_resolve, reject) => {
          rejectHistory = () => reject({
            config,
            response: { status: 500, data: { error: "History unavailable", code: "INTERNAL_ERROR" } },
          });
        });
      }
      if (config.method === "get") return response(config, account());
      return response(config, {});
    };
    renderDashboard();

    expect(await screen.findByLabelText("Loading transactions")).toBeInTheDocument();
    rejectHistory();
    expect(await screen.findByRole("alert")).toHaveTextContent("History unavailable");
    expect(screen.getByRole("button", { name: "Retry" })).toBeInTheDocument();
  });
});
