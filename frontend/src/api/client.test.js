import { afterEach, describe, expect, it } from "vitest";
import apiClient from "./client.js";

const defaultAdapter = apiClient.defaults.adapter;

afterEach(() => {
  apiClient.defaults.adapter = defaultAdapter;
});

describe("apiClient", () => {
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
});
