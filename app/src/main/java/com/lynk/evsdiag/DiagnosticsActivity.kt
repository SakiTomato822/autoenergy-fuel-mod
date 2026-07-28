package com.lynk.dvrprobe

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat

class DiagnosticsActivity : AppCompatActivity() {
    private lateinit var logText: TextView
    private lateinit var logScroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.initialize(this)
        AppLog.i("UI", "diagnostics opened")
        setContentView(buildContent())
        enterImmersiveMode()
        refreshLog()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    private fun buildContent(): View {
        val typeface = ResourcesCompat.getFont(this, R.font.lynkco_type_medium)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 28, 39))
            setPadding(dp(28), dp(20), dp(28), dp(20))
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(
            TextView(this).apply {
                text = "数据诊断日志"
                textSize = 28f
                setTextColor(Color.WHITE)
                this.typeface = typeface
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )

        fun actionButton(label: String, action: () -> Unit): Button = Button(this).apply {
            text = label
            textSize = 16f
            isAllCaps = false
            this.typeface = typeface
            setOnClickListener { action() }
        }

        titleRow.addView(actionButton("刷新", ::refreshLog))
        titleRow.addView(actionButton("复制日志") {
            copyToClipboard("AutoEnergy 日志", AppLog.readText())
        })
        titleRow.addView(actionButton("复制路径") {
            copyToClipboard("AutoEnergy 日志路径", AppLog.path())
        })
        titleRow.addView(actionButton("清空") {
            AppLog.clear()
            refreshLog()
        })
        titleRow.addView(actionButton("返回") { finish() })
        root.addView(titleRow)

        root.addView(TextView(this).apply {
            text = "长按主页面左侧卡片可再次打开  ·  ${AppLog.path()}"
            textSize = 15f
            setTextColor(Color.rgb(141, 183, 204))
            setPadding(0, dp(8), 0, dp(12))
            this.typeface = typeface
        })

        logText = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.rgb(220, 232, 238))
            setTextIsSelectable(true)
            setPadding(dp(18), dp(14), dp(18), dp(20))
            this.typeface = typeface
        }
        val horizontal = HorizontalScrollView(this).apply {
            isFillViewport = true
            addView(logText)
        }
        logScroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(12, 40, 53))
            addView(horizontal)
        }
        root.addView(
            logScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        return root
    }

    private fun refreshLog() {
        if (!::logText.isInitialized) return
        logText.text = AppLog.readText()
        logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
    }

    private fun enterImmersiveMode() {
        window.decorView.windowInsetsController?.apply {
            hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
