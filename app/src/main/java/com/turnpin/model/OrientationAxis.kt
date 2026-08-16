package com.turnpin.model

/**
 * 画面の向きの「軸」。縦か横かだけを表し、上下・左右の別は持たない。
 *
 * ドリフト再適用（仕様 §4.6）の判定に使う。`Display.rotation` は端末の自然向きに
 * 依存する（Fire Max 11 の自然向きは横）ため、そこから期待値を組み立てると機種で壊れる。
 * 一方 `Configuration.orientation` は自然向きに依存せず縦横を返すので、こちらと突き合わせる。
 */
enum class OrientationAxis {
    PORTRAIT,
    LANDSCAPE,
}
