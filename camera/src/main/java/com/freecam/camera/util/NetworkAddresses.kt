package com.freecam.camera.util

import com.freecam.common.util.HostAddress
import com.freecam.common.util.HostChoice
import java.net.Inet4Address
import java.net.NetworkInterface

/** Finds the addresses the camera can be reached on. */
object NetworkAddresses {

    /** Every non-loopback IPv4 address on this device, in interface order. */
    fun localIpv4Addresses(): List<String> {
        val addresses = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val ifaceAddresses = iface.inetAddresses
                while (ifaceAddresses.hasMoreElements()) {
                    val address = ifaceAddresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        addresses.add(address.hostAddress ?: continue)
                    }
                }
            }
        } catch (e: Exception) {
            // A missing address is not fatal; the dashboard shows it as unknown.
        }
        return addresses
    }

    /** Tailnet address if there is one, otherwise the LAN address. */
    fun hostChoice(): HostChoice = HostAddress.selectHost(localIpv4Addresses())
}
