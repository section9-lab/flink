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
package org.apache.flink.streaming.api.scala

import org.apache.flink.api.common.functions.OpenContext
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.scala.AsyncDataStreamITCase._
import org.apache.flink.streaming.api.scala.async.{AsyncRetryStrategies, ResultFuture, RetryPredicates, RichAsyncFunction}
import org.apache.flink.test.util.AbstractTestBaseJUnit4

import org.junit.Assert._
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

import java.{util => ju}
import java.util.concurrent.{CountDownLatch, TimeUnit}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

object AsyncDataStreamITCase {
  private var testResult: mutable.ArrayBuffer[Int] = _

  @Parameters(name = "ordered = {0}")
  def parameters: ju.Collection[Boolean] = ju.Arrays.asList(true, false)
}

@RunWith(value = classOf[Parameterized])
class AsyncDataStreamITCase(ordered: Boolean) extends AbstractTestBaseJUnit4 {

  @Test
  def testAsyncWithTimeout(): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)

    val source = env.fromElements(1)

    val timeout = 1L
    val asyncMapped = if (ordered) {
      AsyncDataStream.orderedWait(
        source,
        new AsyncFunctionWithTimeoutExpired(),
        timeout,
        TimeUnit.MILLISECONDS)
    } else {
      AsyncDataStream.unorderedWait(
        source,
        new AsyncFunctionWithTimeoutExpired(),
        timeout,
        TimeUnit.MILLISECONDS)
    }

    executeAndValidate(ordered, env, asyncMapped, mutable.ArrayBuffer[Int](3))
  }

  @Test
  def testAsyncWithoutTimeout(): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)

    val source = env.fromElements(1)

    val timeout = 1L
    val asyncMapped = if (ordered) {
      AsyncDataStream.orderedWait(
        source,
        new AsyncFunctionWithoutTimeoutExpired(),
        timeout,
        TimeUnit.MILLISECONDS)
    } else {
      AsyncDataStream.unorderedWait(
        source,
        new AsyncFunctionWithoutTimeoutExpired(),
        timeout,
        TimeUnit.MILLISECONDS)
    }

    executeAndValidate(ordered, env, asyncMapped, mutable.ArrayBuffer[Int](2))
  }

  private def executeAndValidate(
      ordered: Boolean,
      env: StreamExecutionEnvironment,
      dataStream: DataStream[Int],
      expectedResult: mutable.ArrayBuffer[Int]): Unit = {

    testResult = mutable.ArrayBuffer[Int]()
    dataStream.addSink(new SinkFunction[Int]() {
      override def invoke(value: Int) {
        testResult += value
      }
    })

    env.execute("testAsyncDataStream")

    if (ordered) {
      assertEquals(expectedResult, testResult)
    } else {
      assertEquals(expectedResult, testResult.sorted)
    }
  }

  @Test
  def testRichAsyncFunctionRuntimeContext(): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)

    val source = env.fromElements(1)

    val timeout = 10000L
    val richAsyncFunction = new MyRichAsyncFunction
    val asyncMapped = if (ordered) {
      AsyncDataStream
        .orderedWait(source, richAsyncFunction, timeout, TimeUnit.MILLISECONDS)
    } else {
      AsyncDataStream
        .unorderedWait(source, richAsyncFunction, timeout, TimeUnit.MILLISECONDS)
    }

    executeAndValidate(false, env, asyncMapped, mutable.ArrayBuffer[Int](2))
  }

  @Test
  def testAsyncWaitUsingAnonymousFunction(): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)

    val source = env.fromElements(1, 2)

    val asyncFunction: (Int, ResultFuture[Int]) => Unit =
      (input, collector: ResultFuture[Int]) =>
        Future {
          collector.complete(Seq(input * 2))
        }(ExecutionContext.global)

    val timeout = 10000L
    val asyncMapped = if (ordered) {
      AsyncDataStream.orderedWait(source, timeout, TimeUnit.MILLISECONDS) {
        asyncFunction
      }
    } else {
      AsyncDataStream.unorderedWait(source, timeout, TimeUnit.MILLISECONDS) {
        asyncFunction
      }
    }

    executeAndValidate(ordered, env, asyncMapped, mutable.ArrayBuffer[Int](2, 4))
  }

  @Test
  def testAsyncWaitWithRetry(): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)

    val source = env.fromElements(1, 2, 3, 4, 5, 6)

    val asyncFunction = new OddInputReturnEmptyAsyncFunc

    val asyncRetryStrategy =
      new AsyncRetryStrategies.FixedDelayRetryStrategyBuilder(3, 10)
        .ifResult(RetryPredicates.EMPTY_RESULT_PREDICATE[Int])
        .ifException(RetryPredicates.HAS_EXCEPTION_PREDICATE)
        .build()

    val timeout = 10000L
    val asyncMapped = if (ordered) {
      AsyncDataStream.orderedWaitWithRetry(
        source,
        asyncFunction,
        timeout,
        TimeUnit.MILLISECONDS,
        asyncRetryStrategy)
    } else {
      AsyncDataStream.unorderedWaitWithRetry(
        source,
        asyncFunction,
        timeout,
        TimeUnit.MILLISECONDS,
        asyncRetryStrategy)
    }

    executeAndValidate(ordered, env, asyncMapped, mutable.ArrayBuffer[Int](2, 4, 6))
  }

  @Test
  def testAsyncWaitWithRetryUsingAnonymousFunction(): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)

    val source = env.fromElements(1, 2, 3, 4, 5, 6)

    val asyncFunction: (Int, ResultFuture[Int]) => Unit =
      (input, collector: ResultFuture[Int]) => {
        Thread.sleep(3)
        if (input % 2 == 1) {
          Future {
            collector.complete(List[Int]())
          }(ExecutionContext.global)
        } else {
          Future {
            collector.complete(List[Int](input))
          }(ExecutionContext.global)
        }
      }

    val timeout = 10000L
    val asyncRetryStrategy = new AsyncRetryStrategies.FixedDelayRetryStrategyBuilder[Int](3, 10)
      .build()

    val asyncMapped = if (ordered) {
      AsyncDataStream.orderedWaitWithRetry(
        source,
        timeout,
        TimeUnit.MILLISECONDS,
        asyncRetryStrategy) {
        asyncFunction
      }
    } else {
      AsyncDataStream.unorderedWaitWithRetry(
        source,
        timeout,
        TimeUnit.MILLISECONDS,
        asyncRetryStrategy) {
        asyncFunction
      }
    }

    executeAndValidate(ordered, env, asyncMapped, mutable.ArrayBuffer[Int](2, 4, 6))
  }

}

