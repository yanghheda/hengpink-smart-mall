import { Button, SafeAreaView, StyleSheet, Text } from "react-native";

export function ScreenState({
  text,
  retry,
}: {
  text: string;
  retry?: () => void;
}) {
  return (
    <SafeAreaView style={styles.center}>
      <Text style={styles.message}>{text}</Text>
      {retry ? <Button title="重试" onPress={retry} /> : null}
    </SafeAreaView>
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
  message: { color: "#536179", fontSize: 15, lineHeight: 22 },
});
