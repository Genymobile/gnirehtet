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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Main {
    private static final String TAG = "Gnirehtet";
    private static final String NL = System.lineSeparator();
    private static final String REQUIRED_APK_VERSION_CODE = "4";

    private Main() {
        // not instantiable
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
        RUN("run", CommandLineArguments.PARAM_SERIAL | CommandLineArguments.PARAM_DNS_SERVER) {
            @Override
            String getDescription() {
                return "Enable reverse tethering for exactly one device:\n"
                        + "  - install the client if necessary;\n"
                        + "  - start the client;\n"
                        + "  - start the relay server;\n"
                        + "  - on Ctrl+C, stop both the relay server and the client.";
            }

            @Override
            @SuppressWarnings("checkstyle:MagicNumber")
            void execute(CommandLineArguments args) throws Exception {
                cmdRun(args.getSerial(), args.getDnsServers());
            }
        },
        START("start", CommandLineArguments.PARAM_SERIAL | CommandLineArguments.PARAM_DNS_SERVER) {
            @Override
            String getDescription() {
                return "Start the client on the Android device and exit.\n"
                        + "If several devices are connected via adb, then serial must be\n"
                        + "specified.\n"
                        + "If -d is given, then make the Android device use the specified\n"
                        + "DNS server(s). Otherwise, use 8.8.8.8 (Google public DNS).\n"
                        + "If the client is already started, then do nothing, and ignore\n"
                        + "DNS servers parameter.\n"
                        + "To use the host 'localhost' as DNS, use 10.0.2.2.";
            }

            @Override
            void execute(CommandLineArguments args) throws Exception {
                cmdStart(args.getSerial(), args.getDnsServers());
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
        RESTART("restart", CommandLineArguments.PARAM_SERIAL | CommandLineArguments.PARAM_DNS_SERVER) {
            @Override
            String getDescription() {
                return "Stop then start.";
            }

            @Override
            void execute(CommandLineArguments args) throws Exception {
                cmdRestart(args.getSerial(), args.getDnsServers());
            }
        },
        TUNNEL("tunnel", CommandLineArguments.PARAM_SERIAL) {
            @Override
            String getDescription() {
                return "Set up the 'adb reverse' tunnel.\n"
                        + "If a device is unplugged then plugged back while gnirehtet is\n"
                        + "active, resetting the tunnel is sufficient to get the\n"
                        + "connection back.";
            }

            @Override
            void execute(CommandLineArguments args) throws Exception {
                cmdTunnel(args.getSerial());
            }
        },
        RELAY("relay", CommandLineArguments.PARAM_NONE) {
            @Override
            String getDescription() {
                return "Start the relay server in the current terminal.";
            }

            @Override
            void execute(CommandLineArguments args) throws Exception {
                cmdRelay();
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
        execAdb(serial, "install", "-r", "gnirehtet.apk");
    }

    private static void cmdUninstall(String serial) throws InterruptedException, IOException, CommandExecutionException {
        Log.i(TAG, "Uninstalling gnirehtet client...");
        execAdb(serial, "uninstall", "com.genymobile.gnirehtet");
    }

    private static void cmdReinstall(String serial) throws InterruptedException, IOException, CommandExecutionException {
        cmdUninstall(serial);
        cmdInstall(serial);
    }

    private static void cmdRun(String serial, String dnsServers) throws InterruptedException, IOException, CommandExecutionException {
        if (mustInstallClient(serial)) {
            cmdInstall(serial);
            // wait a bit after the app is installed so that intent actions are correctly registered
            Thread.sleep(500); // ms
        }

        // start in parallel so that the relay server is ready when the client connects
        new Thread(() -> {
            try {
                cmdStart(serial, dnsServers);
            } catch (Exception e) {
                Log.e(TAG, "Cannot start client", e);
            }
        }).start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // executed on Ctrl+C
            try {
                cmdStop(serial);
            } catch (Exception e) {
                Log.e(TAG, "Cannot stop client", e);
            }
        }));

        cmdRelay();
    }

    private static void cmdStart(String serial, String dnsServers) throws InterruptedException, IOException, CommandExecutionException {
        Log.i(TAG, "Starting client...");
        cmdTunnel(serial);

        List<String> cmd = new ArrayList<>();
        Collections.addAll(cmd, "shell", "am", "broadcast", "-a", "com.genymobile.gnirehtet.START", "-n",
                "com.genymobile.gnirehtet/.GnirehtetControlReceiver");
        if (dnsServers != null) {
            Collections.addAll(cmd, "--esa", "dnsServers", dnsServers);
        }
        execAdb(serial, cmd);
    }

    private static void cmdStop(String serial) throws InterruptedException, IOException, CommandExecutionException {
        Log.i(TAG, "Stopping client...");
        execAdb(serial, "shell", "am", "broadcast", "-a", "com.genymobile.gnirehtet.STOP", "-n",
                "com.genymobile.gnirehtet/.GnirehtetControlReceiver");
    }

    private static void cmdRestart(String serial, String dnsServers) throws InterruptedException, IOException, CommandExecutionException {
        cmdStop(serial);
        cmdStart(serial, dnsServers);
    }

    private static void cmdTunnel(String serial) throws InterruptedException, IOException, CommandExecutionException {
        execAdb(serial, "reverse", "localabstract:gnirehtet", "tcp:31416");
    }

    private static void cmdRelay() throws IOException {
        Log.i(TAG, "Starting relay server...");
        new Relay().run();
    }

    private static void execAdb(String serial, String... adbArgs) throws InterruptedException, IOException, CommandExecutionException {
        execSync(createAdbCommand(serial, adbArgs));
    }

    private static List<String> createAdbCommand(String serial, String... adbArgs) {
        List<String> command = new ArrayList<>();
        command.add("adb");
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
        Log.i(TAG, "Checking gnirehtet client...");
        List<String> command = createAdbCommand(serial, "shell", "dumpsys", "package", "com.genymobile.gnirehtet");
        Log.d(TAG, "Execute: " + command);
        Process process = new ProcessBuilder(command).start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new CommandExecutionException(command, exitCode);
        }
        Scanner scanner = new Scanner(process.getInputStream());
        // read the versionCode of the installed package
        Pattern pattern = Pattern.compile("^    versionCode=(\\p{Digit}+).*");
        while (scanner.hasNextLine()) {
            Matcher matcher = pattern.matcher(scanner.nextLine());
            if (matcher.matches()) {
                String installedVersionCode = matcher.group(1);
                return !REQUIRED_APK_VERSION_CODE.equals(installedVersionCode);
            }
        }
        return true;
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
            builder.append(" [-d|--dns DNS[,DNS2,...]]");
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
