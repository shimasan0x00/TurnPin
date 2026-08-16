import java.util.Properties

plugins {
    // AGP 9 の Kotlin ビルトインサポートにより、これ 1 つで Kotlin もコンパイルされる。
    id("com.android.application")
}

// 署名情報は keystore.properties（Git 管理外）か環境変数（CI）から読む。
// どちらも無い場合は release を未署名でビルドする（fork / クリーンチェックアウトを壊さないため）。
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingValue(propKey: String, envKey: String): String? =
    keystoreProps.getProperty(propKey) ?: System.getenv(envKey)

val releaseStoreFile: String? = signingValue("storeFile", "TURNPIN_STORE_FILE")

android {
    namespace = "com.turnpin"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.turnpin"
        minSdk = 28      // Fire OS 7 = Android 9 = API 28（Fire OS 7/8 の Fire タブレットをカバー）
        // targetSdk 30 は据え置き必須。Android 12（31）でオーバーレイのタッチ透過に制限が入り、
        // Android 16/17 系では大画面での固定向き宣言が無視される。上げると中核機能が壊れる。
        targetSdk = 30
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        // 鍵が用意されているときだけ "release" を定義する。
        val storePath = releaseStoreFile
        if (storePath != null) {
            create("release") {
                storeFile = file(storePath)
                storePassword = signingValue("storePassword", "TURNPIN_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "TURNPIN_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "TURNPIN_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // 鍵が無ければ null ＝ 未署名。CI は apksigner verify で署名を必ず検証する。
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // ExpiredTargetSdkVersion は「Google Play は API 33 以上を要求する」という
        // Play 専用の要件で、lintVitalRelease が release ビルドを致命的エラーで止める。
        // 本アプリの配信先はサイドロードのみで Play には出さない。
        disable += "ExpiredTargetSdkVersion"
    }
}

dependencies {
    // 端末非依存の純粋ロジック（OrientationMode / OrientationController）を
    // JVM 上で検証する plain JUnit。実行時の外部依存は一切追加しない。
    testImplementation("junit:junit:4.13.2")
}
