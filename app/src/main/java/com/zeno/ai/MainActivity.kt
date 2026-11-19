package com.zeno.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import com.zeno.ai.ui.ZenoApp
import com.zeno.ai.ui.theme.ZenoTheme

class MainActivity : ComponentActivity() {
    private lateinit var engine: ZenoEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        engine = ZenoEngine.get(applicationContext)
        setContent {
            ZenoTheme {
                ZenoApp(engine)
            }
        }
        if (savedInstanceState == null) handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    override fun onResume() {
        super.onResume()
        val mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (engine.settings.wake && mic) WakeWordService.start(this)
    }

    /** Text or an image shared from another app ("Share > Zeno AI"). */
    private fun handleShare(i: Intent?) {
        if (i?.action != Intent.ACTION_SEND) return
        val type = i.type ?: return
        engine.openTab = 1
        if (type.startsWith("text/")) {
            i.getStringExtra(Intent.EXTRA_TEXT)?.let {
                engine.send("Ee text ni short ga summarize cheyyi:\n\n$it")
            }
        } else if (type.startsWith("image/")) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
                i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                i.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            uri?.let { u ->
                ImageUtil.toBase64(this, u)?.let { b -> engine.send("Ee image lo em undo cheppu", image = b) }
            }
        }
    }
}
