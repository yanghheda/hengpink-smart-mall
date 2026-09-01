import { useMemo } from "react";
import { WebView } from "react-native-webview";

import type { ApiClient } from "../api/client";
import { createHostBridgeController } from "../bridge/hostBridgeController";
import {
  deviceSessionId,
  smartMallOrigin,
  smartMallUrl,
} from "../config/runtime";
import { navigationStore } from "../navigation/navigationStore";

export function SmartMallScreen({
  api,
  navigation,
}: {
  api: ApiClient;
  navigation: any;
}) {
  const webViewRef = useMemo(
    () => ({ current: null as { postMessage(raw: string): void } | null }),
    [],
  );
  const controller = useMemo(
    () =>
      createHostBridgeController({
        allowedOrigin: smartMallOrigin,
        createTicket: () =>
          api.post<{ ticket: string; expiresAt: string }>(
            "/api/v1/smart-mall/tickets",
            {
              hostType: "REACT_NATIVE",
              deviceSessionId,
              h5Origin: smartMallOrigin,
            },
          ),
        send: (message) =>
          webViewRef.current?.postMessage(JSON.stringify(message)),
        openProduct: (selection) => {
          navigationStore.getState().openProduct(selection);
          navigation.navigate("ProductDetail");
        },
        openMockCheckout: ({ purchaseIntentId }) => {
          navigationStore.getState().openMockCheckout({ purchaseIntentId });
          navigation.navigate("MockCheckout");
        },
      }),
    [api, navigation, webViewRef],
  );

  return (
    <WebView
      ref={(instance) => {
        webViewRef.current = instance;
      }}
      source={{ uri: smartMallUrl }}
      originWhitelist={[smartMallOrigin]}
      onShouldStartLoadWithRequest={(request) => {
        try {
          return new URL(request.url).origin === smartMallOrigin;
        } catch {
          return false;
        }
      }}
      onMessage={(event) => {
        let origin = "";
        try {
          origin = new URL(event.nativeEvent.url).origin;
        } catch {
          return;
        }
        void controller.onMessage(event.nativeEvent.data, origin);
      }}
      javaScriptEnabled
      setSupportMultipleWindows={false}
    />
  );
}
