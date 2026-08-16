package com.turnpin.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Display
import com.turnpin.R
import com.turnpin.core.ApplyResult
import com.turnpin.core.OrientationController
import com.turnpin.model.OrientationAxis
import com.turnpin.model.OrientationMode
import com.turnpin.platform.AndroidPermissionChecker
import com.turnpin.platform.PrefsSettingsStore
import com.turnpin.platform.SystemRotationSync
import com.turnpin.platform.WindowManagerOverlayHandle

/**
 * オーバーレイを保持する前面 Service。
 *
 * **[OrientationController] の実体はプロセス内でここだけが持つ。** Activity 側にも
 * 持たせると多重 addView になり、アプリを閉じた瞬間にオーバーレイが外れてしまう。
 * MainActivity も通知ボタンも、どちらも Intent コマンドとしてここへ届く。
 *
 * 状態が変わったら [ACTION_STATE_CHANGED] をブロードキャストし、
 * MainActivity の UI を追従させる。
 */
class TurnPinService : Service() {

    companion object {
        const val ACTION_APPLY = "com.turnpin.action.APPLY"
        const val ACTION_STOP = "com.turnpin.action.STOP"
        const val ACTION_RESTORE = "com.turnpin.action.RESTORE"

        /** 状態変化を UI へ知らせるブロードキャスト。 */
        const val ACTION_STATE_CHANGED = "com.turnpin.action.STATE_CHANGED"

        const val EXTRA_MODE = "mode"

        /** 適用に失敗したときだけ載る、ユーザー向けのメッセージ。 */
        const val EXTRA_ERROR = "error"

        private const val NOTIF_ID = 1

        /** [mode] を適用する（制御が OFF なら自動的に ON になる）。 */
        fun apply(context: Context, mode: OrientationMode) {
            context.startForegroundService(
                command(context, ACTION_APPLY).putExtra(EXTRA_MODE, mode.name)
            )
        }

        /** 制御を停止し、OS 標準の挙動に戻す。 */
        fun stop(context: Context) {
            context.startForegroundService(command(context, ACTION_STOP))
        }

        /** 保存済みの状態から復帰する（アプリ起動時 / BOOT_COMPLETED 時）。 */
        fun restore(context: Context) {
            context.startForegroundService(command(context, ACTION_RESTORE))
        }

        /** 通知のボタンからも同じコマンドを送るため [NotificationFactory] へ公開する。 */
        internal fun command(context: Context, action: String): Intent =
            Intent(context, TurnPinService::class.java).setAction(action)
    }

    private lateinit var overlay: WindowManagerOverlayHandle
    private lateinit var store: PrefsSettingsStore
    private lateinit var controller: OrientationController
    private lateinit var notifications: NotificationFactory
    private lateinit var rotationSync: SystemRotationSync

