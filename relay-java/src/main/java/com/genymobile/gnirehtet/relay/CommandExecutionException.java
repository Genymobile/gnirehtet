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

package com.genymobile.gnirehtet.relay;

import java.util.List;

public class CommandExecutionException extends Exception {

    private List<String> command;
    private int exitCode;
    private String output;

    public CommandExecutionException(List<String> command, int exitCode) {
        this(command, exitCode, null);
    }

    public CommandExecutionException(List<String> command, int exitCode, String output) {
        super(createMessage(command, exitCode, output));
        this.command = command;
        this.exitCode = exitCode;
        this.output = output;
    }

    private static String createMessage(List<String> command, int exitCode, String output) {
        String message = "Command " + command + " returned with value " + exitCode;
        if (output != null && !output.trim().isEmpty()) {
            message += ": " + output.trim();
        }
        return message;
    }

    public int getExitCode() {
        return exitCode;
    }

    public List<String> getCommand() {
        return command;
    }

    public String getOutput() {
        return output;
    }
}
