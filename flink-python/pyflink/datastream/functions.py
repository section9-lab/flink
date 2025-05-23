################################################################################
#  Licensed to the Apache Software Foundation (ASF) under one
#  or more contributor license agreements.  See the NOTICE file
#  distributed with this work for additional information
#  regarding copyright ownership.  The ASF licenses this file
#  to you under the Apache License, Version 2.0 (the
#  "License"); you may not use this file except in compliance
#  with the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
# limitations under the License.
################################################################################

from abc import ABC, abstractmethod
from py4j.java_gateway import JavaObject
from typing import Union, Any, Generic, TypeVar, Iterable

from pyflink.datastream.state import ValueState, ValueStateDescriptor, ListStateDescriptor, \
    ListState, MapStateDescriptor, MapState, ReducingStateDescriptor, ReducingState, \
    AggregatingStateDescriptor, AggregatingState, BroadcastState, ReadOnlyBroadcastState
from pyflink.datastream.time_domain import TimeDomain
from pyflink.datastream.timerservice import TimerService
from pyflink.java_gateway import get_gateway
from pyflink.metrics import MetricGroup

__all__ = [
    'RuntimeContext',
    'MapFunction',
    'CoMapFunction',
    'FlatMapFunction',
    'CoFlatMapFunction',
    'ReduceFunction',
    'AggregateFunction',
    'KeySelector',
    'FilterFunction',
    'Partitioner',
    'SourceFunction',
    'SinkFunction',
    'ProcessFunction',
    'CoProcessFunction',
    'KeyedProcessFunction',
    'KeyedCoProcessFunction',
    'TimerService',
    'WindowFunction',
    'AllWindowFunction',
    'ProcessWindowFunction',
    'ProcessAllWindowFunction',
    'BaseBroadcastProcessFunction',
    'BroadcastProcessFunction',
    'KeyedBroadcastProcessFunction',
]


W = TypeVar('W')
W2 = TypeVar('W2')
IN = TypeVar('IN')
IN1 = TypeVar('IN1')
IN2 = TypeVar('IN2')
OUT = TypeVar('OUT')
KEY = TypeVar('KEY')


class KeyedStateStore(ABC):

    @abstractmethod
    def get_state(self, state_descriptor: ValueStateDescriptor) -> ValueState:
        """
        Gets a handle to the system's key/value state. THe key/value state is only accessible if the
        function is executed on a KeyedStream. On each access, the state exposes the value for the
        key of the element currently processed by the function. Each function may have multiple
        partitioned states, addressed with different names.

        Because the scope of each value is the key of the currently processed element, and the
        elements are distributed by the Flink runtime, the system can transparently scale out and
        redistribute the state and KeyedStream.
        """
        pass

    @abstractmethod
    def get_list_state(self, state_descriptor: ListStateDescriptor) -> ListState:
        """
        Gets a handle to the system's key/value list state. This state is similar to the value state
        access, but is optimized for state that holds lists. One can add elements to the list, or
        retrieve the list as a whle.

        This state is only accessible if the function is executed on a KeyedStream.
        """
        pass

    @abstractmethod
    def get_map_state(self, state_descriptor: MapStateDescriptor) -> MapState:
        """
        Gets a handle to the system's key/value map state. This state is similar to the value state
        access, but is optimized for state that is composed of user-defined key-value pairs.

        This state is only accessible if the function is executed on a KeyedStream.
        """
        pass

    @abstractmethod
    def get_reducing_state(self, state_descriptor: ReducingStateDescriptor) -> ReducingState:
        """
        Gets a handle to the system's key/value reducing state. This state is similar to the state
        accessed via get_state(ValueStateDescriptor), but is optimized for state that aggregates
        values.

        This state is only accessible if the function is executed on a KeyedStream.
        """
        pass

    @abstractmethod
    def get_aggregating_state(
            self, state_descriptor: AggregatingStateDescriptor) -> AggregatingState:
        """
        Gets a handle to the system's key/value aggregating state. This state is similar to the
        state accessed via get_state(ValueStateDescriptor), but is optimized for state that
        aggregates values with different types.

        This state is only accessible if the function is executed on a KeyedStream.
        """
        pass


class RuntimeContext(KeyedStateStore):
    """
    A RuntimeContext contains information about the context in which functions are executed.
    Each parallel instance of the function will have a context through which it can access
    static contextual information (such as the current parallelism).
    """

    @abstractmethod
    def get_task_name(self) -> str:
        """
        Returns the name of the task in which the UDF runs, as assigned during plan construction.
        """
        pass

    @abstractmethod
    def get_number_of_parallel_subtasks(self) -> int:
        """
        Gets the parallelism with which the parallel task runs.
        """
        pass

    @abstractmethod
    def get_max_number_of_parallel_subtasks(self) -> int:
        """
        Gets the number of max-parallelism with which the parallel task runs.
        """
        pass

    @abstractmethod
    def get_index_of_this_subtask(self) -> int:
        """
        Gets the number of this parallel subtask. The numbering starts from 0 and goes up to
        parallelism-1 (parallelism as returned by
        :func:`~RuntimeContext.get_number_of_parallel_subtasks`).
        """
        pass

    @abstractmethod
    def get_attempt_number(self) -> int:
        """
        Gets the attempt number of this parallel subtask. First attempt is numbered 0.
        """
        pass

    @abstractmethod
    def get_task_name_with_subtasks(self) -> str:
        """
        Returns the name of the task, appended with the subtask indicator, such as "MyTask (3/6)",
        where 3 would be (:func:`~RuntimeContext.get_index_of_this_subtask` + 1), and 6 would be
        :func:`~RuntimeContext.get_number_of_parallel_subtasks`.
        """
        pass

    @abstractmethod
    def get_job_parameter(self, key: str, default_value: str):
        """
        Gets the global job parameter value associated with the given key as a string.
        """
        pass

    @abstractmethod
    def get_metrics_group(self) -> MetricGroup:
        """
        Gets the metric group.
        """
        pass


