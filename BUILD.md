# ビルド手順（WSL2 / sudo・apt 版）

WSL2 上で `sudo` が使える環境向けの、環境構築 → ビルドまでのコピペ手順。

検証済みバージョン: AGP 9.1.1 / Gradle 9.3.1 / JDK 17 / build-tools 36.0.0 / compileSdk 36 / minSdk 28 / targetSdk 30。
（AGP 9.1.1 は Gradle 9.3.1 以上が必須。）

---

## 1. JDK 17 と補助ツール（apt）

```bash
sudo apt update && sudo apt install -y openjdk-17-jdk unzip wget
java -version    # 17.x が出れば OK
# 必要なら JAVA_HOME も設定（apt の既定パス）
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
```

## 2. Android cmdline-tools を Google 公式 zip から取得

```bash
mkdir -p ~/android-sdk/cmdline-tools && cd ~/android-sdk/cmdline-tools
wget https://dl.google.com/android/repository/commandlinetools-linux-14742923_latest.zip
unzip -q commandlinetools-linux-*.zip
mv cmdline-tools latest       # bin/ が latest/ 直下に来る構成にする（重要）
```

## 3. 環境変数（`~/.bashrc` に追記して永続化）

```bash
export ANDROID_HOME=$HOME/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools
```

## 4. SDK パッケージ導入

```bash
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
```

> Gradle Wrapper（`gradlew` / `gradle-wrapper.jar`）は同梱済み。`gradle wrapper` の生成手順は不要。

## 5. SDK の場所を Gradle に伝える + ビルド

```bash
cd /path/to/TurnPin
echo "sdk.dir=$HOME/android-sdk" > local.properties
./gradlew assembleDebug   # 初回は gradlew が Gradle 9.3.1 を自動取得
# 生成物: app/build/outputs/apk/debug/app-debug.apk
```

## 6. テスト・静的解析

```bash
./gradlew test        # 中核ロジックの JVM 単体テスト（Android 実機・エミュ不要）
./gradlew lintDebug   # レポート: app/build/reports/lint-results-debug.html
```

---

## サイドロード（Fire Max 11）

1. `app/build/outputs/apk/debug/app-debug.apk` をクラウド / USB メモリ等で Fire へ転送。
2. Fire 側「設定 > セキュリティとプライバシー > 不明ソースからのアプリ」を許可。
3. ファイルマネージャで APK をタップしてインストール。
4. 初回起動時に **「他のアプリの上に表示」** 権限を許可する（これが無いと動作しない）。

> WSL2 は USB を直接認識しないため `adb` の直結はできません。usbipd-win による USB パススルー、Windows 側の `adb install`、または adb over TCP/IP を使ってください。

---

## release 署名ビルド

署名情報は `keystore.properties`（Git 管理外）か環境変数から読みます。**どちらも無い場合、release は未署名（`app-release-unsigned.apk`）になります。** fork やクリーンチェックアウトでビルドが壊れないようにするための挙動です。

### 鍵を作る

```bash
keytool -genkeypair -v -keystore ~/turnpin-release.jks \
  -alias turnpin -keyalg RSA -keysize 2048 -validity 10000
```

### ローカルでビルドする

```bash
cp keystore.properties.example keystore.properties
# keystore.properties を自分の鍵に合わせて編集してから
./gradlew assembleRelease
# 生成物: app/build/outputs/apk/release/app-release.apk

# 署名を必ず確認する
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

### CI（GitHub Actions）

`.github/workflows/build-apk.yml` が `main` への push と `v*` タグで走ります。以下の Secrets を登録してください。

| Secret | 内容 |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 ~/turnpin-release.jks` の出力 |
| `KEYSTORE_PASSWORD` | キーストアのパスワード |
| `KEY_ALIAS` | 鍵のエイリアス（例: `turnpin`） |
| `KEY_PASSWORD` | 鍵のパスワード |

ワークフローは `test` → `assembleRelease` → `apksigner verify` の順に実行します。Secrets が未登録だと APK が未署名（ファイル名が `app-release-unsigned.apk`）になり、`apksigner verify` のステップで失敗します。これは意図した fail-fast で、**未署名の APK が Releases に出ることはありません。**
