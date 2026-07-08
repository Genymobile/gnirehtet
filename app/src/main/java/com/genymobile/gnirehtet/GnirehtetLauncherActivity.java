/*
 * Based on Gnirehtet.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.genymobile.gnirehtet;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.VpnService;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Visible Android control panel for the phone-side Gnirehtet client.
 */
@SuppressWarnings({"checkstyle:MagicNumber", "checkstyle:MethodLength"})
public final class GnirehtetLauncherActivity extends Activity {

    private static final int VPN_REQUEST_CODE = 0;
    private static final int COLOR_BACKGROUND = Color.rgb(243, 246, 250);
    private static final int COLOR_SURFACE = Color.WHITE;
    private static final int COLOR_TEXT = Color.rgb(26, 32, 42);
    private static final int COLOR_MUTED = Color.rgb(93, 104, 119);
    private static final int COLOR_BORDER = Color.rgb(218, 226, 237);
    private static final int COLOR_BLUE = Color.rgb(38, 104, 204);
    private static final int COLOR_GREEN = Color.rgb(24, 145, 95);
    private static final int COLOR_RED = Color.rgb(190, 55, 64);
    private static final int COLOR_ORANGE = Color.rgb(196, 124, 35);

    private TextView statusTitle;
    private TextView statusDetail;
    private TextView modeValue;
    private TextView pcValue;
    private TextView vpnValue;
    private TextView relayValue;
    private TextView lastStatusValue;
    private Button offButton;
    private Button onButton;
    private Button autoButton;
    private Button connectButton;
    private Button disconnectButton;

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            statusTitle.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.app_name);
        setContentView(createContentView());
        refreshModeSelection();
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        statusTitle.postDelayed(refreshRunnable, 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        statusTitle.removeCallbacks(refreshRunnable);
    }

    private View createContentView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(COLOR_BACKGROUND);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        scrollView.addView(root, matchWidthWrapHeight());

        root.addView(createHeader(), matchWidthWrapHeight());
        root.addView(createStatusCard(), cardLayout());
        root.addView(createModeCard(), cardLayout());
        root.addView(createDetailsCard(), cardLayout());
        root.addView(createActions(), matchWidthWrapHeight());
        return scrollView;
    }

    private View createHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(18));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.app_icon);
        logo.setBackground(makeRoundRect(Color.WHITE, dp(14), COLOR_BORDER));
        logo.setPadding(dp(7), dp(7), dp(7), dp(7));
        header.addView(logo, fixedSize(dp(58), dp(58)));

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        titleBlock.setPadding(dp(14), 0, 0, 0);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);

        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.android_app_subtitle);
        subtitle.setTextColor(COLOR_MUTED);
        subtitle.setTextSize(14);

        titleBlock.addView(title, matchWidthWrapHeight());
        titleBlock.addView(subtitle, matchWidthWrapHeight());
        header.addView(titleBlock, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return header;
    }

    private View createStatusCard() {
        LinearLayout card = createCard();
        card.setPadding(dp(18), dp(18), dp(18), dp(18));

        TextView label = createLabel(R.string.status_current);
        statusTitle = new TextView(this);
        statusTitle.setTextColor(COLOR_TEXT);
        statusTitle.setTextSize(30);
        statusTitle.setTypeface(Typeface.DEFAULT_BOLD);
        statusTitle.setPadding(0, dp(4), 0, dp(2));

        statusDetail = new TextView(this);
        statusDetail.setTextColor(COLOR_MUTED);
        statusDetail.setTextSize(14);

        card.addView(label, matchWidthWrapHeight());
        card.addView(statusTitle, matchWidthWrapHeight());
        card.addView(statusDetail, matchWidthWrapHeight());
        return card;
    }

    private View createModeCard() {
        LinearLayout card = createCard();
        card.addView(createSectionTitle(R.string.status_mode), matchWidthWrapHeight());

        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.setPadding(0, dp(12), 0, 0);

        offButton = createModeButton(R.string.mode_off, GnirehtetSettings.MODE_OFF);
        onButton = createModeButton(R.string.mode_on, GnirehtetSettings.MODE_ON);
        autoButton = createModeButton(R.string.mode_auto, GnirehtetSettings.MODE_AUTO);
        modes.addView(offButton, segmentedButtonLayout(0));
        modes.addView(onButton, segmentedButtonLayout(1));
        modes.addView(autoButton, segmentedButtonLayout(2));
        card.addView(modes, matchWidthWrapHeight());
        return card;
    }

    private Button createModeButton(int labelResId, final String mode) {
        Button button = new Button(this);
        button.setText(labelResId);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setMode(mode);
            }
        });
        return button;
    }

    private View createDetailsCard() {
        LinearLayout card = createCard();
        modeValue = addStatusRow(card, R.string.status_mode);
        pcValue = addStatusRow(card, R.string.status_pc);
        vpnValue = addStatusRow(card, R.string.status_vpn);
        relayValue = addStatusRow(card, R.string.status_relay);
        lastStatusValue = addStatusRow(card, R.string.status_last_event);
        return card;
    }

    private TextView addStatusRow(LinearLayout panel, int labelResId) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        TextView label = createLabel(labelResId);
        row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView value = new TextView(this);
        value.setTextColor(COLOR_TEXT);
        value.setTextSize(15);
        value.setTypeface(Typeface.DEFAULT_BOLD);
        value.setGravity(Gravity.RIGHT);
        row.addView(value, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        panel.addView(row, matchWidthWrapHeight());
        return value;
    }

    private View createActions() {
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(0, dp(8), 0, 0);

        connectButton = createActionButton(R.string.connect_now, COLOR_BLUE);
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setMode(GnirehtetSettings.MODE_ON);
                requestVpnPermission();
            }
        });

        disconnectButton = createActionButton(R.string.disconnect, COLOR_RED);
        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setMode(GnirehtetSettings.MODE_OFF);
                GnirehtetService.stop(GnirehtetLauncherActivity.this);
            }
        });

        actions.addView(connectButton, matchWidthFixedHeight(dp(52)));
        LinearLayout.LayoutParams disconnectParams = matchWidthFixedHeight(dp(52));
        disconnectParams.setMargins(0, dp(10), 0, 0);
        actions.addView(disconnectButton, disconnectParams);
        return actions;
    }

    private void setMode(String mode) {
        GnirehtetSettings.setMode(this, mode);
        if (GnirehtetSettings.MODE_OFF.equals(mode)) {
            GnirehtetService.stop(this);
        }
        refreshModeSelection();
        refreshStatus();
    }

    private void requestVpnPermission() {
        Intent vpnIntent = VpnService.prepare(this);
        if (vpnIntent == null) {
            GnirehtetService.start(this, new VpnConfiguration());
        } else {
            startActivityForResult(vpnIntent, VPN_REQUEST_CODE);
        }
    }

    private void refreshModeSelection() {
        String mode = GnirehtetSettings.getMode(this);
        styleModeButton(offButton, GnirehtetSettings.MODE_OFF.equals(mode));
        styleModeButton(onButton, GnirehtetSettings.MODE_ON.equals(mode));
        styleModeButton(autoButton, GnirehtetSettings.MODE_AUTO.equals(mode));
    }

    private void refreshStatus() {
        String mode = GnirehtetSettings.getMode(this);
        boolean vpnRunning = GnirehtetSettings.isVpnRunning(this);
        boolean relayConnected = GnirehtetSettings.isRelayConnected(this);

        updateHero(mode, vpnRunning, relayConnected);
        modeValue.setText(getModeLabel(mode));
        String pcName = GnirehtetSettings.getPcName(this);
        pcValue.setText(pcName.length() == 0 ? getString(R.string.status_unknown_pc) : pcName);
        vpnValue.setText(vpnRunning ? R.string.status_on : R.string.status_off);
        vpnValue.setTextColor(vpnRunning ? COLOR_GREEN : COLOR_MUTED);
        relayValue.setText(relayConnected ? R.string.status_connected : R.string.status_disconnected);
        relayValue.setTextColor(relayConnected ? COLOR_GREEN : COLOR_RED);

        String lastStatus = GnirehtetSettings.getLastStatus(this);
        if (lastStatus.length() == 0) {
            lastStatus = getString(R.string.status_idle);
        }
        lastStatusValue.setText(lastStatus);

        connectButton.setEnabled(true);
        disconnectButton.setEnabled(vpnRunning || !GnirehtetSettings.MODE_OFF.equals(mode));
    }

    private void updateHero(String mode, boolean vpnRunning, boolean relayConnected) {
        if (relayConnected) {
            statusTitle.setText(R.string.status_connected);
            statusTitle.setTextColor(COLOR_GREEN);
            statusDetail.setText(R.string.android_status_connected_detail);
        } else if (vpnRunning) {
            statusTitle.setText(R.string.status_waiting_pc);
            statusTitle.setTextColor(COLOR_ORANGE);
            statusDetail.setText(R.string.android_status_waiting_detail);
        } else if (GnirehtetSettings.MODE_OFF.equals(mode)) {
            statusTitle.setText(R.string.status_disconnected);
            statusTitle.setTextColor(COLOR_RED);
            statusDetail.setText(R.string.android_status_off_detail);
        } else {
            statusTitle.setText(R.string.status_ready);
            statusTitle.setTextColor(COLOR_BLUE);
            statusDetail.setText(R.string.android_status_ready_detail);
        }
    }

    private String getModeLabel(String mode) {
        if (GnirehtetSettings.MODE_ON.equals(mode)) {
            return getString(R.string.mode_on);
        }
        if (GnirehtetSettings.MODE_AUTO.equals(mode)) {
            return getString(R.string.mode_auto);
        }
        return getString(R.string.mode_off);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK && GnirehtetSettings.acceptsConnection(this)) {
            GnirehtetService.start(this, new VpnConfiguration());
        }
        refreshStatus();
    }

    private LinearLayout createCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(makeRoundRect(COLOR_SURFACE, dp(14), COLOR_BORDER));
        return card;
    }

    private TextView createSectionTitle(int labelResId) {
        TextView title = new TextView(this);
        title.setText(labelResId);
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        return title;
    }

    private TextView createLabel(int labelResId) {
        TextView label = new TextView(this);
        label.setText(labelResId);
        label.setTextColor(COLOR_MUTED);
        label.setTextSize(13);
        return label;
    }

    private Button createActionButton(int labelResId, int color) {
        Button button = new Button(this);
        button.setText(labelResId);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(16);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(makeRoundRect(color, dp(12), color));
        return button;
    }

    private void styleModeButton(Button button, boolean selected) {
        int fillColor = selected ? COLOR_BLUE : Color.rgb(239, 243, 248);
        int textColor = selected ? Color.WHITE : COLOR_MUTED;
        button.setTextColor(textColor);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(makeRoundRect(fillColor, dp(10), selected ? COLOR_BLUE : COLOR_BORDER));
    }

    private GradientDrawable makeRoundRect(int fillColor, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private LinearLayout.LayoutParams matchWidthWrapHeight() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWidthFixedHeight(int height) {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height);
    }

    private LinearLayout.LayoutParams cardLayout() {
        LinearLayout.LayoutParams params = matchWidthWrapHeight();
        params.setMargins(0, 0, 0, dp(14));
        return params;
    }

    private LinearLayout.LayoutParams segmentedButtonLayout(int index) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(46), 1);
        params.setMargins(index == 0 ? 0 : dp(5), 0, index == 2 ? 0 : dp(5), 0);
        return params;
    }

    private LinearLayout.LayoutParams fixedSize(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
