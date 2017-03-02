package com.genymobile.gnirehtet;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class AuthorizationActivity extends Activity {

    public static final String KEY_VPN_INTENT = "com.genymobile.gnirehtet.VPN_INTENT";

    private static final int VPN_REQUEST_CODE = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent vpnIntent = getIntent().getParcelableExtra(KEY_VPN_INTENT);
        startActivityForResult(vpnIntent, VPN_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            startVpnService();
        }
        finish();
    }

    private void startVpnService() {
        startService(new Intent(this, GnirehtetService.class));
    }
}
