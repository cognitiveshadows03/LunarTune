/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.utils

import android.content.Context
import dev.citali.lunartune.constants.IpVersion
import dev.citali.lunartune.constants.IpVersionKey
import dev.citali.lunartune.extensions.toEnum
import okhttp3.Dns
import java.net.Inet4Address
import java.net.Inet6Address

/**
 * OkHttp [Dns] honoring the Network IP version override (ported from vivi-music).
 *
 * AUTO leaves system resolution untouched, while IPV4/IPV6 filter the answer
 * list. The filter falls back to the unfiltered list when the selected family
 * has no address, so resolution never hard-fails. The preference is read live
 * on every lookup, so toggling the setting applies to new connections
 * immediately without rebuilding the HTTP clients.
 */
fun ipVersionDns(context: Context): Dns =
    Dns { hostname ->
        val addresses = Dns.SYSTEM.lookup(hostname)
        when (context.dataStore[IpVersionKey].toEnum(IpVersion.AUTO)) {
            IpVersion.IPV4 -> addresses.filterIsInstance<Inet4Address>().ifEmpty { addresses }
            IpVersion.IPV6 -> addresses.filterIsInstance<Inet6Address>().ifEmpty { addresses }
            IpVersion.AUTO -> addresses
        }
    }
