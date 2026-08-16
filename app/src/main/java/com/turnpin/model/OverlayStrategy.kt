package com.turnpin.model

/**
 * オーバーレイの貼り方の戦略（仕様 §2.3 のフォールバック階段）。
 *
 * 端末・ROM によってはオーバーレイが向き決定に参加しない／`updateViewLayout` で
 * 向きが反映されないことがある。実機で効かなかったときに設定画面から
 * 上から順に試せるよう、実装を差し替え可能にしておく。
 *
 * Android 非依存なので [com.turnpin.core.SettingsStore] がそのまま永続化できる。
 */
enum class OverlayStrategy {
    /** 既定。0x0 サイズ・FLAG_NOT_TOUCHABLE・`updateViewLayout` で更新。 */
    A,

    /** 1x1 サイズ・透明背景。0x0 を無視する ROM 向け。 */
    B,

    /** 更新のたびに `removeView` → `addView` で貼り直す。update が効かない ROM 向け。 */
    C,

    /**
     * MATCH_PARENT × MATCH_PARENT の全画面。完全透明・タッチ透過だが、
     * 他アプリのタッチに影響が出うるので**最終手段**であり既定にしない。
     */
    D,
    ;

    companion object {
        val DEFAULT = A

        /** 永続化された名前から引く。未知の値は [DEFAULT] にフォールバックする。 */
        fun fromName(name: String?): OverlayStrategy =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
