---
name: release-checklist
description: |
  リリース前チェックを標準化する手順。
  ユーザーが「リリース前確認」「最終チェック」を依頼した時に使う。
---

# Release Checklist

## Checklist
1. `./gradlew spotlessCheck`
2. `./gradlew test`
3. 重要エンドポイントの request ファイル更新確認（`requests/*.http`）
4. `docs/en` と `docs/ja` の差分確認
5. 機密情報混入チェック

## Output
- PASS/FAIL の一覧
- FAIL がある場合はブロック理由と修正案