class Function(ABC):
    """
    The base class for all user-defined functions.
    """
    def open(self, runtime_context: RuntimeContext):
        pass

    def close(self):
        pass


class MapFunction(Function):
    """
    Base class for Map functions. Map functions take elements and transform them, element wise. A
    Map function always produces a single result element for each input element. Typical
    applications are parsing elements, converting data types, or projecting out fields. Operations
    that produce multiple result elements from a single input element can be implemented using the
    FlatMapFunction.
    The basic syntax for using a MapFunction is as follows:

    ::
        >>> ds = ...
        >>> new_ds = ds.map(MyMapFunction())
    """

    @abstractmethod
    def map(self, value):
        """
        The mapping method. Takes an element from the input data and transforms it into exactly one
        element.

        :param value: The input value.
        :return: The transformed value.
        """
        pass


class CoMapFunction(Function):
    """
    A CoMapFunction implements a map() transformation over two connected streams.

    The same instance of the transformation function is used to transform both of
    the connected streams. That way, the stream transformations can share state.

    The basic syntax for using a CoMapFunction is as follows:

    ::
        >>> ds1 = ...
        >>> ds2 = ...
        >>> new_ds = ds1.connect(ds2).map(MyCoMapFunction())
    """

    @abstractmethod
    def map1(self, value):
        """
        This method is called for each element in the first of the connected streams.

        :param value: The stream element
        :return: The resulting element
        """
        pass

    @abstractmethod
    def map2(self, value):
        """
        This method is called for each element in the second of the connected streams.

        :param value: The stream element
        :return: The resulting element
        """
        pass


class FlatMapFunction(Function):
    """
    Base class for flatMap functions. FlatMap functions take elements and transform them, into zero,
    one, or more elements. Typical applications can be splitting elements, or unnesting lists and
    arrays. Operations that produce multiple strictly one result element per input element can also
    use the MapFunction.
    The basic syntax for using a MapFUnction is as follows:

    ::
        >>> ds = ...
        >>> new_ds = ds.flat_map(MyFlatMapFunction())
    """

    @abstractmethod
    def flat_map(self, value):
        """
        The core mthod of the FlatMapFunction. Takes an element from the input data and transforms
        it into zero, one, or more elements.
        A basic implementation of flat map is as follows:

        ::
                >>> class MyFlatMapFunction(FlatMapFunction):
                >>>     def flat_map(self, value):
                >>>         for i in range(value):
                >>>             yield i

        :param value: The input value.
        :return: A generator
        """
        pass


class CoFlatMapFunction(Function):
    """
    A CoFlatMapFunction implements a flat-map transformation over two connected streams.

    The same instance of the transformation function is used to transform both of the
    connected streams. That way, the stream transformations can share state.

    An example for the use of connected streams would be to apply rules that change over time
    onto elements of a stream. One of the connected streams has the rules, the other stream the
    elements to apply the rules to. The operation on the connected stream maintains the
    current set of rules in the state. It may receive either a rule update (from the first stream)
    and update the state, or a data element (from the second stream) and apply the rules in the
    state to the element. The result of applying the rules would be emitted.

    The basic syntax for using a CoFlatMapFunction is as follows:

    ::
        >>> ds1 = ...
        >>> ds2 = ...

        >>> class MyCoFlatMapFunction(CoFlatMapFunction):
        >>>     def flat_map1(self, value):
        >>>         for i in range(value):
        >>>             yield i
        >>>     def flat_map2(self, value):
        >>>         for i in range(value):
        >>>             yield i

        >>> new_ds = ds1.connect(ds2).flat_map(MyCoFlatMapFunction())
    """

    @abstractmethod
    def flat_map1(self, value):
        """
        This method is called for each element in the first of the connected streams.

        :param value: The input value.
        :return: A generator
        """
        pass

    @abstractmethod
    def flat_map2(self, value):
        """
        This method is called for each element in the second of the connected streams.

        :param value: The input value.
        :return: A generator
        """
        pass


class ReduceFunction(Function):
    """
    Base interface for Reduce functions. Reduce functions combine groups of elements to a single
    value, by taking always two elements and combining them into one. Reduce functions may be
    used on entire data sets, or on grouped data sets. In the latter case, each group is reduced
    individually.

    The basic syntax for using a ReduceFunction is as follows:

    ::
        >>> ds = ...
        >>> new_ds = ds.key_by(lambda x: x[1]).reduce(MyReduceFunction())
    """

    @abstractmethod
    def reduce(self, value1, value2):
        """
        The core method of ReduceFunction, combining two values into one value of the same type.
        The reduce function is consecutively applied to all values of a group until only a single
        value remains.

        :param value1: The first value to combine.
        :param value2: The second value to combine.
        :return: The combined value of both input values.
        """
        pass


class AggregateFunction(Function):
    """
    The AggregateFunction is a flexible aggregation function, characterized by the following
    features:

        - The aggregates may use different types for input values, intermediate aggregates, and
          result type, to support a wide range of aggregation types.
        - Support for distributive aggregations: Different intermediate aggregates can be merged
          together, to allow for pre-aggregation/final-aggregation optimizations.

    The AggregateFunction's intermediate aggregate (in-progress aggregation state) is called the
    `accumulator`. Values are added to the accumulator, and final aggregates are obtained by
    finalizing the accumulator state. This supports aggregation functions where the intermediate
    state needs to be different than the aggregated values and the final result type, such as for
    example average (which typically keeps a count and sum). Merging intermediate aggregates
    (partial aggregates) means merging the accumulators.

    The AggregationFunction itself is stateless. To allow a single AggregationFunction instance to
    maintain multiple aggregates (such as one aggregate per key), the AggregationFunction creates a
    new accumulator whenever a new aggregation is started.
    """

    @abstractmethod
    def create_accumulator(self):
        """
        Creates a new accumulator, starting a new aggregate.

        The new accumulator is typically meaningless unless a value is added via
        :func:`~AggregateFunction.add`.

        The accumulator is the state of a running aggregation. When a program has multiple
        aggregates in progress (such as per key and window), the state (per key and window) is the
        size of the accumulator.

        :return: A new accumulator, corresponding to an empty aggregate.
        """
        pass

    @abstractmethod
    def add(self, value, accumulator):
        """
        Adds the given input value to the given accumulator, returning the new accumulator value.

        For efficiency, the input accumulator may be modified and returned.

        :param value: The value to add.
        :param accumulator: The accumulator to add the value to.
        :return: The accumulator with the updated state.
        """
        pass

    @abstractmethod
    def get_result(self, accumulator):
        """
        Gets the result of the aggregation from the accumulator.

        :param accumulator: The accumulator of the aggregation.
        :return: The final aggregation result.
        """
        pass

    @abstractmethod
    def merge(self, acc_a, acc_b):
        """
        Merges two accumulators, returning an accumulator with the merged state.

        This function may reuse any of the given accumulators as the target for the merge and
        return that. The assumption is that the given accumulators will not be used any more after
        having been passed to this function.

        :param acc_a: An accumulator to merge.
        :param acc_b: Another accumulator to merge.
        :return: The accumulator with the merged state.
        """
        pass


