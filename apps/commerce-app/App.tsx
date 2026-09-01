import {
  QueryClient,
  QueryClientProvider,
  useQueryClient,
} from "@tanstack/react-query";
import { NavigationContainer } from "@react-navigation/native";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import { useEffect, useMemo, useState } from "react";
import {
  ActivityIndicator,
  SafeAreaView,
  StyleSheet,
  Text,
} from "react-native";

import { createApiClient } from "./src/api/client";
import { createSessionController } from "./src/auth/sessionController";
import { apiBaseUrl } from "./src/config/runtime";
import { HomeScreen } from "./src/screens/HomeScreen";
import { LoginScreen } from "./src/screens/LoginScreen";
import { MockCheckoutScreen } from "./src/screens/MockCheckoutScreen";
import { ProductDetailScreen } from "./src/screens/ProductDetailScreen";
import { SmartMallScreen } from "./src/screens/SmartMallScreen";
import { credentialStore } from "./src/services";
import type { RootStackParams, SessionStatus } from "./src/types/commerce";

// 页面文案由独立组件维护：登录演示商城、商品列表、商品详情、模拟数据。
const Stack = createNativeStackNavigator<RootStackParams>();
const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1 } },
});

function CommerceApp() {
  const [status, setStatus] = useState<SessionStatus>("restoring");
  const [reason, setReason] = useState<string>();
  const queryCache = useQueryClient();
  const api = useMemo(
    () =>
      createApiClient({
        baseUrl: apiBaseUrl,
        getAccessToken: credentialStore.getAccessToken,
        onSessionExpired: async () => {
          await credentialStore.clear();
          queryCache.clear();
          setReason("登录已过期，请重新登录");
          setStatus("anonymous");
        },
      }),
    [queryCache],
  );

  useEffect(() => {
    const controller = createSessionController({
      credentialStore,
      refreshSession: (refreshToken) =>
        api.post("/api/v1/auth/refresh", { refreshToken }),
    });
    controller.restore().then((result) => {
      setReason(
        result.reason === "expired" ? "登录已过期，请重新登录" : undefined,
      );
      setStatus(result.status);
    });
  }, [api]);

  if (status === "restoring") {
    return (
      <SafeAreaView style={styles.center}>
        <ActivityIndicator />
        <Text>正在恢复登录状态…</Text>
      </SafeAreaView>
    );
  }
  if (status === "anonymous") {
    return (
      <LoginScreen
        api={api}
        reason={reason}
        onAuthenticated={() => setStatus("authenticated")}
      />
    );
  }
  return (
    <NavigationContainer>
      <Stack.Navigator>
        <Stack.Screen name="Home" options={{ headerShown: false }}>
          {(props) => <HomeScreen {...props} api={api} />}
        </Stack.Screen>
        <Stack.Screen name="ProductDetail" options={{ headerShown: false }}>
          {(props) => <ProductDetailScreen {...props} api={api} />}
        </Stack.Screen>
        <Stack.Screen name="SmartMall" options={{ title: "智能商城" }}>
          {(props) => <SmartMallScreen {...props} api={api} />}
        </Stack.Screen>
        <Stack.Screen name="MockCheckout" options={{ title: "模拟结算" }}>
          {() => <MockCheckoutScreen api={api} />}
        </Stack.Screen>
      </Stack.Navigator>
    </NavigationContainer>
  );
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <CommerceApp />
    </QueryClientProvider>
  );
}

const styles = StyleSheet.create({
  center: {
    alignItems: "center",
    flex: 1,
    gap: 16,
    justifyContent: "center",
    padding: 24,
  },
});
