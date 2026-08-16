package com.turnpin.core

/**
 * 向きの適用結果。
 *
 * 失敗は必ずこの型で表現し、握りつぶさない。呼び出し側（Service / Activity）は
 * [PermissionDenied] なら権限誘導、[Failed] なら理由を Toast で出す。
 */
sealed interface ApplyResult {

    /** オーバーレイへの適用に成功した。 */
    data object Success : ApplyResult

    /** オーバーレイ権限が無い。オーバーレイには一切触れていない。 */
    data object PermissionDenied : ApplyResult

    /** オーバーレイ操作が例外を投げた。[cause] は投げられた例外そのもの。 */
    data class Failed(val cause: Throwable) : ApplyResult
}