class KeySelector(Function):
    """
    The KeySelector allows to use deterministic objects for operations such as reduce, reduceGroup,
    join coGroup, etc. If invoked multiple times on the same object, the returned key must be the
    same. The extractor takes an object an returns the deterministic key for that object.
    """

    @abstractmethod
    def get_key(self, value):
        """
        User-defined function that deterministically extracts the key from an object.

        :param value: The object to get the key from.
        :return: The extracted key.
        """
        pass


class NullByteKeySelector(KeySelector):
    """
    Used as a dummy KeySelector to allow using keyed operators for non-keyed use cases. Essentially,
    it gives all incoming records the same key, which is a (byte) 0 value.
    """

    def get_key(self, value):
        return 0


class FilterFunction(Function):
    """
    A filter function is a predicate applied individually to each record. The predicate decides
    whether to keep the element, or to discard it.

    The basic syntax for using a FilterFunction is as follows:

    ::
         >>> ds = ...
         >>> result = ds.filter(MyFilterFunction())

    Note that the system assumes that the function does not modify the elements on which the
    predicate is applied. Violating this assumption can lead to incorrect results.
    """

    @abstractmethod
    def filter(self, value):
        """
        The filter function that evaluates the predicate.

        :param value: The value to be filtered.
        :return: True for values that should be retained, false for values to be filtered out.
        """
        pass


class Partitioner(Function):
    """
    Function to implement a custom partition assignment for keys.
    """

    @abstractmethod
    def partition(self, key: Any, num_partitions: int) -> int:
        """
        Computes the partition for the given key.

        :param key: The key.
        :param num_partitions: The number of partitions to partition into.
        :return: The partition index.
        """
        pass


class FunctionWrapper(Function):
    """
    A basic wrapper class for user defined function.
    """

    def __init__(self, func):
        self._func = func


class ReduceFunctionWrapper(FunctionWrapper):
    """
    A wrapper class for ReduceFunction. It's used for wrapping up user defined function in a
    ReduceFunction when user does not implement a ReduceFunction but directly pass a function
    object or a lambda function to reduce() function.
    """

    def __init__(self, func):
        """
        The constructor of ReduceFunctionWrapper.

        :param func: user defined function object.
        """
        super(ReduceFunctionWrapper, self).__init__(func)

    def reduce(self, value1, value2):
        """
        A delegated reduce function to invoke user defined function.

        :param value1: The first value to combine.
        :param value2: The second value to combine.
        :return: The combined value of both input values.
        """
        return self._func(value1, value2)


def _get_python_env():
    """
    An util function to get a python user defined function execution environment.
    """
    gateway = get_gateway()
    exec_type = gateway.jvm.org.apache.flink.table.functions.python.PythonEnv.ExecType.PROCESS
    return gateway.jvm.org.apache.flink.table.functions.python.PythonEnv(exec_type)


class JavaFunctionWrapper(object):
    """
    A wrapper class that maintains a Function implemented in Java.
    """

    def __init__(self, j_function: Union[str, JavaObject]):
        if isinstance(j_function, str):
            j_func_class = get_gateway().jvm.__getattr__(j_function)
            j_function = j_func_class()
        self._j_function = j_function

    def get_java_function(self):
        return self._j_function


class SourceFunction(JavaFunctionWrapper):
    """
    Base class for all stream data source in Flink.
    """

    def __init__(self, source_func: Union[str, JavaObject]):
        """
        Constructor of SinkFunction.

        :param source_func: The java SourceFunction object.
        """
        super(SourceFunction, self).__init__(source_func)


class SinkFunction(JavaFunctionWrapper):
    """
    The base class for SinkFunctions.
    """

    def __init__(self, sink_func: Union[str, JavaObject]):
        """
        Constructor of SinkFunction.

        :param sink_func: The java SinkFunction object or the full name of the SinkFunction class.
        """
        super(SinkFunction, self).__init__(sink_func)


class ProcessFunction(Function):
    """
    A function that process elements of a stream.

    For every element in the input stream process_element(value, ctx, out) is invoked. This can
    produce zero or more elements as output. Implementations can also query the time and set timers
    through the provided Context. For firing timers on_timer(long, ctx, out) will be invoked. This
    can again produce zero or more elements as output and register further timers.

    Note that access to keyed state and timers (which are also scoped to a key) is only available if
    the ProcessFunction is applied on a KeyedStream.
    """

    class Context(ABC):
        """
        Information available in an invocation of process_element(value, ctx, out) or
        on_timer(value, ctx, out).
        """

        @abstractmethod
        def timer_service(self) -> TimerService:
            """
            A Timer service for querying time and registering timers.
            """
            pass

        @abstractmethod
        def timestamp(self) -> int:
            """
            Timestamp of the element currently being processed or timestamp of a firing timer.

            This might be None, depending on the stream's watermark strategy.
            """
            pass

    @abstractmethod
    def process_element(self, value, ctx: 'ProcessFunction.Context'):
        """
        Process one element from the input stream.

        This function can output zero or more elements using the :code:`yield` statement. It can
        read and update the internal state as well as set timers using the Context parameter.

        :param value: The input value.
        :param ctx:  A Context that allows querying the timestamp of the element and getting a
                     TimerService for registering timers and querying the time. The context is only
                     valid during the invocation of this method, do not store it.
        """
        pass


