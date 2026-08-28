import {
  QueryClient,
  QueryClientProvider,
  useQueries,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { NavigationContainer } from "@react-navigation/native";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import { useEffect, useMemo, useState } from "react";
import {
  ActivityIndicator,
  Button,
  FlatList,
  Pressable,
  SafeAreaView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";
import { useStore } from "zustand";

import { ApiError, createApiClient, type ApiClient } from "./src/api/client.js";
import { createMemoryCredentialStore } from "./src/auth/credentialStore.js";
import { secureStorage } from "./src/auth/secureStorage.js";
import { createSessionController } from "./src/auth/sessionController.js";
import { navigationStore } from "./src/navigation/navigationStore.js";

type SessionStatus = "restoring" | "anonymous" | "authenticated";
type RootStackParams = {
  Home: undefined;
  Products: undefined;
  ProductDetail: undefined;
};
type ProductSummary = {
  productId: string;
  displayName: string;
  subtitle?: string;
  skuCount: number;
};
type ProductPage = { items: ProductSummary[]; totalElements: number };
type ProductDetail = {
  productId: string;
  displayName: string;
  subtitle?: string;
  brand: string;
  skus: Array<{ skuId: string; displayName: string }>;
};
type OfferList = {
  skuId: string;
  offers: Array<{ offerId: string; salePrice: string; currency: string }>;
};

const Stack = createNativeStackNavigator<RootStackParams>();
const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1 } },
});
const credentialStore = createMemoryCredentialStore(secureStorage);
const apiBaseUrl =
  process.env.EXPO_PUBLIC_API_BASE_URL ?? "http://127.0.0.1:8080";

function ScreenState({ text, retry }: { text: string; retry?: () => void }) {
  return (
    <SafeAreaView style={styles.center}>
      <Text style={styles.message}>{text}</Text>
      {retry ? <Button title="重试" onPress={retry} /> : null}
    </SafeAreaView>
  );
}

function LoginScreen({
  api,
  reason,
  onAuthenticated,
}: {
  api: ApiClient;
  reason?: string;
  onAuthenticated: () => void;
}) {
  const [account, setAccount] = useState("demo_user");
  const [password, setPassword] = useState("demo_password");
  const [error, setError] = useState(reason ?? "");
  const [submitting, setSubmitting] = useState(false);
  async function submit() {
    setSubmitting(true);
    setError("");
    try {
      const controller = createSessionController({
        credentialStore,
        loginSession: (credentials) =>
          api.post("/api/v1/auth/login", credentials),
      });
      await controller.login({
        account,
        password,
        deviceSessionId: "commerce-app-local-device",
      });
      onAuthenticated();
    } catch (loginError) {
      setError(
        loginError instanceof Error ? loginError.message : "登录失败，请重试",
      );
    } finally {
      setSubmitting(false);
    }
  }
  return (
    <SafeAreaView style={styles.page}>
      <Text style={styles.eyebrow}>HENGPICK COMMERCE</Text>
      <Text style={styles.title}>登录演示商城</Text>
      <Text style={styles.simulated}>全部商品与价格均为项目模拟数据</Text>
      {error ? <Text style={styles.error}>{error}</Text> : null}
      <TextInput
        accessibilityLabel="账号"
        style={styles.input}
        value={account}
        onChangeText={setAccount}
      />
      <TextInput
        accessibilityLabel="密码"
        style={styles.input}
        value={password}
        onChangeText={setPassword}
        secureTextEntry
      />
      <Button
        title={submitting ? "登录中…" : "登录"}
        onPress={submit}
        disabled={submitting}
      />
    </SafeAreaView>
  );
}

function HomeScreen({ navigation }: any) {
  return (
    <SafeAreaView style={styles.page}>
      <Text style={styles.eyebrow}>HENGPICK COMMERCE</Text>
      <Text style={styles.title}>今天想挑点什么？</Text>
      <Text style={styles.simulated}>演示数据 · 金额以服务端返回为准</Text>
      <View style={styles.hero}>
        <Text style={styles.heroTitle}>手机精选</Text>
        <Text style={styles.message}>
          浏览最小商品目录，查看 SKU 与模拟售价。
        </Text>
        <Button
          title="查看商品"
          onPress={() => navigation.navigate("Products")}
        />
      </View>
    </SafeAreaView>
  );
}

function ProductListScreen({
  api,
  navigation,
}: {
  api: ApiClient;
  navigation: any;
}) {
  const query = useQuery({
    queryKey: ["products"],
    queryFn: () => api.get<ProductPage>("/api/v1/products?page=0&size=20"),
  });
  if (query.isPending) return <ScreenState text="正在加载商品…" />;
  if (query.isError)
    return (
      <ScreenState text={query.error.message} retry={() => query.refetch()} />
    );
  return (
    <FlatList
      contentContainerStyle={styles.list}
      data={query.data.items}
      keyExtractor={(item) => item.productId}
      ListHeaderComponent={
        <Text style={styles.simulated}>
          模拟商品 · 共 {query.data.totalElements} 件
        </Text>
      }
      renderItem={({ item }) => (
        <Pressable
          style={styles.card}
          onPress={() => {
            navigationStore
              .getState()
              .openProduct({ productId: item.productId });
            navigation.navigate("ProductDetail");
          }}
        >
          <Text style={styles.cardTitle}>{item.displayName}</Text>
          <Text style={styles.message}>{item.subtitle ?? "暂无副标题"}</Text>
          <Text style={styles.meta}>{item.skuCount} 个可用 SKU</Text>
        </Pressable>
      )}
    />
  );
}

