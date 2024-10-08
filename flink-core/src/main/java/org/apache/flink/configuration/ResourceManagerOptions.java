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

package org.apache.flink.configuration;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.annotation.docs.Documentation;
import org.apache.flink.configuration.description.Description;
import org.apache.flink.configuration.description.TextElement;

import java.time.Duration;

/** The set of configuration options relating to the ResourceManager. */
@PublicEvolving
public class ResourceManagerOptions {

    private static final String START_WORKER_RETRY_INTERVAL_KEY =
            "resourcemanager.start-worker.retry-interval";

    /** Timeout for jobs which don't have a job manager as leader assigned. */
    public static final ConfigOption<Duration> JOB_TIMEOUT =
            ConfigOptions.key("resourcemanager.job.timeout")
                    .durationType()
                    .defaultValue(Duration.ofMinutes(5))
                    .withDescription(
                            "Timeout for jobs which don't have a job manager as leader assigned.");

    /**
     * Defines the network port to connect to for communication with the resource manager. By
     * default, the port of the JobManager, because the same ActorSystem is used. Its not possible
     * to use this configuration key to define port ranges.
     */
    public static final ConfigOption<Integer> IPC_PORT =
            ConfigOptions.key("resourcemanager.rpc.port")
                    .intType()
                    .defaultValue(0)
                    .withDescription(
                            "Defines the network port to connect to for communication with the resource manager. By"
                                    + " default, the port of the JobManager, because the same ActorSystem is used."
                                    + " Its not possible to use this configuration key to define port ranges.");

    @Documentation.Section(Documentation.Sections.EXPERT_SCHEDULING)
    public static final ConfigOption<Integer> MIN_SLOT_NUM =
            ConfigOptions.key("slotmanager.number-of-slots.min")
                    .intType()
                    .defaultValue(0)
                    .withDescription(
                            "Defines the minimum number of slots that the Flink cluster allocates. This configuration option "
                                    + "is meant for cluster to initialize certain workers in best efforts when starting. This can "
                                    + "be used to speed up a job startup process. Note that this configuration option does not take "
                                    + "effect for standalone clusters, where how many slots are allocated is not controlled by Flink.");

    @Documentation.Section(Documentation.Sections.EXPERT_SCHEDULING)
    @Documentation.OverrideDefault("infinite")
    public static final ConfigOption<Integer> MAX_SLOT_NUM =
            ConfigOptions.key("slotmanager.number-of-slots.max")
                    .intType()
                    .defaultValue(Integer.MAX_VALUE)
                    .withDescription(
                            "Defines the maximum number of slots that the Flink cluster allocates. This configuration option "
                                    + "is meant for limiting the resource consumption for batch workloads. It is not recommended to configure this option "
                                    + "for streaming workloads, which may fail if there are not enough slots. Note that this configuration option does not take "
                                    + "effect for standalone clusters, where how many slots are allocated is not controlled by Flink.");

    @Documentation.Section(Documentation.Sections.EXPERT_SCHEDULING)
    public static final ConfigOption<Double> MIN_TOTAL_CPU =
            ConfigOptions.key("slotmanager.min-total-resource.cpu")
                    .doubleType()
                    .noDefaultValue()
                    .withDescription(
                            "Minimum cpu cores the Flink cluster allocates for slots. Resources "
                                    + "for JobManager and TaskManager framework are excluded. If "
                                    + "not configured, it will be derived from '"
                                    + MIN_SLOT_NUM.key()
                                    + "'.");

    @Documentation.Section(Documentation.Sections.EXPERT_SCHEDULING)
    public static final ConfigOption<Double> MAX_TOTAL_CPU =
            ConfigOptions.key("slotmanager.max-total-resource.cpu")
                    .doubleType()
                    .noDefaultValue()
                    .withDescription(
                            "Maximum cpu cores the Flink cluster allocates for slots. Resources "
                                    + "for JobManager and TaskManager framework are excluded. If "
                                    + "not configured, it will be derived from '"
                                    + MAX_SLOT_NUM.key()
                                    + "'.");

