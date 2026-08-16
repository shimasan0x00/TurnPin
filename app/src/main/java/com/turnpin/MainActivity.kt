package com.turnpin

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.turnpin.model.OrientationMode
import com.turnpin.model.OverlayStrategy
import com.turnpin.platform.AndroidPermissionChecker
import com.turnpin.platform.PrefsSettingsStore
import com.turnpin.platform.SystemRotationSync
import com.turnpin.service.TurnPinService

/**
 * TurnPin の唯一の Activity。
 *
 * 自分ではオーバーレイを持たず、[TurnPinService] へ Intent コマンドを送るだけにしている
 * （オーバーレイの所有者を 1 つに保つため）。状態は [PrefsSettingsStore] から読み、
 * 通知など Activity 外での変更は [TurnPinService.ACTION_STATE_CHANGED] で追従する。
 *
 * この Activity 自身も TurnPin のオーバーレイで回されるので、
 * Manifest に `android:screenOrientation` は宣言しない。
 */
class MainActivity : Activity() {

    private lateinit var store: PrefsSettingsStore
    private lateinit var permissions: AndroidPermissionChecker
    private lateinit var rotationSync: SystemRotationSync

    private lateinit var enabledSwitch: Switch
    private lateinit var currentModeText: TextView
    private lateinit var statusText: TextView
    private lateinit var permissionCard: View
    private lateinit var startOnBootCheck: CheckBox
    private lateinit var syncSystemCheck: CheckBox
    private lateinit var compatGroup: RadioGroup

    /** 表示中のモードボタン。選択状態の付け替えに使う。 */
    private val modeButtons = LinkedHashMap<OrientationMode, Button>()

