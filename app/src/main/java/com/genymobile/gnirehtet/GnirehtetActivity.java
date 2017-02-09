package com.genymobile.gnirehtet;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;

public class GnirehtetActivity extends Activity {

    private static final int VPN_REQUEST_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startVpn();
    }

    private void startVpn() {
        Intent vpnIntent = VpnService.prepare(this);
        if (vpnIntent == null) {
            startVpnService();
        } else {
            startActivityForResult(vpnIntent, VPN_REQUEST_CODE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            startVpnService();
        }
    }

    public void startVpnService() {
        startService(new Intent(this, GnirehtetService.class));
        finish();
    }
}
