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

package org.apache.flink.runtime.rest.messages.job;

import org.apache.flink.api.common.JobID;
import org.apache.flink.api.common.JobStatus;
import org.apache.flink.runtime.execution.ExecutionState;
import org.apache.flink.runtime.instance.SlotSharingGroupId;
import org.apache.flink.runtime.jobgraph.JobType;
import org.apache.flink.runtime.jobgraph.JobVertexID;
import org.apache.flink.runtime.rest.messages.JobPlanInfo;
import org.apache.flink.runtime.rest.messages.RestResponseMarshallingTestBase;
import org.apache.flink.runtime.rest.messages.job.metrics.IOMetricsInfo;
import org.apache.flink.testutils.junit.extensions.parameterized.NoOpTestExtension;

import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** Tests (un)marshalling of the {@link JobDetailsInfo}. */
@ExtendWith(NoOpTestExtension.class)
class JobDetailsInfoTest extends RestResponseMarshallingTestBase<JobDetailsInfo> {

    @Override
    protected Class<JobDetailsInfo> getTestResponseClass() {
        return JobDetailsInfo.class;
    }

    @Override
    protected JobDetailsInfo getTestResponseInstance() throws Exception {
        final Random random = new Random();
        final int numJobVertexDetailsInfos = 4;
        final JobPlanInfo.Plan plan = new JobPlanInfo.Plan("1234", "", "", new ArrayList<>());

        final Map<JobStatus, Long> timestamps = new HashMap<>(JobStatus.values().length);
        final Collection<JobDetailsInfo.JobVertexDetailsInfo> jobVertexInfos =
                new ArrayList<>(numJobVertexDetailsInfos);
        final Map<ExecutionState, Integer> jobVerticesPerState =
                new HashMap<>(ExecutionState.values().length);

        for (JobStatus jobStatus : JobStatus.values()) {
            timestamps.put(jobStatus, random.nextLong());
        }

        for (int i = 0; i < numJobVertexDetailsInfos; i++) {
            jobVertexInfos.add(createJobVertexDetailsInfo(random));
        }

        for (ExecutionState executionState : ExecutionState.values()) {
            jobVerticesPerState.put(executionState, random.nextInt());
        }

        return new JobDetailsInfo(
                new JobID(),
                "foobar",
                true,
                JobStatus.values()[random.nextInt(JobStatus.values().length)],
                JobType.STREAMING,
                1L,
                2L,
                1L,
                8888L,
                1984L,
                timestamps,
                jobVertexInfos,
                jobVerticesPerState,
                plan,
                null,
                0);
    }

    private JobDetailsInfo.JobVertexDetailsInfo createJobVertexDetailsInfo(Random random) {
        final Map<ExecutionState, Integer> tasksPerState =
                new HashMap<>(ExecutionState.values().length);
        final IOMetricsInfo jobVertexMetrics =
                new IOMetricsInfo(
                        random.nextLong(),
                        random.nextBoolean(),
                        random.nextLong(),
                        random.nextBoolean(),
                        random.nextLong(),
                        random.nextBoolean(),
                        random.nextLong(),
                        random.nextBoolean(),
                        Math.abs(random.nextLong()),
                        Math.abs(random.nextLong()),
                        Math.abs(random.nextDouble()));

        for (ExecutionState executionState : ExecutionState.values()) {
            tasksPerState.put(executionState, random.nextInt());
        }

        int parallelism = 1 + (random.nextInt() / 3);
        return new JobDetailsInfo.JobVertexDetailsInfo(
                new JobVertexID(),
                new SlotSharingGroupId(),
                "jobVertex" + random.nextLong(),
                2 * parallelism,
                parallelism,
                ExecutionState.values()[random.nextInt(ExecutionState.values().length)],
                random.nextLong(),
                random.nextLong(),
                random.nextLong(),
                tasksPerState,
                jobVertexMetrics);
    }
}
