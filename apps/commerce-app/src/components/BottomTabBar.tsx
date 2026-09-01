import { Pressable, StyleSheet, Text, View } from "react-native";

import type { MainTab } from "../types/commerce";

const tabs: Array<{ key: MainTab; label: string; icon: string }> = [
  { key: "home", label: "首页", icon: "⌂" },
  { key: "messages", label: "消息", icon: "◯" },
  { key: "cart", label: "购物车", icon: "▱" },
  { key: "profile", label: "我的", icon: "♙" },
];

export function getTabLabel(tab: MainTab) {
  return tabs.find((item) => item.key === tab)?.label ?? "";
}

export function BottomTabBar({
  activeTab,
  onChange,
  onOpenAi,
}: {
  activeTab: MainTab;
  onChange: (tab: MainTab) => void;
  onOpenAi: () => void;
}) {
  const renderTab = (tab: (typeof tabs)[number]) => (
    <Pressable
      key={tab.key}
      style={styles.tabItem}
      onPress={() => onChange(tab.key)}
    >
      <Text style={[styles.tabIcon, activeTab === tab.key && styles.active]}>
        {tab.icon}
      </Text>
      <Text style={[styles.tabLabel, activeTab === tab.key && styles.active]}>
        {tab.label}
      </Text>
    </Pressable>
  );

  return (
    <View style={styles.tabBar}>
      {tabs.slice(0, 2).map(renderTab)}
      <View style={styles.aiSpace} />
      {tabs.slice(2).map(renderTab)}
      <Pressable style={styles.aiWrap} onPress={onOpenAi}>
        <View style={styles.aiButton}>
          <Text style={styles.aiSpark}>✦</Text>
        </View>
        <Text style={styles.aiLabel}>AI 导购</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  tabBar: {
    alignItems: "center",
    backgroundColor: "#ffffff",
    borderTopColor: "#ededed",
    borderTopWidth: StyleSheet.hairlineWidth,
    flexDirection: "row",
    height: 72,
    paddingBottom: 4,
    position: "relative",
  },
  tabItem: { alignItems: "center", flex: 1, gap: 3, justifyContent: "center" },
  tabIcon: { color: "#999999", fontSize: 22, height: 27 },
  tabLabel: { color: "#999999", fontSize: 10 },
  active: { color: "#ff5b35", fontWeight: "700" },
  aiSpace: { width: 72 },
  aiWrap: {
    alignItems: "center",
    left: "50%",
    marginLeft: -36,
    position: "absolute",
    top: -25,
    width: 72,
  },
  aiButton: {
    alignItems: "center",
    backgroundColor: "#ff5b35",
    borderColor: "#ffffff",
    borderRadius: 29,
    borderWidth: 5,
    elevation: 8,
    height: 58,
    justifyContent: "center",
    shadowColor: "#ff5b35",
    shadowOffset: { height: 5, width: 0 },
    shadowOpacity: 0.3,
    shadowRadius: 9,
    width: 58,
  },
  aiSpark: { color: "#ffffff", fontSize: 25 },
  aiLabel: { color: "#ff5b35", fontSize: 10, fontWeight: "700", marginTop: 2 },
});
