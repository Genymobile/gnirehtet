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

import com.genymobile.gnirehtet.relay.Relay;

/**
 * Simple specific command-line arguments parser.
 */
public class CommandLineArguments {

    public static final int PARAM_NONE = 0;
    public static final int PARAM_SERIAL = 1;
    public static final int PARAM_DNS_SERVER = 1 << 1;
    public static final int PARAM_ROUTES = 1 << 2;
    public static final int PARAM_PORT = 1 << 3;
    private int port = Relay.DEFAULT_PORT;
    private String serial;
    private String dnsServers;
    private String routes;

    public static CommandLineArguments parse(int acceptedParameters, String... args) {
        CommandLineArguments arguments = new CommandLineArguments();
        for (int i = 0; i < args.length; ++i) {
            String arg = args[i];
            if ((acceptedParameters & PARAM_DNS_SERVER) != 0 && "-d".equals(arg)) {
                if (arguments.dnsServers != null) {
                    throw new IllegalArgumentException("DNS servers already set");
                }
                if (i == args.length - 1) {
                    throw new IllegalArgumentException("Missing -d parameter");
                }
                arguments.dnsServers = args[i + 1];
                ++i; // consume the -d parameter
            } else if ((acceptedParameters & PARAM_ROUTES) != 0 && "-r".equals(arg)) {
                if (arguments.routes != null) {
                    throw new IllegalArgumentException("Routes already set");
                }
                if (i == args.length - 1) {
                    throw new IllegalArgumentException("Missing -r parameter");
                }
                arguments.routes = args[i + 1];
                ++i; // consume the -r parameter
            } else if ((acceptedParameters & PARAM_PORT) != 0 && "-p".equals(arg)) {
                if (arguments.routes != null) {
                    throw new IllegalArgumentException("Port already set");
                }

                if (i == args.length -1) {
                    throw new IllegalArgumentException("Missing -p parameter");
                }
                arguments.port = Integer.parseInt(args[i + 1]);
                ++i;
            } else if ((acceptedParameters & PARAM_SERIAL) != 0 && arguments.serial == null) {
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

    public String getRoutes() {
        return routes;
    }

    public int getPort() {
        return port;
    }
}
