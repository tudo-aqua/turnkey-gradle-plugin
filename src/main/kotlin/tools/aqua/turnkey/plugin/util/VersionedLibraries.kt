/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2019-2025 The TurnKey Authors
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

private const val SO_EXT = ".so"

/**
 * An ELF library name, consisting of a [name] and a potentially empty [version] (i.e., sequence of
 * numbers).
 *
 * ELF library names have the form `libbasename.so.1.2.3`, with `libbasename` being the name and
 * `1.2.3` being the version.
 */
internal data class VersionedELFLibrary(val name: String, val version: List<Int> = emptyList()) {
  /** Holder for factory function. */
  companion object {
    /** Parse the [fullName] into a [VersionedELFLibrary]. */
    @JvmStatic
    fun parse(fullName: String): VersionedELFLibrary {
      val soIndex = fullName.lastIndexOf(SO_EXT)
      require(soIndex >= 0) { "Library name '$fullName' must contain '$SO_EXT'" }

      val name = fullName.substring(0, soIndex)
      val versionString = fullName.substring(soIndex + SO_EXT.length).removePrefix(".")
      val version =
          if (versionString.isEmpty()) emptyList()
          else {
            versionString.split('.').map {
              requireNotNull(it.toIntOrNull()) {
                "Library name '$fullName' contains non-numerical version component '$it'"
              }
            }
          }

      return VersionedELFLibrary(name, version)
    }
  }

  override fun toString(): String =
      if (version.isEmpty()) "$name.so" else "$name.so.${version.joinToString(".")}"

  /** The library's name without version. */
  val baseName
    get() = "$name.so"
}

private const val DYLIB_EXT = ".dylib"

/**
 * A Mach-O library name, consisting of a [name] and a potentially empty [version] (i.e., sequence
 * of strings).
 *
 * Mach-O library names have the form `libbasename.1.2.A.dylib`, with `libbasename` being the name
 * and `1.2.A` being the version.
 */
internal data class VersionedMachOLibrary(
    val name: String,
    val version: List<String> = emptyList()
) {
  /** Holder for factory function. */
  companion object {
    /** Parse the [fullName] into a [VersionedMachOLibrary]. */
    @JvmStatic
    fun parse(fullName: String): VersionedMachOLibrary {
      val nameAndVersion = fullName.removeSuffix(DYLIB_EXT)
      require(nameAndVersion != fullName) { "Library name '$fullName' must end with '$DYLIB_EXT'" }

      val components = nameAndVersion.split('.')
      val name = components.first()
      val version = components.subList(1, components.size)

      return VersionedMachOLibrary(name, version)
    }
  }

  override fun toString(): String =
      if (version.isEmpty()) "$name.dylib" else "$name.${version.joinToString(".")}.dylib"

  /** The library's name without version. */
  val baseName
    get() = "$name.dylib"
}
