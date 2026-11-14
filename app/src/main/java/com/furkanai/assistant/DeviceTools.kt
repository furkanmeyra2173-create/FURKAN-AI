package com.furkanai.assistant

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.StatFs
import android.provider.Settings
import java.util.Locale

object DeviceTools {
    data class LaunchableApp(val label: String, val packageName: String)

    fun deviceSummary(context: Context): String {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memory)
        val runtime = Runtime.getRuntime()
        val totalStorage = StatFs(context.filesDir.absolutePath).totalBytes
        val freeStorage = StatFs(context.filesDir.absolutePath).availableBytes
        val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryPct = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val ramTotal = memory.totalMem / (1024L * 1024L)
        val ramFree = memory.availMem / (1024L * 1024L)
        val storageTotal = totalStorage / (1024L * 1024L)
        val storageFree = freeStorage / (1024L * 1024L)
        val appMaxHeap = runtime.maxMemory() / (1024L * 1024L)
        return "Pil: ${batteryPct.coerceAtLeast(0)}%\n" +
            "RAM: ${ramFree} MB boş / ${ramTotal} MB toplam\n" +
            "Uygulama bellek sınırı: ${appMaxHeap} MB\n" +
            "Uygulama dosya alanı: ${storageFree} MB boş / ${storageTotal} MB toplam"
    }

    fun launchableApps(context: Context): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { info ->
                val appInfo = info.activityInfo?.applicationInfo ?: return@mapNotNull null
                if (appInfo.packageName == context.packageName) return@mapNotNull null
                LaunchableApp(
                    info.loadLabel(context.packageManager).toString(),
                    appInfo.packageName
                )
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
        val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return true
    }

    fun handleOfflineCommand(context: Context, text: String): String? {
        val command = text.trim().lowercase(Locale.getDefault())
        if (command.contains("pil") || command.contains("batarya") ||
            command.contains("battery") || command.contains("ram") ||
            command.contains("depolama") || command.contains("hafıza durumu")
        ) return deviceSummary(context)

        val normalized = command
            .removePrefix("furkan ai")
            .replace("aç", "")
            .replace("başlat", "")
            .replace("open", "")
            .replace("launch", "")
            .replace("uygulamasını", "")
            .replace("uygulama", "")
            .replace("uygulamasını", "")
            .trim()

        val prefixes = listOf("chrome", "youtube", "kamera", "camera", "maps", "haritalar", "ayarlar", "settings")
        val appName = prefixes.firstOrNull { command.contains(it) }
        if (appName != null && (command.contains("aç") || command.contains("başlat") ||
                    command.contains("open") || command.contains("launch"))) {
            val actual = when (appName) {
                "kamera" -> "camera"
                "haritalar" -> "maps"
                "ayarlar" -> "settings"
                else -> appName
            }
            return if (openApp(context, actual)) {
                "${appName.replaceFirstChar { it.uppercase() }} açılıyor."
            } else {
                "Bu uygulamayı bulamadım. Uygulama listesinden açmayı deneyebilirsin."
            }
        }

        if (command.contains("uçak modu")) {
            return "Güvenlik nedeniyle uçak modunu otomatik değiştirmiyorum. Android hızlı ayarlarını açabilirsin."
        }
        if (command.contains("wifi") || command.contains("wi-fi")) {
            context.startActivity(
                Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return "Wi-Fi ayarları açıldı."
        }
        return null
    }
}