class KeyedProcessFunction(Function):
    """
    A keyed function processes elements of a stream.

    For every element in the input stream, process_element() is invoked. This can produce zero or
    more elements as output. Implementations can also query the time and set timers through the
    provided Context. For firing timers on_timer() will be invoked. This can again produce zero or
    more elements as output and register further timers.

    Note that access to keyed state and timers (which are also scoped to a key) is only available if
    the KeyedProcessFunction is applied on a KeyedStream.
    """

    class Context(ABC):

        @abstractmethod
        def get_current_key(self):
            pass

        @abstractmethod
        def timer_service(self) -> TimerService:
            """
            A Timer service for querying time and registering timers.
            """
            pass

        @abstractmethod
        def timestamp(self) -> int:
            """
            Timestamp of the element currently being processed or timestamp of a firing timer.

            This might be None, depending on the stream's watermark strategy.
            """
            pass

    class OnTimerContext(Context):

        @abstractmethod
        def time_domain(self) -> TimeDomain:
            """
            The TimeDomain of the firing timer.
            :return: The TimeDomain of current fired timer.
            """
            pass

    @abstractmethod
    def process_element(self, value, ctx: 'KeyedProcessFunction.Context'):
        """
        Process one element from the input stream.

        This function can output zero or more elements and also update
        internal state or set timers using the Context parameter.

        :param value: The input value.
        :param ctx:  A Context that allows querying the timestamp of the element and getting a
                     TimerService for registering timers and querying the time. The context is only
                     valid during the invocation of this method, do not store it.
        """
        pass

    def on_timer(self, timestamp: int, ctx: 'KeyedProcessFunction.OnTimerContext'):
        """
        Called when a timer set using TimerService fires.

        :param timestamp: The timestamp of the firing timer.
        :param ctx: An OnTimerContext that allows querying the timestamp of the firing timer,
                    querying the TimeDomain of the firing timer and getting a TimerService for
                    registering timers and querying the time. The context is only valid during the
                    invocation of this method, do not store it.
        """
        pass


class CoProcessFunction(Function):
    """
    A function that processes elements of two streams and produces a single output one.

    The function will be called for every element in the input streams and can produce zero or
    more output elements. Contrary to the :class:`CoFlatMapFunction`, this function can also query
    the time (both event and processing) and set timers, through the provided
    :class:`CoProcessFunction.Context`. When reacting to the firing of set timers the function can
    emit yet more elements.

    An example use-case for connected streams would be the application of a set of rules that
    change over time ({@code stream A}) to the elements contained in another stream (stream {@code
    B}). The rules contained in {@code stream A} can be stored in the state and wait for new
    elements to arrive on {@code stream B}. Upon reception of a new element on {@code stream B},
    the function can now apply the previously stored rules to the element and directly emit a
    result, and/or register a timer that will trigger an action in the future.
    """

    class Context(ABC):

        @abstractmethod
        def timer_service(self) -> TimerService:
            """
            A Timer service for querying time and registering timers.
            """
            pass

        @abstractmethod
        def timestamp(self) -> int:
            """
            Timestamp of the element currently being processed or timestamp of a firing timer.

            This might be None, depending on the stream's watermark strategy.
            """
            pass

    @abstractmethod
    def process_element1(self, value, ctx: 'CoProcessFunction.Context'):
        """
        This method is called for each element in the first of the connected streams.

        This function can output zero or more elements using the :code:`yield` statement. It can
        read and update the internal state as well as set timers using the Context parameter.

        :param value: The input value.
        :param ctx:  A Context that allows querying the timestamp of the element and getting a
                     TimerService for registering timers and querying the time. The context is only
                     valid during the invocation of this method, do not store it.
        """
        pass

    @abstractmethod
    def process_element2(self, value, ctx: 'CoProcessFunction.Context'):
        """
        This method is called for each element in the second of the connected streams.

        This function can output zero or more elements using the :code:`yield` statement. It can
        read and update the internal state as well as set timers using the Context parameter.

        :param value: The input value.
        :param ctx:  A Context that allows querying the timestamp of the element and getting a
                     TimerService for registering timers and querying the time. The context is only
                     valid during the invocation of this method, do not store it.
        """
        pass


