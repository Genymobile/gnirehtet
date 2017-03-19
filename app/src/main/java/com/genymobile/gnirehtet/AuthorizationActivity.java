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
