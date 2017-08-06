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

/**
 * Simple specific command-line arguments parser.
 */
public class CommandLineArguments {
    private String serial;
    private String dnsServers;

    public static CommandLineArguments parse(String... args) {
        CommandLineArguments arguments = new CommandLineArguments();
        for (int i = 0; i < args.length; ++i) {
            String arg = args[i];
            if ("-d".equals(arg)) {
                if (arguments.dnsServers != null) {
                    throw new IllegalArgumentException("DNS servers already set");
                }
                if (i == args.length - 1) {
                    throw new IllegalArgumentException("Missing -d parameter");
                }
                arguments.dnsServers = args[i + 1];
                ++i; // consume the -d parameter
            } else if (arguments.serial == null) {
                arguments.serial = arg;
            } else {
                throw new IllegalArgumentException("Unexpected argument: \"" + arg + "\"");
            }
        }
        return arguments;
    }

    public String getSerial() {
        return serial;
    }

    public String getDnsServers() {
        return dnsServers;
    }

    public boolean hasSerial() {
        return serial != null;
    }

    public boolean hasDnsServers() {
        return dnsServers != null;
    }

    public boolean isEmpty() {
        return serial == null && dnsServers == null;
    }
}
