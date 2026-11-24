import { afterEach, describe, expect, it } from "vitest";
import apiClient from "./client.js";

const defaultAdapter = apiClient.defaults.adapter;

afterEach(() => {
  apiClient.defaults.adapter = defaultAdapter;
});

describe("apiClient", () => {
  it("passes successful responses through unchanged", async () => {
    const response = { data: { ok: true }, status: 200 };
    apiClient.defaults.adapter = async () => response;

    await expect(apiClient.get("/health")).resolves.toBe(response);
  });

  it("normalizes API errors", async () => {
    apiClient.defaults.adapter = async (config) =>
      Promise.reject({
        config,
        response: {
          data: { error: "Account not found", code: "ACCOUNT_NOT_FOUND" },
          status: 404,
        },
      });

    await expect(apiClient.get("/accounts/missing")).rejects.toMatchObject({
      name: "ApiError",
      message: "Account not found",
      error: "Account not found",
      code: "ACCOUNT_NOT_FOUND",
      status: 404,
    });
  });

  it("falls back to the transport message for network failures", async () => {
    apiClient.defaults.adapter = async (config) => Promise.reject({ config, message: "Network unavailable" });

    await expect(apiClient.get("/health")).rejects.toMatchObject({
      name: "ApiError",
      message: "Network unavailable",
      code: "NETWORK_ERROR",
    });
  });

  it("uses a generic message and HTTP code when an error payload is malformed", async () => {
    apiClient.defaults.adapter = async (config) =>
      Promise.reject({
        config,
        message: "",
        response: { status: 500, data: { error: "", code: 500 } },
      });

    await expect(apiClient.get("/health")).rejects.toMatchObject({
      name: "ApiError",
      message: "Request failed",
      code: "HTTP_ERROR",
      status: 500,
    });
  });
});
