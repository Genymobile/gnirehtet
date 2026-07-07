/*
 * Based on Gnirehtet.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.genymobile.gnirehtet;

import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;

public class AppTrafficTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {
        "Aplicacion", "UID", "Recibido", "Enviado", "Velocidad", "Promedio", "Total"
    };

    private final List<AppTrafficStats> rows = new ArrayList<>();

    public void setRows(List<AppTrafficStats> rows) {
        this.rows.clear();
        this.rows.addAll(rows);
        fireTableDataChanged();
    }

    public AppTrafficStats getRowAt(int rowIndex) {
        return rows.get(rowIndex);
    }

    @Override
    public int getRowCount() {
        return rows.size();
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
    public Class<?> getColumnClass(int columnIndex) {
        if (columnIndex == 0 || columnIndex == 1) {
            return String.class;
        }
        return Long.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        AppTrafficStats stats = rows.get(rowIndex);
        switch (columnIndex) {
            case 0:
                return stats.getAppName();
            case 1:
                return stats.getUidLabel();
            case 2:
                return stats.getReceivedBytes();
            case 3:
                return stats.getSentBytes();
            case 4:
                return stats.getCurrentBytesPerSecond();
            case 5:
                return stats.getAverageBytesPerSecond();
            case 6:
                return stats.getTotalBytes();
            default:
                throw new IllegalArgumentException("Unknown column: " + columnIndex);
        }
    }
}
