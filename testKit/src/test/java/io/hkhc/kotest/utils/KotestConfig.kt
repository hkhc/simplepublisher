/*
 * Copyright (c) 2021. Herman Cheung
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */

package io.hkhc.kotest.utils

import io.hkhc.gradle.test.StringTreeShow
import io.hkhc.utils.tree.NoBarTheme
import io.hkhc.utils.tree.Node
import io.hkhc.utils.tree.defaulTreeTHeme
import io.kotest.assertions.show.Shows
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.spec.IsolationMode
import io.kotest.core.test.TestCaseOrder
import kotlin.time.seconds

object KotestConfig: AbstractProjectConfig() {
    override val isolationMode = IsolationMode.InstancePerLeaf
    override val testCaseOrder = TestCaseOrder.Random
    override val parallelism = 2 // use lower parallelism if the tests get mysterious error.
    override val invocationTimeout = 1200000L
}