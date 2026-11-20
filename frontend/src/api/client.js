import axios from "axios";

const apiClient = axios.create({ baseURL: "/api" });

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const data = error.response?.data;
    const message =
      typeof data?.error === "string" && data.error.length > 0
        ? data.error
        : error.message || "Request failed";
    const normalizedError = new Error(message);

    normalizedError.name = "ApiError";
    normalizedError.error = message;
    normalizedError.code =
      typeof data?.code === "string"
        ? data.code
        : error.response
          ? "HTTP_ERROR"
          : "NETWORK_ERROR";
    normalizedError.status = error.response?.status;

    return Promise.reject(normalizedError);
  },
);

export default apiClient;
