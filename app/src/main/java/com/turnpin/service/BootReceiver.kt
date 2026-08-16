package com.turnpin.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.turnpin.platform.AndroidPermissionChecker
import com.turnpin.platform.PrefsSettingsStore

/**
 * 端末起動時に前回の状態へ復帰させる（仕様 §4.5）。
 *
 * `start_on_boot` と `enabled` の両方が true のときだけサービスを前面起動する。
 * オーバーレイ権限が失効していた場合は**サービスを起動せず通知も出さない**
 * （起動しても PermissionDenied で畳むだけなので、通知が一瞬出るのを避ける）。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val store = PrefsSettingsStore(context)
        if (!store.startOnBoot || !store.enabled) return
        if (!AndroidPermissionChecker(context).canDrawOverlays()) return

        TurnPinService.restore(context)
    }
}