class KeyedCoProcessFunction(Function):
    """
A function that processes elements of two keyed streams and produces a single output one.

The function will be called for every element in the input streams and can produce zero or
more output elements. Contrary to the :class:`CoFlatMapFunction`, this function can also query the
time (both event and processing) and set timers, through the provided {@link Context}. When
reacting to the firing of set timers the function can emit yet more elements.

An example use-case for connected streams would be the application of a set of rules that
change over time ({@code stream A}) to the elements contained in another stream (stream {@code
B}). The rules contained in {@code stream A} can be stored in the state and wait for new elements
to arrive on {@code stream B}. Upon reception of a new element on {@code stream B}, the function
can now apply the previously stored rules to the element and directly emit a result, and/or
register a timer that will trigger an action in the future.
    """

    class Context(ABC):

        @abstractmethod
        def get_current_key(self):
            pass

        @abstractmethod
        def timer_service(self) -> TimerService:
            """
            A Timer service for querying time and registering timers.
            """
            pass

        @abstractmethod
        def timestamp(self) -> int:
            """
            Timestamp of the element currently being processed or timestamp of a firing timer.

            This might be None, depending on the stream's watermark strategy.
            """
            pass

    class OnTimerContext(Context):

        @abstractmethod
        def time_domain(self) -> TimeDomain:
            """
            The TimeDomain of the firing timer.
            :return: The TimeDomain of current fired timer.
            """
            pass

    @abstractmethod
    def process_element1(self, value, ctx: 'KeyedCoProcessFunction.Context'):
        """
        Process one element from the input stream.

        This function can output zero or more elements using the :code:`yield` statement. It can
        read and update the internal state as well as set timers using the Context parameter.

        :param value: The input value.
        :param ctx:  A Context that allows querying the timestamp of the element and getting a
                     TimerService for registering timers and querying the time. The context is only
                     valid during the invocation of this method, do not store it.
        """
        pass

    @abstractmethod
    def process_element2(self, value, ctx: 'KeyedCoProcessFunction.Context'):
        """
        Process one element from the input stream.

        This function can output zero or more elements using the :code:`yield` statement. It can
        read and update the internal state as well as set timers using the Context parameter.

        :param value: The input value.
        :param ctx:  A Context that allows querying the timestamp of the element and getting a
                     TimerService for registering timers and querying the time. The context is only
                     valid during the invocation of this method, do not store it.
        """
        pass

    def on_timer(self, timestamp: int, ctx: 'KeyedCoProcessFunction.OnTimerContext'):
        """
        Called when a timer set using TimerService fires.

        :param timestamp: The timestamp of the firing timer.
        :param ctx: An OnTimerContext that allows querying the timestamp of the firing timer,
                    querying the TimeDomain of the firing timer and getting a TimerService for
                    registering timers and querying the time. The context is only valid during the
                    invocation of this method, do not store it.
        """
        pass


class WindowFunction(Function, Generic[IN, OUT, KEY, W]):
    """
    Base interface for functions that are evaluated over keyed (grouped) windows.
    """

    @abstractmethod
    def apply(self, key: KEY, window: W, inputs: Iterable[IN]) -> Iterable[OUT]:
        """
        Evaluates the window and outputs none or several elements.

        :param key: The key for which this window is evaluated.
        :param window: The window that is being evaluated.
        :param inputs: The elements in the window being evaluated.
        """
        pass


class AllWindowFunction(Function, Generic[IN, OUT, W]):
    """
    Base interface for functions that are evaluated over non-keyed windows.
    """

    @abstractmethod
    def apply(self, window: W, inputs: Iterable[IN]) -> Iterable[OUT]:
        """
        Evaluates the window and outputs none or several elements.

        :param window: The window that is being evaluated.
        :param inputs: The elements in the window being evaluated.
        """
        pass


class ProcessWindowFunction(Function, Generic[IN, OUT, KEY, W]):
    """
    Base interface for functions that are evaluated over keyed (grouped) windows using a context
    for retrieving extra information.
    """

    class Context(ABC, Generic[W2]):
        """
        The context holding window metadata.
        """

        @abstractmethod
        def window(self) -> W2:
            """
            :return: The window that is being evaluated.
            """
            pass

        @abstractmethod
        def current_processing_time(self) -> int:
            """
            :return: The current processing time.
            """
            pass

        @abstractmethod
        def current_watermark(self) -> int:
            """
            :return: The current event-time watermark.
            """
            pass

        @abstractmethod
        def window_state(self) -> KeyedStateStore:
            """
            State accessor for per-key and per-window state.

            .. note::
                If you use per-window state you have to ensure that you clean it up by implementing
                :func:`~ProcessWindowFunction.clear`.

            :return: The :class:`KeyedStateStore` used to access per-key and per-window states.
            """
            pass

        @abstractmethod
        def global_state(self) -> KeyedStateStore:
            """
            State accessor for per-key global state.
            """
            pass

    @abstractmethod
    def process(self,
                key: KEY,
                context: 'ProcessWindowFunction.Context',
                elements: Iterable[IN]) -> Iterable[OUT]:
        """
        Evaluates the window and outputs none or several elements.

        :param key: The key for which this window is evaluated.
        :param context: The context in which the window is being evaluated.
        :param elements: The elements in the window being evaluated.
        :return: The iterable object which produces the elements to emit.
        """
        pass

    def clear(self, context: 'ProcessWindowFunction.Context') -> None:
        """
        Deletes any state in the :class:`Context` when the Window expires (the watermark passes its
        max_timestamp + allowed_lateness).

        :param context: The context to which the window is being evaluated.
        """
        pass


class ProcessAllWindowFunction(Function, Generic[IN, OUT, W]):
    """
    Base interface for functions that are evaluated over non-keyed windows using a context
    for retrieving extra information.
    """

    class Context(ABC, Generic[W2]):
        """
        The context holding window metadata.
        """

        @abstractmethod
        def window(self) -> W2:
            """
            :return: The window that is being evaluated.
            """
            pass

        @abstractmethod
        def window_state(self) -> KeyedStateStore:
            """
            State accessor for per-key and per-window state.

            .. note::
                If you use per-window state you have to ensure that you clean it up by implementing
                :func:`~ProcessAllWindowFunction.clear`.

            :return: The :class:`KeyedStateStore` used to access per-key and per-window states.
            """
            pass

        @abstractmethod
        def global_state(self) -> KeyedStateStore:
            """
            State accessor for per-key global state.
            """
            pass

    @abstractmethod
    def process(self,
                context: 'ProcessAllWindowFunction.Context',
                elements: Iterable[IN]) -> Iterable[OUT]:
        """
        Evaluates the window and outputs none or several elements.

        :param context: The context in which the window is being evaluated.
        :param elements: The elements in the window being evaluated.
        :return: The iterable object which produces the elements to emit.
        """
        pass

    def clear(self, context: 'ProcessAllWindowFunction.Context') -> None:
        """
        Deletes any state in the :class:`Context` when the Window expires (the watermark passes its
        max_timestamp + allowed_lateness).

        :param context: The context to which the window is being evaluated.
        """
        pass


class PassThroughWindowFunction(WindowFunction[IN, IN, KEY, W]):

    def apply(self, key: KEY, window: W, inputs: Iterable[IN]) -> Iterable[IN]:
        yield from inputs


