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
package com.genymobile.gnirehtet

class InvalidCIDRException : Exception {
    val cIDR: String?

    constructor(cidr: String?, cause: Throwable?) : super(createMessage(cidr), cause) {
        this.cIDR = cidr
    }

    constructor(cidr: String) : super(createMessage(cidr)) {
        this.cIDR = cidr
    }

    companion object {
        private fun createMessage(cidr: String?): String {
            return "Invalid CIDR:" + cidr
        }
    }
}