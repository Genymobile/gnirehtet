package com.genymobile.gnirehtet

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle

/**
 * This (invisible) activity receives the [START][.ACTION_GNIREHTET_START] and
 * [.ACTION_GNIREHTET_STOP] actions from the command line.
 *
 *
 * Recent versions of Android refuse to directly start a [Service][android.app.Service] or a
 * [BroadcastReceiver][android.content.BroadcastReceiver], so actions are always managed by
 * this activity.
 */
class GnirehtetActivity : Activity() {
    companion object {
        private val TAG = DLog.forTag(GnirehtetActivity::class.java)

        const val ACTION_GNIREHTET_START: String = "com.genymobile.gnirehtet.START"
        const val ACTION_GNIREHTET_STOP: String = "com.genymobile.gnirehtet.STOP"

        const val EXTRA_DNS_SERVERS: String = "dnsServers"
        const val EXTRA_ROUTES: String = "routes"

        private const val VPN_REQUEST_CODE = 0

        private fun createConfig(intent: Intent): VpnConfiguration {
            var dnsServers = intent.getStringArrayExtra(EXTRA_DNS_SERVERS)
            if (dnsServers == null) {
                dnsServers = arrayOfNulls<String>(0)
            }
            var routes = intent.getStringArrayExtra(EXTRA_ROUTES)
            if (routes == null) {
                routes = arrayOfNulls<String>(0)
            }
            return VpnConfiguration(Net.toInetAddresses(*dnsServers), Net.toCIDRs(*routes))
        }
    }

    private var requestedConfig: VpnConfiguration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(getIntent())
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.getAction()
        DLog.debug(TAG, "Received request " + action)
        var finish = true
        if (ACTION_GNIREHTET_START == action) {
            val config: VpnConfiguration = createConfig(intent)
            finish = startGnirehtet(config)
        } else if (ACTION_GNIREHTET_STOP == action) {
            stopGnirehtet()
        }

        if (finish) {
            finish()
        }
    }

    private fun startGnirehtet(config: VpnConfiguration?): Boolean {
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent == null) {
            DLog.debug(TAG, "VPN was already authorized")
            // we got the permission, start the service now
            GnirehtetService.start(this, config)
            return true
        }

        DLog.warning(TAG, "VPN requires the authorization from the user, requesting...")
        requestAuthorization(vpnIntent, config)
        return false // do not finish now
    }

    private fun stopGnirehtet() {
        GnirehtetService.stop(this)
    }

    private fun requestAuthorization(vpnIntent: Intent?, config: VpnConfiguration?) {
        this.requestedConfig = config
        startActivityForResult(vpnIntent, VPN_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            GnirehtetService.start(this, requestedConfig)
        }
        requestedConfig = null
        finish()
    }
}