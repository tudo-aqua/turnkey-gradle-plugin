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

import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import org.gradle.api.logging.Logging.getLogger
import tools.aqua.turnkey.plugin.util.PlatformPath
import tools.aqua.turnkey.plugin.util.execGetOutputAndLogError
import tools.aqua.turnkey.plugin.util.execLogOutputAndLogError

/**
 * Wrapper for a `patchelf`. This is provided by the NixOS project for most platforms. Since linkage
 * information for ELF uses full paths as RPaths, all such instances use [PlatformPath].
 */
abstract class PatchELF : AbstractTool() {
  private companion object {
    private val logger = getLogger(PatchELF::class.java)
  }

  private val patchelf: String by lazy { parameters.extension.flatMap { it.patchelf }.get() }

  /** Get the tool's version string. */
  fun version(): CharSequence =
      exec.execGetOutputAndLogError(logger) {
        executable(patchelf)
        args("--version")
      }

  /** Get [file]'s SONAME. */
  fun getSOName(file: Path): String {
    val line =
        exec
            .execGetOutputAndLogError(logger) {
              executable(patchelf)
              args("--print-soname", file.absolutePathString())
            }
            .lineSequence()
            .singleOrNull() ?: error("output contains multiple IDs for $file")
    try {
      return line
    } catch (e: InvalidPathException) {
      throw IllegalStateException("output contains unreadable ID $line", e)
    }
  }

  /** Set [file]'s SONAME to [soName]. */
  fun setSOName(file: Path, soName: String) =
      exec.execLogOutputAndLogError(logger) {
        executable(patchelf)
        args("--set-soname", soName, file.absolutePathString())
      }

  /** Add all [entries] to [file]'s RPATH. */
  fun addRPath(file: Path, vararg entries: String) = addRPath(file, entries.asIterable())

  /** Add all [entries] to [file]'s RPATH. */
  fun addRPath(file: Path, entries: Iterable<String>) =
      exec.execLogOutputAndLogError(logger) {
        executable(patchelf)
        args("--add-rpath", entries.joinToString(":"), file.absolutePathString())
      }

  /** Get [file]'s RPATH. */
  fun getRPath(file: Path): List<PlatformPath> =
      exec
          .execGetOutputAndLogError(logger) {
            executable(patchelf)
            args("--print-rpath", file.absolutePathString())
          }
          .trim()
          .split(':')
          .map { PlatformPath.unix(it) }

  /** Remove all entries from [file]'s RPATH. */
  fun clearRPath(file: Path) =
      exec.execLogOutputAndLogError(logger) {
        executable(patchelf)
        args("--remove-rpath", file.absolutePathString())
      }

  /** Replace all entries in [file]'s RPATH with [entries]. */
  fun setRPath(file: Path, vararg entries: PlatformPath) = setRPath(file, entries.asIterable())

  /** Replace all entries in [file]'s RPATH with [entries]. */
  fun setRPath(file: Path, entries: Iterable<PlatformPath>) =
      exec.execLogOutputAndLogError(logger) {
        executable(patchelf)
        args("--set-rpath", entries.joinToString(":"), file.absolutePathString())
      }

  /** Add all [entries] to [file]'s needed libraries. */
  fun addNeeded(file: Path, vararg entries: String) = addNeeded(file, entries.asIterable())

  /** Add all [entries] to [file]'s needed libraries. */
  fun addNeeded(file: Path, entries: Iterable<String>) =
      exec.execLogOutputAndLogError(logger) {
        executable(patchelf)
        entries.forEach { args("--add-needed", it) }
        args(file.absolutePathString())
      }

  /** Get [file]'s needed libraries. */
  fun getNeeded(file: Path): List<String> =
      exec
          .execGetOutputAndLogError(logger) {
            executable(patchelf)
            args("--print-needed", file.absolutePathString())
          }
          .trim()
          .lines()

  /** Remove the given [entries] from [file]'s needed libraries. */
  fun removeNeeded(file: Path, vararg entries: String) = removeNeeded(file, entries.asIterable())

  /** Remove the given [entries] from [file]'s needed libraries. */
  fun removeNeeded(file: Path, entries: Iterable<String>) =
      exec.execLogOutputAndLogError(logger) {
        executable(patchelf)
        entries.forEach { args("--remove-needed", it) }
        args(file.absolutePathString())
      }

  /** Replace entries in [file]'s needed libraries according to the given [replacements]. */
  fun replaceNeeded(file: Path, vararg replacements: Pair<String, String>) =
      replaceNeeded(file, replacements.asIterable())

  /** Replace entries in [file]'s needed libraries according to the given [replacements]. */
  fun replaceNeeded(file: Path, replacements: Iterable<Pair<String, String>>) =
      exec.execLogOutputAndLogError(logger) {
        executable(patchelf)
        replacements.forEach { (from, to) -> args("--replace-needed", from, to) }
        args(file.absolutePathString())
      }
}
