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
import tools.aqua.turnkey.plugin.util.PlatformPath
import tools.aqua.turnkey.plugin.util.execGetOutputAndLogError
import tools.aqua.turnkey.plugin.util.execLogOutputAndLogError

/**
 * Wrapper for an `install_name_tool`. This is provided on macOS by Apple. The LLVM project provides
 * an implementation for Linux. Since linkage information for Mach-O uses paths to identify
 * libraries, all such instances use [PlatformPath].
 */
abstract class InstallNameTool : AbstractTool() {
  private companion object {
    private val logger = getLogger(InstallNameTool::class.java)
  }

  private val installNameTool: String by lazy {
    parameters.extension.flatMap { it.installNameTool }.get()
  }

  /** Get the tool's version string. */
  fun version(): CharSequence =
      exec.execGetOutputAndLogError(logger) {
        executable(installNameTool)
        args("--version")
      }

  /** Add [entries] to the RPATH of [file]. */
  fun addRPath(file: Path, vararg entries: PlatformPath) = addRPath(file, entries.asIterable())

  /** Add [entries] to the RPATH of [file]. */
  fun addRPath(file: Path, entries: Iterable<PlatformPath>) =
      exec.execLogOutputAndLogError(logger) {
        executable(installNameTool)
        entries.forEach { args("-add_rpath", it.toString()) }
        args(file.absolutePathString())
      }

  /** Remove the RPATH of [file]. */
  fun clearRPath(file: Path) =
      exec.execLogOutputAndLogError(logger) {
        executable(installNameTool)
        args("-delete_all_rpaths", file.absolutePathString())
      }

  /** Remove [entries] from the RPATH of [file]. */
  fun deleteRPath(file: Path, vararg entries: PlatformPath) =
      deleteRPath(file, entries.asIterable())

  /** Remove [entries] from the RPATH of [file]. */
  fun deleteRPath(file: Path, entries: Iterable<PlatformPath>) =
      exec.execLogOutputAndLogError(logger) {
        executable(installNameTool)
        entries.forEach { args("-delete_rpath", it.toString()) }
        args(file.absolutePathString())
      }

  /** Prepend [entries] to the RPATH of [file]. */
  fun prependRPath(file: Path, vararg entries: PlatformPath) =
      prependRPath(file, entries.asIterable())

  /** Prepend [entries] to the RPATH of [file]. */
  fun prependRPath(file: Path, entries: Iterable<PlatformPath>) =
      exec.execLogOutputAndLogError(logger) {
        executable(installNameTool)
        entries.forEach { args("-prepend_rpath", it.toString()) }
        args(file.absolutePathString())
      }

  /** Update the RPATH of [file] by replacing all [Pair.first] of [entries] with [Pair.second]. */
  fun replaceRPath(file: Path, vararg entries: Pair<PlatformPath, PlatformPath>) =
      replaceRPath(file, entries.asIterable())

  /** Update the RPATH of [file] by replacing all [Pair.first] of [entries] with [Pair.second]. */
  fun replaceRPath(file: Path, entries: Iterable<Pair<PlatformPath, PlatformPath>>) =
      exec.execLogOutputAndLogError(logger) {
        executable(installNameTool)
        entries.forEach { (from, to) -> args("-rpath", from.toString(), to.toString()) }
        args(file.absolutePathString())
      }

  /** Set the library ID of [file] to [id]. */
  fun setID(file: Path, id: PlatformPath) =
      exec.execLogOutputAndLogError(logger) {
        executable(installNameTool)
        args("-id", id.toString(), file.absolutePathString())
      }

  /**
   * Update the dependencies of [file] by replacing all [Pair.first] of [entries] with
   * [Pair.second].
   */
  fun replaceLibrary(file: Path, vararg entries: Pair<PlatformPath, PlatformPath>) =
      replaceLibrary(file, entries.asIterable())

  /**
   * Update the dependencies of [file] by replacing all [Pair.first] of [entries] with
   * [Pair.second].
   */
  fun replaceLibrary(file: Path, entries: Iterable<Pair<PlatformPath, PlatformPath>>) =
      exec.execLogOutputAndLogError(logger) {
        executable(installNameTool)
        entries.forEach { (from, to) -> args("-change", from.toString(), to.toString()) }
        args(file.absolutePathString())
      }
}