class AsyncFunctionWithTimeoutExpired extends RichAsyncFunction[Int, Int] {
  @transient var invokeLatch: CountDownLatch = _

  override def open(openContext: OpenContext): Unit = {
    invokeLatch = new CountDownLatch(1)
  }

  override def asyncInvoke(input: Int, resultFuture: ResultFuture[Int]): Unit = {
    Future {
      invokeLatch.await()
      resultFuture.complete(Seq(input * 2))
    }(ExecutionContext.global)
  }

  override def timeout(input: Int, resultFuture: ResultFuture[Int]): Unit = {
    resultFuture.complete(Seq(input * 3))
    invokeLatch.countDown()
  }
}

/**
 * The asyncInvoke and timeout might be invoked at the same time. The target is checking whether
 * there is a race condition or not between asyncInvoke, timeout and timer cancellation. See
 * https://issues.apache.org/jira/browse/FLINK-13605 for more details.
 */
class AsyncFunctionWithoutTimeoutExpired extends RichAsyncFunction[Int, Int] {
  @transient var timeoutLatch: CountDownLatch = _

  override def open(openContext: OpenContext): Unit = {
    timeoutLatch = new CountDownLatch(1)
  }

  override def asyncInvoke(input: Int, resultFuture: ResultFuture[Int]): Unit = {
    Future {
      resultFuture.complete(Seq(input * 2))
      timeoutLatch.countDown()
    }(ExecutionContext.global)
  }

  override def timeout(input: Int, resultFuture: ResultFuture[Int]): Unit = {
    // this sleeping helps reproducing race condition with cancellation
    Thread.sleep(10)
    timeoutLatch.await()
    resultFuture.complete(Seq(input * 3))
  }
}

class MyRichAsyncFunction extends RichAsyncFunction[Int, Int] {

  override def open(openContext: OpenContext): Unit = {
    assertEquals(getRuntimeContext.getTaskInfo.getNumberOfParallelSubtasks, 1)
  }

  override def asyncInvoke(input: Int, resultFuture: ResultFuture[Int]): Unit = {
    Future {
      resultFuture.complete(Seq(input * 2))
    }(ExecutionContext.global)
  }

  override def timeout(input: Int, resultFuture: ResultFuture[Int]): Unit = {
    resultFuture.complete(Seq(input * 3))
  }
}

class OddInputReturnEmptyAsyncFunc extends RichAsyncFunction[Int, Int] {

  override def asyncInvoke(input: Int, resultFuture: ResultFuture[Int]): Unit = {
    Thread.sleep(3)
    if (input % 2 == 1) {
      Future {
        resultFuture.complete(List[Int]())
      }(ExecutionContext.global)
    } else {
      Future {
        resultFuture.complete(List[Int](input))
      }(ExecutionContext.global)
    }
  }
}