    @Documentation.Section(Documentation.Sections.EXPERT_SCHEDULING)
    public static final ConfigOption<MemorySize> MIN_TOTAL_MEM =
            ConfigOptions.key("slotmanager.min-total-resource.memory")
                    .memoryType()
                    .noDefaultValue()
                    .withDescription(
                            "Minimum memory size the Flink cluster allocates for slots. Resources "
                                    + "for JobManager and TaskManager framework are excluded. If "
                                    + "not configured, it will be derived from '"
                                    + MIN_SLOT_NUM.key()
                                    + "'.");

    @Documentation.Section(Documentation.Sections.EXPERT_SCHEDULING)
    public static final ConfigOption<MemorySize> MAX_TOTAL_MEM =
            ConfigOptions.key("slotmanager.max-total-resource.memory")
                    .memoryType()
                    .noDefaultValue()
                    .withDescription(
                            "Maximum memory size the Flink cluster allocates for slots. Resources "
                                    + "for JobManager and TaskManager framework are excluded. If "
                                    + "not configured, it will be derived from '"
                                    + MAX_SLOT_NUM.key()
                                    + "'.");

    /**
     * The number of redundant task managers. Redundant task managers are extra task managers
     * started by Flink, in order to speed up job recovery in case of failures due to task manager
     * lost. Note that this feature is available only to the active deployments (native K8s, Yarn).
     * For fine-grained resource requirement, Redundant resources will be reserved, but it is
     * possible that we have many small pieces of free resources form multiple TMs, which added up
     * larger than the desired redundant resources, but each piece is too small to match the
     * resource requirement of tasks from the failed worker.
     */
    public static final ConfigOption<Integer> REDUNDANT_TASK_MANAGER_NUM =
            ConfigOptions.key("slotmanager.redundant-taskmanager-num")
                    .intType()
                    .defaultValue(0)
                    .withDescription(
                            "The number of redundant task managers. Redundant task managers are extra task managers "
                                    + "started by Flink, in order to speed up job recovery in case of failures due to task manager lost. "
                                    + "Note that this feature is available only to the active deployments (native K8s, Yarn)."
                                    + "For fine-grained resource requirement, Redundant resources will be reserved, but it is possible that "
                                    + "we have many small pieces of free resources form multiple TMs, which added up larger than the desired "
                                    + "redundant resources, but each piece is too small to match the resource requirement of tasks from the failed worker.");

    /**
     * The maximum number of start worker failures (Native Kubernetes / Yarn) per minute before
     * pausing requesting new workers. Once the threshold is reached, subsequent worker requests
     * will be postponed to after a configured retry interval ({@link
     * #START_WORKER_RETRY_INTERVAL}).
     */
    public static final ConfigOption<Double> START_WORKER_MAX_FAILURE_RATE =
            ConfigOptions.key("resourcemanager.start-worker.max-failure-rate")
                    .doubleType()
                    .defaultValue(10.0)
                    .withDescription(
                            "The maximum number of start worker failures (Native Kubernetes / Yarn) per minute "
                                    + "before pausing requesting new workers. Once the threshold is reached, subsequent "
                                    + "worker requests will be postponed to after a configured retry interval ('"
                                    + START_WORKER_RETRY_INTERVAL_KEY
                                    + "').");

    /**
     * The time to wait before requesting new workers (Native Kubernetes / Yarn) once the max
     * failure rate of starting workers ({@link #START_WORKER_MAX_FAILURE_RATE}) is reached.
     */
    public static final ConfigOption<Duration> START_WORKER_RETRY_INTERVAL =
            ConfigOptions.key(START_WORKER_RETRY_INTERVAL_KEY)
                    .durationType()
                    .defaultValue(Duration.ofSeconds(3))
                    .withDescription(
                            "The time to wait before requesting new workers (Native Kubernetes / Yarn) once the "
                                    + "max failure rate of starting workers ('"
                                    + START_WORKER_MAX_FAILURE_RATE.key()
                                    + "') is reached.");

    @Documentation.ExcludeFromDocumentation(
            "This is an expert option, that we do not want to expose in the documentation")
    public static final ConfigOption<Duration> REQUIREMENTS_CHECK_DELAY =
            ConfigOptions.key("slotmanager.requirement-check.delay")
                    .durationType()
                    .defaultValue(Duration.ofMillis(50))
                    .withDescription("The delay of the resource requirements check.");

