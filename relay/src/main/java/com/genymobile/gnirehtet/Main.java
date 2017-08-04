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

public final class Main {
    private static final String TAG = Main.class.getSimpleName();

    private Main() {
        // not instantiable
    }

    enum Command {
        INSTALL("install", "[serial]") {
            @Override
            String getDescription() {
                return "Install the client on the Android device and exit.\n"
                        + "If several devices are connected via adb, then serial must be\n"
                        + "specified.";
            }

            @Override
            void execute(List<String> args) throws Exception {
                Log.i(TAG, "Installing gnirehtet...");
                String serial = args.isEmpty() ? null : args.get(0);
                execAdb(serial, "install", "-r", "gnirehtet.apk");
            }
        },
        UNINSTALL("uninstall", "[serial]") {
            @Override
            String getDescription() {
                return "Uninstall the client from the Android device and exit.\n"
                        + "If several devices are connected via adb, then serial must be\n"
                        + "specified.";
            }

            @Override
            void execute(List<String> args) throws Exception {
                Log.i(TAG, "Uninstalling gnirehtet...");
                String serial = args.isEmpty() ? null : args.get(0);
                execAdb(serial, "uninstall", "com.genymobile.gnirehtet");
            }
        },
        REINSTALL("reinstall", "[serial]") {
            @Override
            String getDescription() {
                return "Uninstall then install.";
            }

            @Override
            void execute(List<String> args) throws Exception {
                UNINSTALL.execute(args);
                INSTALL.execute(args);
            }
        },
        RT("rt", "[serial] [-d DNS[,DNS2,...]]") {
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
            void execute(List<String> args) throws Exception {
                String dns = extractDnsArg(args); // don't care about the result, just remove them to get the serial
                String serial = args.isEmpty() ? null : args.get(0);

                if (!isGnirehtetInstalled(serial)) {
                    // args don't contain the dns args anymore
                    INSTALL.execute(args);
                    // wait a bit after the app is installed so that intent actions are correctly registered
                    Thread.sleep(500); // ms
                }

                // start in parallel so that the relay server is ready when the client connects
                new Thread(() -> {
                    try {
                        startGnirehtet(serial, dns);
                    } catch (Exception e) {
                        Log.e(TAG, "Cannot start gnirehtet", e);
                    }
                }).start();

                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    // executed on Ctrl+C
                    try {
                        STOP.execute(Arrays.asList(serial));
                    } catch (Exception e) {
                        Log.e(TAG, "Cannot stop gnirehtet", e);
                    }
                }));

                RELAY.execute(Collections.EMPTY_LIST);
            }
        },
        START("start", "[serial] [-d DNS[,DNS2,...]]") {
            @Override
            String getDescription() {
                return "Start a client on the Android device and exit.\n"
                        + "If several devices are connected via adb, then serial must be\n"
                        + "specified.\n"
                        + "If -d is given, then make the Android device use the specified\n"
                        + "DNS server(s). Otherwise, use 8.8.8.8 (Google public DNS).\n"
                        + "If the client is already started, then do nothing, and ignore\n"
                        + "DNS servers parameter.\n"
                        + "To use the host 'localhost' as DNS, use 10.0.2.2.";
            }

            @Override
            void execute(List<String> args) throws Exception {
                String dns = extractDnsArg(args);
                String serial = args.isEmpty() ? null : args.get(0);
                startGnirehtet(serial, dns);
            }
        },
        STOP("stop", "[serial]") {
            @Override
            String getDescription() {
                return "Stop the client on the Android device and exit.\n"
                        + "If several devices are connected via adb, then serial must be\n"
                        + "specified.";
            }

            @Override
            void execute(List<String> args) throws Exception {
                Log.i(TAG, "Stopping gnirehtet...");
                String serial = args.isEmpty() ? null : args.get(0);
                execAdb(serial, "shell", "am", "startservice", "-a", "com.genymobile.gnirehtet.STOP");
            }
        },
        RELAY("relay") {
            @Override
            String getDescription() {
                return "Start the relay server in the current terminal.";
            }

            @Override
            void execute(List<String> args) throws Exception {
                Log.i(TAG, "Starting relay server...");
                new Relay().start();
            }
        };

        private String command;
        private String syntax;

        Command(String command) {
            this.command = command;
        }

        Command(String command, String syntax) {
            this(command);
            this.syntax = syntax;
        }

        abstract String getDescription();

        abstract void execute(List<String> args) throws Exception;
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
        Log.i(TAG, "Execute: " + command);
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT).redirectError(ProcessBuilder.Redirect.INHERIT);
        Process process = processBuilder.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new CommandExecutionException(command, exitCode);
        }
    }

    private static boolean isGnirehtetInstalled(String serial) throws IOException {
        List<String> command = createAdbCommand(serial, "shell", "pm", "list", "packages", "com.genymobile.gnirehtet");
        Log.i(TAG, "Execute: " + command);
        Process process = new ProcessBuilder(command).start();
        Scanner scanner = new Scanner(process.getInputStream());
        // empty output when not found
        return scanner.hasNextLine();
    }

    private static void startGnirehtet(String serial, String dns) throws InterruptedException, IOException, CommandExecutionException {
        Log.i(TAG, "Starting gnirehtet...");
        execAdb(serial, "reverse", "tcp:31416", "tcp:31416");

        List<String> cmd = new ArrayList<>();
        Collections.addAll(cmd, "shell", "am", "startservice", "-a", "com.genymobile.gnirehtet.START");
        if (dns != null) {
            Collections.addAll(cmd, "--esa", "dnsServers", dns);
        }
        execAdb(serial, cmd);
    }

    /**
     * Remove {@code ["-d", DNS_SERV]} from {@code command} and return {@code DNS_SERV}.
     *
     * @param args the command arguments
     * @return the DNS servers value, {@code null} if none specified
     */
    private static String extractDnsArg(List<String> args) {
        // "-d" may not be in last position, since it requires an argument
        for (int i = 0; i < args.size() - 1; ++i) {
            if ("-d".equals(args.get(i))) {
                String dns = args.get(i + 1);
                args.subList(i, i + 2).clear();
                return dns;
            }
        }
        return null;
    }

    private static void printUsage() {
        final String newLine = System.lineSeparator();

        StringBuilder builder = new StringBuilder("Syntax: gnirehtet (");
        Command[] commands = Command.values();
        for (int i = 0; i < commands.length; ++i) {
            if (i != 0) {
                builder.append('|');
            }
            builder.append(commands[i].command);
        }
        builder.append(") ...").append(newLine);

        for (Command command : commands) {
            builder.append(newLine).append("  gnirehtet ").append(command.command);
            if (command.syntax != null) {
                builder.append(' ').append(command.syntax);
            }
            builder.append(newLine);
            String[] descLines = command.getDescription().split("\n");
            for (String descLine : descLines) {
                builder.append("      ").append(descLine).append(newLine);
            }
        }

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
                List<String> commandArgs = new ArrayList<>(args.length - 1);
                // forget args[0] containing the command name
                for (int i = 1; i < args.length; ++i) {
                    commandArgs.add(args[i]);
                }
                command.execute(commandArgs);
                return;
            }
        }

        System.err.println("Unknown command: " + cmd);
        printUsage();
    }
}
