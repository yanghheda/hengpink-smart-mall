import { SafeAreaView, StatusBar, StyleSheet, Text, View } from "react-native";

import { appContent } from "./src/appContent.js";

export default function App() {
  return (
    <SafeAreaView style={styles.screen}>
      <StatusBar barStyle="dark-content" />
      <View style={styles.card}>
        <Text style={styles.eyebrow}>{appContent.eyebrow}</Text>
        <Text style={styles.title}>{appContent.title}</Text>
        <Text style={styles.description}>{appContent.description}</Text>
        <Text style={styles.badge}>{appContent.badge}</Text>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: {
    alignItems: "center",
    backgroundColor: "#f4f7fb",
    flex: 1,
    justifyContent: "center",
    padding: 24,
  },
  card: {
    backgroundColor: "#ffffff",
    borderColor: "#dbe4f0",
    borderRadius: 24,
    borderWidth: 1,
    maxWidth: 520,
    padding: 28,
    width: "100%",
  },
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
    marginTop: 12,
  },
  description: {
    color: "#536179",
    fontSize: 16,
    lineHeight: 24,
    marginTop: 12,
  },
  badge: {
    alignSelf: "flex-start",
    backgroundColor: "#eef4ff",
    borderRadius: 999,
    color: "#1d4ed8",
    fontSize: 13,
    marginTop: 22,
    overflow: "hidden",
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
});
