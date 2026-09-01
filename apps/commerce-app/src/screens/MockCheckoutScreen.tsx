import { useQuery } from "@tanstack/react-query";
import { Button, SafeAreaView, StyleSheet, Text } from "react-native";
import { useStore } from "zustand";

import type { ApiClient } from "../api/client";
import { ScreenState } from "../components/ScreenState";
import { navigationStore } from "../navigation/navigationStore";
import type { PurchaseIntent } from "../types/commerce";

export function MockCheckoutScreen({ api }: { api: ApiClient }) {
  const intentId = useStore(
    navigationStore,
    (state) => state.selectedPurchaseIntentId,
  );
  const query = useQuery({
    queryKey: ["purchase-intent", intentId],
    enabled: Boolean(intentId),
    queryFn: () =>
      api.get<PurchaseIntent>(
        `/api/v1/purchase-intents/${encodeURIComponent(intentId!)}`,
      ),
  });
  if (!intentId) return <ScreenState text="未选择购买意向" />;
  if (query.isPending) return <ScreenState text="正在读取服务端价格快照…" />;
  if (query.isError)
    return (
      <ScreenState text={query.error.message} retry={() => query.refetch()} />
    );
  return (
    <SafeAreaView style={styles.page}>
      <Text style={styles.notice}>模拟结算 · 不支付、不扣库存、不生成订单</Text>
      <Text style={styles.title}>确认模拟购买</Text>
      <Text style={styles.meta}>SKU：{query.data.skuId}</Text>
      <Text style={styles.price}>
        预计到手价 ¥{query.data.pricePlanSnapshot.finalPrice}
      </Text>
      <Text style={styles.meta}>状态：{query.data.status}</Text>
      <Button
        title={
          query.data.status === "CONFIRMED" ? "已完成模拟确认" : "确认模拟购买"
        }
        disabled={query.data.status !== "CREATED"}
        onPress={async () => {
          await api.post(
            `/api/v1/purchase-intents/${encodeURIComponent(intentId)}/confirm`,
            {},
          );
          await query.refetch();
        }}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  page: { backgroundColor: "#f4f7fb", flex: 1, padding: 20 },
  notice: { color: "#8a4b08", marginBottom: 18 },
  title: {
    color: "#172033",
    fontSize: 28,
    fontWeight: "700",
    marginBottom: 12,
    marginTop: 12,
  },
  meta: { color: "#344054", marginVertical: 14 },
  price: { color: "#b42318", fontSize: 16, fontWeight: "700" },
});
