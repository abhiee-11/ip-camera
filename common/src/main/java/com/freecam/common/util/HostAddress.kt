package com.freecam.common.util

/** Where the camera is reachable, and whether a tailnet address was found. */
data class HostChoice(
    val host: String?,
    val tailnetIp: String?
)

object HostAddress {

    /**
     * Tailscale uses the CGNAT range 100.64.0.0/10, so 100.64.0.0 - 100.127.255.255.
     * A public 100.x.x.x address outside that range is not a tailnet address.
     */
    fun isTailnetAddress(ip: String): Boolean {
        val octets = ip.split('.')
        if (octets.size != 4) return false
        val first = octets[0].toIntOrNull() ?: return false
        val second = octets[1].toIntOrNull() ?: return false
        return first == 100 && second in 64..127
    }

    /**
     * Picks the address the camera should be reached on. A tailnet address wins;
     * otherwise the first usable LAN address is used. Loopback and malformed
     * addresses are ignored.
     */
    fun selectHost(addresses: List<String>): HostChoice {
        var lan: String? = null
        var tailnet: String? = null
        for (address in addresses) {
            if (!isUsable(address)) continue
            if (isTailnetAddress(address)) {
                if (tailnet == null) tailnet = address
            } else if (lan == null) {
                lan = address
            }
        }
        val tailnetIp = tailnet
        return HostChoice(host = tailnetIp ?: lan, tailnetIp = tailnetIp)
    }

    private fun isUsable(address: String): Boolean {
        if (address.isBlank()) return false
        if (address.startsWith("127.")) return false
        val octets = address.split('.')
        if (octets.size != 4) return false
        return octets.all { it.toIntOrNull() in 0..255 }
    }
}
