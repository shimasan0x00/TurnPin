package com.turnpin.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.turnpin.MainActivity
import com.turnpin.R
import com.turnpin.model.OrientationMode

/**
 * 常駐通知の組み立て（仕様 §4.3）。
 *
 * ボタンから直接 [TurnPinService] へコマンドを送るので、アプリを開かずに
 * 向きを切り替えられる。**「停止」ボタンは必ず置く**。オーバーレイが原因で
 * 他アプリのボタンが押せなくなる事故（§11-2）から復帰する唯一の手段になる。
 */
internal class NotificationFactory(private val context: Context) {

    /**
     * [mode] を現在値として表示する通知を組み立てる。
     *
     * `DecoratedCustomViewStyle` を使い、ヘッダー（アプリ名・時刻）はシステムに任せて
     * コンテンツ領域だけを差し替える。シェードのテーマに沿った余白・展開操作が
     * そのまま効くため、自前で全体を描くより壊れにくい。
     */
    fun build(mode: OrientationMode): Notification {
        ensureChannel()

        val modeLabel = context.getString(mode.labelResId)
        val title = context.getString(R.string.notif_title, modeLabel)

        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            // カスタムビューを描けない表示先（一部のランチャーや車載など）向けの控え。
            .setContentTitle(title)
            // 折りたたみ時は高さも幅も厳しいので 1 行版を使い、
            // 見出しはモード名だけにする（アプリ名は通知ヘッダーが既に出している）。
            .setCustomContentView(buildViews(R.layout.notification_control, modeLabel))
            // 展開時は余裕があるので 2 行版＋「TurnPin: <モード>」のフル表記。
            .setCustomBigContentView(buildViews(R.layout.notification_control_big, title))
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .build()
    }

    /**
     * 折りたたみ版・展開版に共通の設定を流し込む。
     *
     * 折りたたみ版には [R.id.notifOpen] が無いが、`RemoteViews` の
     * `setOnClickPendingIntent` は存在しない id を黙って無視するので分岐は要らない
     * （通知本体のタップでもアプリは開ける）。
     */
    private fun buildViews(layoutId: Int, title: String): RemoteViews =
        RemoteViews(context.packageName, layoutId).apply {
            setTextViewText(R.id.notifTitle, title)

            setOnClickPendingIntent(R.id.notifOpen, openAppIntent())
            setOnClickPendingIntent(R.id.notifPortrait, applyIntent(OrientationMode.PORTRAIT))
            setOnClickPendingIntent(R.id.notifLandscape, applyIntent(OrientationMode.LANDSCAPE))
            setOnClickPendingIntent(
                R.id.notifPortraitReverse, applyIntent(OrientationMode.PORTRAIT_REVERSE)
            )
            setOnClickPendingIntent(
                R.id.notifLandscapeReverse, applyIntent(OrientationMode.LANDSCAPE_REVERSE)
            )
            setOnClickPendingIntent(R.id.notifAuto, applyIntent(OrientationMode.AUTO))
            setOnClickPendingIntent(R.id.notifStop, stopIntent())
        }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            // IMPORTANCE_LOW: 常駐通知なので音もバナーも出さない。
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = context.getString(R.string.channel_description) }
            manager.createNotificationChannel(channel)
        }
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context, REQ_OPEN, Intent(context, MainActivity::class.java), PENDING_FLAGS
    )

    private fun stopIntent(): PendingIntent = PendingIntent.getService(
        context, REQ_STOP, TurnPinService.command(context, TurnPinService.ACTION_STOP), PENDING_FLAGS
    )

    private fun applyIntent(mode: OrientationMode): PendingIntent = PendingIntent.getService(
        context,
        // requestCode を用途ごとに変える。同じ値だと FLAG_UPDATE_CURRENT で
        // 既存の PendingIntent が使い回され、全ボタンが同じ向きを適用してしまう。
        REQ_MODE_BASE + mode.ordinal,
        TurnPinService.command(context, TurnPinService.ACTION_APPLY)
            .putExtra(TurnPinService.EXTRA_MODE, mode.name),
        PENDING_FLAGS
    )

    private companion object {
        const val CHANNEL_ID = "turnpin_control"

        // FLAG_IMMUTABLE は Android 12 以降で必須（付けないと IllegalArgumentException）。
        const val PENDING_FLAGS =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        const val REQ_OPEN = 100
        const val REQ_STOP = 101
        const val REQ_MODE_BASE = 200
    }
}
