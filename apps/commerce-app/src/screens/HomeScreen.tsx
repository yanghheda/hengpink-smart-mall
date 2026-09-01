import { useState } from "react";
import { SafeAreaView, StyleSheet, View } from "react-native";

import type { ApiClient } from "../api/client";
import { BottomTabBar, getTabLabel } from "../components/BottomTabBar";
import { EmptyTab } from "../components/EmptyTab";
import type { MainTab } from "../types/commerce";
import { ProductListScreen } from "./ProductListScreen";

export function HomeScreen({
  api,
  navigation,
}: {
  api: ApiClient;
  navigation: any;
}) {
  const [activeTab, setActiveTab] = useState<MainTab>("home");
  return (
    <SafeAreaView style={styles.shell}>
      <View style={styles.content}>
        {activeTab === "home" ? (
          <ProductListScreen api={api} navigation={navigation} />
        ) : (
          <EmptyTab label={getTabLabel(activeTab)} />
        )}
      </View>
      <BottomTabBar
        activeTab={activeTab}
        onChange={setActiveTab}
        onOpenAi={() => navigation.navigate("SmartMall")}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  shell: { backgroundColor: "#f6f6f6", flex: 1 },
  content: { flex: 1 },
});
