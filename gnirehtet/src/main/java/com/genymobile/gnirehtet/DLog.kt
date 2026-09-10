/*
 * Copyright 2025 David Johansson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package  com.genymobile.gnirehtet

import android.os.Bundle
import android.util.Log

object DLog {
    private const val VERBOSE = true

    fun forTag(clazz: Class<*>): String = "@@.${clazz.simpleName}"

    fun method(tag: String, msg: Any) {
        Log.d(tag, "\uD83D\uDFE6$msg") // Blue box
    }

    fun verbose(tag: String, msg: Any) {
        if(VERBOSE)
            Log.v(tag, "\uD83D\uDFE6$msg") // Blue box
    }

    fun debug(tag: String, msg: Any) {
        Log.d(tag, "\uD83D\uDFE6$msg") // Blue box
    }

    fun info(tag: String, msg: Any) {
        Log.i(tag, "\uD83D\uDFE9$msg") // green ball
    }

    fun warning(tag: String, msg: Any) {
        Log.w(tag, "\uD83D\uDFE7$msg") // orange ball
    }

    fun error(tag: String, msg: Any) {
        Log.e(tag, "\uD83D\uDFE5$msg") // red box
    }

    fun exception(tag: String, msg: Any, e: Throwable) {
        Log.e(tag, "\uD83D\uDFE5$msg", e) // red box
    }

    fun bundle(tag: String, bundle: Bundle?) {
        for (key in bundle?.keySet()!!) {
            debug(tag, "$key = " + bundle.get(key))
        }
    }
}