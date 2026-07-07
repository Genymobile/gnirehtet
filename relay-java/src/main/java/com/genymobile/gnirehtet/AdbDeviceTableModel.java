/*
 * Based on Gnirehtet.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.genymobile.gnirehtet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.table.AbstractTableModel;

public class AdbDeviceTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"Telefono", "Serie", "Estado", "APK", "Conexion"};

    private final List<AdbDevice> devices = new ArrayList<>();
    private final Set<String> activeSerials = new HashSet<>();

    public void setDevices(List<AdbDevice> devices) {
        this.devices.clear();
        this.devices.addAll(devices);
        fireTableDataChanged();
    }

    public void setActiveSerials(Set<String> activeSerials) {
        this.activeSerials.clear();
        this.activeSerials.addAll(activeSerials);
        if (!devices.isEmpty()) {
            fireTableRowsUpdated(0, devices.size() - 1);
        }
    }

    public AdbDevice getDeviceAt(int row) {
        return devices.get(row);
    }

    public int indexOf(String serial) {
        for (int i = 0; i < devices.size(); ++i) {
            if (devices.get(i).getSerial().equals(serial)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getRowCount() {
        return devices.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        AdbDevice device = devices.get(rowIndex);
        switch (columnIndex) {
            case 0:
                return device.getDisplayName();
            case 1:
                return device.getSerial();
            case 2:
                return device.getState();
            case 3:
                return device.getInstallStatus();
            case 4:
                return activeSerials.contains(device.getSerial()) ? "Conectado" : "Desconectado";
            default:
                throw new IllegalArgumentException("Unknown column: " + columnIndex);
        }
    }
}
