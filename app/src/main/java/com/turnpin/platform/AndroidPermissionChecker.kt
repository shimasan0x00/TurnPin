package com.turnpin.platform

import android.content.Context
import android.provider.Settings
import com.turnpin.core.PermissionChecker

/**
 * [PermissionChecker] の実装。
 *
 * SYSTEM_ALERT_WINDOW は install 時に付与されず、ユーザーが OS 設定
 * （`Settings.ACTION_MANAGE_OVERLAY_PERMISSION`）で許可する必要がある。
 * 許可後に**取り消される**こともあるため、キャッシュせず毎回問い合わせる。
 */
class AndroidPermissionChecker(private val context: Context) : PermissionChecker {
    override fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)
}
