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

import java.io.StringWriter
import org.apache.commons.io.output.WriterOutputStream
import org.gradle.api.Action
import org.gradle.api.logging.Logger
import org.gradle.process.ExecOperations
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec

/** An [ExecResult] [result] with separately gathered [standardOutput] and [standardError]. */
internal data class ExecResultWithOutput(
    val result: ExecResult,
    val standardOutput: CharSequence,
    val standardError: CharSequence,
)

private inline fun ExecOperations.execWithFinally(
    action: Action<in ExecSpec>,
    finalAction: (CharSequence, CharSequence) -> Unit
): tools.aqua.turnkey.plugin.util.ExecResultWithOutput {
  val stdOut = StringWriter()
  val stdErr = StringWriter()
  val result =
      try {
        exec {
          action.execute(this)
          standardOutput = WriterOutputStream.builder().setWriter(stdOut).get()
          errorOutput = WriterOutputStream.builder().setWriter(stdErr).get()
        }
      } finally {
        finalAction(stdOut.buffer, stdErr.buffer)
      }
  return tools.aqua.turnkey.plugin.util.ExecResultWithOutput(result, stdOut.buffer, stdErr.buffer)
}

internal fun ExecOperations.execGetOutputAndLogError(
    logger: Logger,
    action: Action<in ExecSpec>
): CharSequence {
  val (_, out, _) = execWithFinally(action) { _, err -> logger.warnMultiline(err) }
  return out
}

internal fun ExecOperations.execLogOutputAndLogError(logger: Logger, action: Action<in ExecSpec>) {
  execWithFinally(action) { out, err ->
    logger.warnMultiline(err)
    logger.infoMultiline(out)
  }
}
