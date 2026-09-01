import { StyleSheet, Text, View } from "react-native";

export function EmptyTab({ label }: { label: string }) {
  return (
    <View style={styles.container}>
      <Text style={styles.icon}>□</Text>
      <Text style={styles.title}>{label}</Text>
      <Text style={styles.text}>这里暂时还没有内容</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: "center",
    flex: 1,
    justifyContent: "center",
    paddingBottom: 60,
  },
  icon: { color: "#dedede", fontSize: 56 },
  title: { color: "#333333", fontSize: 21, fontWeight: "700", marginTop: 14 },
  text: { color: "#aaaaaa", fontSize: 13, marginTop: 8 },
});
