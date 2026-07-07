/*
 * Based on Gnirehtet.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.genymobile.gnirehtet;

public class AdbDevice {

    private final String serial;
    private final String state;
    private final String model;
    private final String product;
    private final String gnirehtetVersion;

    public AdbDevice(String serial, String state, String model, String product) {
        this(serial, state, model, product, null);
    }

    public AdbDevice(String serial, String state, String model, String product, String gnirehtetVersion) {
        this.serial = serial;
        this.state = state;
        this.model = model;
        this.product = product;
        this.gnirehtetVersion = gnirehtetVersion;
    }

    public String getSerial() {
        return serial;
    }

    public String getState() {
        return state;
    }

    public String getModel() {
        return model;
    }

    public String getProduct() {
        return product;
    }

    public String getGnirehtetVersion() {
        return gnirehtetVersion;
    }

    public String getInstallStatus() {
        if (!isReady()) {
            return "sin acceso";
        }
        return isGnirehtetInstalled() ? gnirehtetVersion : "no instalado";
    }

    public boolean isGnirehtetInstalled() {
        return gnirehtetVersion != null && !gnirehtetVersion.isEmpty();
    }

    public boolean isReady() {
        return "device".equals(state);
    }

    public AdbDevice withGnirehtetVersion(String version) {
        return new AdbDevice(serial, state, model, product, version);
    }

    public String getDisplayName() {
        if (model != null && !model.isEmpty()) {
            return model.replace('_', ' ');
        }
        if (product != null && !product.isEmpty()) {
            return product.replace('_', ' ');
        }
        return serial;
    }
}
