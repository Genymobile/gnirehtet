/*
 * Copyright (C) 2018 Genymobile
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

public class InvalidCIDRException extends Exception {

    private String cidr;

    private static String createMessage(String cidr) {
        return "Invalid CIDR:" + cidr;
    }

    public InvalidCIDRException(String cidr, Throwable cause) {
        super(createMessage(cidr), cause);
        this.cidr = cidr;
    }

    public InvalidCIDRException(String cidr) {
        super(createMessage(cidr));
        this.cidr = cidr;
    }

    public String getCIDR() {
        return cidr;
    }
}
