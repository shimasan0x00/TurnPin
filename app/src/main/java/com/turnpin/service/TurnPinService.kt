package com.turnpin.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.IBinder
import com.turnpin.MainActivity
import com.turnpin.R
import com.turnpin.core.ApplyResult
import com.turnpin.core.OrientationController
import com.turnpin.model.OrientationMode
import com.turnpin.platform.AndroidPermissionChecker
import com.turnpin.platform.PrefsSettingsStore
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

        private const val CHANNEL_ID = "turnpin_control"
        private const val NOTIF_ID = 1

        // PendingIntent は requestCode が同じだと使い回されるため、用途ごとに別の値を割り当てる。
        private const val REQ_OPEN = 100
        private const val REQ_STOP = 101

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

        private fun command(context: Context, action: String): Intent =
            Intent(context, TurnPinService::class.java).setAction(action)
    }

    private lateinit var overlay: WindowManagerOverlayHandle
    private lateinit var store: PrefsSettingsStore
    private lateinit var controller: OrientationController

    override fun onCreate() {
        super.onCreate()
        store = PrefsSettingsStore(this)
        overlay = WindowManagerOverlayHandle(this, store.overlayStrategy)
        controller = OrientationController(overlay, store, AndroidPermissionChecker(this))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService から 5 秒以内に startForeground する必要があるため、
        // 権限チェックや適用処理より先に呼ぶ。
        startForeground(NOTIF_ID, buildNotification(controller.currentMode()))

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

    override fun onDestroy() {
        super.onDestroy()
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

    // ---- 通知 ---------------------------------------------------------------

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(controller.currentMode()))
    }

    private fun buildNotification(mode: OrientationMode): Notification {
        createChannel()

        val openPending = PendingIntent.getActivity(
            this, REQ_OPEN, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPending = PendingIntent.getService(
            this, REQ_STOP, command(this, ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_notification),
            getString(R.string.notif_stop),
            stopPending
        ).build()

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title, getString(mode.labelResId)))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openPending)
            .addAction(stopAction)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            // IMPORTANCE_LOW: 常駐通知なので音もバナーも出さない。
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = getString(R.string.channel_description) }
            manager.createNotificationChannel(channel)
        }
    }
}