function ProductDetailScreen({ api }: { api: ApiClient }) {
  const selected = useStore(navigationStore, (state) => state.selectedProduct);
  const query = useQuery({
    queryKey: ["product", selected?.productId, selected?.skuId],
    enabled: Boolean(selected),
    queryFn: () =>
      api.get<ProductDetail>(
        `/api/v1/products/${encodeURIComponent(selected!.productId)}${selected?.skuId ? `?skuId=${encodeURIComponent(selected.skuId)}` : ""}`,
      ),
    retry: (count, error) =>
      !(error instanceof ApiError && error.status === 404) && count < 1,
  });
  const offerQueries = useQueries({
    queries: (query.data?.skus ?? []).map((sku) => ({
      queryKey: ["offers", sku.skuId],
      queryFn: () =>
        api.get<OfferList>(
          `/api/v1/skus/${encodeURIComponent(sku.skuId)}/offers`,
        ),
    })),
  });
  if (!selected) return <ScreenState text="未选择商品" />;
  if (query.isPending) return <ScreenState text="正在加载商品详情…" />;
  if (
    query.error instanceof ApiError &&
    query.error.code === "PRODUCT_NOT_FOUND"
  )
    return <ScreenState text="商品不存在或已下架" />;
  if (query.isError)
    return (
      <ScreenState text={query.error.message} retry={() => query.refetch()} />
    );
  return (
    <SafeAreaView style={styles.page}>
      <Text style={styles.simulated}>商品信息与价格均为模拟数据</Text>
      <Text style={styles.title}>{query.data.displayName}</Text>
      <Text style={styles.message}>{query.data.subtitle ?? "暂无副标题"}</Text>
      <Text style={styles.meta}>品牌：{query.data.brand}</Text>
      {query.data.skus.map((sku, index) => {
        const offerQuery = offerQueries[index];
        const firstOffer = offerQuery.data?.offers[0];
        return (
          <View key={sku.skuId} style={styles.card}>
            <Text style={styles.cardTitle}>{sku.displayName}</Text>
            {offerQuery.isPending ? (
              <Text style={styles.message}>正在加载有效报价…</Text>
            ) : null}
            {offerQuery.isError ? (
              <Text style={styles.error}>有效报价加载失败</Text>
            ) : null}
            {firstOffer ? (
              <Text style={styles.price}>模拟售价 ¥{firstOffer.salePrice}</Text>
            ) : null}
            {!offerQuery.isPending && !offerQuery.isError && !firstOffer ? (
              <Text style={styles.message}>当前暂无有效报价</Text>
            ) : null}
          </View>
        );
      })}
    </SafeAreaView>
  );
}

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
  if (status === "restoring")
    return (
      <SafeAreaView style={styles.center}>
        <ActivityIndicator />
        <Text>正在恢复登录状态…</Text>
      </SafeAreaView>
    );
  if (status === "anonymous")
    return (
      <LoginScreen
        api={api}
        reason={reason}
        onAuthenticated={() => setStatus("authenticated")}
      />
    );
  return (
    <NavigationContainer>
      <Stack.Navigator>
        <Stack.Screen
          name="Home"
          options={{ title: "衡选商城" }}
          component={HomeScreen}
        />
        <Stack.Screen name="Products" options={{ title: "商品列表" }}>
          {(props) => <ProductListScreen {...props} api={api} />}
        </Stack.Screen>
        <Stack.Screen name="ProductDetail" options={{ title: "商品详情" }}>
          {() => <ProductDetailScreen api={api} />}
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
  page: { backgroundColor: "#f4f7fb", flex: 1, padding: 20 },
  center: {
    alignItems: "center",
    flex: 1,
    gap: 16,
    justifyContent: "center",
    padding: 24,
  },
  list: { backgroundColor: "#f4f7fb", gap: 12, padding: 16 },
  eyebrow: {
    color: "#2563eb",
    fontSize: 12,
    fontWeight: "700",
    letterSpacing: 1.5,
  },
  title: {
    color: "#172033",
    fontSize: 28,
    fontWeight: "700",
    marginBottom: 12,
    marginTop: 12,
  },
  simulated: { color: "#8a4b08", marginBottom: 18 },
  hero: {
    backgroundColor: "#ffffff",
    borderRadius: 20,
    gap: 12,
    marginTop: 14,
    padding: 22,
  },
  heroTitle: { color: "#172033", fontSize: 22, fontWeight: "700" },
  input: {
    backgroundColor: "#ffffff",
    borderColor: "#dbe4f0",
    borderRadius: 12,
    borderWidth: 1,
    marginBottom: 12,
    minHeight: 48,
    paddingHorizontal: 14,
  },
  error: { color: "#b42318", marginBottom: 12 },
  message: { color: "#536179", fontSize: 15, lineHeight: 22 },
  meta: { color: "#344054", marginVertical: 14 },
  card: {
    backgroundColor: "#ffffff",
    borderColor: "#dbe4f0",
    borderRadius: 16,
    borderWidth: 1,
    gap: 8,
    padding: 18,
  },
  cardTitle: { color: "#172033", fontSize: 17, fontWeight: "700" },
  price: { color: "#b42318", fontSize: 16, fontWeight: "700" },
});
