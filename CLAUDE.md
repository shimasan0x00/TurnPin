# CLAUDE.md

## 開発ルール

### Git ブランチ

- **main への直接コミット・push は禁止**（グローバル hook で強制）
- 開発は必ず `feature/<作業名>` ブランチを作成して行う

### テスト方針: TDD

1. **TDD:** Red → Green → Refactor．
2. テスト名は仕様（振る舞い）を表す。1テスト1概念。AAA パターン
3. エラーを握りつぶさない．必ず検証すること．

### 本プロジェクト固有

4. 実行時の外部依存を追加しない（AndroidX / Material / Compose 不使用）．テスト用の JUnit4 のみ許可．
5. targetSdk を 30 から上げない．
6. Android フレームワークに触れるコードは interface 越しにし，中核ロジックは JVM 単体テストで検証する．