class PassThroughAllWindowFunction(AllWindowFunction[IN, IN, W]):

    def apply(self, window: W, inputs: Iterable[IN]) -> Iterable[IN]:
        yield from inputs


class InternalWindowFunction(Function, Generic[IN, OUT, KEY, W]):

    class InternalWindowContext(ABC):

        @abstractmethod
        def current_processing_time(self) -> int:
            pass

        @abstractmethod
        def current_watermark(self) -> int:
            pass

        @abstractmethod
        def window_state(self) -> KeyedStateStore:
            pass

        @abstractmethod
        def global_state(self) -> KeyedStateStore:
            pass

    @abstractmethod
    def process(self,
                key: KEY,
                window: W,
                context: InternalWindowContext,
                input_data: IN) -> Iterable[OUT]:
        pass

    @abstractmethod
    def clear(self, window: W, context: InternalWindowContext):
        pass


class InternalSingleValueWindowFunction(InternalWindowFunction[IN, OUT, KEY, W]):

    def __init__(self, wrapped_function: WindowFunction):
        self._wrapped_function = wrapped_function

    def open(self, runtime_context: RuntimeContext):
        self._wrapped_function.open(runtime_context)

    def close(self):
        self._wrapped_function.close()

    def process(self,
                key: KEY,
                window: W,
                context: InternalWindowFunction.InternalWindowContext,
                input_data: IN) -> Iterable[OUT]:
        return self._wrapped_function.apply(key, window, [input_data])

    def clear(self, window: W, context: InternalWindowFunction.InternalWindowContext):
        pass


class InternalSingleValueAllWindowFunction(InternalWindowFunction[IN, OUT, int, W]):

    def __init__(self, wrapped_function: AllWindowFunction):
        self._wrapped_function = wrapped_function

    def open(self, runtime_context: RuntimeContext):
        self._wrapped_function.open(runtime_context)

    def close(self):
        self._wrapped_function.close()

    def process(self,
                key: int,
                window: W,
                context: InternalWindowFunction.InternalWindowContext,
                input_data: IN) -> Iterable[OUT]:
        return self._wrapped_function.apply(window, [input_data])

    def clear(self, window: W, context: InternalWindowFunction.InternalWindowContext):
        pass


class InternalIterableWindowFunction(InternalWindowFunction[Iterable[IN], OUT, KEY, W]):

    def __init__(self, wrapped_function: WindowFunction):
        self._wrapped_function = wrapped_function

    def open(self, runtime_context: RuntimeContext):
        self._wrapped_function.open(runtime_context)

    def close(self):
        self._wrapped_function.close()

    def process(self,
                key: KEY,
                window: W,
                context: InternalWindowFunction.InternalWindowContext,
                input_data: Iterable[IN]) -> Iterable[OUT]:
        return self._wrapped_function.apply(key, window, input_data)

    def clear(self,
              window: W,
              context: InternalWindowFunction.InternalWindowContext):
        pass


class InternalIterableAllWindowFunction(InternalWindowFunction[Iterable[IN], OUT, int, W]):

    def __init__(self, wrapped_function: AllWindowFunction):
        self._wrapped_function = wrapped_function

    def open(self, runtime_context: RuntimeContext):
        self._wrapped_function.open(runtime_context)

    def close(self):
        self._wrapped_function.close()

    def process(self,
                key: int,
                window: W,
                context: InternalWindowFunction.InternalWindowContext,
                input_data: Iterable[IN]) -> Iterable[OUT]:
        return self._wrapped_function.apply(window, input_data)

    def clear(self,
              window: W,
              context: InternalWindowFunction.InternalWindowContext):
        pass


class InternalProcessWindowContext(ProcessWindowFunction.Context[W]):

    def __init__(self):
        self._underlying = None
        self._window = None

    def window(self) -> W:
        return self._window

    def current_processing_time(self) -> int:
        return self._underlying.current_processing_time()

    def current_watermark(self) -> int:
        return self._underlying.current_watermark()

    def window_state(self) -> KeyedStateStore:
        return self._underlying.window_state()

    def global_state(self) -> KeyedStateStore:
        return self._underlying.global_state()


class InternalProcessAllWindowContext(ProcessAllWindowFunction.Context[W]):

    def __init__(self):
        self._underlying = None
        self._window = None

    def window(self) -> W:
        return self._window

    def window_state(self) -> KeyedStateStore:
        return self._underlying.window_state()

    def global_state(self) -> KeyedStateStore:
        return self._underlying.global_state()


class InternalSingleValueProcessWindowFunction(InternalWindowFunction[IN, OUT, KEY, W]):

    def __init__(self, wrapped_function: ProcessWindowFunction):
        self._wrapped_function = wrapped_function
        self._internal_context: InternalProcessWindowContext = \
            InternalProcessWindowContext()

    def open(self, runtime_context: RuntimeContext):
        self._wrapped_function.open(runtime_context)

    def close(self):
        self._wrapped_function.close()

    def process(self,
                key: KEY,
                window: W,
                context: InternalWindowFunction.InternalWindowContext,
                input_data: IN) -> Iterable[OUT]:
        self._internal_context._window = window
        self._internal_context._underlying = context
        return self._wrapped_function.process(key, self._internal_context, [input_data])

    def clear(self, window: W, context: InternalWindowFunction.InternalWindowContext):
        self._internal_context._window = window
        self._internal_context._underlying = context
        self._wrapped_function.clear(self._internal_context)


