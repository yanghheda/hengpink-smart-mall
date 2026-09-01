export const apiBaseUrl =
  process.env.EXPO_PUBLIC_API_BASE_URL ?? "http://127.0.0.1:8080";

export const smartMallUrl =
  process.env.EXPO_PUBLIC_SMART_MALL_URL ?? "http://127.0.0.1:5173/standalone";

export const smartMallOrigin = new URL(smartMallUrl).origin;

export const deviceSessionId =
  process.env.EXPO_PUBLIC_DEVICE_SESSION_ID ?? "commerce-app-local-device";
