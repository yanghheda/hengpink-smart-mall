import { useEffect, useState } from "react";
import { Image, StyleSheet, Text, View } from "react-native";

import { getProductImage } from "../config/productImages";

type Props = {
  productId: string;
  categoryId: string;
  brand?: string;
  large?: boolean;
};

export function ProductImage({
  productId,
  categoryId,
  brand = "HENGPICK",
  large = false,
}: Props) {
  const uri = getProductImage(productId, categoryId);
  const [failed, setFailed] = useState(false);

  useEffect(() => setFailed(false), [uri]);

  return (
    <View style={[styles.container, large && styles.large]}>
      {uri && !failed ? (
        <Image
          accessibilityLabel={`${brand} 商品图片`}
          resizeMode="cover"
          source={{ uri }}
          style={styles.image}
          onError={() => setFailed(true)}
        />
      ) : (
        <View style={styles.fallback}>
          <Text style={styles.fallbackMark}>{brand.slice(0, 1)}</Text>
          <Text style={styles.fallbackText}>{brand}</Text>
        </View>
      )}
      <View style={styles.brandPill}>
        <Text style={styles.brandText}>{brand}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: "#eceff3",
    height: 172,
    overflow: "hidden",
  },
  large: { height: 410, width: "100%" },
  image: { height: "100%", width: "100%" },
  fallback: {
    alignItems: "center",
    backgroundColor: "#e8f0ff",
    flex: 1,
    justifyContent: "center",
  },
  fallbackMark: { color: "#5b7cfa", fontSize: 74, fontWeight: "900" },
  fallbackText: { color: "#5b7cfa", fontSize: 11, fontWeight: "800" },
  brandPill: {
    backgroundColor: "rgba(20,20,20,0.72)",
    borderRadius: 7,
    bottom: 11,
    left: 11,
    paddingHorizontal: 8,
    paddingVertical: 4,
    position: "absolute",
  },
  brandText: {
    color: "#ffffff",
    fontSize: 8,
    fontWeight: "800",
    letterSpacing: 0.6,
  },
});
