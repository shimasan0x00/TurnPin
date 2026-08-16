package com.turnpin.platform

import android.content.Context
import android.content.SharedPreferences
import com.turnpin.core.SettingsStore
import com.turnpin.model.OrientationMode
import com.turnpin.model.OverlayStrategy

/**
 * [SettingsStore] の実装（仕様 §4.4）。
 *
 * MainActivity と TurnPinService は同一プロセスで動くため、この
 * SharedPreferences が両者の唯一の状態源になる。
 *
 * enum は名前の文字列で保存する。序数で保存すると enum の並び替えで
 * 意味が変わってしまうため。未知の値・欠損はクラッシュさせず既定へ倒す。
 */
class PrefsSettingsStore(context: Context) : SettingsStore {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    override var mode: OrientationMode
        get() = OrientationMode.fromName(prefs.getString(KEY_MODE, null))
        set(value) = prefs.edit().putString(KEY_MODE, value.name).apply()

    override var startOnBoot: Boolean
        get() = prefs.getBoolean(KEY_START_ON_BOOT, false)
        set(value) = prefs.edit().putBoolean(KEY_START_ON_BOOT, value).apply()

    override var syncSystemSettings: Boolean
        get() = prefs.getBoolean(KEY_SYNC_SYSTEM_SETTINGS, false)
        set(value) = prefs.edit().putBoolean(KEY_SYNC_SYSTEM_SETTINGS, value).apply()

    override var overlayStrategy: OverlayStrategy
        get() = OverlayStrategy.fromName(prefs.getString(KEY_OVERLAY_STRATEGY, null))
        set(value) = prefs.edit().putString(KEY_OVERLAY_STRATEGY, value.name).apply()

    private companion object {
        const val PREFS_NAME = "turnpin_prefs"

        const val KEY_ENABLED = "enabled"
        const val KEY_MODE = "mode"
        const val KEY_START_ON_BOOT = "start_on_boot"
        const val KEY_SYNC_SYSTEM_SETTINGS = "sync_system_settings"
        const val KEY_OVERLAY_STRATEGY = "overlay_strategy"
    }
}
