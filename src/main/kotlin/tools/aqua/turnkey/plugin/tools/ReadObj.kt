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

package tools.aqua.turnkey.plugin.tools

import java.nio.file.Path
import kotlin.io.path.absolutePathString
import org.gradle.api.logging.Logging.getLogger
import tools.aqua.turnkey.plugin.util.execGetOutputAndLogError

/** Wrapper for a `readobj`. This is provided by GNU and LLVM projects for most platforms. */
abstract class ReadObj : AbstractTool() {

  private companion object {
    private val logger = getLogger(ReadObj::class.java)
  }

  private val readobj: String by lazy { parameters.extension.flatMap { it.readobj }.get() }

  /** Get the tool's version string. */
  fun version(): CharSequence =
      exec.execGetOutputAndLogError(logger) {
        executable(readobj)
        args("--version")
      }

  /** Get [file]'s imported libraries. */
  fun getCOFFImports(file: Path): List<String> {
    val lines =
        exec
            .execGetOutputAndLogError(logger) {
              executable(readobj)
              args("--coff-imports", file.absolutePathString())
            }
            .trim()
            .lines()
    return buildList {
      var inImport = false
      var foundName = false
      lines.asSequence().map(String::trim).withIndex().forEach { (index, line) ->
        if (line == "Import {") {
          check(!inImport) { "Unterminated import, second import in line $index" }
          inImport = true
          foundName = false
        } else if (inImport && line == "}") {
          check(foundName) { "Import without name, closing bracket in line $index" }
          inImport = false
        } else if (inImport && line.startsWith("Name: ")) {
          check(!foundName) { "Duplicate name in line $index" }
          this += line.removePrefix("Name: ")
          foundName = true
        }
      }
    }
  }
}