    /**
     * 表示状態の変化を拾ってドリフトを検出する（仕様 §4.6）。
     * ポーリングは使わない。バッテリーを無駄に食ううえ Fire OS で殺されやすい。
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId != Display.DEFAULT_DISPLAY) return
            reapplyIfDrifted(resources.configuration.orientation)
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = PrefsSettingsStore(this)
        overlay = WindowManagerOverlayHandle(this, store.overlayStrategy)
        controller = OrientationController(overlay, store, AndroidPermissionChecker(this))
        notifications = NotificationFactory(this)
        rotationSync = SystemRotationSync(this)

        // WindowManager の操作はメインスレッド限定なので、コールバックもメインで受ける。
        getSystemService(DisplayManager::class.java)
            .registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService から 5 秒以内に startForeground する必要があるため、
        // 権限チェックや適用処理より先に呼ぶ。
        startForeground(NOTIF_ID, notifications.build(controller.currentMode()))

        applyStrategyIfChanged()

        return when (intent?.action) {
            ACTION_STOP -> {
                controller.stop()
                broadcastState(error = null)
                stopSelf()
                START_NOT_STICKY
            }

            ACTION_APPLY -> {
                val mode = OrientationMode.fromName(intent.getStringExtra(EXTRA_MODE))
                handle(controller.apply(mode))
                // プロセスが殺されても最後の intent を再配達し、向きを復帰させる。
                START_REDELIVER_INTENT
            }

            // ACTION_RESTORE、および intent が null（プロセス再生成）の場合も復帰扱い。
            else -> {
                handle(controller.restoreFromStore())
                START_REDELIVER_INTENT
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        reapplyIfDrifted(newConfig.orientation)
    }

    override fun onDestroy() {
        super.onDestroy()
        getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener)
        // AC9: プロセスが消えるときにオーバーレイを残さない。
        // store.enabled はここでは書き換えない。OS 都合の停止とユーザーの明示停止を
        // 区別しないと、Fire OS にサービスを殺されただけで設定まで OFF になってしまう。
        overlay.detach()
    }

    /** 適用結果を通知・ブロードキャストへ反映する。失敗は必ずユーザーへ伝える。 */
    private fun handle(result: ApplyResult) {
        when (result) {
            ApplyResult.Success -> {
                if (controller.isRunning()) {
                    // §2.4 の補助機能。既定 OFF で、権限が無ければ黙ってスキップされる。
                    if (store.syncSystemSettings) rotationSync.apply(controller.currentMode())
                    updateNotification()
                    broadcastState(error = null)
                } else {
                    // restoreFromStore が「前回 OFF なので何もしない」を返したケース。
                    // 貼るものが無いので前面サービスを畳む。
                    broadcastState(error = null)
                    stopSelf()
                }
            }

            ApplyResult.PermissionDenied -> {
                broadcastState(error = getString(R.string.permission_required))
                stopSelf()
            }

            is ApplyResult.Failed -> {
                val reason = result.cause.message ?: result.cause.javaClass.simpleName
                broadcastState(error = getString(R.string.apply_failed, reason))
                stopSelf()
            }
        }
    }

    private fun broadcastState(error: String?) {
        val intent = Intent(ACTION_STATE_CHANGED)
            .setPackage(packageName)
            .putExtra(EXTRA_MODE, controller.currentMode().name)
        if (error != null) intent.putExtra(EXTRA_ERROR, error)
        sendBroadcast(intent)
    }

    /**
     * 互換モードが設定画面で変わっていたら、貼り方を切り替えられるよう一度剥がす。
     * `updateViewLayout` では LayoutParams の組み立て方までは差し替えられないため、
     * 貼り直しが必要になる。剥がした後の attach は通常の適用経路に任せる。
     */
    private fun applyStrategyIfChanged() {
        val desired = store.overlayStrategy
        if (overlay.strategy == desired) return
        overlay.detach()
        overlay.strategy = desired
    }

    /**
     * 相手アプリに向きを奪われていたら貼り直す（仕様 §4.6）。
     *
     * 判定には `Configuration.orientation` を使う。`Display.rotation` は端末の
     * 自然向きからの回転量なので、そこから期待値を組み立てると機種で壊れる。
     * 上下逆・左右逆までは区別できないが、「横専用アプリに縦を奪われる」という
     * 実際に困るケースは軸の比較で捕まえられる。
     */
    private fun reapplyIfDrifted(configOrientation: Int) {
        if (!controller.isRunning()) return

        val mode = controller.currentMode()
        // AUTO / LOCK_CURRENT はどの向きでも食い違いではない。
        val required = mode.requiredAxis() ?: return

        val actual = when (configOrientation) {
            Configuration.ORIENTATION_PORTRAIT -> OrientationAxis.PORTRAIT
            Configuration.ORIENTATION_LANDSCAPE -> OrientationAxis.LANDSCAPE
            // ORIENTATION_UNDEFINED。判定できないので何もしない。
            else -> return
        }
        if (actual == required) return

        handle(controller.apply(mode))
    }

    /** 現在のモードを通知のタイトルへ反映する。 */
    private fun updateNotification() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, notifications.build(controller.currentMode()))
    }
}
