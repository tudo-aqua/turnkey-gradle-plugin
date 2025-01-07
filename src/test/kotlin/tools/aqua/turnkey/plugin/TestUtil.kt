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

package tools.aqua.turnkey.plugin

import org.assertj.core.api.AbstractMapAssert
import org.assertj.core.api.Assertions.entry

fun <SELF : AbstractMapAssert<SELF, ACTUAL, K, V>, ACTUAL : Map<K, V>, K, V> AbstractMapAssert<
    SELF, ACTUAL, K, V>
    .contains(vararg entries: Pair<K, V>): SELF =
    @Suppress("SpreadOperator") contains(*entries.map { (k, v) -> entry(k, v) }.toTypedArray())

fun <SELF : AbstractMapAssert<SELF, ACTUAL, K, V>, ACTUAL : Map<K, V>, K, V> AbstractMapAssert<
    SELF, ACTUAL, K, V>
    .containsExactly(vararg entries: Pair<K, V>): SELF =
    @Suppress("SpreadOperator")
    containsExactly(*entries.map { (k, v) -> entry(k, v) }.toTypedArray())

fun <SELF : AbstractMapAssert<SELF, ACTUAL, K, V>, ACTUAL : Map<K, V>, K, V> AbstractMapAssert<
    SELF, ACTUAL, K, V>
    .containsOnly(vararg entries: Pair<K, V>): SELF =
    @Suppress("SpreadOperator") containsOnly(*entries.map { (k, v) -> entry(k, v) }.toTypedArray())
