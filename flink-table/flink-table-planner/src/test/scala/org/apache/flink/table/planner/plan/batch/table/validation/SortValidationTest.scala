/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.flink.table.planner.plan.batch.table.validation

import org.apache.flink.table.api._
import org.apache.flink.table.planner.utils.TableTestBase

import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test

class SortValidationTest extends TableTestBase {

  @Test
  def testFetchBeforeOffset(): Unit = {
    val util = batchTestUtil()
    val ds = util.addTableSource[(Int, Long, String)]("Table3", 'a, 'b, 'c)

    assertThatExceptionOfType(classOf[ValidationException])
      .isThrownBy(() => ds.orderBy('a.asc).fetch(5).offset(10))
  }

  @Test
  def testOffsetBeforeOffset(): Unit = {
    val util = batchTestUtil()
    val ds = util.addTableSource[(Int, Long, String)]("Table3", 'a, 'b, 'c)

    assertThatExceptionOfType(classOf[ValidationException])
      .isThrownBy(() => ds.orderBy('a.asc).offset(10).offset(5))
  }

  @Test
  def testNegativeFetch(): Unit = {
    val util = batchTestUtil()
    val ds = util.addTableSource[(Int, Long, String)]("Table3", 'a, 'b, 'c)

    assertThatExceptionOfType(classOf[ValidationException])
      .isThrownBy(() => ds.orderBy('a.asc).offset(-1))
  }
}
