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

package tools.aqua.turnkey.plugin.analysis

import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.assertThrows

@TestInstance(PER_CLASS)
class DependencyGraphTest {

  private companion object Fixtures {
    const val ROOT_SO = "root.so"
    const val ROOT_1_SO = "root1.so"
    const val ROOT_2_SO = "root2.so"

    const val DEPENDENT_SO = "dependent.so"
    const val DEPENDENT_1_SO = "dependent1.so"
    const val DEPENDENT_2_SO = "dependent2.so"
    const val DEPENDENT_3_SO = "dependent3.so"

    const val SYSTEM_SO = "system.so"

    const val A_SO = "a.so"
    const val B_SO = "b.so"
    const val C_SO = "c.so"

    private data class SimpleAnalyzableNativeLibrary(
        override val linkageName: String,
        override val libraryDependencies: Set<SimpleSpeculativeNativeLibrary> = emptySet(),
    ) : AnalyzableNativeLibrary<String, SimpleSpeculativeNativeLibrary> {
      override val fuzzyName: String = linkageName

      override val fuzzyMismatch: Int
        get() = 0

      override val path: Path
        get() = error("unsupported in test")
    }

    private data class SimpleSpeculativeNativeLibrary(
        override val linkageName: String,
    ) : SpeculativeNamedLibrary<String> {
      override val fuzzyName: String = linkageName
    }

    private fun String.analyzable(vararg dependencies: SimpleSpeculativeNativeLibrary) =
        SimpleAnalyzableNativeLibrary(this, dependencies.toSet())

    private fun String.dependency() = SimpleSpeculativeNativeLibrary(this)

    private fun SimpleAnalyzableNativeLibrary.dependency() =
        SimpleSpeculativeNativeLibrary(linkageName)
  }

  @Test
  fun `test dependency analysis works`() {
    val system = SYSTEM_SO.dependency()
    val dependent = DEPENDENT_SO.analyzable()

    val root = ROOT_SO.analyzable(dependent.dependency(), system)

    val dependencyGraph = DependencyGraph.of(root, dependent)

    assertThat(dependencyGraph.linkageRoots).containsOnly(root)
    assertThat(dependencyGraph.localLibrariesInLoadOrder).containsExactly(root, dependent)
    assertThat(dependencyGraph.systemLibraries).containsOnly(system)
  }

  @Test
  fun `test dependency analysis handles merging paths`() {
    val dependent1 = DEPENDENT_1_SO.analyzable()
    val dependent2 = DEPENDENT_2_SO.analyzable(dependent1.dependency())
    val dependent3 = DEPENDENT_3_SO.analyzable(dependent1.dependency())
    val root = ROOT_SO.analyzable(dependent2.dependency(), dependent3.dependency())

    val dependencyGraph = DependencyGraph.of(root, dependent1, dependent2, dependent3)

    assertThat(dependencyGraph.linkageRoots).containsOnly(root)
    assertThat(dependencyGraph.localLibrariesInLoadOrder)
        .containsExactlyInAnyOrder(root, dependent1, dependent2, dependent3)
        .containsSubsequence(root, dependent2, dependent1)
        .containsSubsequence(root, dependent3, dependent1)
    assertThat(dependencyGraph.systemLibraries).isEmpty()
  }

  @Test
  fun `test dependency analysis handles multiple roots`() {
    val dependent1 = DEPENDENT_1_SO.analyzable()
    val dependent2 = DEPENDENT_2_SO.analyzable()
    val dependent3 = DEPENDENT_3_SO.analyzable()
    val root1 = ROOT_1_SO.analyzable(dependent1.dependency(), dependent2.dependency())
    val root2 = ROOT_2_SO.analyzable(dependent2.dependency(), dependent3.dependency())

    val dependencyGraph = DependencyGraph.of(root1, root2, dependent1, dependent2, dependent3)

    assertThat(dependencyGraph.linkageRoots).containsOnly(root1, root2)
    assertThat(dependencyGraph.localLibrariesInLoadOrder)
        .containsOnly(root1, root2, dependent1, dependent2, dependent3)
    assertThat(dependencyGraph.systemLibraries).isEmpty()

    val root1Graph = dependencyGraph.subgraphFrom(root1)
    assertThat(root1Graph.localLibrariesInLoadOrder).containsOnly(root1, dependent1, dependent2)
    assertThat(root1Graph.systemLibraries).isEmpty()

    val root2Graph = dependencyGraph.subgraphFrom(root2)
    assertThat(root2Graph.localLibrariesInLoadOrder).containsOnly(root2, dependent2, dependent3)
    assertThat(root2Graph.systemLibraries).isEmpty()
  }

  @Test
  fun `test loop detection`() {
    val a = A_SO.analyzable(B_SO.dependency())
    val b = A_SO.analyzable(C_SO.dependency())
    val c = A_SO.analyzable(A_SO.dependency())

    assertThrows<IllegalArgumentException> { DependencyGraph.of(a, b, c) }
  }
}
