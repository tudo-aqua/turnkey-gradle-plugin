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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import tools.aqua.turnkey.plugin.containsOnly

@TestInstance(PER_CLASS)
class MapMergeTest {
  @Test
  fun `test ascending merge`() {
    val result =
        listOf(mapOf("a" to 1, "b" to 2), mapOf("b" to -2, "c" to 3)).mergeWithAscendingPrecedence()

    assertThat(result).containsOnly("a" to 1, "b" to -2, "c" to 3)
  }

  @Test
  fun `test descending merge`() {
    val result =
        sequenceOf(mapOf("a" to 1, "b" to 2), mapOf("b" to -2, "c" to 3))
            .asIterable()
            .mergeWithDescendingPrecedence()

    assertThat(result).containsOnly("a" to 1, "b" to 2, "c" to 3)
  }

  @Test
  fun `test optimized descending merge`() {
    val result =
        listOf(mapOf("a" to 1, "b" to 2), mapOf("b" to -2, "c" to 3))
            .mergeWithDescendingPrecedence()

    assertThat(result).containsOnly("a" to 1, "b" to 2, "c" to 3)
  }
}
