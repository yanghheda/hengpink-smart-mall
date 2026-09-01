import { useQueries, useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import {
  Pressable,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { useStore } from "zustand";

import { ApiError, type ApiClient } from "../api/client";
import { ProductImage } from "../components/ProductImage";
import { ScreenState } from "../components/ScreenState";
import { navigationStore } from "../navigation/navigationStore";
import type { OfferList, ProductDetail } from "../types/commerce";

export function ProductDetailScreen({
  api,
  navigation,
}: {
  api: ApiClient;
  navigation: any;
}) {
  const selected = useStore(navigationStore, (state) => state.selectedProduct);
  const [selectedSkuId, setSelectedSkuId] = useState<string | undefined>(
    selected?.skuId,
  );
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

  useEffect(() => {
    const skus = query.data?.skus ?? [];
    if (!skus.length) return;
    const requestedSkuExists = skus.some(
      (sku) => sku.skuId === selected?.skuId,
    );
    setSelectedSkuId(
      requestedSkuExists
        ? selected?.skuId
        : (current) => current ?? skus[0].skuId,
    );
  }, [query.data?.productId, query.data?.skus, selected?.skuId]);

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

  const selectedSkuIndex = Math.max(
    0,
    query.data.skus.findIndex((sku) => sku.skuId === selectedSkuId),
  );
  const selectedSku = query.data.skus[selectedSkuIndex];
  const displayPrice =
    offerQueries[selectedSkuIndex]?.data?.offers[0]?.salePrice;
  return (
    <SafeAreaView style={styles.page}>
      <ScrollView
        showsVerticalScrollIndicator={false}
        contentContainerStyle={styles.scroll}
      >
        <View style={styles.hero}>
          <ProductImage
            large
            brand={query.data.brand}
            categoryId={query.data.categoryId}
            productId={query.data.productId}
          />
          <Pressable
            style={[styles.floating, styles.back]}
            onPress={() => navigation.goBack()}
          >
            <Text style={styles.floatingText}>‹</Text>
          </Pressable>
          <Pressable style={[styles.floating, styles.share]}>
            <Text style={styles.floatingText}>↗</Text>
          </Pressable>
          <View style={styles.dots}>
            <View style={styles.dotActive} />
            <View style={styles.dot} />
            <View style={styles.dot} />
          </View>
        </View>
        <View style={styles.info}>
          <View style={styles.priceRow}>
            <Text style={styles.priceSymbol}>¥</Text>
            <Text style={styles.price}>{displayPrice ?? "--"}</Text>
            <View style={styles.priceTag}>
              <Text style={styles.priceTagText}>预估到手价</Text>
            </View>
          </View>
          <Text style={styles.name}>{query.data.displayName}</Text>
          <Text style={styles.subtitle}>
            {query.data.subtitle ??
              `${query.data.brand} ${query.data.model} 官方精选商品`}
          </Text>
          <View style={styles.services}>
            <Text style={styles.service}>✓ 正品保障</Text>
            <Text style={styles.service}>✓ 7天无理由</Text>
            <Text style={styles.service}>✓ 极速退款</Text>
          </View>
        </View>
        <View style={styles.section}>
          <View style={styles.sectionHeader}>
            <Text style={styles.sectionTitle}>选择规格</Text>
            <Text style={styles.sectionArrow}>
              已选：{selectedSku?.displayName ?? "请选择"}
            </Text>
          </View>
          <ScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            contentContainerStyle={styles.skuRow}
          >
            {query.data.skus.map((sku, index) => {
              const offerQuery = offerQueries[index];
              const firstOffer = offerQuery.data?.offers[0];
              return (
                <Pressable
                  key={sku.skuId}
                  accessibilityRole="button"
                  accessibilityState={{ selected: sku.skuId === selectedSkuId }}
                  style={({ pressed }) => [
                    styles.skuCard,
                    sku.skuId === selectedSkuId && styles.skuSelected,
                    pressed && styles.skuPressed,
                  ]}
                  onPress={() => {
                    setSelectedSkuId(sku.skuId);
                    navigationStore.getState().openProduct({
                      productId: query.data.productId,
                      skuId: sku.skuId,
                    });
                  }}
                >
                  <Text numberOfLines={2} style={styles.skuName}>
                    {sku.displayName}
                  </Text>
                  <Text style={styles.skuPrice}>
                    {offerQuery.isPending
                      ? "加载中…"
                      : firstOffer
                        ? `¥${firstOffer.salePrice}`
                        : "暂无报价"}
                  </Text>
                </Pressable>
              );
            })}
          </ScrollView>
        </View>
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>商品详情</Text>
          <View style={styles.parameter}>
            <Text style={styles.parameterLabel}>品类</Text>
            <Text style={styles.parameterValue}>{query.data.categoryName}</Text>
          </View>
          <View style={styles.parameter}>
            <Text style={styles.parameterLabel}>品牌</Text>
            <Text style={styles.parameterValue}>{query.data.brand}</Text>
          </View>
          <View style={styles.parameter}>
            <Text style={styles.parameterLabel}>商品编号</Text>
            <Text style={styles.parameterValue}>{query.data.productId}</Text>
          </View>
          <View style={styles.parameter}>
            <Text style={styles.parameterLabel}>当前规格</Text>
            <Text style={styles.parameterValue}>
              {selectedSku?.displayName ?? "暂无可选规格"}
            </Text>
          </View>
          <View style={styles.detailBanner}>
            <Text style={styles.detailMark}>{query.data.brand}</Text>
            <Text style={styles.detailText}>品质好物 · 用心甄选</Text>
          </View>
        </View>
      </ScrollView>
      <View style={styles.purchaseBar}>
        <Pressable style={styles.utility}>
          <Text style={styles.utilityIcon}>⌂</Text>
          <Text style={styles.utilityText}>店铺</Text>
        </Pressable>
        <Pressable style={styles.utility}>
          <Text style={styles.utilityIcon}>♡</Text>
          <Text style={styles.utilityText}>收藏</Text>
        </Pressable>
        <Pressable style={styles.cartButton}>
          <Text style={styles.cartText}>加入购物车</Text>
        </Pressable>
        <Pressable style={styles.buyButton}>
          <Text style={styles.buyText}>立即购买</Text>
        </Pressable>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  page: { backgroundColor: "#f5f5f5", flex: 1 },
  scroll: { paddingBottom: 18 },
  hero: { position: "relative" },
  floating: {
    alignItems: "center",
    backgroundColor: "rgba(25,25,25,0.64)",
    borderRadius: 20,
    height: 40,
    justifyContent: "center",
    position: "absolute",
    top: 14,
    width: 40,
  },
  back: { left: 15 },
  share: { right: 15 },
  floatingText: { color: "#ffffff", fontSize: 27, lineHeight: 30 },
  dots: {
    bottom: 15,
    flexDirection: "row",
    gap: 5,
    left: "50%",
    marginLeft: -17,
    position: "absolute",
  },
  dot: {
    backgroundColor: "rgba(0,0,0,0.25)",
    borderRadius: 3,
    height: 5,
    width: 5,
  },
  dotActive: {
    backgroundColor: "#ff5b35",
    borderRadius: 3,
    height: 5,
    width: 16,
  },
  info: { backgroundColor: "#ffffff", padding: 18 },
  priceRow: { alignItems: "center", flexDirection: "row" },
  priceSymbol: {
    color: "#f04423",
    fontSize: 17,
    fontWeight: "800",
    marginRight: 2,
    marginTop: 8,
  },
  price: { color: "#f04423", fontSize: 34, fontWeight: "900" },
  priceTag: {
    backgroundColor: "#f04423",
    borderRadius: 5,
    marginLeft: 9,
    paddingHorizontal: 7,
    paddingVertical: 4,
  },
  priceTagText: { color: "#ffffff", fontSize: 10, fontWeight: "700" },
  name: {
    color: "#181818",
    fontSize: 20,
    fontWeight: "800",
    lineHeight: 28,
    marginTop: 9,
  },
  subtitle: { color: "#777777", fontSize: 13, lineHeight: 20, marginTop: 8 },
  services: {
    borderTopColor: "#eeeeee",
    borderTopWidth: StyleSheet.hairlineWidth,
    flexDirection: "row",
    justifyContent: "space-between",
    marginTop: 17,
    paddingTop: 14,
  },
  service: { color: "#666666", fontSize: 11 },
  section: {
    backgroundColor: "#ffffff",
    borderRadius: 14,
    marginHorizontal: 10,
    marginTop: 10,
    padding: 16,
  },
  sectionHeader: {
    alignItems: "center",
    flexDirection: "row",
    justifyContent: "space-between",
  },
  sectionTitle: { color: "#222222", fontSize: 17, fontWeight: "800" },
  sectionArrow: { color: "#999999", fontSize: 12 },
  skuRow: { gap: 9, paddingTop: 14 },
  skuCard: {
    backgroundColor: "#f6f6f6",
    borderColor: "transparent",
    borderRadius: 10,
    borderWidth: 1,
    padding: 11,
    width: 132,
  },
  skuSelected: { backgroundColor: "#fff5f1", borderColor: "#ff6b45" },
  skuPressed: { opacity: 0.72 },
  skuName: { color: "#333333", fontSize: 12, lineHeight: 17 },
  skuPrice: { color: "#f04423", fontSize: 12, fontWeight: "700", marginTop: 7 },
  parameter: { flexDirection: "row", marginTop: 15 },
  parameterLabel: { color: "#999999", fontSize: 12, width: 72 },
  parameterValue: { color: "#444444", flex: 1, fontSize: 12 },
  detailBanner: {
    alignItems: "center",
    backgroundColor: "#20242f",
    borderRadius: 12,
    height: 180,
    justifyContent: "center",
    marginTop: 18,
  },
  detailMark: {
    color: "#ffffff",
    fontSize: 26,
    fontWeight: "900",
    letterSpacing: 3,
    textTransform: "uppercase",
  },
  detailText: { color: "#b9bdc7", fontSize: 12, marginTop: 12 },
  purchaseBar: {
    alignItems: "center",
    backgroundColor: "#ffffff",
    borderTopColor: "#eeeeee",
    borderTopWidth: StyleSheet.hairlineWidth,
    flexDirection: "row",
    gap: 7,
    minHeight: 70,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  utility: { alignItems: "center", justifyContent: "center", width: 42 },
  utilityIcon: { color: "#444444", fontSize: 20 },
  utilityText: { color: "#555555", fontSize: 9, marginTop: 2 },
  cartButton: {
    alignItems: "center",
    backgroundColor: "#fff0e6",
    borderRadius: 22,
    flex: 1,
    justifyContent: "center",
    minHeight: 45,
  },
  cartText: { color: "#ff7a1a", fontSize: 14, fontWeight: "800" },
  buyButton: {
    alignItems: "center",
    backgroundColor: "#ff5b35",
    borderRadius: 22,
    flex: 1,
    justifyContent: "center",
    minHeight: 45,
  },
  buyText: { color: "#ffffff", fontSize: 14, fontWeight: "800" },
});
