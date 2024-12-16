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

/** Merge all maps in this sequence. In case of duplicate entries, prefer values from later maps. */
internal fun <K, V> Iterable<Map<K, V>>.mergeWithAscendingPrecedence(): Map<K, V> = buildMap {
  this@mergeWithAscendingPrecedence.forEach { this += it }
}

/** Merge all maps in this list. In case of duplicate entries, prefer values from earlier maps. */
internal fun <K, V> List<Map<K, V>>.mergeWithDescendingPrecedence(): Map<K, V> =
    asReversed().mergeWithAscendingPrecedence()

/**
 * Merge all maps in this sequence. In case of duplicate entries, prefer values from earlier maps.
 */
internal fun <K, V> Iterable<Map<K, V>>.mergeWithDescendingPrecedence(): Map<K, V> =
    reversed().mergeWithAscendingPrecedence()