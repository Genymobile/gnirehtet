package com.genymobile.gnirehtet;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class AuthorizationActivity extends Activity {

    public static final String EXTRA_VPN_INTENT = "com.genymobile.gnirehtet.VPN_INTENT";
    public static final String EXTRA_VPN_CONFIGURATION = "vpnConfiguration";

    private static final int VPN_REQUEST_CODE = 0;

    private VpnConfiguration config;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        Intent vpnIntent = intent.getParcelableExtra(EXTRA_VPN_INTENT);
        config = intent.getParcelableExtra(EXTRA_VPN_CONFIGURATION);
        startActivityForResult(vpnIntent, VPN_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            GnirehtetService.start(this, config);
        }
        finish();
    }
}
