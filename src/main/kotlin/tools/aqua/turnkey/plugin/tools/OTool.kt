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

/**
 * Wrapper for an `otool`. This is provided on macOS by Apple. The LLVM project provides an
 * implementation for Linux. Since linkage information for Mach-O uses paths to identify libraries,
 * all such instances use [PlatformPath].
 */
abstract class OTool : AbstractTool() {
  private companion object {
    private val logger = getLogger(OTool::class.java)
    private val usedLibraryRegex = """(.*) \(compatibility version.*\)""".toRegex()
    private val rPathRegex = """path (.*) \(offset [0-9]+\)""".toRegex()
  }

  private val otool: String by lazy { parameters.extension.flatMap { it.otool }.get() }

  /** Get the tool's version string. */
  fun version(): CharSequence =
      exec.execGetOutputAndLogError(logger) {
        executable(otool)
        args("--version")
      }

  /** Get [file]'s RPATH. */
  fun getRPath(file: Path): List<PlatformPath> = getRPaths(listOf(file)).values.single()

  /** Get the RPATHs for all [files], associated by file. */
  fun getRPaths(files: Iterable<Path>): Map<Path, List<PlatformPath>> =
      exec
          .execGetOutputAndLogError(logger) {
            executable(otool)
            args("-D")
            files.forEach { args(it.absolutePathString()) }
          }
          .separateByFileHeaders(files)
          .mapValues { (file, lines) ->
            lines
                .withIndex()
                .filter { (_, line) -> line.trim() == "cmd LC_RPATH" }
                .map {
                  val commandIndex = it.index + 2
                  assert(commandIndex < lines.size) {
                    "output too short after LC_RPATH command in line ${it.index} for file $file"
                  }
                  val line = lines[commandIndex].trim()
                  val match =
                      rPathRegex.matchEntire(line)
                          ?: error("output contains unreadable rpath $line for file $file")
                  val (library) = match.destructured

                  try {
                    PlatformPath.unix(library)
                  } catch (e: InvalidPathException) {
                    throw IllegalStateException(
                        "output contains non-path rpath $library for $file", e)
                  }
                }
          }

  /** Get the library ID for [file]. */
  fun getSharedLibraryID(file: Path): PlatformPath =
      getSharedLibraryIDs(listOf(file)).values.single()

  /** Get the library ID for all [files], associated by file. */
  fun getSharedLibraryIDs(files: Iterable<Path>): Map<Path, PlatformPath> =
      exec
          .execGetOutputAndLogError(logger) {
            executable(otool)
            args("-D")
            files.forEach { args(it.absolutePathString()) }
          }
          .separateByFileHeaders(files)
          .mapValues { (file, lines) ->
            val line = lines.singleOrNull() ?: error("output contains multiple IDs for $file")
            try {
              PlatformPath.unix(line)
            } catch (e: InvalidPathException) {
              throw IllegalStateException("output contains unreadable ID $line", e)
            }
          }

  /** Get the libraries used by [file]. */
  fun getUsedSharedLibraries(file: Path): List<PlatformPath> =
      getUsedSharedLibraries(listOf(file)).values.single()

  /** Get the libraries used by the [files], associated by file. */
  fun getUsedSharedLibraries(files: Iterable<Path>): Map<Path, List<PlatformPath>> =
      exec
          .execGetOutputAndLogError(logger) {
            executable(otool)
            args("-L")
            files.forEach { args(it.absolutePathString()) }
          }
          .separateByFileHeaders(files)
          .mapValues { (file, lines) ->
            lines.map {
              val line = it.trim()
              val match =
                  usedLibraryRegex.matchEntire(line)
                      ?: error("output contains unreadable linkage $line for file $file")
              val (library) = match.destructured
              try {
                PlatformPath.unix(library)
              } catch (e: InvalidPathException) {
                throw IllegalStateException(
                    "output contains non-path linkage $library for $file", e)
              }
            }
          }
}

private fun CharSequence.separateByFileHeaders(files: Iterable<Path>): Map<Path, List<String>> =
    lines().filter(String::isNotBlank).separateByFileHeaders(files)

private fun Iterable<String>.separateByFileHeaders(files: Iterable<Path>): Map<Path, List<String>> {
  val nameToFile = files.associateBy { it.toString() }
  return separateByHeaders(nameToFile.keys).mapKeys { (file, _) -> nameToFile.getValue(file) }
}

private fun Iterable<String>.separateByHeaders(
    headers: Iterable<String>
): Map<String, List<String>> {
  val result =
      headers.associateWithTo(mutableMapOf<String, MutableList<String>>()) { mutableListOf() }
  var currentResult: MutableList<String>? = null

  forEach {
    if (it.endsWith(':')) {
      val sectionHeading = it.removeSuffix(":")
      currentResult =
          (result[sectionHeading] ?: error("output contains unknown section $sectionHeading"))
    } else {
      (currentResult ?: error("output contains data before section")).add(it)
    }
  }

  return result
}
