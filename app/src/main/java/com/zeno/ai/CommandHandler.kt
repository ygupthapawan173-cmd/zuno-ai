package com.zeno.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Handles phone commands locally (no AI call needed).
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
        val orig = raw.trim().trim('.', '?', '!', ' ')
        val t = orig.lowercase(Locale.ROOT)
        if (t.isEmpty()) return null

        // WhatsApp message: "whatsapp ravi happy birthday"
        Regex(
            "^(?:send\\s+)?(?:a\\s+)?(?:whatsapp|message|msg)\\s+(?:to\\s+)?([\\p{L}]+)\\s+(?:saying\\s+|that\\s+)?(.+)$",
            RegexOption.IGNORE_CASE,
        ).find(orig)?.let { return sendWhatsApp(it.groupValues[1].lowercase(Locale.ROOT), it.groupValues[2]) }

        Regex("^(open|launch|ఓపెన్)\\s+(.+)$").find(t)?.let { return openApp(it.groupValues[2].trim()) }

        Regex("^(call|dial|కాల్)\\s+([+0-9 ]{3,})$").find(t)?.let {
            val number = it.groupValues[2].replace(" ", "")
            return if (start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))) "$number ki dial screen open chesanu"
            else "Dialer open cheyalekapoyanu"
        }

        // call by contact name
        Regex("^(call|dial|కాల్)\\s+([\\p{L}]+)$").find(t)?.let { return callContact(it.groupValues[2]) }

        Regex("^(search|google|సెర్చ్)\\s+(.+)$").find(t)?.let {
            val q = it.groupValues[2].trim()
            return if (start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)))))
                "\"$q\" kosam search chesthunnanu" else "Browser open cheyalekapoyanu"
        }

        Regex("^play\\s+(.+?)(\\s+on youtube)?$").find(t)?.let {
            val q = it.groupValues[1].trim()
            if (q in setOf("music", "song", "songs")) return media(KeyEvent.KEYCODE_MEDIA_PLAY, "Music play chesthunnanu")
            return if (start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q)))))
                "YouTube lo \"$q\" chupisthunnanu" else "YouTube open cheyalekapoyanu"
        }

        // alarms and timers
        if (t.contains("alarm") || t.contains("అలారం")) return setAlarm(t)
        if (t.contains("timer") || t.contains("టైమర్")) return setTimer(t)

        // media and volume
        when (t) {
            "pause", "pause music", "pause song", "stop music" -> return media(KeyEvent.KEYCODE_MEDIA_PAUSE, "Music pause chesanu")
            "resume", "resume music", "continue music" -> return media(KeyEvent.KEYCODE_MEDIA_PLAY, "Music resume chesanu")
            "next song", "next track", "skip song" -> return media(KeyEvent.KEYCODE_MEDIA_NEXT, "Next song")
            "previous song", "previous track", "last song" -> return media(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Previous song")
            "volume up", "increase volume", "sound up" -> return volume(AudioManager.ADJUST_RAISE, "Volume penchanu")
            "volume down", "decrease volume", "sound down" -> return volume(AudioManager.ADJUST_LOWER, "Volume tagginchanu")
            "mute", "volume mute" -> return volume(AudioManager.ADJUST_MUTE, "Mute chesanu")
        }

        // system panels (Android does not let apps flip these directly)
        if (t.length < 30 && (t.contains("wifi") || t.contains("wi-fi"))) {
            val ok = start(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
            return if (ok) "WiFi panel open chesanu. Android security valla nenu direct ga on/off cheyalenu." else "WiFi settings open cheyalekapoyanu"
        }
        if (t.length < 30 && t.contains("bluetooth")) {
            return if (start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))) "Bluetooth settings open chesanu" else "Bluetooth settings open cheyalekapoyanu"
        }
        if (t.length < 30 && t.contains("brightness")) {
            return if (start(Intent(Settings.ACTION_DISPLAY_SETTINGS))) "Display settings open chesanu" else "Settings open cheyalekapoyanu"
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

    // ------------------------------------------------------------ alarms / timers

    private fun setAlarm(t: String): String {
        val m = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(t) ?: return "Alarm time cheppandi, udaharanaki \"alarm 6:30 am\""
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toIntOrNull() ?: 0
        val ampm = m.groupValues[3]
        if (ampm == "pm" && h < 12) h += 12
        if (ampm == "am" && h == 12) h = 0
        if (h > 23 || min > 59) return "Aa time sarigga ledu"
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, h)
            .putExtra(AlarmClock.EXTRA_MINUTES, min)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "Zeno")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return if (start(intent)) "Alarm %02d:%02d set chesanu".format(h, min) else "Alarm set cheyalekapoyanu"
    }

    private fun setTimer(t: String): String {
        val m = Regex("(\\d+)\\s*(hours?|hrs?|minutes?|mins?|seconds?|secs?|గంట\\p{L}*|నిమిష\\p{L}*)").find(t)
            ?: return "Timer entha sepo cheppandi, udaharanaki \"timer 5 minutes\""
        val n = m.groupValues[1].toInt()
        val unit = m.groupValues[2]
        val seconds = when {
            unit.startsWith("h") || unit.startsWith("గ") -> n * 3600
            unit.startsWith("m") || unit.startsWith("న") -> n * 60
            else -> n
        }
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "Zeno")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return if (start(intent)) "$n ${unit} timer start chesanu" else "Timer set cheyalekapoyanu"
    }

    // ------------------------------------------------------------ contacts / whatsapp

    private fun hasContacts(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun findNumber(name: String): String? {
        if (!hasContacts()) return null
        val cursor = ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null, null, null,
        ) ?: return null
        var best: String? = null
        cursor.use { c ->
            while (c.moveToNext()) {
                val n = c.getString(0)?.lowercase(Locale.ROOT) ?: continue
                val num = c.getString(1) ?: continue
                if (n == name) return num
                if (best == null && n.contains(name)) best = num
            }
        }
        return best
    }

    private val noContactsPermission = "Contacts permission ivvandi (Android Settings > Apps > Zeno AI > Permissions)"

    private fun callContact(name: String): String {
        if (!hasContacts()) return noContactsPermission
        val number = findNumber(name) ?: return "\"$name\" contact kanapadaledu"
        return if (start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))) "$name ki dial screen open chesanu"
        else "Dialer open cheyalekapoyanu"
    }

    private fun sendWhatsApp(name: String, message: String): String {
        if (!hasContacts()) return noContactsPermission
        val number = findNumber(name) ?: return "\"$name\" contact kanapadaledu"
        var digits = number.filter { it.isDigit() }
        if (digits.length == 10) digits = "91$digits"
        val uri = Uri.parse("https://wa.me/$digits?text=" + Uri.encode(message))
        return if (start(Intent(Intent.ACTION_VIEW, uri))) "$name ki WhatsApp message ready. Send nokkandi."
        else "WhatsApp open cheyalekapoyanu"
    }

    // ------------------------------------------------------------ media / volume

    private fun media(keyCode: Int, reply: String): String {
        return try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            reply
        } catch (e: Exception) {
            "Media control cheyalekapoyanu"
        }
    }

    private fun volume(direction: Int, reply: String): String {
        return try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            reply
        } catch (e: Exception) {
            "Volume marchalekapoyanu"
        }
    }

    // ------------------------------------------------------------ apps / torch

    fun openApp(spoken: String): String {
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
