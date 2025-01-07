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

import com.google.common.jimfs.Configuration.unix
import com.google.common.jimfs.Jimfs.newFileSystem
import java.nio.file.FileSystem
import kotlin.io.path.div
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import tools.aqua.turnkey.plugin.containsOnly

@TestInstance(PER_CLASS)
class SmartPathLookupTest {

  private companion object Fixtures {
    @BeforeAll
    @JvmStatic
    fun initializeFileSystem() {
      fs = newFileSystem(unix())
    }

    @AfterAll
    @JvmStatic
    fun destroyFileSystem() {
      fs.close()
    }

    @JvmStatic private lateinit var fs: FileSystem
    @JvmStatic
    private val root
      get() = fs.rootDirectories.single()

    @JvmStatic
    private val test
      get() = root / "test"

    @JvmStatic
    val otherA
      get() = root / "other" / "a"

    @JvmStatic
    val testA
      get() = test / "a"

    @JvmStatic
    val testAExe
      get() = test / "a.exe"

    @JvmStatic
    val testLlvmA
      get() = test / "llvm-a"

    @JvmStatic
    val testA5
      get() = test / "a-5"

    @JvmStatic
    val testA10
      get() = test / "a-10"

    @JvmStatic
    val testLlvmA10
      get() = test / "llvm-a-10"

    @JvmStatic
    val testB
      get() = test / "b"

    @JvmStatic
    val testBExe
      get() = test / "b.exe"

    @JvmStatic
    val testLlvmB
      get() = test / "llvm-b"

    @JvmStatic
    val testB7
      get() = test / "b-7"

    @JvmStatic
    val testCExe
      get() = test / "c.exe"

    @JvmStatic
    val testCFoo
      get() = test / "c.foo"

    @JvmStatic
    val testLlvmC
      get() = test / "llvm-c"

    @JvmStatic
    val testFooC
      get() = test / "foo-c"

    @JvmStatic
    val testC5
      get() = test / "c-5"

    @JvmStatic
    val testC10
      get() = test / "c-10"

    @JvmStatic
    val testCX
      get() = test / "c-X"
  }

  @Test
  fun `test discovery from single path`() {
    val paths = listOf(testA, testB)

    val lookup = paths.toLookup()

    assertThat(lookup).containsOnly("a" to testA, "b" to testB)
  }

  @Test
  fun `test FNE aliasing`() {
    val lookup =
        mutableMapOf("a.exe" to testAExe, "b" to testB, "b.exe" to testBExe, "c.foo" to testCFoo)

    val new = lookup.toImplicitFNEAliases(setOf(".exe", ".bat"))

    assertThat(new).containsOnly("a" to testAExe)
  }

  @Test
  fun `test LLVM aliasing`() {
    val lookup =
        mutableMapOf(
            "llvm-a" to testLlvmA, "b" to testB, "llvm-b" to testLlvmB, "foo-c" to testFooC)

    val new = lookup.toLLVMAliases()

    assertThat(new).containsOnly("a" to testLlvmA)
  }

  @Test
  fun `test version aliasing`() {
    val lookup =
        mutableMapOf(
            "a-5" to testA5, "a-10" to testA10, "b" to testB, "b-7" to testB7, "c-x" to testCX)

    val new = lookup.toVersionAliases()

    assertThat(new).containsOnly("a" to testA10)
  }

  @Test
  fun `test path merging`() {
    val lookup = computeSmartPathLookup(listOf(listOf(testA, testB), listOf(otherA)), emptySet())

    assertThat(lookup).containsOnly("a" to testA, "b" to testB)
  }

  @Test
  fun `test FNE merging`() {
    val lookup =
        computeSmartPathLookup(
            listOf(listOf(testA, testAExe, testB, testCExe)), setOf(".exe", ".bat"))

    assertThat(lookup)
        .containsOnly(
            "a" to testA, "a.exe" to testAExe, "b" to testB, "c" to testCExe, "c.exe" to testCExe)
  }

  @Test
  fun `test LLVM merging`() {
    val lookup =
        computeSmartPathLookup(listOf(listOf(testA, testLlvmA, testB, testLlvmC)), emptySet())

    assertThat(lookup)
        .containsOnly(
            "a" to testA,
            "llvm-a" to testLlvmA,
            "b" to testB,
            "c" to testLlvmC,
            "llvm-c" to testLlvmC)
  }

  @Test
  fun `test version merging`() {
    val lookup =
        computeSmartPathLookup(listOf(listOf(testA, testA5, testB, testC5, testC10)), emptySet())

    assertThat(lookup)
        .containsOnly(
            "a" to testA,
            "a-5" to testA5,
            "b" to testB,
            "c" to testC10,
            "c-5" to testC5,
            "c-10" to testC10)
  }

  @Test
  fun `test LLVM version merging`() {
    val lookup = computeSmartPathLookup(listOf(listOf(testA5, testLlvmA10)), emptySet())

    assertThat(lookup)
        .containsOnly(
            "a" to testA5,
            "a-5" to testA5,
            "llvm-a" to testLlvmA10,
            "a-10" to testLlvmA10,
            "llvm-a-10" to testLlvmA10)
  }
}
