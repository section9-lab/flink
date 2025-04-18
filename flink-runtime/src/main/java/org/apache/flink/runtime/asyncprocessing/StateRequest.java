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

package org.apache.flink.runtime.asyncprocessing;

import org.apache.flink.api.common.state.v2.State;
import org.apache.flink.core.state.InternalStateFuture;
import org.apache.flink.runtime.state.v2.internal.InternalPartitionedState;

import javax.annotation.Nullable;

import java.io.Serializable;

/**
 * A request encapsulates the necessary data to perform a state request.
 *
 * @param <K> Type of partitioned key.
 * @param <IN> Type of input of this request.
 * @param <N> Type of namespace.
 * @param <OUT> Type of value that request will return.
 */
public class StateRequest<K, N, IN, OUT> implements Serializable {

    /**
     * The underlying state to be accessed, can be empty for {@link StateRequestType#SYNC_POINT}.
     */
    @Nullable private final State state;

    /** The type of this request. */
    private final StateRequestType type;

    private final boolean sync;

    /** The payload(input) of this request. */
    @Nullable private final IN payload;

    /** The future to collect the result of the request. */
    private final InternalStateFuture<OUT> stateFuture;

    /** The record context of this request. */
    private final RecordContext<K> context;

    @Nullable private final N namespace;

    public StateRequest(
            @Nullable State state,
            StateRequestType type,
            boolean sync,
            @Nullable IN payload,
            InternalStateFuture<OUT> stateFuture,
            RecordContext<K> context) {
        this.state = state;
        this.type = type;
        this.sync = sync;
        this.payload = payload;
        this.stateFuture = stateFuture;
        this.context = context;
        this.namespace =
                state == null ? null : context.getNamespace((InternalPartitionedState<N>) state);
    }

    public StateRequestType getRequestType() {
        return type;
    }

    @Nullable
    public IN getPayload() {
        return payload;
    }

    public boolean isSync() {
        return sync;
    }

    @Nullable
    public State getState() {
        return state;
    }

    public InternalStateFuture<OUT> getFuture() {
        return stateFuture;
    }

    public RecordContext<K> getRecordContext() {
        return context;
    }

    public @Nullable N getNamespace() {
        return namespace;
    }
}
