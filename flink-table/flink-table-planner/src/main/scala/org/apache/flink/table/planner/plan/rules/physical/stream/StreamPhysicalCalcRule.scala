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
package org.apache.flink.table.planner.plan.rules.physical.stream

import org.apache.flink.table.planner.plan.nodes.FlinkConventions
import org.apache.flink.table.planner.plan.nodes.logical.FlinkLogicalCalc
import org.apache.flink.table.planner.plan.nodes.physical.stream.StreamPhysicalCalc
import org.apache.flink.table.planner.plan.utils.AsyncUtil.containsAsyncCall
import org.apache.flink.table.planner.plan.utils.PythonUtil.containsPythonCall

import org.apache.calcite.plan.{RelOptRule, RelOptRuleCall, RelTraitSet}
import org.apache.calcite.rel.RelNode
import org.apache.calcite.rel.convert.ConverterRule
import org.apache.calcite.rel.convert.ConverterRule.Config

import scala.collection.JavaConverters._

/** Rule that converts [[FlinkLogicalCalc]] to [[StreamPhysicalCalc]]. */
class StreamPhysicalCalcRule(config: Config) extends ConverterRule(config) {

  override def matches(call: RelOptRuleCall): Boolean = {
    val calc: FlinkLogicalCalc = call.rel(0)
    val program = calc.getProgram
    !program.getExprList.asScala.exists(containsPythonCall(_)) &&
    !program.getExprList.asScala.exists(containsAsyncCall)
  }

  def convert(rel: RelNode): RelNode = {
    val calc: FlinkLogicalCalc = rel.asInstanceOf[FlinkLogicalCalc]
    val traitSet: RelTraitSet = rel.getTraitSet.replace(FlinkConventions.STREAM_PHYSICAL)
    val newInput = RelOptRule.convert(calc.getInput, FlinkConventions.STREAM_PHYSICAL)

    new StreamPhysicalCalc(rel.getCluster, traitSet, newInput, calc.getProgram, rel.getRowType)
  }
}

object StreamPhysicalCalcRule {
  val INSTANCE: RelOptRule = new StreamPhysicalCalcRule(
    Config.INSTANCE.withConversion(
      classOf[FlinkLogicalCalc],
      FlinkConventions.LOGICAL,
      FlinkConventions.STREAM_PHYSICAL,
      "StreamPhysicalCalcRule"))
}