class InternalSingleValueProcessAllWindowFunction(InternalWindowFunction[IN, OUT, int, W]):

    def __init__(self, wrapped_function: ProcessAllWindowFunction):
        self._wrapped_function = wrapped_function
        self._internal_context: InternalProcessAllWindowContext = \
            InternalProcessAllWindowContext()

    def open(self, runtime_context: RuntimeContext):
        self._wrapped_function.open(runtime_context)

    def close(self):
        self._wrapped_function.close()

    def process(self,
                key: int,
                window: W,
                context: InternalWindowFunction.InternalWindowContext,
                input_data: IN) -> Iterable[OUT]:
        self._internal_context._window = window
        self._internal_context._underlying = context
        return self._wrapped_function.process(self._internal_context, [input_data])

    def clear(self, window: W, context: InternalWindowFunction.InternalWindowContext):
        self._internal_context._window = window
        self._internal_context._underlying = context
        self._wrapped_function.clear(self._internal_context)


class InternalIterableProcessWindowFunction(InternalWindowFunction[Iterable[IN], OUT, KEY, W]):

    def __init__(self, wrapped_function: ProcessWindowFunction):
        self._wrapped_function = wrapped_function
        self._internal_context: InternalProcessWindowContext = \
            InternalProcessWindowContext()

    def open(self, runtime_context: RuntimeContext):
        self._wrapped_function.open(runtime_context)

    def close(self):
        self._wrapped_function.close()

    def process(self,
                key: KEY,
                window: W,
                context: InternalWindowFunction.InternalWindowContext,
                input_data: Iterable[IN]) -> Iterable[OUT]:
        self._internal_context._window = window
        self._internal_context._underlying = context
        return self._wrapped_function.process(key, self._internal_context, input_data)

    def clear(self, window: W, context: InternalWindowFunction.InternalWindowContext):
        self._internal_context._window = window
        self._internal_context._underlying = context
        self._wrapped_function.clear(self._internal_context)


class BaseBroadcastProcessFunction(Function):
    """
    The base class containing the functionality available to all broadcast process functions. These
    include :class:`BroadcastProcessFunction` and :class:`KeyedBroadcastProcessFunction`.
    """

    class BaseContext(ABC):
        """
        The base context available to all methods in a broadcast process function. This includes
        :class:`BroadcastProcessFunction` and :class:`KeyedBroadcastProcessFunction`.
        """

        @abstractmethod
        def timestamp(self) -> int:
            """
            Timestamp of the element currently being processed or timestamp of a firing timer.
            This might be None, depending on the stream's watermark strategy.
            """
            pass

        @abstractmethod
        def current_processing_time(self) -> int:
            """Returns the current processing time."""
            pass

        @abstractmethod
        def current_watermark(self) -> int:
            """Returns the current watermark."""
            pass

    class Context(BaseContext):
        """
        A :class:`BaseContext` available to the broadcasted stream side of a
        :class:`BroadcastConnectedStream`.

        Apart from the basic functionality of a :class:`BaseContext`, this also allows to get and
        update the elements stored in the :class:`BroadcastState`. In other words, it gives read/
        write access to the broadcast state.
        """

        @abstractmethod
        def get_broadcast_state(self, state_descriptor: MapStateDescriptor) -> BroadcastState:
            """
            Fetches the :class:`BroadcastState` with the specified name.

            :param state_descriptor: the :class:`MapStateDescriptor` of the state to be fetched.
            :return: The required :class:`BroadcastState`.
            """
            pass

    class ReadOnlyContext(BaseContext):
        """
        A :class:`BaseContext` available to the non-broadcasted stream side of a
        :class:`BroadcastConnectedStream`.

        Apart from the basic functionality of a :class:`BaseContext`, this also allows to get
        read-only access to the elements stored in the broadcast state.
        """

        @abstractmethod
        def get_broadcast_state(
            self, state_descriptor: MapStateDescriptor
        ) -> ReadOnlyBroadcastState:
            """
            Fetches a read-only view of the broadcast state with the specified name.

            :param state_descriptor: the :class:`MapStateDescriptor` of the state to be fetched.
            :return: The required read-only view of the broadcast state.
            """
            pass


class BroadcastProcessFunction(BaseBroadcastProcessFunction, Generic[IN1, IN2, OUT]):
    """
    A function to be applied to a :class:`BroadcastConnectedStream` that connects
    :class:`BroadcastStream`, i.e. a stream with broadcast state, with a non-keyed
    :class:`DataStream`.

    The stream with the broadcast state can be created using the :meth:`DataStream.broadcast`
    method.

    The user has to implement two methods:

    * the :meth:`process_broadcast_element` which will be applied to each element in the broadcast
      side
    * the :meth:`process_element` which will be applied to the non-broadcasted side.

    The :meth:`process_broadcast_element` takes a context as an argument (among others), which
    allows it to read/write to the broadcast state, while the :meth:`process_element` has read-only
    access to the broadcast state.

    .. versionadded:: 1.16.0
    """

    class Context(BaseBroadcastProcessFunction.Context, ABC):
        """
        A :class:`BaseBroadcastProcessFunction.Context` available to the broadcast side of a
        :class:`BroadcastConnectedStream`.
        """
        pass

    class ReadOnlyContext(BaseBroadcastProcessFunction.ReadOnlyContext, ABC):
        """
        A :class:`BaseBroadcastProcessFunction.ReadOnlyContext` available to the non-keyed side of a
        :class:`BroadcastConnectedStream` (if any).
        """
        pass

    @abstractmethod
    def process_element(self, value: IN1, ctx: ReadOnlyContext):
        """
        This method is called for each element in the (non-broadcast) :class:`DataStream`.

        This function can output zero or more elements via :code:`yield` statement, and query the
        current processing/event time. Finally, it has read-only access to the broadcast state. The
        context is only valid during the invocation of this method, do not store it.

        :param value: The stream element.
        :param ctx: A :class:`BroadcastProcessFunction.ReadOnlyContext` that allows querying the
            timestamp of the element, querying the current processing/event time and reading the
            broadcast state. The context is only valid during the invocation of this method, do not
            store it.
        """
        pass

    @abstractmethod
    def process_broadcast_element(self, value: IN2, ctx: Context):
        """
        This method is called for each element in the :class:`BroadcastStream`.

        This function can output zero or more elements via :code:`yield` statement, query the
        current processing/event time, and also query and update the internal
        :class:`state.BroadcastState`. These can be done through the provided
        :class:`BroadcastProcessFunction.Context`. The context is only valid during the invocation
        of this method, do not store it.

        :param value: The stream element.
        :param ctx: A :class:`BroadcastProcessFunction.Context` that allows querying the timestamp
            of the element, querying the current processing/event time and updating the broadcast
            state. The context is only valid during the invocation of this method, do not store it.
        """
        pass


