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
package org.apache.flink.table.planner.plan.optimize.program

import org.apache.flink.configuration.ReadableConfig
import org.apache.flink.table.api.config.OptimizerConfigOptions
import org.apache.flink.table.planner.plan.nodes.FlinkConventions
import org.apache.flink.table.planner.plan.rules.FlinkStreamRuleSets
import org.apache.flink.table.planner.plan.rules.logical.EventTimeTemporalJoinRewriteRule
import org.apache.flink.table.planner.plan.rules.physical.stream.{FlinkDuplicateChangesTraitInitProgram, FlinkMarkChangelogNormalizeProgram}

import org.apache.calcite.plan.hep.HepMatchOrder

/** Defines a sequence of programs to optimize for stream table plan. */
object FlinkStreamProgram {

  val SUBQUERY_REWRITE = "subquery_rewrite"
  val TEMPORAL_JOIN_REWRITE = "temporal_join_rewrite"
  val DECORRELATE = "decorrelate"
  val DEFAULT_REWRITE = "default_rewrite"
  val PREDICATE_PUSHDOWN = "predicate_pushdown"
  val JOIN_REORDER = "join_reorder"
  val MULTI_JOIN = "multi_join"
  val PROJECT_REWRITE = "project_rewrite"
  val LOGICAL = "logical"
  val LOGICAL_REWRITE = "logical_rewrite"
  val TIME_INDICATOR = "time_indicator"
  val PHYSICAL = "physical"
  val PHYSICAL_REWRITE = "physical_rewrite"

