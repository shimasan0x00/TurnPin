package com.turnpin.core

/**
 * オーバーレイ権限（SYSTEM_ALERT_WINDOW）の判定口。
 *
 * この権限は install 時に付与されず、ユーザーが OS 設定で許可する必要がある。
 * さらに**後から取り消される**ことがあるため、適用のたびに毎回問い合わせる。
 * 実装は [com.turnpin.platform.AndroidPermissionChecker]。
 */
interface PermissionChecker {
    fun canDrawOverlays(): Boolean
}
