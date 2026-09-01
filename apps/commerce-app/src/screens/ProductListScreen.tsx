import { useQuery } from "@tanstack/react-query";
import { Pressable, ScrollView, StyleSheet, Text, View } from "react-native";

import type { ApiClient } from "../api/client";
import { navigationStore } from "../navigation/navigationStore";
import type { ProductPage, ProductSummary } from "../types/commerce";
import { ProductImage } from "../components/ProductImage";
import { ScreenState } from "../components/ScreenState";

export function ProductListScreen({
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

  const columns = query.data.items.reduce<[ProductSummary[], ProductSummary[]]>(
    (result, item, index) => {
      result[index % 2].push(item);
      return result;
    },
    [[], []],
  );

  const renderCard = (item: ProductSummary, index: number) => (
    <Pressable
      key={item.productId}
      style={styles.productCard}
      onPress={() => {
        navigationStore.getState().openProduct({ productId: item.productId });
        navigation.navigate("ProductDetail");
      }}
    >
      <ProductImage
        brand={item.brand}
        categoryId={item.categoryId}
        productId={item.productId}
      />
      <View style={styles.cardBody}>
        <Text numberOfLines={2} style={styles.productName}>
          {item.displayName}
        </Text>
        {item.subtitle ? (
          <Text numberOfLines={index % 2 ? 2 : 1} style={styles.subtitle}>
            {item.subtitle}
          </Text>
        ) : (
          <Text numberOfLines={1} style={styles.subtitle}>
            {item.brand} {item.model}
          </Text>
        )}
        <View style={styles.cardFooter}>
          <Text style={styles.badge}>{item.categoryName || "官方精选"}</Text>
          <Text style={styles.skuCount}>{item.skuCount}款可选</Text>
        </View>
      </View>
    </Pressable>
  );

  return (
    <ScrollView
      showsVerticalScrollIndicator={false}
      contentContainerStyle={styles.scroll}
    >
      <View style={styles.header}>
        <View>
          <Text style={styles.greeting}>上午好</Text>
          <Text style={styles.title}>发现好物</Text>
        </View>
        <View style={styles.search}>
          <Text style={styles.searchText}>⌕</Text>
        </View>
      </View>
      <View style={styles.banner}>
        <View style={styles.bannerCopy}>
          <Text style={styles.bannerEyebrow}>HENGPICK 精选</Text>
          <Text style={styles.bannerTitle}>认真选，更好买</Text>
          <Text style={styles.bannerText}>全场商品均为演示数据</Text>
        </View>
        <Text style={styles.bannerMark}>H</Text>
      </View>
      <View style={styles.sectionHeading}>
        <Text style={styles.sectionTitle}>商品列表 · 猜你喜欢</Text>
        <Text style={styles.sectionMeta}>共 {query.data.totalElements} 件</Text>
      </View>
      <View style={styles.masonry}>
        {columns.map((column, columnIndex) => (
          <View key={columnIndex} style={styles.column}>
            {column.map(renderCard)}
          </View>
        ))}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scroll: { paddingBottom: 24 },
  header: {
    alignItems: "center",
    flexDirection: "row",
    justifyContent: "space-between",
    paddingBottom: 16,
    paddingHorizontal: 18,
    paddingTop: 12,
  },
  greeting: { color: "#999999", fontSize: 12, marginBottom: 2 },
  title: { color: "#151515", fontSize: 26, fontWeight: "800" },
  search: {
    alignItems: "center",
    backgroundColor: "#ffffff",
    borderRadius: 20,
    height: 40,
    justifyContent: "center",
    width: 40,
  },
  searchText: { color: "#222222", fontSize: 25 },
  banner: {
    backgroundColor: "#20242f",
    borderRadius: 22,
    flexDirection: "row",
    height: 148,
    marginHorizontal: 16,
    overflow: "hidden",
    padding: 22,
  },
  bannerCopy: { flex: 1, justifyContent: "center" },
  bannerEyebrow: {
    color: "#ffb89e",
    fontSize: 12,
    fontWeight: "700",
    letterSpacing: 1,
  },
  bannerTitle: {
    color: "#ffffff",
    fontSize: 25,
    fontWeight: "800",
    marginTop: 8,
  },
  bannerText: { color: "#b8bdc9", fontSize: 12, marginTop: 9 },
  bannerMark: {
    color: "#ff5b35",
    fontSize: 104,
    fontWeight: "900",
    opacity: 0.88,
    position: "absolute",
    right: 10,
    top: 10,
  },
  sectionHeading: {
    alignItems: "baseline",
    flexDirection: "row",
    justifyContent: "space-between",
    paddingBottom: 12,
    paddingHorizontal: 17,
    paddingTop: 25,
  },
  sectionTitle: { color: "#1a1a1a", fontSize: 18, fontWeight: "800" },
  sectionMeta: { color: "#9a9a9a", fontSize: 12 },
  masonry: {
    alignItems: "flex-start",
    flexDirection: "row",
    gap: 10,
    paddingHorizontal: 12,
  },
  column: { flex: 1, gap: 10 },
  productCard: {
    backgroundColor: "#ffffff",
    borderRadius: 16,
    overflow: "hidden",
  },
  cardBody: { padding: 12 },
  productName: {
    color: "#222222",
    fontSize: 15,
    fontWeight: "700",
    lineHeight: 21,
  },
  subtitle: { color: "#8a8a8a", fontSize: 11, lineHeight: 17, marginTop: 5 },
  cardFooter: {
    alignItems: "center",
    flexDirection: "row",
    justifyContent: "space-between",
    marginTop: 10,
  },
  badge: {
    backgroundColor: "#fff0eb",
    borderRadius: 5,
    color: "#ef5636",
    fontSize: 9,
    fontWeight: "700",
    overflow: "hidden",
    paddingHorizontal: 6,
    paddingVertical: 3,
  },
  skuCount: { color: "#999999", fontSize: 10 },
});
