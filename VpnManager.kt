package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.provider.Settings

object VpnManager {
    private val packages = listOf(
        "com.cloudflare.onedotonedotonedotone", "com.nordvpn.android", "com.expressvpn.vpn",
        "com.protonvpn.android", "com.tunnelbear.android"
    )
    fun openVpn(context: Context, name: String = ""): Boolean {
        val q = name.lowercase()
        val pkg = when {
            q.contains("warp") || q.contains("کلادفلر") || q.contains("1.1.1.1") -> "com.cloudflare.onedotonedotonedotone"
            q.contains("nord") -> "com.nordvpn.android"
            q.contains("express") -> "com.expressvpn.vpn"
            q.contains("proton") -> "com.protonvpn.android"
            q.contains("tunnelbear") -> "com.tunnelbear.android"
            else -> packages.firstOrNull { context.packageManager.getLaunchIntentForPackage(it) != null }
        }
        if (pkg != null) {
            val i = context.packageManager.getLaunchIntentForPackage(pkg)
            if (i != null) return try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(i); true } catch (_: Exception) { false }
        }
        return try { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (_: Exception) { false }
    }
}
