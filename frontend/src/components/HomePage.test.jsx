import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import apiClient from "../api/client.js";
import HomePage from "./HomePage.jsx";

const originalAdapter = apiClient.defaults.adapter;
afterEach(() => {
  cleanup();
  apiClient.defaults.adapter = originalAdapter;
});

function renderHome(props = {}) {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <HomePage {...props} />
    </QueryClientProvider>,
  );
}

function openCreateDialog() {
  fireEvent.click(screen.getAllByRole("button", { name: "Create account" }).at(-1));
}

describe("HomePage account creation", () => {
  it("traps keyboard focus in the modal and restores it when Escape closes", () => {
    renderHome();
    const trigger = screen.getAllByRole("button", { name: "Create account" }).at(-1);
    fireEvent.click(trigger);
    const dialog = screen.getByRole("dialog");
    const close = within(dialog).getByRole("button", { name: "Close create account dialog" });
    const submit = within(dialog).getByRole("button", { name: "Create account" });

    close.focus();
    fireEvent.keyDown(close, { key: "Tab", shiftKey: true });
    expect(submit).toHaveFocus();
    fireEvent.keyDown(submit, { key: "Tab" });
    expect(close).toHaveFocus();
    fireEvent.keyDown(close, { key: "Escape" });

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });

  it("validates required fields and a positive initial deposit before calling the API", () => {
    const adapter = vi.fn();
    apiClient.defaults.adapter = adapter;
    renderHome();
    openCreateDialog();
    fireEvent.change(screen.getByLabelText("Initial deposit (optional)"), { target: { value: "0" } });
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Create account" }));

    expect(screen.getByText("Enter your name.")).toBeInTheDocument();
    expect(screen.getByText("Enter your email.")).toBeInTheDocument();
    expect(screen.getByText(/amount greater than 0/)).toBeInTheDocument();
    expect(adapter).not.toHaveBeenCalled();
    expect(screen.getByLabelText("Name")).toHaveFocus();
  });

  it("shows a duplicate-email backend error on the email field", async () => {
    apiClient.defaults.adapter = async (config) =>
      Promise.reject({
        config,
        response: {
          status: 409,
          data: { error: "An account with this email already exists.", code: "EMAIL_ALREADY_EXISTS" },
        },
      });
    renderHome();
    openCreateDialog();
    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "Ada Lovelace" } });
    fireEvent.change(screen.getByLabelText("Email"), { target: { value: "ada@example.com" } });
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Create account" }));

    const email = screen.getByLabelText("Email");
    await waitFor(() => expect(email).toHaveAttribute("aria-invalid", "true"));
    expect(screen.getByText("An account with this email already exists.")).toBeInTheDocument();
    expect(email).toHaveAttribute("aria-describedby", "email-error");
  });

  it("submits a valid account and navigates to its dashboard", async () => {
    const onNavigate = vi.fn();
    let requestBody;
    apiClient.defaults.adapter = async (config) => {
      requestBody = JSON.parse(config.data);
      return ({
      config,
      data: { id: "new-account-id", name: "Ada Lovelace", email: "ada@example.com", balance: 25, createdAt: "now" },
      status: 201,
      statusText: "Created",
      headers: {},
      });
    };
    renderHome({ onNavigate });
    openCreateDialog();
    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "Ada Lovelace" } });
    fireEvent.change(screen.getByLabelText("Email"), { target: { value: "ada@example.com" } });
    fireEvent.change(screen.getByLabelText("Initial deposit (optional)"), { target: { value: "25.00" } });
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Create account" }));

    await waitFor(() => expect(onNavigate).toHaveBeenCalledWith("new-account-id"));
    expect(requestBody).toEqual({ name: "Ada Lovelace", email: "ada@example.com", initial_deposit: 25 });
  });
});

describe("HomePage account search", () => {
  it("requires an account ID before searching", () => {
    const adapter = vi.fn();
    apiClient.defaults.adapter = adapter;
    renderHome();
    fireEvent.click(screen.getByRole("button", { name: "Search" }));

    expect(screen.getByText("Enter your account ID.")).toBeInTheDocument();
    expect(adapter).not.toHaveBeenCalled();
  });
});
