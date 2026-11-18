package com.zeno.ai

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Handles simple phone commands locally (no AI call needed).
 * Returns a short reply when the text was a command, or null so it goes to the AI.
 */
class CommandHandler(private val ctx: Context) {

    private val aliases = mapOf(
        "యూట్యూబ్" to "youtube",
        "వాట్సాప్" to "whatsapp",
        "కెమెరా" to "camera",
        "సెట్టింగ్స్" to "settings",
        "క్రోమ్" to "chrome",
        "మ్యాప్స్" to "maps",
        "ఇన్స్టాగ్రామ్" to "instagram",
        "జీమెయిల్" to "gmail",
    )

    fun handle(raw: String): String? {
        val t = raw.trim().trim('.', '?', '!', ' ').lowercase(Locale.ROOT)
        if (t.isEmpty()) return null

        Regex("^(open|launch|ఓపెన్)\\s+(.+)$").find(t)?.let { return openApp(it.groupValues[2].trim()) }

        Regex("^(call|dial|కాల్)\\s+([+0-9 ]{3,})$").find(t)?.let {
            val number = it.groupValues[2].replace(" ", "")
            return if (start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))) "$number ki dial screen open chesanu"
            else "Dialer open cheyalekapoyanu"
        }

        Regex("^(search|google|సెర్చ్)\\s+(.+)$").find(t)?.let {
            val q = it.groupValues[2].trim()
            return if (start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)))))
                "\"$q\" kosam search chesthunnanu" else "Browser open cheyalekapoyanu"
        }

        Regex("^play\\s+(.+?)(\\s+on youtube)?$").find(t)?.let {
            val q = it.groupValues[1].trim()
            return if (start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q)))))
                "YouTube lo \"$q\" chupisthunnanu" else "YouTube open cheyalekapoyanu"
        }

        if (t in setOf("torch on", "flashlight on", "టార్చ్ ఆన్")) return torch(true)
        if (t in setOf("torch off", "flashlight off", "టార్చ్ ఆఫ్")) return torch(false)

        if (t == "time" || t.contains("what time") || t.contains("టైమ్ ఎంత") || t.contains("సమయం ఎంత")) {
            return "Ippudu " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
        }
        if (t.contains("what date") || t.contains("today's date") || t.contains("ఈ రోజు తేదీ")) {
            return "Ee roju " + DateFormat.getDateInstance(DateFormat.FULL).format(Date())
        }
        return null
    }

    private fun openApp(spoken: String): String {
        val name = aliases[spoken] ?: spoken
        val pm = ctx.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0)
        val match = apps.firstOrNull { it.loadLabel(pm).toString().lowercase(Locale.ROOT) == name }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase(Locale.ROOT).contains(name) }
        val intent = match?.let { pm.getLaunchIntentForPackage(it.activityInfo.packageName) }
        return if (intent != null && start(intent)) "$name open chesthunnanu"
        else "\"$spoken\" app naku kanapadaledu"
    }

    private fun start(intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }

    private fun torch(on: Boolean): String = try {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        if (id == null) "Ee phone lo flash ledu" else {
            cm.setTorchMode(id, on)
            if (on) "Torch on chesanu" else "Torch off chesanu"
        }
    } catch (e: Exception) {
        "Torch control cheyalekapoyanu"
    }
}