class KeyedBroadcastProcessFunction(BaseBroadcastProcessFunction, Generic[KEY, IN1, IN2, OUT]):
    """
    A function to be applied to a :class:`BroadcastConnectedStream` that connects
    :class:`BroadcastStream`, i.e. a stream with broadcast state, with a :class:`KeyedStream`.

    The stream with the broadcast state can be created using the :meth:`DataStream.broadcast`
    method.

    The user has to implement two methods:

    * the :meth:`process_broadcast_element` which will be applied to each element in the broadcast
      side
    * the :meth:`process_element` which will be applied to the non-broadcasted/keyed side.

    The :meth:`process_broadcast_element` takes a context as an argument (among others), which
    allows it to read/write to the broadcast state, while the :meth:`process_element` has read-only
    access to the broadcast state, but can read/write to the keyed state and register timers.

    .. versionadded:: 1.16.0
    """

    class Context(BaseBroadcastProcessFunction.Context, ABC):
        """
        A :class:`BaseBroadcastProcessFunction.Context` available to the broadcast side of a
        :class:`BroadcastConnectedStream`.

        Currently, the function ``applyToKeyedState`` in Java is not supported in PyFlink.
        """
        pass

    class ReadOnlyContext(BaseBroadcastProcessFunction.ReadOnlyContext, ABC):
        """
        A :class:`BaseBroadcastProcessFunction.ReadOnlyContext` available to the non-keyed side of a
        :class:`BroadcastConnectedStream` (if any).

        Apart from the basic functionality of a :class:`BaseBroadcastProcessFunction.Context`, this
        also allows to get a read-only iterator over the elements stored in the broadcast state and
        a :class:`TimerService` for querying time and registering timers.
        """

        @abstractmethod
        def timer_service(self) -> TimerService:
            """
            A :class:`TimerService` for querying time and registering timers.
            """
            pass

        @abstractmethod
        def get_current_key(self) -> KEY:  # type: ignore[type-var]
            """
            Get key of the element being processed.
            """
            pass

    class OnTimerContext(ReadOnlyContext, ABC):
        """
        Information available in an invocation of :meth:`KeyedBroadcastProcessFunction.on_timer`.
        """

        @abstractmethod
        def time_domain(self) -> TimeDomain:
            """
            The :class:`TimeDomain` of the firing timer, i.e. if it is event or processing time
            timer.
            """
            pass

        @abstractmethod
        def get_current_key(self) -> KEY:  # type: ignore[type-var]
            """
            Get the key of the firing timer.
            """
            pass

    @abstractmethod
    def process_element(self, value: IN1, ctx: ReadOnlyContext):
        """
        This method is called for each element in the (non-broadcast) :class:`KeyedStream`.

        It can output zero or more elements via ``yield`` statement, query the current
        processing/event time, and also query and update the local keyed state. In addition,
        it can get a :class:`TimerService` for registering timers and querying the time. Finally, it
        has read-only access to the broadcast state. The context is only valid during the invocation
        of this method, do not store it.

        :param value: The stream element.
        :param ctx: A :class:`KeyedBroadcastProcessFunction.ReadOnlyContext` that allows querying
            the timestamp of the element, querying the current processing/event time and iterating
            the broadcast state with read-only access. The context is only valid during the
            invocation of this method, do not store it.
        """
        pass

    @abstractmethod
    def process_broadcast_element(self, value: IN2, ctx: Context):
        """
        This method is called for each element in the :class:`BroadcastStream`.

        It can output zero or more elements via ``yield`` statement, query the current
        processing/event time, and also query and update the internal :class:`state.BroadcastState`.
        Currently, ``applyToKeyedState`` is not supported in PyFlink. The context is only valid
        during the invocation of this method, do not store it.

        :param value: The stream element.
        :param ctx: A :class:`KeyedBroadcastProcessFunction.Context` that allows querying the
            timestamp of the element, querying the current processing/event time and updating the
            broadcast state. The context is only valid during the invocation of this method, do not
            store it.
        """
        pass

    def on_timer(self, timestamp: int, ctx: OnTimerContext):
        """
        Called when a timer set using :class:`TimerService` fires.

        :param timestamp: The timestamp of the firing timer.
        :param ctx: An :class:`KeyedBroadcastProcessFunction.OnTimerContext` that allows querying
            the timestamp of the firing timer, querying the current processing/event time, iterating
            the broadcast state with read-only access, querying the :class:`TimeDomain` of the
            firing timer and getting a :class:`TimerService` for registering timers and querying the
            time. The context is only valid during the invocation of this method, do not store it.
        """
        pass


class InternalIterableProcessAllWindowFunction(InternalWindowFunction[Iterable[IN], OUT, int, W]):

    def __init__(self, wrapped_function: ProcessAllWindowFunction):
        self._wrapped_function = wrapped_function
        self._internal_context: InternalProcessAllWindowContext = \
            InternalProcessAllWindowContext()

    def open(self, runtime_context: RuntimeContext):
        self._wrapped_function.open(runtime_context)

    def close(self):
        self._wrapped_function.close()

    def process(self,
                key: int,
                window: W,
                context: InternalWindowFunction.InternalWindowContext,
                input_data: Iterable[IN]) -> Iterable[OUT]:
        self._internal_context._window = window
        self._internal_context._underlying = context
        return self._wrapped_function.process(self._internal_context, input_data)

    def clear(self, window: W, context: InternalWindowFunction.InternalWindowContext):
        self._internal_context._window = window
        self._internal_context._underlying = context
        self._wrapped_function.clear(self._internal_context)
