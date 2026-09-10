/*
 * Copyright (C) 2017 Genymobile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.genymobile.gnirehtet

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Message
import android.os.ParcelFileDescriptor
import java.io.IOException
import java.net.InetAddress

class GnirehtetService : VpnService() {
    companion object {
        private val TAG = DLog.forTag(GnirehtetService::class.java)

        const val ACTION_START_VPN = "com.genymobile.gnirehtet.START_VPN"
        const val ACTION_CLOSE_VPN = "com.genymobile.gnirehtet.CLOSE_VPN"
        private const val EXTRA_VPN_CONFIGURATION = "vpnConfiguration"

        private val VPN_ADDRESS: InetAddress = Net.toInetAddress(byteArrayOf(10, 0, 0, 2))

        // magic value: higher (like 0x8000 or 0xffff) or lower (like 1500) values show poorer performances
        private const val MTU = 0x4000

        fun start(context: Context, config: VpnConfiguration?) {
            val intent = Intent(context, GnirehtetService::class.java)
            intent.setAction(ACTION_START_VPN)
            intent.putExtra(EXTRA_VPN_CONFIGURATION, config)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(createStopIntent(context))
            } else {
                context.startService(createStopIntent(context))
            }
        }

        fun createStopIntent(context: Context?): Intent {
            val intent = Intent(context, GnirehtetService::class.java)
            intent.action = ACTION_CLOSE_VPN
            return intent
        }
    }

    private val notifier = Notifier(this)
    private val handler: Handler = RelayTunnelConnectionStateHandler(this)

    private var vpnInterface: ParcelFileDescriptor? = null
    private var forwarder: Forwarder? = null

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val action = intent.getAction()
        DLog.debug(TAG, "Received request " + action)
        if (ACTION_START_VPN == action) {
            if (this.isRunning) {
                DLog.debug(TAG, "VPN already running, ignore START request")
            } else {
                var config = intent.getParcelableExtra<VpnConfiguration?>(EXTRA_VPN_CONFIGURATION)
                if (config == null) {
                    config = VpnConfiguration()
                }
                startVpn(config)
            }
        } else if (ACTION_CLOSE_VPN == action) {
            close()
        }
        return START_NOT_STICKY
    }

    private val isRunning: Boolean
        get() = vpnInterface != null

    private fun startVpn(config: VpnConfiguration) {
        notifier.start()
        if (setupVpn(config)) {
            startForwarding()
        }
    }

    private fun setupVpn(config: VpnConfiguration): Boolean {
        val builder =  Builder()
        builder.addAddress(VPN_ADDRESS, 32)
        builder.setSession(getString(R.string.app_name))

        val routes = config.routes
        if (routes?.size == 0) {
            // no routes defined, redirect the whole network traffic
            builder.addRoute("0.0.0.0", 0)
        } else {
            if (routes != null) {
                for (route in routes) {
                    builder.addRoute(route?.address!!, route.prefixLength)
                }
            }
        }

        val dnsServers = config.dnsServers
        if (dnsServers.size == 0) {
            // no DNS server defined, use Google DNS
            builder.addDnsServer("8.8.8.8")
        } else {
            for (dnsServer in dnsServers) {
                if (dnsServer != null) {
                    builder.addDnsServer(dnsServer)
                }
            }
        }

        // non-blocking by default, but FileChannel is not selectable, that's stupid!
        // so switch to synchronous I/O to avoid polling
        builder.setBlocking(true)
        builder.setMtu(MTU)

        vpnInterface = builder.establish()
        if (vpnInterface == null) {
            DLog.warning(TAG, "VPN starting failed, please retry")
            // establish() may return null if the application is not prepared or is revoked
            return false
        }

        setAsUndernlyingNetwork()
        return true
    }

    private fun setAsUndernlyingNetwork() {
        val vpnNetwork = findVpnNetwork()
        if (vpnNetwork != null) {
            // so that applications knows that network is available
            setUnderlyingNetworks(arrayOf<Network>(vpnNetwork))
        }
    }

    private fun findVpnNetwork(): Network? {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val networks = cm.getAllNetworks()
        for (network in networks) {
            val linkProperties = cm.getLinkProperties(network)
            val addresses = linkProperties!!.getLinkAddresses()
            for (addr in addresses) {
                if (addr.getAddress() == VPN_ADDRESS) {
                    return network
                }
            }
        }
        return null
    }

    private fun startForwarding() {
        forwarder =
            Forwarder(this, vpnInterface!!.getFileDescriptor(), RelayTunnelListener(handler))
        forwarder!!.forward()
    }

    private fun close() {
        if (!this.isRunning) {
            // already closed
            return
        }

        notifier.stop()

        try {
            forwarder!!.stop()
            forwarder = null
            vpnInterface!!.close()
            vpnInterface = null
        } catch (e: IOException) {
            DLog.exception(TAG, "Cannot close VPN file descriptor", e)
        }
    }


    private class RelayTunnelConnectionStateHandler(private val vpnService: GnirehtetService) :
        Handler() {
        override fun handleMessage(message: Message) {
            if (!vpnService.isRunning) {
                // if the VPN is not running anymore, ignore obsolete events
                return
            }
            when (message.what) {
                RelayTunnelListener.MSG_RELAY_TUNNEL_CONNECTED -> {
                    DLog.debug(TAG, "Relay tunnel connected")
                    vpnService.notifier.setFailure(false)
                }

                RelayTunnelListener.MSG_RELAY_TUNNEL_DISCONNECTED -> {
                    DLog.debug(TAG, "Relay tunnel disconnected")
                    vpnService.notifier.setFailure(true)
                }

                else -> {}
            }
        }
    }
}