#!/usr/bin/env bash
set -u

# P00-S02：只检查本机工具链，不安装、升级、启动或修改任何系统状态。

# 非交互 shell 可能没有加载用户的 Homebrew PATH；优先补充 Homebrew 的
# opt 链接，不修改系统，只影响本次检查进程。
if command -v brew >/dev/null 2>&1; then
  brew_prefix=$(brew --prefix 2>/dev/null || true)
  for candidate in \
    "$brew_prefix/opt/openjdk/bin" \
    "$brew_prefix/opt/python@3.12/bin" \
    "$brew_prefix/opt/python/bin"; do
    if [ -d "$candidate" ]; then
      PATH="$candidate:$PATH"
    fi
  done
  while IFS= read -r formula; do
    candidate=$(brew --prefix "$formula" 2>/dev/null)/bin
    if [ -d "$candidate" ]; then
      PATH="$candidate:$PATH"
    fi
  done < <(brew list --formula 2>/dev/null | awk '/^python(@|$)/')
  export PATH
fi

min_version() {
  local actual="$1" required="$2"
  awk -v a="$actual" -v b="$required" 'BEGIN {
    na=split(a,A,"."); nb=split(b,B,"."); n=(na>nb?na:nb)
    for (i=1; i<=n; i++) {
      x=(i<=na?A[i]+0:0); y=(i<=nb?B[i]+0:0)
      if (x<y) exit 1; if (x>y) exit 0
    }
    exit 0
  }'
}

extract_version() {
  sed -E 's/[^0-9]*([0-9]+(\.[0-9]+){0,2}).*/\1/' <<< "$1"
}

errors=0
warnings=0
printf '%s\n' 'HengPick Smart Mall local toolchain check (read-only)'

check_version() {
  local label="$1" command_name="$2" version_command="$3" required="$4"
  if ! command -v "$command_name" >/dev/null 2>&1; then
    printf 'FAIL %-12s missing (%s >= %s)\n' "$label" "$command_name" "$required"
    errors=$((errors + 1))
    return
  fi
  local raw actual
  raw=$(eval "$version_command" 2>&1 | head -n 1)
  actual=$(extract_version "$raw")
  if [ -z "$actual" ] || ! min_version "$actual" "$required"; then
    printf 'FAIL %-12s found=%s required>=%s\n' "$label" "${actual:-unknown}" "$required"
    errors=$((errors + 1))
  else
    printf 'PASS %-12s %s (required >= %s)\n' "$label" "$actual" "$required"
  fi
}

check_version 'Java' java 'java -version' '21'
check_version 'Node' node 'node --version' '22.13'
check_version 'Python' python3 'python3 --version' '3.12'
check_version 'Maven' mvn 'mvn --version' '3.6.3'

if ! command -v docker >/dev/null 2>&1; then
  printf '%s\n' 'INFO Docker       unavailable on host; expected when infra runs in VMware'
else
  docker_version=$(docker --version 2>&1 | extract_version)
  if [ -z "$docker_version" ] || ! min_version "$docker_version" '24'; then
    printf 'WARN Docker       found=%s but local Docker baseline is >=24\n' "${docker_version:-unknown}"
    warnings=$((warnings + 1))
  else
    printf 'PASS Docker       %s (required >= 24)\n' "$docker_version"
  fi
  if docker compose version >/dev/null 2>&1; then
    printf 'PASS Compose      Docker Compose v2 available\n'
  else
    printf '%s\n' 'WARN Compose      Docker Compose v2 unavailable; use VMware VM endpoint instead'
    warnings=$((warnings + 1))
  fi
fi

if command -v xcodebuild >/dev/null 2>&1; then
  printf 'PASS Xcode        %s\n' "$(xcodebuild -version 2>/dev/null | head -n 1)"
else
  printf '%s\n' 'WARN Xcode        unavailable; required only for iOS App build/verification'
  warnings=$((warnings + 1))
fi

printf 'Summary: %d failure(s), %d warning(s)\n' "$errors" "$warnings"
if [ "$errors" -gt 0 ]; then
  printf '%s\n' 'Action: install or activate the missing dependency, then rerun this read-only check.'
  exit 1
fi
exit 0
