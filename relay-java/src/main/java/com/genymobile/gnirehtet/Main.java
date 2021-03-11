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

import com.genymobile.gnirehtet.relay.CommandExecutionException;
import com.genymobile.gnirehtet.relay.Log;
import com.genymobile.gnirehtet.relay.Relay;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Main {
    private static final String TAG = "Gnirehtet";
    private static final String NL = System.lineSeparator();
    private static final String REQUIRED_APK_VERSION_CODE = "8";

    private Main() {
        // not instantiable
    }

    private static String getAdbPath() {
        String adb = System.getenv("ADB");
        return adb != null ? adb : "adb";
    }

    private static String getApkPath() {
        String apk = System.getenv("GNIREHTET_APK");
        return apk != null ? apk : "gnirehtet.apk";
    }

    enum Command {
        INSTALL("install", CommandLineArguments.PARAM_SERIAL) {
            @Override
            String getDescription() {
                return "Install the client on the Android device and exit.\n"
                        + "If several devices are connected via adb, then serial must be\n"
                        + "specified.";
            }

            @Override
            void execute(CommandLineArguments args) throws Exception {
                cmdInstall(args.getSerial());
            }
        },
        UNINSTALL("uninstall", CommandLineArguments.PARAM_SERIAL) {
            @Override
            String getDescription() {
                return "Uninstall the client from the Android device and exit.\n"
                        + "If several devices are connected via adb, then serial must be\n"
                        + "specified.";
            }

            @Override
            void execute(CommandLineArguments args) throws Exception {
                cmdUninstall(args.getSerial());
            }
        },
        REINSTALL("reinstall", CommandLineArguments.PARAM_SERIAL) {
            @Override
            String getDescription() {
                return "Uninstall then install.";
            }

            @Override
            void execute(CommandLineArguments args) throws Exception {
                cmdReinstall(args.getSerial());
            }
        },
        RUN("run", CommandLineArguments.PARAM_SERIAL | CommandLineArguments.PARAM_DNS_SERVER | CommandLineArguments.PARAM_ROUTES
                | CommandLineArguments.PARAM_PORT) {
            @Override
            String getDescription() {
                return "Enable reverse tethering for exactly one device:\n"
                        + "  - install the client if necessary;\n"
                        + "  - start the client;\n"
                        + "  - start the relay server;\n"
                        + "  - on Ctrl+C, stop both the relay server and the client.";
            }

            @Override
            void execute(CommandLineArguments args) throws Exception {
                cmdRun(args.getSerial(), args.getDnsServers(), args.getRoutes(), args.getPort());
            }
        },
        AUTORUN("autorun", CommandLineArguments.PARAM_DNS_SERVER | CommandLineArguments.PARAM_ROUTES | CommandLineArguments.PARAM_PORT) {
            @Override
            String getDescription() {
                return "Enable reverse tethering for all devices:\n"
                        + "  - monitor devices and start clients (autostart);\n"
                        + "  - start the relay server.";
            }

            @Override
            void execute(CommandLineArguments args) throws Exception {
                cmdAutorun(args.getDnsServers(), args.getRoutes(), args.getPort());
            }
        },
        START("start", CommandLineArguments.PARAM_SERIAL | CommandLineArguments.PARAM_DNS_SERVER | CommandLineArguments.PARAM_ROUTES
                | CommandLineArguments.PARAM_PORT) {
            @Override
            String getDescription() {
                return "Start a client on the Android device and exit.\n"
                        + "If several devices are connected via adb, then serial must be\n"
                        + "specified.\n"
                        + "If -d is given, then make the Android device use the specified\n"
                        + "DNS server(s). Otherwise, use 8.8.8.8 (Google public DNS).\n"
                        + "If -r is given, then only reverse tether the specified routes.\n"
                        + "If -p is given, then make the relay server listen on the specified\n"
                        + "port. Otherwise, use port 31416.\n"
                        + "Otherwise, use 0.0.0.0/0 (redirect the whole traffic).\n"
                        + "If the client is already started, then do nothing, and ignore\n"
                        + "the other parameters.\n"
                        + "10.0.2.2 is mapped to the host 'localhost'.";
            }

            @Override
            void execute(CommandLineArguments args) throws Exception {
                cmdStart(args.getSerial(), args.getDnsServers(), args.getRoutes(), args.getPort());
            }
        },
        AUTOSTART("autostart", CommandLineArguments.PARAM_DNS_SERVER | CommandLineArguments.PARAM_ROUTES | CommandLineArguments.PARAM_PORT) {
            @Override
            String getDescription() {
                return "Listen for device connexions and start a client on every detected\n"
                        + "device.\n"
                        + "Accept the same parameters as the start command (excluding the\n"
                        + "serial, which will be taken from the detected device).";
            }

            @Override
            void execute(CommandLineArguments args) throws Exception {
                cmdAutostart(args.getDnsServers(), args.getRoutes(), args.getPort());
            }
        },
        STOP("stop", CommandLineArguments.PARAM_SERIAL) {
            @Override
            String getDescription() {
                return "Stop the client on the Android device and exit.\n"
                        + "If several devices are connected via adb, then serial must be\n"
                        + "specified.";
            }

            @Override
            void execute(CommandLineArguments args) throws Exception {
                cmdStop(args.getSerial());
            }
        },
        RESTART("restart", CommandLineArguments.PARAM_SERIAL | CommandLineArguments.PARAM_DNS_SERVER | CommandLineArguments.PARAM_ROUTES
                | CommandLineArguments.PARAM_PORT) {
            @Override
            String getDescription() {
                return "Stop then start.";
            }

            @Override
            void execute(CommandLineArguments args) throws Exception {
                cmdRestart(args.getSerial(), args.getDnsServers(), args.getRoutes(), args.getPort());
            }
        },
        TUNNEL("tunnel", CommandLineArguments.PARAM_SERIAL | CommandLineArguments.PARAM_PORT) {
            @Override
            String getDescription() {
                return "Set up the 'adb reverse' tunnel.\n"
                        + "If a device is unplugged then plugged back while gnirehtet is\n"
                        + "active, resetting the tunnel is sufficient to get the\n"
                        + "connection back.";
            }

            @Override
            void execute(CommandLineArguments args) throws Exception {
                cmdTunnel(args.getSerial(), args.getPort());
            }
        },
        RELAY("relay", CommandLineArguments.PARAM_PORT) {
            @Override
            String getDescription() {
                return "Start the relay server in the current terminal.";
            }

            @Override
            void execute(CommandLineArguments args) throws Exception {
                cmdRelay(args.getPort());
            }
        };

        private String command;
        private int acceptedParameters;

        Command(String command, int acceptedParameters) {
            this.command = command;
            this.acceptedParameters = acceptedParameters;
        }

        abstract String getDescription();

        abstract void execute(CommandLineArguments args) throws Exception;
    }

    private static void cmdInstall(String serial) throws InterruptedException, IOException, CommandExecutionException {
        Log.i(TAG, "Installing gnirehtet client...");
        execAdb(serial, "install", "-r", getApkPath());
    }

    private static void cmdUninstall(String serial) throws InterruptedException, IOException, CommandExecutionException {
        Log.i(TAG, "Uninstalling gnirehtet client...");
        execAdb(serial, "uninstall", "com.genymobile.gnirehtet");
    }

    private static void cmdReinstall(String serial) throws InterruptedException, IOException, CommandExecutionException {
        cmdUninstall(serial);
        cmdInstall(serial);
    }

    private static void cmdRun(String serial, String dnsServers, String routes, int port) throws IOException {
        // start in parallel so that the relay server is ready when the client connects
        asyncStart(serial, dnsServers, routes, port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // executed on Ctrl+C
            try {
                cmdStop(serial);
            } catch (Exception e) {
                Log.e(TAG, "Cannot stop client", e);
            }
        }));

        cmdRelay(port);
    }

    private static void cmdAutorun(final String dnsServers, final String routes, int port) throws IOException {
        new Thread(() -> {
            try {
                cmdAutostart(dnsServers, routes, port);
            } catch (Exception e) {
                Log.e(TAG, "Cannot auto start clients", e);
            }
        }).start();

        cmdRelay(port);
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private static void cmdStart(String serial, String dnsServers, String routes, int port) throws InterruptedException, IOException,
            CommandExecutionException {
        if (mustInstallClient(serial)) {
            cmdInstall(serial);
            // wait a bit after the app is installed so that intent actions are correctly registered
            Thread.sleep(500); // ms
        }

        Log.i(TAG, "Starting client...");
        cmdTunnel(serial, port);

        List<String> cmd = new ArrayList<>();
        Collections.addAll(cmd, "shell", "am", "start", "-a", "com.genymobile.gnirehtet.START", "-n",
                "com.genymobile.gnirehtet/.GnirehtetActivity");
        if (dnsServers != null) {
            Collections.addAll(cmd, "--esa", "dnsServers", dnsServers);
        }
        if (routes != null) {
            Collections.addAll(cmd, "--esa", "routes", routes);
        }
        execAdb(serial, cmd);
    }

    private static void cmdAutostart(final String dnsServers, final String routes, int port) {
        AdbMonitor adbMonitor = new AdbMonitor((serial) -> {
            asyncStart(serial, dnsServers, routes, port);
        });
        adbMonitor.monitor();
    }

    private static void cmdStop(String serial) throws InterruptedException, IOException, CommandExecutionException {
        Log.i(TAG, "Stopping client...");
        execAdb(serial, "shell", "am", "start", "-a", "com.genymobile.gnirehtet.STOP", "-n",
                "com.genymobile.gnirehtet/.GnirehtetActivity");
    }

    private static void cmdRestart(String serial, String dnsServers, String routes, int port) throws InterruptedException, IOException,
            CommandExecutionException {
        cmdStop(serial);
        cmdStart(serial, dnsServers, routes, port);
    }

    private static void cmdTunnel(String serial, int port) throws InterruptedException, IOException, CommandExecutionException {
        execAdb(serial, "reverse", "localabstract:gnirehtet", "tcp:" + port);
    }

    private static void cmdRelay(int port) throws IOException {
        Log.i(TAG, "Starting relay server on port " + port + "...");
        new Relay(port).run();
    }

    private static void asyncStart(String serial, String dnsServers, String routes, int port) {
        new Thread(() -> {
            try {
                cmdStart(serial, dnsServers, routes, port);
            } catch (Exception e) {
                Log.e(TAG, "Cannot start client", e);
            }
        }).start();
    }

    private static void execAdb(String serial, String... adbArgs) throws InterruptedException, IOException, CommandExecutionException {
        execSync(createAdbCommand(serial, adbArgs));
    }

    private static List<String> createAdbCommand(String serial, String... adbArgs) {
        List<String> command = new ArrayList<>();
        command.add(getAdbPath());
        if (serial != null) {
            command.add("-s");
            command.add(serial);
        }
        Collections.addAll(command, adbArgs);
        return command;
    }

    private static void execAdb(String serial, List<String> adbArgList) throws InterruptedException, IOException, CommandExecutionException {
        String[] adbArgs = adbArgList.toArray(new String[adbArgList.size()]);
        execAdb(serial, adbArgs);
    }

    private static void execSync(List<String> command) throws InterruptedException, IOException, CommandExecutionException {
        Log.d(TAG, "Execute: " + command);
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT).redirectError(ProcessBuilder.Redirect.INHERIT);
        Process process = processBuilder.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new CommandExecutionException(command, exitCode);
        }
    }

    private static boolean mustInstallClient(String serial) throws InterruptedException, IOException, CommandExecutionException {
        Log.i(TAG, "Checking gnirehtet client " + serial);
        List<String> command = createAdbCommand(serial, "shell", "dumpsys", "package", "com.genymobile.gnirehtet");
        Log.d(TAG, "Execute: " + command);
        Path tmpFile = null;
        try {
            tmpFile = Files.createTempFile("gnirehtet_" + serial, ".log");
            Process process = new ProcessBuilder(command).redirectOutput(tmpFile.toFile()).start();
            boolean exitCode = process.waitFor(10, TimeUnit.SECONDS);
            if (!exitCode) {
                throw new CommandExecutionException(command, -1);
            }
            try (Scanner scanner = new Scanner(new FileInputStream(tmpFile.toFile()))) {
                // read the versionCode of the installed package
                Pattern pattern = Pattern.compile("^    versionCode=(\\p{Digit}+).*");
                while (scanner.hasNextLine()) {
                    Matcher matcher = pattern.matcher(scanner.nextLine());
                    if (matcher.matches()) {
                        String installedVersionCode = matcher.group(1);
                        return !REQUIRED_APK_VERSION_CODE.equals(installedVersionCode);
                    }
                }
            }
            return true;
        } finally {
            if (tmpFile != null) {
                tmpFile.toFile().delete();
            }
        }
    }


    private static void printUsage() {
        StringBuilder builder = new StringBuilder("Syntax: gnirehtet (");
        Command[] commands = Command.values();
        for (int i = 0; i < commands.length; ++i) {
            if (i != 0) {
                builder.append('|');
            }
            builder.append(commands[i].command);
        }
        builder.append(") ...").append(NL);

        for (Command command : commands) {
            builder.append(NL);
            appendCommandUsage(builder, command);
        }

        System.err.print(builder.toString());
    }

    private static void appendCommandUsage(StringBuilder builder, Command command) {
        builder.append("  gnirehtet ").append(command.command);
        if ((command.acceptedParameters & CommandLineArguments.PARAM_SERIAL) != 0) {
            builder.append(" [serial]");
        }
        if ((command.acceptedParameters & CommandLineArguments.PARAM_DNS_SERVER) != 0) {
            builder.append(" [-d DNS[,DNS2,...]]");
        }
        if ((command.acceptedParameters & CommandLineArguments.PARAM_PORT) != 0) {
            builder.append(" [-p PORT]");
        }
        if ((command.acceptedParameters & CommandLineArguments.PARAM_ROUTES) != 0) {
            builder.append(" [-r ROUTE[,ROUTE2,...]]");
        }
        builder.append(NL);
        String[] descLines = command.getDescription().split("\n");
        for (String descLine : descLines) {
            builder.append("      ").append(descLine).append(NL);
        }
    }

    private static void printCommandUsage(Command command) {
        StringBuilder builder = new StringBuilder();
        appendCommandUsage(builder, command);
        System.err.print(builder.toString());
    }

    public static void main(String... args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String cmd = args[0];
        for (Command command : Command.values()) {
            if (cmd.equals(command.command)) {
                // forget args[0] containing the command name
                String[] commandArgs = Arrays.copyOfRange(args, 1, args.length);

                CommandLineArguments arguments;
                try {
                    arguments = CommandLineArguments.parse(command.acceptedParameters, commandArgs);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, e.getMessage());
                    printCommandUsage(command);
                    return;
                }

                command.execute(arguments);
                return;
            }
        }

        if ("rt".equals(cmd)) {
            Log.e(TAG, "The 'rt' command has been renamed to 'run'. Try 'gnirehtet run' instead.");
            printCommandUsage(Command.RUN);
        } else {
            Log.e(TAG, "Unknown command: " + cmd);
            printUsage();
        }
    }
}
