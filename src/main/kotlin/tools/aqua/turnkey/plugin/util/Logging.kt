/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2019-2024 The TurnKey Authors
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

package tools.aqua.turnkey.plugin.util

import org.gradle.api.logging.LogLevel.DEBUG
import org.gradle.api.logging.LogLevel.ERROR
import org.gradle.api.logging.LogLevel.INFO
import org.gradle.api.logging.LogLevel.LIFECYCLE
import org.gradle.api.logging.LogLevel.QUIET
import org.gradle.api.logging.LogLevel.WARN
import org.gradle.api.logging.Logger

internal fun Logger.debugMultiline(message: CharSequence) {
  if (isDebugEnabled && message.isNotEmpty()) message.lineSequence().forEach(::debug)
}

internal inline fun Logger.debug(message: () -> String) {
  if (isDebugEnabled) log(DEBUG, message())
}

internal inline fun Logger.debug(throwable: Throwable, message: () -> String) {
  if (isDebugEnabled) log(DEBUG, message(), throwable)
}

internal fun Logger.infoMultiline(message: CharSequence) {
  if (isDebugEnabled && message.isNotEmpty()) message.lineSequence().forEach(::info)
}

internal inline fun Logger.info(message: () -> String) {
  if (isInfoEnabled) log(INFO, message())
}

internal inline fun Logger.info(throwable: Throwable, message: () -> String) {
  if (isInfoEnabled) log(INFO, message(), throwable)
}

internal fun Logger.lifecycleMultiline(message: CharSequence) {
  if (isDebugEnabled && message.isNotEmpty()) message.lineSequence().forEach(::lifecycle)
}

internal inline fun Logger.lifecycle(message: () -> String) {
  if (isLifecycleEnabled) log(LIFECYCLE, message())
}

internal inline fun Logger.lifecycle(throwable: Throwable, message: () -> String) {
  if (isLifecycleEnabled) log(LIFECYCLE, message(), throwable)
}

internal fun Logger.warnMultiline(message: CharSequence) {
  if (isDebugEnabled && message.isNotEmpty()) message.lineSequence().forEach(::warn)
}

internal inline fun Logger.warn(message: () -> String) {
  if (isWarnEnabled) log(WARN, message())
}

internal inline fun Logger.warn(throwable: Throwable, message: () -> String) {
  if (isWarnEnabled) log(WARN, message(), throwable)
}

internal fun Logger.quietMultiline(message: CharSequence) {
  if (isDebugEnabled && message.isNotEmpty()) message.lineSequence().forEach(::quiet)
}

internal inline fun Logger.quiet(message: () -> String) {
  if (isQuietEnabled) log(QUIET, message())
}

internal inline fun Logger.quiet(throwable: Throwable, message: () -> String) {
  if (isQuietEnabled) log(QUIET, message(), throwable)
}

internal fun Logger.errorMultiline(message: CharSequence) {
  if (isDebugEnabled && message.isNotEmpty()) message.lineSequence().forEach(::error)
}

internal inline fun Logger.error(message: () -> String) {
  if (isQuietEnabled) log(ERROR, message())
}

internal inline fun Logger.error(throwable: Throwable, message: () -> String) {
  if (isQuietEnabled) log(ERROR, message(), throwable)
}
