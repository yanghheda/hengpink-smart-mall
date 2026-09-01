import { useState } from "react";
import {
  Button,
  SafeAreaView,
  StyleSheet,
  Text,
  TextInput,
} from "react-native";

import type { ApiClient } from "../api/client";
import { credentialStore } from "../services";
import { createSessionController } from "../auth/sessionController";
import { deviceSessionId } from "../config/runtime";

export function LoginScreen({
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
      await controller.login({ account, password, deviceSessionId });
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
      <Text style={styles.notice}>全部商品与价格均为项目模拟数据</Text>
      {error ? <Text style={styles.error}>{error}</Text> : null}
      <TextInput
        accessibilityLabel="账号"
        style={styles.input}
        value={account}
        onChangeText={setAccount}
      />
      <TextInput
        accessibilityLabel="密码"
        secureTextEntry
        style={styles.input}
        value={password}
        onChangeText={setPassword}
      />
      <Button
        title={submitting ? "登录中…" : "登录"}
        disabled={submitting}
        onPress={submit}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  page: { backgroundColor: "#f4f7fb", flex: 1, padding: 20 },
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
  notice: { color: "#8a4b08", marginBottom: 18 },
  error: { color: "#b42318", marginBottom: 12 },
  input: {
    backgroundColor: "#ffffff",
    borderColor: "#dbe4f0",
    borderRadius: 12,
    borderWidth: 1,
    marginBottom: 12,
    minHeight: 48,
    paddingHorizontal: 14,
  },
});