  def buildProgram(tableConfig: ReadableConfig): FlinkChainedProgram[StreamOptimizeContext] = {
    val chainedProgram = new FlinkChainedProgram[StreamOptimizeContext]()

    // rewrite sub-queries to joins
    chainedProgram.addLast(
      SUBQUERY_REWRITE,
      FlinkGroupProgramBuilder
        .newBuilder[StreamOptimizeContext]
        // rewrite QueryOperationCatalogViewTable before rewriting sub-queries
        .addProgram(
          FlinkHepRuleSetProgramBuilder.newBuilder
            .setHepRulesExecutionType(HEP_RULES_EXECUTION_TYPE.RULE_SEQUENCE)
            .setHepMatchOrder(HepMatchOrder.BOTTOM_UP)
            .add(FlinkStreamRuleSets.TABLE_REF_RULES)
            .build(),
          "convert table references before rewriting sub-queries to semi-join"
        )
        .addProgram(
          FlinkHepRuleSetProgramBuilder.newBuilder
            .setHepRulesExecutionType(HEP_RULES_EXECUTION_TYPE.RULE_SEQUENCE)
            .setHepMatchOrder(HepMatchOrder.BOTTOM_UP)
            .add(FlinkStreamRuleSets.SEMI_JOIN_RULES)
            .build(),
          "rewrite sub-queries to semi-join"
        )
        .addProgram(
          FlinkHepRuleSetProgramBuilder.newBuilder
            .setHepRulesExecutionType(HEP_RULES_EXECUTION_TYPE.RULE_COLLECTION)
            .setHepMatchOrder(HepMatchOrder.BOTTOM_UP)
            .add(FlinkStreamRuleSets.TABLE_SUBQUERY_RULES)
            .build(),
          "sub-queries remove"
        )
        // convert RelOptTableImpl (which exists in SubQuery before) to FlinkRelOptTable
        .addProgram(
          FlinkHepRuleSetProgramBuilder.newBuilder
            .setHepRulesExecutionType(HEP_RULES_EXECUTION_TYPE.RULE_SEQUENCE)
            .setHepMatchOrder(HepMatchOrder.BOTTOM_UP)
            .add(FlinkStreamRuleSets.TABLE_REF_RULES)
            .build(),
          "convert table references after sub-queries removed"
        )
        .build()
    )

    // rewrite special temporal join plan
    chainedProgram.addLast(
      TEMPORAL_JOIN_REWRITE,
      FlinkGroupProgramBuilder
        .newBuilder[StreamOptimizeContext]
        .addProgram(
          FlinkHepRuleSetProgramBuilder.newBuilder
            .setHepRulesExecutionType(HEP_RULES_EXECUTION_TYPE.RULE_SEQUENCE)
            .setHepMatchOrder(HepMatchOrder.BOTTOM_UP)
            .add(FlinkStreamRuleSets.EXPAND_PLAN_RULES)
            .build(),
          "convert correlate to temporal table join"
        )
        .addProgram(
          FlinkHepRuleSetProgramBuilder.newBuilder
            .setHepRulesExecutionType(HEP_RULES_EXECUTION_TYPE.RULE_SEQUENCE)
            .setHepMatchOrder(HepMatchOrder.BOTTOM_UP)
            .add(FlinkStreamRuleSets.POST_EXPAND_CLEAN_UP_RULES)
            .build(),
          "convert enumerable table scan"
        )
        .build()
    )

    // query decorrelation
    chainedProgram.addLast(
      DECORRELATE,
      FlinkGroupProgramBuilder
        .newBuilder[StreamOptimizeContext]
        // rewrite before decorrelation
        .addProgram(
          FlinkHepRuleSetProgramBuilder.newBuilder
            .setHepRulesExecutionType(HEP_RULES_EXECUTION_TYPE.RULE_SEQUENCE)
            .setHepMatchOrder(HepMatchOrder.BOTTOM_UP)
            .add(FlinkStreamRuleSets.PRE_DECORRELATION_RULES)
            .build(),
          "pre-rewrite before decorrelation"
        )
        .addProgram(new FlinkDecorrelateProgram)
        .build()
    )

    // default rewrite, includes: predicate simplification, expression reduction, window
    // properties rewrite, etc.
    chainedProgram.addLast(
      DEFAULT_REWRITE,
      FlinkHepRuleSetProgramBuilder.newBuilder
        .setHepRulesExecutionType(HEP_RULES_EXECUTION_TYPE.RULE_SEQUENCE)
        .setHepMatchOrder(HepMatchOrder.BOTTOM_UP)
        .add(FlinkStreamRuleSets.DEFAULT_REWRITE_RULES)
        .build()
    )

    // rule based optimization: push down predicate(s) in where clause, so it only needs to read
    // the required data
    chainedProgram.addLast(
      PREDICATE_PUSHDOWN,
      FlinkGroupProgramBuilder
        .newBuilder[StreamOptimizeContext]
        .addProgram(
          FlinkGroupProgramBuilder
            .newBuilder[StreamOptimizeContext]
            .addProgram(
              FlinkHepRuleSetProgramBuilder
                .newBuilder[StreamOptimizeContext]
                .setHepRulesExecutionType(HEP_RULES_EXECUTION_TYPE.RULE_SEQUENCE)
                .setHepMatchOrder(HepMatchOrder.BOTTOM_UP)
                .add(FlinkStreamRuleSets.JOIN_PREDICATE_REWRITE_RULES)
                .build(),
              "join predicate rewrite"
            )
            .addProgram(
              FlinkHepRuleSetProgramBuilder.newBuilder
                .setHepRulesExecutionType(HEP_RULES_EXECUTION_TYPE.RULE_COLLECTION)
                .setHepMatchOrder(HepMatchOrder.BOTTOM_UP)
                .add(FlinkStreamRuleSets.FILTER_PREPARE_RULES)
                .build(),
              "filter rules"
            )
            .setIterations(5)
            .build(),
          "predicate rewrite"
        )
        .addProgram(
          // PUSH_PARTITION_DOWN_RULES should always be in front of PUSH_FILTER_DOWN_RULES
          // to prevent PUSH_FILTER_DOWN_RULES from consuming the predicates in partitions
          FlinkGroupProgramBuilder
            .newBuilder[StreamOptimizeContext]
            .addProgram(
              FlinkHepRuleSetProgramBuilder.newBuilder
                .setHepRulesExecutionType(HEP_RULES_EXECUTION_TYPE.RULE_SEQUENCE)
                .setHepMatchOrder(HepMatchOrder.BOTTOM_UP)
                .add(FlinkStreamRuleSets.PUSH_PARTITION_DOWN_RULES)
                .build(),
              "push down partitions into table scan"
            )
            .addProgram(
              FlinkHepRuleSetProgramBuilder.newBuilder
                .setHepRulesExecutionType(HEP_RULES_EXECUTION_TYPE.RULE_SEQUENCE)
                .setHepMatchOrder(HepMatchOrder.BOTTOM_UP)
                .add(FlinkStreamRuleSets.PUSH_FILTER_DOWN_RULES)
                .build(),
              "push down filters into table scan"
            )
            .build(),
          "push predicate into table scan"
        )
        .addProgram(
          FlinkHepRuleSetProgramBuilder.newBuilder
            .setHepRulesExecutionType(HEP_RULES_EXECUTION_TYPE.RULE_SEQUENCE)
            .setHepMatchOrder(HepMatchOrder.BOTTOM_UP)
            .add(FlinkStreamRuleSets.PRUNE_EMPTY_RULES)
            .build(),
          "prune empty after predicate push down"
        )
        .build()
    )

    // join reorder
    if (tableConfig.get(OptimizerConfigOptions.TABLE_OPTIMIZER_JOIN_REORDER_ENABLED)) {
      chainedProgram.addLast(
        JOIN_REORDER,
        FlinkGroupProgramBuilder
          .newBuilder[StreamOptimizeContext]
          .addProgram(
            FlinkHepRuleSetProgramBuilder.newBuilder
              .setHepRulesExecutionType(HEP_RULES_EXECUTION_TYPE.RULE_COLLECTION)
              .setHepMatchOrder(HepMatchOrder.BOTTOM_UP)
              .add(FlinkStreamRuleSets.JOIN_REORDER_PREPARE_RULES)
              .build(),
            "merge join into MultiJoin"
          )
          .addProgram(
            FlinkHepRuleSetProgramBuilder.newBuilder
              .setHepRulesExecutionType(HEP_RULES_EXECUTION_TYPE.RULE_SEQUENCE)
              .setHepMatchOrder(HepMatchOrder.BOTTOM_UP)
              .add(FlinkStreamRuleSets.JOIN_REORDER_RULES)
              .build(),
            "do join reorder"
          )
          .build()
      )
    }

    // multi-join
    if (tableConfig.get(OptimizerConfigOptions.TABLE_OPTIMIZER_MULTI_JOIN_ENABLED)) {
      chainedProgram.addLast(
        MULTI_JOIN,
        FlinkGroupProgramBuilder
          .newBuilder[StreamOptimizeContext]
          .addProgram(
            FlinkHepRuleSetProgramBuilder.newBuilder
              .setHepRulesExecutionType(HEP_RULES_EXECUTION_TYPE.RULE_COLLECTION)
              .setHepMatchOrder(HepMatchOrder.BOTTOM_UP)
              .add(FlinkStreamRuleSets.MULTI_JOIN_RULES)
              .build(),
            "merge binary regular joins into MultiJoin"
          )
          .build()
      )
    }

    // project rewrite
    chainedProgram.addLast(
      PROJECT_REWRITE,
      FlinkHepRuleSetProgramBuilder.newBuilder
        .setHepRulesExecutionType(HEP_RULES_EXECUTION_TYPE.RULE_COLLECTION)
        .setHepMatchOrder(HepMatchOrder.BOTTOM_UP)
        .add(FlinkStreamRuleSets.PROJECT_RULES)
        .build()
    )

    // optimize the logical plan
    chainedProgram.addLast(
      LOGICAL,
      FlinkVolcanoProgramBuilder.newBuilder
        .add(FlinkStreamRuleSets.LOGICAL_OPT_RULES)
        .setRequiredOutputTraits(Array(FlinkConventions.LOGICAL))
        .build()
    )

    // logical rewrite
    chainedProgram.addLast(
      LOGICAL_REWRITE,
      FlinkGroupProgramBuilder
        .newBuilder[StreamOptimizeContext]
        .addProgram(
          FlinkHepRuleSetProgramBuilder.newBuilder
            .setHepRulesExecutionType(HEP_RULES_EXECUTION_TYPE.RULE_SEQUENCE)
            .setHepMatchOrder(HepMatchOrder.BOTTOM_UP)
            .add(FlinkStreamRuleSets.LOGICAL_REWRITE)
            .build())
        .addProgram(
          FlinkHepRuleSetProgramBuilder.newBuilder
            .setHepRulesExecutionType(HEP_RULES_EXECUTION_TYPE.RULE_SEQUENCE)
            .setHepMatchOrder(HepMatchOrder.BOTTOM_UP)
            .add(EventTimeTemporalJoinRewriteRule.EVENT_TIME_TEMPORAL_JOIN_REWRITE_RULES)
            .build())
        .build()
    )

    // convert time indicators
    chainedProgram.addLast(TIME_INDICATOR, new FlinkRelTimeIndicatorProgram)

    // optimize the physical plan
    chainedProgram.addLast(
      PHYSICAL,
      FlinkVolcanoProgramBuilder.newBuilder
        .add(FlinkStreamRuleSets.PHYSICAL_OPT_RULES)
        .setRequiredOutputTraits(Array(FlinkConventions.STREAM_PHYSICAL))
        .build()
    )

    // physical rewrite
    chainedProgram.addLast(
      PHYSICAL_REWRITE,
      FlinkGroupProgramBuilder
        .newBuilder[StreamOptimizeContext]
        .addProgram(
          new FlinkMarkChangelogNormalizeProgram,
          "mark changelog normalize reusing same source")
        // add a HEP program for watermark transpose rules to make this optimization deterministic
        // Applying these rules before the changelog mode inference is important because
        // 1. if we transpose calc and projection before we infer the changelog mode, we
        //    can, e.g., project out metadata columns which may potentially allow us to drop the
        //    changelog normalize node
        // 2. if we push a condition into a changelog normalize we may emit only UPDATE_AFTER, if
        //    the downstream operators don't need UPDATE_BEFORE, without the push we need to emit
        //    UPDATE_BEFORE. With the condition evaluated inside of changelog normalize we can
        //    emit DELETEs instead of UPDATE_BEFORE if the condition is not met.
        .addProgram(
          FlinkHepRuleSetProgramBuilder.newBuilder
            .setHepRulesExecutionType(HEP_RULES_EXECUTION_TYPE.RULE_COLLECTION)
            .setHepMatchOrder(HepMatchOrder.BOTTOM_UP)
            .add(FlinkStreamRuleSets.CHANGELOG_NORMALIZE_TRANSPOSE_RULES)
            .build(),
          "watermark transpose"
        )
        .addProgram(new FlinkChangelogModeInferenceProgram, "Changelog mode inference")
        .addProgram(
          new FlinkMiniBatchIntervalTraitInitProgram,
          "Initialization for mini-batch interval inference")
        .addProgram(
          FlinkHepRuleSetProgramBuilder.newBuilder
            .setHepRulesExecutionType(HEP_RULES_EXECUTION_TYPE.RULE_SEQUENCE)
            .setHepMatchOrder(HepMatchOrder.TOP_DOWN)
            .add(FlinkStreamRuleSets.MINI_BATCH_RULES)
            .build(),
          "mini-batch interval rules"
        )
        .addProgram(
          new FlinkDuplicateChangesTraitInitProgram,
          "initialization for duplicate changes inference")
        .addProgram(
          FlinkHepRuleSetProgramBuilder.newBuilder
            .setHepRulesExecutionType(HEP_RULES_EXECUTION_TYPE.RULE_SEQUENCE)
            .setHepMatchOrder(HepMatchOrder.TOP_DOWN)
            .add(FlinkStreamRuleSets.DUPLICATE_CHANGES_RULES)
            .build(),
          "duplicate changes rules"
        )
        .addProgram(
          FlinkHepRuleSetProgramBuilder.newBuilder
            .setHepRulesExecutionType(HEP_RULES_EXECUTION_TYPE.RULE_COLLECTION)
            .setHepMatchOrder(HepMatchOrder.BOTTOM_UP)
            .add(FlinkStreamRuleSets.PHYSICAL_REWRITE)
            .build(),
          "physical rewrite"
        )
        .build()
    )

    chainedProgram
  }
}
