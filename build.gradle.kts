// トップレベル build ファイル。AGP 9 は Kotlin をビルトインで内蔵するため
// org.jetbrains.kotlin.android は適用しない（適用すると新 DSL と競合する）。
plugins {
    id("com.android.application") version "9.1.1" apply false
}