    @Documentation.ExcludeFromDocumentation(
            "This is an expert option, that we do not want to expose in the documentation")
    public static final ConfigOption<Duration> DECLARE_NEEDED_RESOURCE_DELAY =
            ConfigOptions.key("slotmanager.declare-needed-resource.delay")
                    .durationType()
                    .defaultValue(Duration.ofMillis(50))
                    .withDescription("The delay of the declare needed resources.");

    /**
     * Time in milliseconds of the start-up period of a standalone cluster. During this time,
     * resource manager of the standalone cluster expects new task executors to be registered, and
     * will not fail slot requests that can not be satisfied by any current registered slots. After
     * this time, it will fail pending and new coming requests immediately that can not be satisfied
     * by registered slots. If not set, {@link JobManagerOptions#SLOT_REQUEST_TIMEOUT} will be used
     * by default.
     */
    public static final ConfigOption<Duration> STANDALONE_CLUSTER_STARTUP_PERIOD_TIME =
            ConfigOptions.key("resourcemanager.standalone.start-up-time")
                    .durationType()
                    .noDefaultValue()
                    .withDescription(
                            Description.builder()
                                    .text(
                                            "Time of the start-up period of a standalone cluster. During this time, "
                                                    + "resource manager of the standalone cluster expects new task executors to be registered, and will not "
                                                    + "fail slot requests that can not be satisfied by any current registered slots. After this time, it will "
                                                    + "fail pending and new coming requests immediately that can not be satisfied by registered slots. If not "
                                                    + "set, %s will be used by default.",
                                            TextElement.code(
                                                    JobManagerOptions.SLOT_REQUEST_TIMEOUT.key()))
                                    .build());

    /** The timeout for an idle task manager to be released. */
    public static final ConfigOption<Duration> TASK_MANAGER_TIMEOUT =
            ConfigOptions.key("resourcemanager.taskmanager-timeout")
                    .durationType()
                    .defaultValue(Duration.ofMillis(30000L))
                    .withDeprecatedKeys("slotmanager.taskmanager-timeout")
                    .withDescription(
                            Description.builder()
                                    .text("The timeout for an idle task manager to be released.")
                                    .build());

    /**
     * Prefix for passing custom environment variables to Flink's master process. For example for
     * passing LD_LIBRARY_PATH as an env variable to the AppMaster, set:
     * containerized.master.env.LD_LIBRARY_PATH: "/usr/lib/native" in the config.yaml.
     */
    public static final String CONTAINERIZED_MASTER_ENV_PREFIX = "containerized.master.env.";

    /**
     * Similar to the {@see CONTAINERIZED_MASTER_ENV_PREFIX}, this configuration prefix allows
     * setting custom environment variables for the workers (TaskManagers).
     */
    public static final String CONTAINERIZED_TASK_MANAGER_ENV_PREFIX =
            "containerized.taskmanager.env.";

    /** Timeout for TaskManagers to register at the active resource managers. */
    public static final ConfigOption<Duration> TASK_MANAGER_REGISTRATION_TIMEOUT =
            ConfigOptions.key("resourcemanager.taskmanager-registration.timeout")
                    .durationType()
                    .defaultValue(TaskManagerOptions.REGISTRATION_TIMEOUT.defaultValue())
                    .withFallbackKeys(TaskManagerOptions.REGISTRATION_TIMEOUT.key())
                    .withDescription(
                            "Timeout for TaskManagers to register at the active resource managers. "
                                    + "If exceeded, active resource manager will release and try to "
                                    + "re-request the resource for the worker. If not configured, "
                                    + "fallback to '"
                                    + TaskManagerOptions.REGISTRATION_TIMEOUT.key()
                                    + "'.");

    /** Timeout for ResourceManager to recover all the previous attempts workers. */
    public static final ConfigOption<Duration> RESOURCE_MANAGER_PREVIOUS_WORKER_RECOVERY_TIMEOUT =
            ConfigOptions.key("resourcemanager.previous-worker.recovery.timeout")
                    .durationType()
                    .defaultValue(Duration.ofSeconds(0))
                    .withDescription(
                            "Timeout for resource manager to recover all the previous attempts workers. If exceeded,"
                                    + " resource manager will handle new resource requests by requesting new workers."
                                    + " If you would like to reuse the previous workers as much as possible, you should"
                                    + " configure a longer timeout time to wait for previous workers to register.");

    // ---------------------------------------------------------------------------------------------

    /** Not intended to be instantiated. */
    private ResourceManagerOptions() {}
}
