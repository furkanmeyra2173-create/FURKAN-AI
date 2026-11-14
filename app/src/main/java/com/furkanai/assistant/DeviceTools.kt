package com.furkanai.assistant

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.StatFs
import android.provider.CalendarContract
import android.provider.Settings
import java.util.Locale

object DeviceTools {
    data class LaunchableApp(val label: String, val packageName: String)

    fun deviceSummary(context: Context): String {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memory)
        val runtime = Runtime.getRuntime()
        val storage = StatFs(android.os.Environment.getDataDirectory().absolutePath)
        val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryPct = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val ramTotal = memory.totalMem / (1024L * 1024L)
        val ramFree = memory.availMem / (1024L * 1024L)
        val storageTotal = storage.totalBytes / (1024L * 1024L)
        val storageFree = storage.availableBytes / (1024L * 1024L)
        val appHeap = runtime.maxMemory() / (1024L * 1024L)
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val hasNetwork = connectivity.activeNetwork != null
        return "Pil: ${batteryPct.coerceAtLeast(0)}%\n" +
            "RAM: ${ramFree} MB boş / ${ramTotal} MB toplam\n" +
            "Uygulama bellek sınırı: ${appHeap} MB\n" +
            "Dahili depolama: ${storageFree} MB boş / ${storageTotal} MB toplam\n" +
            "Ağ bağlantısı: ${if (hasNetwork) "Bağlı" else "Bağlı değil"}"
    }

    fun launchableApps(context: Context): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { info ->
                val appInfo = info.activityInfo?.applicationInfo ?: return@mapNotNull null
                if (appInfo.packageName == context.packageName) return@mapNotNull null
                LaunchableApp(info.loadLabel(context.packageManager).toString(), appInfo.packageName)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    fun openApp(context: Context, name: String): Boolean {
        val query = name.trim().lowercase(Locale.getDefault())
        if (query.isBlank()) return false
        val app = launchableApps(context).firstOrNull {
            it.label.lowercase(Locale.getDefault()).contains(query) ||
                it.packageName.lowercase(Locale.getDefault()).contains(query)
        } ?: return false
        val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return true
    }

    fun openCalendarEvent(context: Context, title: String, description: String, minutesFromNow: Int) {
        val start = System.currentTimeMillis() + minutesFromNow * 60_000L
        val end = start + 60 * 60_000L
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.Events.DESCRIPTION, description)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun handleOfflineCommand(context: Context, text: String): String? {
        val command = text.trim().lowercase(Locale.getDefault())
        if (command.contains("pil") || command.contains("batarya") || command.contains("battery") ||
            command.contains("ram") || command.contains("depolama") || command.contains("cihaz durumu")
        ) return deviceSummary(context)

        if (command.contains("wi-fi ayar") || command.contains("wifi ayar")) {
            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return "Wi-Fi ayarları açıldı."
        }

        val dialMatch = Regex("(?:ara|telefon et|call)\\s+([+0-9][+0-9() .-]{4,})").find(command)
        if (dialMatch != null) {
            val number = dialMatch.groupValues[1].trim()
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return "Arama ekranı açıldı. Aramayı telefonundan onaylayabilirsin."
        }

        val appsToTry = listOf(
            "chrome", "youtube", "kamera", "camera", "maps", "haritalar", "ayarlar", "settings",
            "whatsapp", "telegram", "spotify", "play store"
        )
        val target = appsToTry.firstOrNull { command.contains(it) }
        if (target != null && listOf("aç", "başlat", "open", "launch").any(command::contains)) {
            val actual = when (target) {
                "kamera" -> "camera"
                "haritalar" -> "maps"
                "ayarlar" -> "settings"
                else -> target
            }
            return if (openApp(context, actual)) "${target.replaceFirstChar { it.uppercase() }} açılıyor."
            else "Bu uygulamayı bulamadım. Uygulama listesinden açmayı deneyebilirsin."
        }
        if (command.contains("uçak modu")) {
            return "Güvenlik nedeniyle uçak modunu otomatik değiştirmiyorum. Hızlı ayarları açabilirsin."
        }
        return null
    }
}
