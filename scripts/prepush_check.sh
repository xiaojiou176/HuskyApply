#!/usr/bin/env bash
set -euo pipefail

# 阈值：单文件 2MB 拦截
MAX_SIZE=$((2*1024*1024))

# 仅检查将被推送的变化
range="$(git rev-parse --abbrev-ref HEAD)@{upstream}..HEAD" 2>/dev/null || range="$(git merge-base --fork-point HEAD 2>/dev/null)..HEAD" 2>/dev/null || range="HEAD~10..HEAD"
files=$(git diff --name-only --diff-filter=ACMRT $range 2>/dev/null || git ls-files)

echo "[CHECK] Pre-push validation for HuskyApply"

# 规则 1：ASCII-only（保证纯英文仓库）
non_ascii_files=""
while IFS= read -r f; do
    if [[ -f "$f" && "$f" != *".git"* ]]; then
        if LC_ALL=C grep -P "[^\x00-\x7F]" "$f" >/dev/null 2>&1; then
            non_ascii_files+="$f "
        fi
    fi
done <<< "$files"

if [[ -n "$non_ascii_files" ]]; then
    echo "[ERROR] Non-ASCII characters found (repository must be English-only):"
    echo "$non_ascii_files"
    exit 1
fi

# 规则 2：禁止 Office/设计文件
if echo "$files" | grep -E '\.(docx?|pptx?|xlsx?|pages|numbers|sketch|fig)$' >/dev/null 2>&1; then
    echo "[ERROR] Office/design files are not allowed."
    echo "$files" | grep -E '\.(docx?|pptx?|xlsx?|pages|numbers|sketch|fig)$'
    exit 1
fi

# 规则 3：大文件拦截
while IFS= read -r f; do
  [ -f "$f" ] || continue
  sz=$(wc -c <"$f" 2>/dev/null || echo 0)
  if [ "$sz" -ge "$MAX_SIZE" ]; then
    echo "[ERROR] Large file blocked: $f ($(numfmt --to=iec $sz 2>/dev/null || echo "$sz bytes"))"
    exit 1
  fi
done <<< "$files"

# 规则 4：常见密钥/凭证特征
secret_files=""
while IFS= read -r f; do
    if [[ -f "$f" && "$f" != *"prepush_check.sh" ]]; then
        if grep -E "(AKIA[0-9A-Z]{16}|ghp_[0-9A-Za-z]{36}|ssh-rsa|-----BEGIN (RSA|OPENSSH) PRIVATE KEY-----)" "$f" >/dev/null 2>&1; then
            secret_files+="$f "
        fi
    fi
done <<< "$files"

if [[ -n "$secret_files" ]]; then
    echo "[ERROR] Potential secrets detected. Remove or replace with env vars:"
    echo "$secret_files"
    exit 1
fi

# 规则 5：AI attribution detection (but allow API integration)
ai_attribution=$(git log -n 20 --pretty=%B 2>/dev/null | grep -Ei '(Generated (by|with).*(Claude|ChatGPT|GPT|Anthropic)|Co-Authored-By:.*(Claude|ChatGPT)|Built (by|with).*(Claude|ChatGPT)|Created (by|with).*(Claude|ChatGPT))' || true)
if [[ -n "$ai_attribution" ]]; then
    echo "[ERROR] AI assistant attribution found in commit messages. Please rewrite:"
    echo "$ai_attribution"
    exit 1
fi

ai_files=""
while IFS= read -r f; do
    if [[ -f "$f" && "$f" != *"prepush_check.sh" ]]; then
        if grep -E -i "(claude|chatgpt|copilot|gpt-?[a-z0-9_-]*)" "$f" >/dev/null 2>&1; then
            ai_files+="$f "
        fi
    fi
done <<< "$files"

if [[ -n "$ai_files" ]]; then
    echo "[ERROR] AI-related wording found in files. Please remove:"
    echo "$ai_files"
    exit 1
fi

echo "[SUCCESS] pre-push checks passed."