/*
 * Based on Gnirehtet.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.genymobile.gnirehtet;

import com.genymobile.gnirehtet.relay.TrafficStats;

import java.awt.AWTException;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.RowFilter;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;

@SuppressWarnings({"checkstyle:MagicNumber", "checkstyle:MethodLength"})
public final class GnirehtetGui extends JFrame {

    private static final Color BACKGROUND = new Color(241, 244, 248);
    private static final Color SURFACE = Color.WHITE;
    private static final Color INK = new Color(28, 34, 43);
    private static final Color MUTED = new Color(101, 111, 125);
    private static final Color BORDER = new Color(221, 227, 235);
    private static final Color GREEN = new Color(27, 150, 100);
    private static final Color BLUE = new Color(42, 106, 201);
    private static final Color RED = new Color(195, 68, 72);
    private static final Color ORANGE = new Color(191, 122, 38);
    private static final Color TEAL = new Color(22, 139, 121);
    private static final Color TABLE_SELECTION = new Color(226, 238, 255);
    private static final Color DISABLED_BUTTON = new Color(218, 225, 235);
    private static final Color DISABLED_BUTTON_TEXT = new Color(86, 99, 118);
    private static final String ICON_RESOURCE = "/com/genymobile/gnirehtet/app_icon.png";

    private static final int DEFAULT_WIDTH = 1160;
    private static final int DEFAULT_HEIGHT = 820;
    private static final int RIGHT_PANEL_WIDTH = 470;
    private static final int RIGHT_PANEL_GAP = 12;
    private static final int STAT_PANEL_HEIGHT = 142;
    private static final int APP_TRAFFIC_MIN_HEIGHT = 240;
    private static final int DETAIL_TABLE_HEIGHT = 440;
    private static final int DEVICE_REFRESH_DELAY = 2000;
    private static final int STATS_REFRESH_DELAY = 1000;
    private static final int APP_STATS_REFRESH_DELAY = 2500;

    private final DesktopGnirehtetService service = new DesktopGnirehtetService();
    private final ExecutorService executor = Executors.newSingleThreadExecutor((runnable) -> {
        Thread thread = new Thread(runnable, "gnirehtet-gui-worker");
        thread.setDaemon(true);
        return thread;
    });

    private final AdbDeviceTableModel deviceModel = new AdbDeviceTableModel();
    private final JTable deviceTable = new JTable(deviceModel);
    private final AppTrafficTableModel appTrafficModel = new AppTrafficTableModel();
    private final JTable appTrafficTable = new JTable(appTrafficModel);
    private final Map<String, AppTrafficWindow> appTrafficWindows = new HashMap<>();
    private final PowerSwitch powerSwitch = new PowerSwitch();
    private final JToggleButton autoToggle = new JToggleButton("Auto");
    private final JLabel selectedLabel = new JLabel("Sin telefono seleccionado");
    private final JLabel connectionSummary = new JLabel("Esperando telefonos");
    private final JLabel appTrafficTitle = new JLabel("Consumo por aplicacion");
    private final JLabel appTrafficStatus = new JLabel("Selecciona un telefono activo");
    private final JSpinner portSpinner = new JSpinner(new SpinnerNumberModel(31416, 1, 65535, 1));
    private final JTextField dnsField = new JTextField("8.8.8.8");
    private final JTextField routesField = new JTextField("0.0.0.0/0");
    private final JButton refreshButton = createButton("Actualizar", BLUE);
    private final JButton installButton = createButton("Instalar APK", INK);
    private final JButton reinstallButton = createButton("Reinstalar APK", INK);
    private final JButton uninstallButton = createButton("Desinstalar APK", RED);
    private final JButton tunnelButton = createButton("Reset tunnel", INK);
    private final JButton clearLogButton = createButton("Limpiar terminal", INK);
    private final JMenuItem relayMenuItem = new JMenuItem();
    private final JTextArea logArea = new JTextArea();
    private final StatPanel incomingPanel = new StatPanel("Entrante por Relay", BLUE);
    private final StatPanel outgoingPanel = new StatPanel("Saliente por Relay", GREEN);
    private final SimpleDateFormat logTimeFormat = new SimpleDateFormat("HH:mm:ss");
    private final Set<String> lastNotifiedActiveSerials = new HashSet<>();

    private TrafficStats.Snapshot lastSnapshot;
    private long lastSnapshotTime;
    private TrayIcon trayIcon;
    private String preferredSerial;
    private boolean updatingControls;
    private boolean busy;
    private boolean refreshingDevices;
    private boolean refreshingAppTraffic;

    private GnirehtetGui() {
        super("Gnirehtet Desktop");
        configureWindow();
        configureNotifications();
        setJMenuBar(createMenuBar());
        setContentPane(createContent());
        wireActions();
        startTimers();
        refreshDevices(false);
    }

    public static void open() {
        SwingUtilities.invokeLater(() -> {
            setSystemLookAndFeel();
            new GnirehtetGui().setVisible(true);
        });
    }

    public static void main(String... args) {
        open();
    }

    private static void setSystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Keep Swing's default look and feel if the system one is not available.
        }
    }

    private void configureWindow() {
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1040, 760));
        setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        setLocationRelativeTo(null);
        Image image = loadAppIcon();
        if (image != null) {
            setIconImage(image);
        }
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                closeApplication();
            }
        });
    }

    private JPanel createContent() {
        JPanel root = new JPanel(new BorderLayout(16, 16));
        root.setBackground(BACKGROUND);
        root.setBorder(new EmptyBorder(18, 18, 18, 18));
        root.add(createHeader(), BorderLayout.NORTH);
        root.add(createMainArea(), BorderLayout.CENTER);
        root.add(createLogPanel(), BorderLayout.SOUTH);
        return root;
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 8));
        header.setOpaque(false);

        JPanel titlePanel = new JPanel(new GridLayout(2, 1, 0, 2));
        titlePanel.setOpaque(false);
        JLabel title = new JLabel("Gnirehtet Desktop");
        title.setForeground(INK);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 25f));
        JLabel subtitle = new JLabel("Reverse tethering por ADB");
        subtitle.setForeground(MUTED);
        subtitle.setFont(subtitle.getFont().deriveFont(13f));
        titlePanel.add(title);
        titlePanel.add(subtitle);

        header.add(titlePanel, BorderLayout.WEST);

        connectionSummary.setOpaque(true);
        connectionSummary.setForeground(INK);
        connectionSummary.setBackground(new Color(232, 241, 253));
        connectionSummary.setBorder(new EmptyBorder(8, 14, 8, 14));
        connectionSummary.setFont(connectionSummary.getFont().deriveFont(Font.BOLD, 12f));
        header.add(connectionSummary, BorderLayout.EAST);
        return header;
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu connectionMenu = new JMenu("Conexion");
        connectionMenu.add(createMenuItem("Consumo de aplicaciones", this::openSelectedAppTrafficWindow));
        relayMenuItem.addActionListener((event) -> toggleRelayServer());
        connectionMenu.add(relayMenuItem);
        updateRelayMenuItem();

        JMenu helpMenu = new JMenu("Ayuda");
        helpMenu.add(createMenuItem("Guia rapida", this::showCommandHelp));

        menuBar.add(connectionMenu);
        menuBar.add(helpMenu);
        return menuBar;
    }

    private JMenuItem createMenuItem(String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener((event) -> action.run());
        return item;
    }

    private JPanel createMainArea() {
        JPanel main = new JPanel(new BorderLayout(16, 0));
        main.setOpaque(false);
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createDevicePanel(), createRightPanel());
        splitPane.setResizeWeight(0.72);
        splitPane.setOneTouchExpandable(true);
        splitPane.setBorder(null);
        splitPane.setOpaque(false);
        SwingUtilities.invokeLater(() -> splitPane.setDividerLocation(0.72));
        main.add(splitPane, BorderLayout.CENTER);
        return main;
    }

    private JPanel createDevicePanel() {
        JPanel panel = createPanel();
        panel.setLayout(new GridBagLayout());
        panel.setMinimumSize(new Dimension(620, 470));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel label = createSectionTitle("Telefonos disponibles");
        top.add(label, BorderLayout.WEST);
        top.add(refreshButton, BorderLayout.EAST);

        configureDeviceTable();
        JScrollPane scrollPane = new JScrollPane(deviceTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER));
        scrollPane.getViewport().setBackground(SURFACE);
        scrollPane.setMinimumSize(new Dimension(0, 170));
        scrollPane.setPreferredSize(new Dimension(0, 185));

        GridBagConstraints topConstraints = new GridBagConstraints();
        topConstraints.gridx = 0;
        topConstraints.gridy = 0;
        topConstraints.weightx = 1;
        topConstraints.fill = GridBagConstraints.HORIZONTAL;
        topConstraints.insets = new Insets(0, 0, 12, 0);
        panel.add(top, topConstraints);

        GridBagConstraints tableConstraints = new GridBagConstraints();
        tableConstraints.gridx = 0;
        tableConstraints.gridy = 1;
        tableConstraints.weightx = 1;
        tableConstraints.weighty = 1;
        tableConstraints.fill = GridBagConstraints.BOTH;
        tableConstraints.insets = new Insets(0, 0, 12, 0);
        panel.add(scrollPane, tableConstraints);

        GridBagConstraints controlsConstraints = new GridBagConstraints();
        controlsConstraints.gridx = 0;
        controlsConstraints.gridy = 2;
        controlsConstraints.weightx = 1;
        controlsConstraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(createControlsPanel(), controlsConstraints);
        return panel;
    }

    private void configureDeviceTable() {
        deviceTable.setRowHeight(38);
        deviceTable.setShowGrid(false);
        deviceTable.setSelectionBackground(TABLE_SELECTION);
        deviceTable.setSelectionForeground(INK);
        deviceTable.setFillsViewportHeight(true);
        deviceTable.setDefaultRenderer(Object.class, new DeviceCellRenderer());
        deviceTable.setAutoCreateRowSorter(true);
        deviceTable.getSelectionModel().addListSelectionListener((event) -> {
            if (!event.getValueIsAdjusting()) {
                onSelectedDeviceChanged();
            }
        });
        deviceTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && event.getButton() == MouseEvent.BUTTON1) {
                    openSelectedAppTrafficWindow();
                }
            }

            @Override
            public void mousePressed(MouseEvent event) {
                if (SwingUtilities.isRightMouseButton(event)) {
                    showDeviceContextMenu(event);
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                if (event.isPopupTrigger() && !SwingUtilities.isRightMouseButton(event)) {
                    showDeviceContextMenu(event);
                }
            }
        });
        deviceTable.getColumnModel().getColumn(0).setPreferredWidth(150);
        deviceTable.getColumnModel().getColumn(1).setPreferredWidth(140);
        deviceTable.getColumnModel().getColumn(2).setPreferredWidth(90);
        deviceTable.getColumnModel().getColumn(3).setPreferredWidth(120);
        deviceTable.getColumnModel().getColumn(4).setPreferredWidth(125);

        JTableHeader header = deviceTable.getTableHeader();
        header.setReorderingAllowed(false);
        header.setBackground(new Color(248, 250, 252));
        header.setForeground(MUTED);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 12f));
    }

    private void showDeviceContextMenu(MouseEvent event) {
        int row = deviceTable.rowAtPoint(event.getPoint());
        if (row < 0) {
            return;
        }
        deviceTable.getSelectionModel().setSelectionInterval(row, row);
        AdbDevice device = getSelectedDeviceOrNull();
        if (device == null) {
            return;
        }

        boolean ready = device.isReady();
        boolean connected = service.isDeviceActive(device.getSerial());
        JPopupMenu menu = new JPopupMenu();
        JMenuItem startItem = createMenuItem("Iniciar conexion", this::startSelectedDevice);
        JMenuItem stopItem = createMenuItem("Detener conexion", this::stopSelectedDevice);
        JMenuItem restartItem = createMenuItem("Reiniciar conexion", this::restartSelectedDevice);

        startItem.setEnabled(!busy && ready && !connected);
        stopItem.setEnabled(!busy && ready && connected);
        restartItem.setEnabled(!busy && ready);

        menu.add(startItem);
        menu.add(stopItem);
        menu.add(restartItem);
        menu.show(deviceTable, event.getX(), event.getY());
    }

    private JPanel createControlsPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setOpaque(false);
        panel.add(createPowerPanel(), BorderLayout.NORTH);
        panel.add(createOptionsPanel(), BorderLayout.CENTER);
        panel.add(createActionPanel(), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createPowerPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 0));
        panel.setOpaque(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.setOpaque(false);
        left.add(powerSwitch);
        styleAutoToggle();
        left.add(autoToggle);

        selectedLabel.setForeground(MUTED);
        selectedLabel.setFont(selectedLabel.getFont().deriveFont(12f));

        panel.add(left, BorderLayout.WEST);
        panel.add(selectedLabel, BorderLayout.CENTER);
        return panel;
    }

    private void styleAutoToggle() {
        autoToggle.setFocusPainted(false);
        autoToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        autoToggle.setBorder(new EmptyBorder(12, 18, 12, 18));
        autoToggle.setBackground(new Color(236, 241, 247));
        autoToggle.setForeground(INK);
    }

    private JPanel createOptionsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);

        addField(panel, "Puerto", portSpinner, 0, 0);
        addField(panel, "DNS", dnsField, 1, 0);
        addField(panel, "Rutas", routesField, 0, 1);
        return panel;
    }

    private void addField(JPanel panel, String label, Component component, int column, int row) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = column;
        labelConstraints.gridy = row * 2;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(0, column == 0 ? 0 : 12, 4, 0);
        JLabel fieldLabel = new JLabel(label);
        fieldLabel.setForeground(MUTED);
        panel.add(fieldLabel, labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = column;
        fieldConstraints.gridy = row * 2 + 1;
        fieldConstraints.weightx = 1;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.insets = new Insets(0, column == 0 ? 0 : 12, 10, 0);
        panel.add(component, fieldConstraints);
    }

    private JPanel createActionPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        panel.setOpaque(false);
        panel.add(installButton);
        panel.add(reinstallButton);
        panel.add(uninstallButton);
        panel.add(tunnelButton);
        return panel;
    }

    private JPanel createRightPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setPreferredSize(new Dimension(RIGHT_PANEL_WIDTH, 0));

        incomingPanel.setPreferredSize(new Dimension(0, STAT_PANEL_HEIGHT));
        outgoingPanel.setPreferredSize(new Dimension(0, STAT_PANEL_HEIGHT));

        JPanel stats = new JPanel(new GridLayout(2, 1, 0, RIGHT_PANEL_GAP));
        stats.setOpaque(false);
        stats.add(incomingPanel);
        stats.add(outgoingPanel);
        stats.setPreferredSize(new Dimension(0, STAT_PANEL_HEIGHT * 2 + RIGHT_PANEL_GAP));

        GridBagConstraints statsConstraints = new GridBagConstraints();
        statsConstraints.gridx = 0;
        statsConstraints.gridy = 0;
        statsConstraints.weightx = 1;
        statsConstraints.fill = GridBagConstraints.BOTH;
        statsConstraints.insets = new Insets(0, 0, RIGHT_PANEL_GAP, 0);
        panel.add(stats, statsConstraints);

        JPanel appTrafficPanel = createAppTrafficPanel();
        appTrafficPanel.setMinimumSize(new Dimension(0, APP_TRAFFIC_MIN_HEIGHT));

        GridBagConstraints appTrafficConstraints = new GridBagConstraints();
        appTrafficConstraints.gridx = 0;
        appTrafficConstraints.gridy = 1;
        appTrafficConstraints.weightx = 1;
        appTrafficConstraints.weighty = 1;
        appTrafficConstraints.fill = GridBagConstraints.BOTH;
        panel.add(appTrafficPanel, appTrafficConstraints);
        return panel;
    }

    private JPanel createAppTrafficPanel() {
        JPanel panel = createPanel();
        panel.setLayout(new BorderLayout(0, 12));
        appTrafficTitle.setForeground(INK);
        appTrafficTitle.setFont(appTrafficTitle.getFont().deriveFont(Font.BOLD, 15f));
        appTrafficStatus.setForeground(MUTED);
        appTrafficStatus.setFont(appTrafficStatus.getFont().deriveFont(12f));

        JPanel top = new JPanel(new GridLayout(2, 1, 0, 2));
        top.setOpaque(false);
        top.add(appTrafficTitle);
        top.add(appTrafficStatus);

        configureAppTrafficTable();
        JScrollPane scrollPane = new JScrollPane(appTrafficTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER));
        scrollPane.getViewport().setBackground(SURFACE);

        panel.add(top, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private void configureAppTrafficTable() {
        configureAppTrafficTable(appTrafficTable, true);
    }

    private void configureAppTrafficTable(JTable table, boolean compact) {
        table.setRowHeight(32);
        table.setShowGrid(false);
        table.setFillsViewportHeight(true);
        table.setSelectionBackground(TABLE_SELECTION);
        table.setSelectionForeground(INK);
        AppTrafficCellRenderer renderer = new AppTrafficCellRenderer();
        table.setDefaultRenderer(Object.class, renderer);
        table.setDefaultRenderer(String.class, renderer);
        table.setDefaultRenderer(Long.class, renderer);
        table.setAutoCreateRowSorter(true);

        JTableHeader header = table.getTableHeader();
        header.setToolTipText("Recibido, enviado y total en B/KB/MB. Velocidad y promedio en B/s, KB/s o MB/s.");
        header.setReorderingAllowed(false);
        header.setBackground(new Color(248, 250, 252));
        header.setForeground(MUTED);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 12f));
        table.getColumnModel().getColumn(0).setPreferredWidth(compact ? 190 : 340);
        table.getColumnModel().getColumn(1).setPreferredWidth(compact ? 72 : 150);
        table.getColumnModel().getColumn(2).setPreferredWidth(compact ? 78 : 120);
        table.getColumnModel().getColumn(3).setPreferredWidth(compact ? 78 : 120);
        table.getColumnModel().getColumn(4).setPreferredWidth(compact ? 86 : 130);
        table.getColumnModel().getColumn(5).setPreferredWidth(compact ? 86 : 130);
        table.getColumnModel().getColumn(6).setPreferredWidth(compact ? 78 : 120);
        if (!compact) {
            table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        }
    }

    private JPanel createLogPanel() {
        JPanel panel = createPanel();
        panel.setLayout(new BorderLayout(0, 10));
        panel.setPreferredSize(new Dimension(0, 170));
        panel.add(createSectionTitle("Eventos"), BorderLayout.NORTH);

        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setForeground(INK);
        logArea.setBackground(new Color(249, 251, 253));
        logArea.setBorder(new EmptyBorder(10, 10, 10, 10));
        panel.add(new JScrollPane(logArea), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        bottom.setOpaque(false);
        bottom.add(clearLogButton);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    private void wireActions() {
        refreshButton.addActionListener((event) -> refreshDevices(true));
        installButton.addActionListener((event) -> installSelectedDevice());
        reinstallButton.addActionListener((event) -> reinstallSelectedDevice());
        uninstallButton.addActionListener((event) -> uninstallSelectedDevice());
        tunnelButton.addActionListener((event) -> resetSelectedTunnel());
        clearLogButton.addActionListener((event) -> logArea.setText(""));
        powerSwitch.addActionListener((event) -> {
            if (!updatingControls) {
                handlePowerChanged(powerSwitch.isSelected());
            }
        });
        autoToggle.addActionListener((event) -> {
            appendLog(autoToggle.isSelected() ? "Modo Auto activado." : "Modo Auto desactivado.");
            if (autoToggle.isSelected() && !powerSwitch.isSelected()) {
                setPowerSelected(true);
                handlePowerChanged(true);
            } else if (autoToggle.isSelected() && powerSwitch.isSelected()) {
                startAutoForCurrentDevices(false);
            }
        });
    }

    private void startTimers() {
        Timer statsTimer = new Timer(STATS_REFRESH_DELAY, (event) -> updateTrafficStats());
        statsTimer.start();

        Timer refreshTimer = new Timer(DEVICE_REFRESH_DELAY, (event) -> refreshDevices(false));
        refreshTimer.start();

        Timer appStatsTimer = new Timer(APP_STATS_REFRESH_DELAY, (event) -> refreshAppTrafficViews(false));
        appStatsTimer.start();
    }

    private void handlePowerChanged(boolean enabled) {
        if (enabled) {
            if (autoToggle.isSelected()) {
                startAutoForCurrentDevices(true);
            } else {
                startSelectedDevice();
            }
        } else {
            stopSharing();
        }
    }

    private void startSelectedDevice() {
        AdbDevice device;
        try {
            device = getSelectedReadyDevice();
        } catch (IllegalStateException e) {
            showError(e.getMessage());
            setPowerSelected(false);
            return;
        }

        runTask("Iniciando " + device.getDisplayName() + "...", () -> {
            service.startDevice(device.getSerial(), dnsField.getText(), routesField.getText(), getPort());
            return null;
        }, () -> {
            appendLog("Internet compartido con " + device.getDisplayName() + ".");
            refreshAppTrafficViews(true);
            updateStatus();
        }, true);
    }

    private void startAutoMode() {
        if (!autoToggle.isSelected()) {
            autoToggle.doClick();
            return;
        }
        if (!powerSwitch.isSelected()) {
            setPowerSelected(true);
            handlePowerChanged(true);
        } else {
            startAutoForCurrentDevices(true);
        }
    }

    private void startRelayOnly() {
        runTask("Iniciando servidor interno (Relay) en puerto " + getPort() + "...", () -> {
            service.startRelay(getPort());
            return null;
        }, () -> {
            appendLog("Servidor interno (Relay) activo en puerto " + getPort() + ".");
            updateStatus();
        }, true);
    }

    private void toggleRelayServer() {
        if (service.isRelayRunning()) {
            stopRelayServer();
        } else {
            startRelayOnly();
        }
    }

    private void stopRelayServer() {
        runTask("Deteniendo servidor interno (Relay)...", () -> {
            service.stopAll();
            return null;
        }, () -> {
            setPowerSelected(false);
            appendLog("Servidor interno (Relay) detenido.");
            refreshAppTrafficViews(false);
            updateStatus();
        }, true);
    }

    private void startAutoForCurrentDevices(boolean showEmptyMessage) {
        runTask("Buscando telefonos para Auto...", () -> {
            List<AdbDevice> devices = service.listDevices();
            int started = service.startReadyDevices(devices, dnsField.getText(), routesField.getText(), getPort());
            return new AutoResult(devices, started);
        }, (result) -> {
            AutoResult autoResult = (AutoResult) result;
            applyDevices(autoResult.devices);
            if (autoResult.started > 0) {
                appendLog("Auto inicio " + autoResult.started + " telefono(s).");
            } else if (showEmptyMessage) {
                appendLog("Auto activo. Conecta un telefono con depuracion USB autorizada.");
            }
            updateStatus();
        }, true);
    }

    private void stopSharing() {
        runTask("Deteniendo conexion...", () -> {
            service.stopAll();
            return null;
        }, () -> {
            appendLog("Conexion detenida.");
            updateStatus();
        }, true);
    }

    private void stopSelectedDevice() {
        AdbDevice device;
        try {
            device = getSelectedReadyDevice();
        } catch (IllegalStateException e) {
            showError(e.getMessage());
            return;
        }
        runTask("Deteniendo " + device.getDisplayName() + "...", () -> {
            service.stopDevice(device.getSerial());
            if (service.getActiveSerials().isEmpty()) {
                service.stopAll();
            }
            return null;
        }, () -> {
            appendLog("Conexion detenida para " + device.getDisplayName() + ".");
            refreshAppTrafficViews(false);
            updateStatus();
        }, true);
    }

    private void stopSharingFromMenu() {
        setPowerSelected(false);
        stopSharing();
    }

    private void closeApplication() {
        if (busy) {
            return;
        }
        if (service.isRelayRunning() || !service.getActiveSerials().isEmpty()) {
            runTask("Cerrando conexion antes de salir...", () -> {
                service.stopAll();
                return null;
            }, this::shutdownAndExit, true);
        } else {
            shutdownAndExit();
        }
    }

    private void shutdownAndExit() {
        executor.shutdownNow();
        if (trayIcon != null && SystemTray.isSupported()) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
        dispose();
        System.exit(0);
    }

    private void installSelectedDevice() {
        AdbDevice device;
        try {
            device = getSelectedDevice();
        } catch (IllegalStateException e) {
            showError(e.getMessage());
            return;
        }
        runTask("Instalando APK en " + device.getDisplayName() + "...", () -> {
            service.installDevice(device.getSerial());
            return null;
        }, () -> {
            appendLog("APK instalado en " + device.getDisplayName() + ".");
            refreshDevices(true);
        }, true);
    }

    private void reinstallSelectedDevice() {
        AdbDevice device;
        try {
            device = getSelectedDevice();
        } catch (IllegalStateException e) {
            showError(e.getMessage());
            return;
        }
        runTask("Reinstalando APK en " + device.getDisplayName() + "...", () -> {
            service.reinstallDevice(device.getSerial());
            return null;
        }, () -> {
            appendLog("APK reinstalado en " + device.getDisplayName() + ".");
            refreshDevices(true);
        }, true);
    }

    private void uninstallSelectedDevice() {
        AdbDevice device;
        try {
            device = getSelectedDevice();
        } catch (IllegalStateException e) {
            showError(e.getMessage());
            return;
        }
        runTask("Desinstalando APK de " + device.getDisplayName() + "...", () -> {
            service.uninstallDevice(device.getSerial());
            return null;
        }, () -> {
            appendLog("APK desinstalado de " + device.getDisplayName() + ".");
            refreshDevices(true);
        }, true);
    }

    private void restartSelectedDevice() {
        AdbDevice device;
        try {
            device = getSelectedReadyDevice();
        } catch (IllegalStateException e) {
            showError(e.getMessage());
            return;
        }
        runTask("Reiniciando " + device.getDisplayName() + "...", () -> {
            service.restartDevice(device.getSerial(), dnsField.getText(), routesField.getText(), getPort());
            return null;
        }, () -> {
            appendLog("Conexion reiniciada para " + device.getDisplayName() + ".");
            refreshAppTrafficViews(true);
            updateStatus();
        }, true);
    }

    private void resetSelectedTunnel() {
        AdbDevice device;
        try {
            device = getSelectedReadyDevice();
        } catch (IllegalStateException e) {
            showError(e.getMessage());
            return;
        }
        runTask("Reiniciando tunnel ADB para " + device.getDisplayName() + "...", () -> {
            service.resetTunnel(device.getSerial(), getPort());
            return null;
        }, () -> appendLog("Tunnel reiniciado para " + device.getDisplayName() + "."), true);
    }

    private void refreshDevices(boolean showLog) {
        if ((busy || refreshingDevices) && !showLog) {
            return;
        }
        boolean autoEnabled = autoToggle.isSelected();
        boolean powerEnabled = powerSwitch.isSelected();
        String dnsServers = dnsField.getText();
        String routes = routesField.getText();
        int port = getPort();
        String serialToReconnect = getPreferredSerial();

        refreshingDevices = true;
        runTask(showLog ? "Actualizando telefonos..." : null, () -> {
            List<AdbDevice> devices = service.listDevices();
            int started = 0;
            if (powerEnabled) {
                if (autoEnabled) {
                    started = service.startReadyDevices(devices, dnsServers, routes, port);
                } else {
                    started = startPreferredReadyDevice(devices, serialToReconnect, dnsServers, routes, port);
                }
            }
            return new AutoResult(devices, started);
        }, (result) -> {
            AutoResult autoResult = (AutoResult) result;
            applyDevices(autoResult.devices);
            if (showLog) {
                appendLog("Telefonos detectados: " + autoResult.devices.size() + ".");
            }
            if (autoResult.started > 0) {
                appendLog("Inicio automatico " + autoResult.started + " telefono(s).");
            }
            refreshingDevices = false;
        }, false, showLog);
    }

    private void applyDevices(List<AdbDevice> devices) {
        String selectedSerial = getSelectedSerial();
        if (selectedSerial != null) {
            preferredSerial = selectedSerial;
        }
        Set<String> activeSerials = service.getActiveSerials();
        notifyConnectionChanges(devices, activeSerials);
        deviceModel.setDevices(devices);
        deviceModel.setActiveSerials(activeSerials);
        restoreSelection(selectedSerial);
        updateConnectionSummary(activeSerials);
        updateSelectedLabel();
        refreshAppTrafficViews(false);
        deviceTable.repaint();
    }

    private int startPreferredReadyDevice(List<AdbDevice> devices, String serial, String dnsServers, String routes, int port)
            throws Exception {
        AdbDevice fallback = null;
        for (AdbDevice device : devices) {
            if (!device.isReady() || service.isDeviceActive(device.getSerial())) {
                continue;
            }
            if (serial != null && serial.equals(device.getSerial())) {
                service.startDevice(device.getSerial(), dnsServers, routes, port);
                return 1;
            }
            if (fallback == null) {
                fallback = device;
            }
        }
        if (serial == null && fallback != null && devices.size() == 1) {
            service.startDevice(fallback.getSerial(), dnsServers, routes, port);
            return 1;
        }
        return 0;
    }

    private void notifyConnectionChanges(List<AdbDevice> devices, Set<String> activeSerials) {
        for (String serial : activeSerials) {
            if (!lastNotifiedActiveSerials.contains(serial)) {
                String name = findDeviceName(devices, serial);
                String message = name + " conectado. Compartiendo internet desde la PC.";
                appendLog(message);
                showDesktopNotification("Gnirehtet conectado", message, TrayIcon.MessageType.INFO);
            }
        }
        for (String serial : lastNotifiedActiveSerials) {
            if (!activeSerials.contains(serial)) {
                String name = findDeviceName(devices, serial);
                String message = name + " desconectado. Se detuvo la medicion y la VPN del telefono.";
                appendLog(message);
                showDesktopNotification("Gnirehtet desconectado", message, TrayIcon.MessageType.WARNING);
            }
        }
        lastNotifiedActiveSerials.clear();
        lastNotifiedActiveSerials.addAll(activeSerials);
    }

    private String findDeviceName(List<AdbDevice> devices, String serial) {
        for (AdbDevice device : devices) {
            if (device.getSerial().equals(serial)) {
                return device.getDisplayName();
            }
        }
        return serial;
    }

    private void showDesktopNotification(String title, String message, TrayIcon.MessageType type) {
        if (trayIcon != null) {
            trayIcon.displayMessage(title, message, type);
        }
    }

    private void onSelectedDeviceChanged() {
        String selectedSerial = getSelectedSerial();
        if (selectedSerial != null) {
            preferredSerial = selectedSerial;
        }
        updateSelectedLabel();
        refreshAppTrafficViews(false);
    }

    private void restoreSelection(String selectedSerial) {
        if (selectedSerial != null) {
            int modelRow = deviceModel.indexOf(selectedSerial);
            if (modelRow >= 0) {
                int viewRow = deviceTable.convertRowIndexToView(modelRow);
                deviceTable.getSelectionModel().setSelectionInterval(viewRow, viewRow);
                return;
            }
        }
        if (deviceModel.getRowCount() > 0 && deviceTable.getSelectedRow() < 0) {
            deviceTable.getSelectionModel().setSelectionInterval(0, 0);
        }
    }

    private String getSelectedSerial() {
        int row = deviceTable.getSelectedRow();
        if (row < 0) {
            return null;
        }
        int modelRow = deviceTable.convertRowIndexToModel(row);
        return deviceModel.getDeviceAt(modelRow).getSerial();
    }

    private String getPreferredSerial() {
        String selectedSerial = getSelectedSerial();
        if (selectedSerial != null) {
            preferredSerial = selectedSerial;
            return selectedSerial;
        }
        return preferredSerial;
    }

    private AdbDevice getSelectedDevice() {
        AdbDevice device = getSelectedDeviceOrNull();
        if (device != null) {
            return device;
        }
        throw new IllegalStateException("Selecciona un telefono.");
    }

    private AdbDevice getSelectedDeviceOrNull() {
        int row = deviceTable.getSelectedRow();
        if (row < 0) {
            if (deviceModel.getRowCount() == 1) {
                return deviceModel.getDeviceAt(0);
            }
            return null;
        }
        int modelRow = deviceTable.convertRowIndexToModel(row);
        return deviceModel.getDeviceAt(modelRow);
    }

    private AdbDevice getSelectedReadyDevice() {
        AdbDevice device = getSelectedDevice();
        if (!device.isReady()) {
            throw new IllegalStateException("El telefono no esta listo: " + device.getState() + ".");
        }
        return device;
    }

    private int getPort() {
        return ((Number) portSpinner.getValue()).intValue();
    }

    private void updateSelectedLabel() {
        String selectedSerial = getSelectedSerial();
        if (selectedSerial == null) {
            selectedLabel.setText("Sin telefono seleccionado");
            return;
        }
        boolean active = service.isDeviceActive(selectedSerial);
        selectedLabel.setText(active ? "Seleccionado: activo" : "Seleccionado: listo para iniciar");
    }

    private void updateStatus() {
        Set<String> activeSerials = service.getActiveSerials();
        deviceModel.setActiveSerials(activeSerials);
        portSpinner.setEnabled(!service.isRelayRunning() && !busy);
        updateRelayMenuItem();
        updateConnectionSummary(activeSerials);
        updateSelectedLabel();
        deviceTable.repaint();
    }

    private void updateRelayMenuItem() {
        boolean relayRunning = service.isRelayRunning();
        if (relayRunning) {
            relayMenuItem.setText("Detener Servidor Interno (Relay)");
        } else {
            relayMenuItem.setText("Iniciar Servidor Interno (Relay)");
        }
        relayMenuItem.setToolTipText(relayRunning
                ? "Detiene el servidor local y cierra las conexiones de telefonos que dependen de el."
                : "Inicia solo el servidor local que puentea el trafico ADB entre el telefono e internet.");
        relayMenuItem.setEnabled(!busy);
    }

    private void updateConnectionSummary(Set<String> activeSerials) {
        if (!activeSerials.isEmpty()) {
            connectionSummary.setText(activeSerials.size() == 1 ? "1 telefono conectado"
                    : activeSerials.size() + " telefonos conectados");
            connectionSummary.setBackground(new Color(222, 245, 237));
            connectionSummary.setForeground(GREEN);
        } else if (service.isRelayRunning()) {
            connectionSummary.setText("Servidor Interno (Relay) activo");
            connectionSummary.setBackground(new Color(225, 244, 242));
            connectionSummary.setForeground(TEAL);
        } else if (autoToggle.isSelected()) {
            connectionSummary.setText("Auto activo, esperando telefono");
            connectionSummary.setBackground(new Color(232, 241, 253));
            connectionSummary.setForeground(BLUE);
        } else {
            connectionSummary.setText("Sin conexion activa");
            connectionSummary.setBackground(new Color(239, 243, 248));
            connectionSummary.setForeground(MUTED);
        }
    }

    private void setPowerSelected(boolean selected) {
        updatingControls = true;
        powerSwitch.setSelected(selected);
        updatingControls = false;
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        refreshButton.setEnabled(!busy);
        installButton.setEnabled(!busy);
        reinstallButton.setEnabled(!busy);
        uninstallButton.setEnabled(!busy);
        tunnelButton.setEnabled(!busy);
        powerSwitch.setEnabled(!busy);
        autoToggle.setEnabled(!busy);
        relayMenuItem.setEnabled(!busy);
        updateRelayMenuItem();
    }

    private void updateTrafficStats() {
        TrafficStats.Snapshot snapshot = service.getTrafficStats().snapshot();
        long now = System.currentTimeMillis();
        long incomingSpeed = 0;
        long outgoingSpeed = 0;
        if (lastSnapshot != null) {
            long elapsed = Math.max(1, now - lastSnapshotTime);
            incomingSpeed = (snapshot.getNetworkToClientBytes() - lastSnapshot.getNetworkToClientBytes()) * 1000 / elapsed;
            outgoingSpeed = (snapshot.getClientToNetworkBytes() - lastSnapshot.getClientToNetworkBytes()) * 1000 / elapsed;
        }
        incomingPanel.update(snapshot.getNetworkToClientBytes(), incomingSpeed, snapshot.getNetworkToClientPackets());
        outgoingPanel.update(snapshot.getClientToNetworkBytes(), outgoingSpeed, snapshot.getClientToNetworkPackets());
        lastSnapshot = snapshot;
        lastSnapshotTime = now;
        updateStatus();
    }

    private void refreshAppTrafficViews(boolean showLogOnError) {
        if (refreshingAppTraffic) {
            return;
        }
        Map<String, AdbDevice> devices = collectAppTrafficDevices();
        if (devices.isEmpty()) {
            return;
        }
        refreshingAppTraffic = true;
        executor.submit(() -> {
            Map<String, List<AppTrafficStats>> results = new HashMap<>();
            Map<String, Exception> errors = new HashMap<>();
            for (AdbDevice device : devices.values()) {
                try {
                    results.put(device.getSerial(), service.getApplicationTrafficStats(device.getSerial()));
                } catch (Exception e) {
                    errors.put(device.getSerial(), e);
                }
            }
            SwingUtilities.invokeLater(() -> {
                for (Map.Entry<String, List<AppTrafficStats>> entry : results.entrySet()) {
                    applyAppTrafficStats(entry.getKey(), entry.getValue());
                }
                for (Map.Entry<String, Exception> entry : errors.entrySet()) {
                    applyAppTrafficError(entry.getKey(), entry.getValue(), showLogOnError);
                }
                refreshingAppTraffic = false;
            });
        });
    }

    private void configureNotifications() {
        if (!SystemTray.isSupported()) {
            return;
        }
        Image image = loadAppIcon();
        if (image == null) {
            image = Toolkit.getDefaultToolkit().createImage(new byte[0]);
        }
        trayIcon = new TrayIcon(image, "Gnirehtet Desktop");
        trayIcon.setImageAutoSize(true);
        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            trayIcon = null;
        }
    }

    private Image loadAppIcon() {
        try {
            return ImageIO.read(GnirehtetGui.class.getResource(ICON_RESOURCE));
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, AdbDevice> collectAppTrafficDevices() {
        Map<String, AdbDevice> devices = new LinkedHashMap<>();
        AdbDevice device = getSelectedDeviceOrNull();
        prepareSidebarAppTraffic(device, devices);
        for (AppTrafficWindow window : appTrafficWindows.values()) {
            prepareDetailAppTraffic(window, findDeviceBySerial(window.getSerial()), devices);
        }
        return devices;
    }

    private void prepareSidebarAppTraffic(AdbDevice device, Map<String, AdbDevice> devices) {
        if (device == null) {
            appTrafficModel.setRows(Collections.emptyList());
            appTrafficStatus.setText("Selecciona un telefono activo");
            return;
        }
        if (!device.isReady()) {
            appTrafficModel.setRows(Collections.emptyList());
            appTrafficStatus.setText("Sin acceso ADB: " + device.getState());
            return;
        }
        if (!service.isDeviceActive(device.getSerial())) {
            appTrafficModel.setRows(Collections.emptyList());
            appTrafficStatus.setText("Activa ON para comenzar la medicion");
            return;
        }
        appTrafficStatus.setText("Actualizando consumo...");
        devices.put(device.getSerial(), device);
    }

    private void prepareDetailAppTraffic(AppTrafficWindow window, AdbDevice device, Map<String, AdbDevice> devices) {
        if (device == null) {
            window.setRows(Collections.emptyList());
            window.setStatus("Telefono desconectado");
            return;
        }
        if (!device.isReady()) {
            window.setRows(Collections.emptyList());
            window.setStatus("Sin acceso ADB: " + device.getState());
            return;
        }
        if (!service.isDeviceActive(device.getSerial())) {
            window.setRows(Collections.emptyList());
            window.setStatus("Activa ON para comenzar la medicion");
            return;
        }
        window.setStatus("Actualizando consumo...");
        devices.put(device.getSerial(), device);
    }

    private void applyAppTrafficStats(String serial, List<AppTrafficStats> stats) {
        AdbDevice selectedDevice = getSelectedDeviceOrNull();
        if (selectedDevice != null && serial.equals(selectedDevice.getSerial())) {
            appTrafficModel.setRows(stats);
            appTrafficStatus.setText(getAppTrafficStatus(stats));
        }
        AppTrafficWindow window = appTrafficWindows.get(serial);
        if (window != null) {
            window.setRows(stats);
            window.setStatus(getAppTrafficStatus(stats));
        }
    }

    private void applyAppTrafficError(String serial, Exception exception, boolean showLogOnError) {
        String message = cleanMessage(exception);
        AdbDevice selectedDevice = getSelectedDeviceOrNull();
        if (selectedDevice != null && serial.equals(selectedDevice.getSerial())) {
            appTrafficModel.setRows(Collections.emptyList());
            appTrafficStatus.setText("Estadisticas por app no disponibles");
        }
        AppTrafficWindow window = appTrafficWindows.get(serial);
        if (window != null) {
            window.setRows(Collections.emptyList());
            window.setStatus("Estadisticas por app no disponibles");
        }
        if (showLogOnError) {
            appendLog("Estadisticas por app: " + message);
        }
    }

    private String getAppTrafficStatus(List<AppTrafficStats> stats) {
        return stats.isEmpty() ? "Esperando consumo de aplicaciones"
                : stats.size() + " app(s) con consumo - B/KB/MB y B/s";
    }

    private AdbDevice findDeviceBySerial(String serial) {
        int index = deviceModel.indexOf(serial);
        return index < 0 ? null : deviceModel.getDeviceAt(index);
    }

    private void openSelectedAppTrafficWindow() {
        AdbDevice device = getSelectedDeviceOrNull();
        if (device == null) {
            showError("Selecciona un telefono.");
            return;
        }
        String serial = device.getSerial();
        AppTrafficWindow window = appTrafficWindows.get(serial);
        if (window == null) {
            window = new AppTrafficWindow(device);
            appTrafficWindows.put(serial, window);
        }
        window.setVisible(true);
        window.toFront();
        refreshAppTrafficViews(true);
    }

    private void runTask(String message, UiTask task, UiSuccess success, boolean showDialogOnError) {
        runTask(message, task, success, showDialogOnError, true);
    }

    private void runTask(String message, UiTask task, UiSuccess success, boolean showDialogOnError, boolean markBusy) {
        if (message != null) {
            appendLog(message);
        }
        if (markBusy) {
            setBusy(true);
        }
        executor.submit(() -> {
            try {
                Object result = task.run();
                SwingUtilities.invokeLater(() -> {
                    if (markBusy) {
                        setBusy(false);
                    }
                    success.onSuccess(result);
                    updateStatus();
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    if (markBusy) {
                        setBusy(false);
                    }
                    refreshingDevices = false;
                    appendLog("Error: " + cleanMessage(e));
                    if (showDialogOnError) {
                        showError(cleanMessage(e));
                    }
                    updateStatus();
                });
            }
        });
    }

    private void runTask(String message, UiTask task, Runnable success, boolean showDialogOnError) {
        runTask(message, task, (result) -> success.run(), showDialogOnError);
    }

    private void appendLog(String message) {
        if (message == null) {
            return;
        }
        String line = logTimeFormat.format(new Date()) + "  " + message + System.lineSeparator();
        logArea.append(line);
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Gnirehtet", JOptionPane.ERROR_MESSAGE);
    }

    private void showCommandHelp() {
        String message = "Guia rapida de Gnirehtet Desktop:" + System.lineSeparator()
                + "ON inicia internet para el telefono seleccionado." + System.lineSeparator()
                + "Auto inicia telefonos listos cuando se detectan." + System.lineSeparator()
                + "La columna Conexion muestra Conectado o Desconectado." + System.lineSeparator()
                + "Click derecho sobre un telefono: iniciar, detener o reiniciar." + System.lineSeparator()
                + "Los botones inferiores gestionan APK y reset tunnel." + System.lineSeparator()
                + "Doble click abre el consumo por aplicacion.";
        JOptionPane.showMessageDialog(this, message, "Ayuda", JOptionPane.INFORMATION_MESSAGE);
    }

    private static JButton createButton(String text, Color color) {
        JButton button = new SolidButton(text, color);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(new EmptyBorder(10, 14, 10, 14));
        return button;
    }

    private static JLabel createSectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(INK);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 15f));
        return label;
    }

    private static JPanel createPanel() {
        JPanel panel = new RoundedPanel();
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));
        return panel;
    }

    private static String cleanMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty() ? e.getClass().getSimpleName() : message;
    }

    private interface UiTask {
        Object run() throws Exception;
    }

    private interface UiSuccess {
        void onSuccess(Object result);
    }

    private static final class AutoResult {
        private final List<AdbDevice> devices;
        private final int started;

        private AutoResult(List<AdbDevice> devices, int started) {
            this.devices = devices;
            this.started = started;
        }
    }

    private final class AppTrafficWindow extends JFrame {
        private final String serial;
        private final AppTrafficDetailPanel panel;

        private AppTrafficWindow(AdbDevice device) {
            super("Consumo de aplicaciones - " + device.getDisplayName());
            serial = device.getSerial();
            panel = new AppTrafficDetailPanel(device);
            setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            setMinimumSize(new Dimension(720, 460));
            setSize(900, 560);
            setLocationRelativeTo(GnirehtetGui.this);
            setContentPane(panel);
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent event) {
                    appTrafficWindows.remove(serial);
                }
            });
        }

        private String getSerial() {
            return serial;
        }

        private void setRows(List<AppTrafficStats> stats) {
            panel.setRows(stats);
        }

        private void setStatus(String status) {
            panel.setStatus(status);
        }
    }

    private final class AppTrafficDetailPanel extends JPanel {
        private final AppTrafficTableModel model = new AppTrafficTableModel();
        private final JTable table = new JTable(model);
        private final TableRowSorter<AppTrafficTableModel> sorter = new TableRowSorter<>(model);
        private final JLabel statusLabel = new JLabel("Esperando consumo de aplicaciones");
        private final JTextField searchField = new JTextField();
        private final AppTrafficSummaryPanel incomingSummary = new AppTrafficSummaryPanel("Entrante apps visibles", BLUE);
        private final AppTrafficSummaryPanel outgoingSummary = new AppTrafficSummaryPanel("Saliente apps visibles", GREEN);
        private final AppTrafficSummaryPanel totalSummary = new AppTrafficSummaryPanel("Total apps visibles", TEAL);

        private AppTrafficDetailPanel(AdbDevice device) {
            super(new BorderLayout(0, 12));
            setBackground(BACKGROUND);
            setBorder(new EmptyBorder(14, 14, 14, 14));

            JPanel header = new JPanel(new BorderLayout(12, 8));
            header.setOpaque(false);
            JLabel title = createSectionTitle("Consumo por aplicacion - " + device.getDisplayName());
            statusLabel.setForeground(MUTED);
            statusLabel.setFont(statusLabel.getFont().deriveFont(12f));
            header.add(title, BorderLayout.WEST);
            header.add(createSearchPanel(), BorderLayout.EAST);

            JPanel statusPanel = new JPanel(new BorderLayout(0, 8));
            statusPanel.setOpaque(false);
            statusPanel.add(statusLabel, BorderLayout.NORTH);
            statusPanel.add(createTrafficSummaryPanel(), BorderLayout.CENTER);

            JPanel top = new JPanel(new BorderLayout(0, 8));
            top.setOpaque(false);
            top.add(header, BorderLayout.NORTH);
            top.add(statusPanel, BorderLayout.CENTER);

            configureAppTrafficTable(table, false);
            table.setRowSorter(sorter);
            sorter.addRowSorterListener((event) -> updateVisibleTotals());
            JScrollPane scrollPane = new JScrollPane(table);
            scrollPane.setBorder(BorderFactory.createLineBorder(BORDER));
            scrollPane.getViewport().setBackground(SURFACE);
            scrollPane.setPreferredSize(new Dimension(0, DETAIL_TABLE_HEIGHT));

            add(top, BorderLayout.NORTH);
            add(scrollPane, BorderLayout.CENTER);
        }

        private JPanel createTrafficSummaryPanel() {
            JPanel panel = new JPanel(new GridLayout(1, 3, 10, 0));
            panel.setOpaque(false);
            panel.setPreferredSize(new Dimension(0, 112));
            panel.add(incomingSummary);
            panel.add(outgoingSummary);
            panel.add(totalSummary);
            return panel;
        }

        private JPanel createSearchPanel() {
            JPanel panel = new JPanel(new BorderLayout(6, 0));
            panel.setOpaque(false);
            panel.setPreferredSize(new Dimension(260, 30));
            JLabel searchIcon = new JLabel(new SearchIcon());
            searchIcon.setBorder(new EmptyBorder(0, 6, 0, 0));
            searchField.setToolTipText("Buscar aplicacion o UID");
            searchField.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent event) {
                    updateFilter();
                }

                @Override
                public void removeUpdate(DocumentEvent event) {
                    updateFilter();
                }

                @Override
                public void changedUpdate(DocumentEvent event) {
                    updateFilter();
                }
            });
            panel.setBorder(BorderFactory.createLineBorder(BORDER));
            panel.setBackground(SURFACE);
            panel.add(searchIcon, BorderLayout.WEST);
            panel.add(searchField, BorderLayout.CENTER);
            return panel;
        }

        private void updateFilter() {
            String text = searchField.getText().trim();
            if (text.isEmpty()) {
                sorter.setRowFilter(null);
            } else {
                sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
            }
            updateVisibleTotals();
        }

        private void setRows(List<AppTrafficStats> stats) {
            model.setRows(stats);
            updateVisibleTotals();
        }

        private void setStatus(String status) {
            statusLabel.setText(status);
        }

        private void updateVisibleTotals() {
            long receivedBytes = 0;
            long sentBytes = 0;
            long receivedSpeed = 0;
            long sentSpeed = 0;
            long averageReceivedSpeed = 0;
            long averageSentSpeed = 0;
            int visibleRows = table.getRowCount();

            for (int row = 0; row < visibleRows; ++row) {
                int modelRow = table.convertRowIndexToModel(row);
                AppTrafficStats stats = model.getRowAt(modelRow);
                receivedBytes += stats.getReceivedBytes();
                sentBytes += stats.getSentBytes();
                receivedSpeed += stats.getCurrentReceivedBytesPerSecond();
                sentSpeed += stats.getCurrentSentBytesPerSecond();
                averageReceivedSpeed += stats.getAverageReceivedBytesPerSecond();
                averageSentSpeed += stats.getAverageSentBytesPerSecond();
            }

            incomingSummary.update(receivedBytes, receivedSpeed, averageReceivedSpeed, visibleRows);
            outgoingSummary.update(sentBytes, sentSpeed, averageSentSpeed, visibleRows);
            long totalSpeed = receivedSpeed + sentSpeed;
            long totalAverageSpeed = averageReceivedSpeed + averageSentSpeed;
            totalSummary.update(receivedBytes + sentBytes, totalSpeed, totalAverageSpeed, visibleRows);
        }
    }

    private static final class AppTrafficSummaryPanel extends JPanel {
        private final Color accent;
        private final JLabel valueLabel = new JLabel("0 B");
        private final JLabel speedLabel = new JLabel("0 B/s");
        private final JLabel averageLabel = new JLabel("0 B/s promedio");

        private AppTrafficSummaryPanel(String title, Color accent) {
            super(new BorderLayout(0, 6));
            this.accent = accent;
            setOpaque(false);
            setBorder(new EmptyBorder(10, 12, 10, 12));

            JLabel titleLabel = new JLabel(title);
            titleLabel.setForeground(MUTED);
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12f));

            valueLabel.setForeground(accent);
            valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 24f));

            speedLabel.setForeground(INK);
            speedLabel.setFont(speedLabel.getFont().deriveFont(Font.BOLD, 13f));

            averageLabel.setForeground(MUTED);
            averageLabel.setFont(averageLabel.getFont().deriveFont(12f));

            JPanel values = new JPanel(new GridLayout(2, 1, 0, 2));
            values.setOpaque(false);
            values.add(speedLabel);
            values.add(averageLabel);

            add(titleLabel, BorderLayout.NORTH);
            add(valueLabel, BorderLayout.CENTER);
            add(values, BorderLayout.SOUTH);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics2D.setColor(SURFACE);
            graphics2D.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            graphics2D.setColor(BORDER);
            graphics2D.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            graphics2D.setColor(accent);
            graphics2D.fillRoundRect(0, 0, 4, getHeight(), 12, 12);
            graphics2D.dispose();
            super.paintComponent(graphics);
        }

        private void update(long bytes, long bytesPerSecond, long averageBytesPerSecond, int appCount) {
            valueLabel.setText(ByteFormatter.format(bytes));
            speedLabel.setText(ByteFormatter.format(bytesPerSecond) + "/s");
            String average = ByteFormatter.format(averageBytesPerSecond) + "/s promedio";
            averageLabel.setText(average + " - " + appCount + " app(s)");
        }
    }

    private final class DeviceCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row,
                int column) {
            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setBorder(new EmptyBorder(0, 10, 0, 10));
            setForeground(isSelected ? INK : MUTED);
            if (!isSelected) {
                setBackground(row % 2 == 0 ? SURFACE : new Color(249, 251, 253));
            }
            int modelRow = table.convertRowIndexToModel(row);
            int modelColumn = table.convertColumnIndexToModel(column);
            AdbDevice device = deviceModel.getDeviceAt(modelRow);
            if (modelColumn == 2) {
                setText(getStateLabel(device));
                setForeground(getStateColor(device));
            } else if (modelColumn == 3) {
                setForeground(getInstallColor(device));
            } else if (modelColumn == 4) {
                setForeground(service.isDeviceActive(device.getSerial()) ? GREEN : MUTED);
            }
            return component;
        }

        private String getStateLabel(AdbDevice device) {
            if (device.isReady()) {
                return "listo";
            }
            return device.getState();
        }

        private Color getStateColor(AdbDevice device) {
            if (device.isReady()) {
                return GREEN;
            }
            if ("unauthorized".equals(device.getState())) {
                return ORANGE;
            }
            return RED;
        }

        private Color getInstallColor(AdbDevice device) {
            if (!device.isReady()) {
                return MUTED;
            }
            return device.isGnirehtetInstalled() ? GREEN : ORANGE;
        }
    }

    private static final class AppTrafficCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row,
                int column) {
            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setBorder(new EmptyBorder(0, 8, 0, 8));
            int modelColumn = table.convertColumnIndexToModel(column);
            if (value instanceof Long) {
                String formatted = ByteFormatter.format((Long) value);
                setText(modelColumn == 4 || modelColumn == 5 ? formatted + "/s" : formatted);
            }
            if (!isSelected) {
                setBackground(row % 2 == 0 ? SURFACE : new Color(249, 251, 253));
                setForeground(modelColumn == 0 ? INK : MUTED);
            }
            if (modelColumn > 0) {
                setHorizontalAlignment(RIGHT);
            } else {
                setHorizontalAlignment(LEFT);
            }
            return component;
        }
    }

    private static final class SearchIcon implements Icon {
        private static final int SIZE = 14;

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics2D.setColor(MUTED);
            graphics2D.setStroke(new BasicStroke(2f));
            graphics2D.drawOval(x + 1, y + 1, 8, 8);
            graphics2D.drawLine(x + 9, y + 9, x + 13, y + 13);
            graphics2D.dispose();
        }
    }

    private static final class SolidButton extends JButton {
        private final Color color;

        private SolidButton(String text, Color color) {
            super(text);
            this.color = color;
            setContentAreaFilled(false);
            setOpaque(false);
            setBorderPainted(false);
            setForeground(Color.WHITE);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color fill = isEnabled() ? color : DISABLED_BUTTON;
            if (getModel().isRollover() && isEnabled()) {
                fill = fill.brighter();
            }
            graphics2D.setColor(fill);
            graphics2D.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            graphics2D.setFont(getFont());
            graphics2D.setColor(isEnabled() ? Color.WHITE : DISABLED_BUTTON_TEXT);
            FontMetrics metrics = graphics2D.getFontMetrics();
            int textX = (getWidth() - metrics.stringWidth(getText())) / 2;
            int textY = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
            graphics2D.drawString(getText(), Math.max(0, textX), textY);
            graphics2D.dispose();
        }
    }

    private static final class RoundedPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics2D.setColor(SURFACE);
            graphics2D.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
            graphics2D.setColor(BORDER);
            graphics2D.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            graphics2D.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class StatPanel extends JPanel {
        private final JLabel valueLabel = new JLabel("0 B");
        private final JLabel speedLabel = new JLabel("0 B/s");
        private final JLabel packetLabel = new JLabel("0 paquetes");

        private StatPanel(String title, Color accent) {
            super(new BorderLayout(0, 10));
            setOpaque(false);
            setBorder(new EmptyBorder(16, 16, 16, 16));

            JLabel titleLabel = new JLabel(title);
            titleLabel.setForeground(MUTED);
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
            valueLabel.setForeground(accent);
            valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 28f));
            speedLabel.setForeground(INK);
            speedLabel.setFont(speedLabel.getFont().deriveFont(Font.BOLD, 14f));
            packetLabel.setForeground(MUTED);

            JPanel bottom = new JPanel(new GridLayout(2, 1, 0, 2));
            bottom.setOpaque(false);
            bottom.add(speedLabel);
            bottom.add(packetLabel);

            add(titleLabel, BorderLayout.NORTH);
            add(valueLabel, BorderLayout.CENTER);
            add(bottom, BorderLayout.SOUTH);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics2D.setColor(SURFACE);
            graphics2D.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
            graphics2D.setColor(BORDER);
            graphics2D.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            graphics2D.dispose();
            super.paintComponent(graphics);
        }

        private void update(long bytes, long bytesPerSecond, long packets) {
            valueLabel.setText(ByteFormatter.format(bytes));
            speedLabel.setText(ByteFormatter.format(bytesPerSecond) + "/s");
            packetLabel.setText(packets + " paquetes");
        }
    }

    private static final class PowerSwitch extends JToggleButton {
        private PowerSwitch() {
            setPreferredSize(new Dimension(128, 52));
            setMinimumSize(new Dimension(128, 52));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();
            int arc = height;
            g.setColor(isSelected() ? GREEN : new Color(210, 217, 226));
            g.fillRoundRect(0, 0, width, height, arc, arc);

            int knobSize = height - 10;
            int knobX = isSelected() ? width - knobSize - 5 : 5;
            g.setColor(Color.WHITE);
            g.fillOval(knobX, 5, knobSize, knobSize);

            g.setFont(getFont().deriveFont(Font.BOLD, 13f));
            g.setColor(isSelected() ? Color.WHITE : INK);
            String text = isSelected() ? "ON" : "OFF";
            int textX = isSelected() ? 20 : width - 48;
            int textY = height / 2 + 5;
            g.drawString(text, textX, textY);
            g.dispose();
        }
    }
}