    /** 通知の「停止」など、この Activity の外で状態が変わったときに UI を追従させる。 */
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != TurnPinService.ACTION_STATE_CHANGED) return
            // 失敗は握りつぶさずユーザーへ見せる。
            intent.getStringExtra(TurnPinService.EXTRA_ERROR)?.let { showMessage(it) }
            syncUi()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = PrefsSettingsStore(this)
        permissions = AndroidPermissionChecker(this)
        rotationSync = SystemRotationSync(this)

        enabledSwitch = findViewById(R.id.enabledSwitch)
        currentModeText = findViewById(R.id.currentModeText)
        statusText = findViewById(R.id.statusText)
        permissionCard = findViewById(R.id.permissionCard)
        startOnBootCheck = findViewById(R.id.startOnBootCheck)
        syncSystemCheck = findViewById(R.id.syncSystemCheck)
        compatGroup = findViewById(R.id.compatGroup)

        setupPermissionCard()
        setupSwitch()
        setupModeButtons()
        setupSettings()
    }

    override fun onResume() {
        super.onResume()
        registerStateReceiver()
        syncUi()

        // Fire OS にサービスを停止されていても、開き直せば貼り直る（自己修復）。
        // restoreFromStore は attach 済みなら更新だけなので、毎回呼んでも二重にはならない。
        if (store.enabled && permissions.canDrawOverlays()) TurnPinService.restore(this)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(stateReceiver)
    }

    /**
     * 自アプリ内だけで飛ぶブロードキャストなので、受け口も非公開にする。
     * 送信側は `setPackage` 済みだが、API 33 以降は受信側でも明示が必要になる
     * （targetSdk 30 では強制されないものの、意図を明示しておく）。
     *
     * lint は else 側（API 32 以下）にもフラグを要求するが、その API レベルには
     * フラグ付きのオーバーロードが存在しない。AndroidX の ContextCompat も
     * 「実行時の外部依存を追加しない」方針により使えないため、ここは抑制する。
     */
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerStateReceiver() {
        val filter = IntentFilter(TurnPinService.ACTION_STATE_CHANGED)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stateReceiver, filter)
        }
    }

    // ---- セットアップ --------------------------------------------------------

    private fun setupPermissionCard() {
        findViewById<Button>(R.id.permissionButton).setOnClickListener {
            // package: を付けると TurnPin の行が選択された状態で設定画面が開く。
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                // 一部の ROM はこの画面を持たない。手動で辿ってもらう案内を出す。
                showMessage(getString(R.string.permission_settings_missing))
            }
        }
    }

    private fun setupSwitch() {
        // setOnCheckedChangeListener だと syncUi の isChecked 代入でも発火してしまうため、
        // ユーザーのタップだけを拾える setOnClickListener を使う。
        enabledSwitch.setOnClickListener {
            if (enabledSwitch.isChecked) selectMode(store.mode) else TurnPinService.stop(this)
        }
    }

    private fun setupModeButtons() {
        // 方向グリッド（3×3 の十字）
        bindModeButton(R.id.btnPortrait, OrientationMode.PORTRAIT)
        bindModeButton(R.id.btnLandscape, OrientationMode.LANDSCAPE)
        bindModeButton(R.id.btnPortraitReverse, OrientationMode.PORTRAIT_REVERSE)
        bindModeButton(R.id.btnLandscapeReverse, OrientationMode.LANDSCAPE_REVERSE)
        bindModeButton(R.id.btnLockCurrent, OrientationMode.LOCK_CURRENT)

        // その他のモード
        bindModeButton(R.id.btnAuto, OrientationMode.AUTO)
        bindModeButton(R.id.btnPortraitSensor, OrientationMode.PORTRAIT_SENSOR)
        bindModeButton(R.id.btnLandscapeSensor, OrientationMode.LANDSCAPE_SENSOR)
    }

    private fun setupSettings() {
        startOnBootCheck.setOnClickListener {
            store.startOnBoot = startOnBootCheck.isChecked
        }

        syncSystemCheck.setOnClickListener {
            if (!syncSystemCheck.isChecked) {
                store.syncSystemSettings = false
                return@setOnClickListener
            }
            // WRITE_SETTINGS は別途ユーザーの許可が要る。未許可なら設定画面へ送り、
            // チェックは戻しておく（許可されたかは onResume の syncUi で反映される）。
            if (rotationSync.canSync()) {
                store.syncSystemSettings = true
            } else {
                syncSystemCheck.isChecked = false
                showMessage(getString(R.string.write_settings_required))
                openWriteSettings()
            }
        }

        compatGroup.setOnCheckedChangeListener { _, checkedId ->
            val selected = COMPAT_OPTIONS.entries.firstOrNull { it.value == checkedId }?.key
                ?: return@setOnCheckedChangeListener
            // syncUi の check() でもこのリスナは発火するので、変化が無ければ何もしない。
            if (selected == store.overlayStrategy) return@setOnCheckedChangeListener

            store.overlayStrategy = selected
            // 貼り方そのものが変わるので、適用中なら貼り直す
            // （updateViewLayout では LayoutParams の作り方までは差し替えられない）。
            if (store.enabled && permissions.canDrawOverlays()) {
                TurnPinService.apply(this, store.mode)
            }
        }
    }

    private fun openWriteSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:$packageName")
        )
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // 一部の ROM はこの画面を持たない。手動で辿ってもらう案内を出す。
            showMessage(getString(R.string.write_settings_missing))
        }
    }

    /** ラベルは [OrientationMode.labelResId] から流し込む（表示名の定義を散らさない）。 */
    private fun bindModeButton(viewId: Int, mode: OrientationMode) {
        val button = findViewById<Button>(viewId)
        button.setText(mode.labelResId)
        button.setOnClickListener { selectMode(mode) }
        modeButtons[mode] = button
    }

    // ---- 操作 ---------------------------------------------------------------

    /**
     * [mode] を適用する。制御が停止中でもボタンは無効化せず、
     * 押されたら自動的に制御を開始する（操作を 1 タップ減らすため。仕様 §4.3）。
     */
    private fun selectMode(mode: OrientationMode) {
        if (!permissions.canDrawOverlays()) {
            syncUi()  // 権限カードを出し、Switch を実態に戻す
            showMessage(getString(R.string.permission_required))
            return
        }
        TurnPinService.apply(this, mode)
    }

    // ---- 表示の同期 ----------------------------------------------------------

    private fun syncUi() {
        val granted = permissions.canDrawOverlays()
        // 権限は OS 設定からいつでも取り消せるので、再開のたびに評価し直す（AC8）。
        permissionCard.visibility = if (granted) View.GONE else View.VISIBLE

        // 権限が無ければ、保存上 ON でも実際には何も適用されていない。
        val running = store.enabled && granted
        val mode = store.mode

        enabledSwitch.isChecked = running
        currentModeText.setText(mode.labelResId)
        statusText.setText(if (running) R.string.status_running else R.string.status_stopped)

        modeButtons.forEach { (candidate, button) ->
            button.isSelected = candidate == mode
        }

        startOnBootCheck.isChecked = store.startOnBoot
        // WRITE_SETTINGS も後から取り消せるので、権限と設定の AND を表示する。
        syncSystemCheck.isChecked = store.syncSystemSettings && rotationSync.canSync()
        COMPAT_OPTIONS[store.overlayStrategy]?.let(compatGroup::check)
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private companion object {
        /** 互換モードと RadioButton の対応。両方向に引けるようここ 1 箇所で定義する。 */
        val COMPAT_OPTIONS = mapOf(
            OverlayStrategy.A to R.id.compatA,
            OverlayStrategy.B to R.id.compatB,
            OverlayStrategy.C to R.id.compatC,
            OverlayStrategy.D to R.id.compatD,
        )
    }
}
