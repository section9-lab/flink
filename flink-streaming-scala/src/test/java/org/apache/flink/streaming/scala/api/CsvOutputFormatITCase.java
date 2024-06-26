/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.streaming.scala.api;

import org.apache.flink.core.fs.FileSystem;
import org.apache.flink.streaming.api.scala.OutputFormatTestPrograms;
import org.apache.flink.test.testdata.WordCountData;
import org.apache.flink.test.util.AbstractTestBaseJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.apache.flink.test.util.TestBaseUtils.compareResultsByLinesInMemory;
import static org.apache.flink.util.ExceptionUtils.findThrowableWithMessage;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** IT cases for the {@link org.apache.flink.api.java.io.CsvOutputFormat}. */
public class CsvOutputFormatITCase extends AbstractTestBaseJUnit4 {

    protected String resultPath;

    @Before
    public void createFile() throws Exception {
        File resultFile = createAndRegisterTempFile("result");
        resultPath = resultFile.toURI().toString();
    }

    @Test
    public void testPath() throws Exception {
        OutputFormatTestPrograms.wordCountToCsv(WordCountData.TEXT, resultPath);
    }

    @Test
    public void testPathMillis() throws Exception {
        OutputFormatTestPrograms.wordCountToCsv(WordCountData.TEXT, resultPath);
    }

    @Test
    public void testPathWriteMode() throws Exception {
        OutputFormatTestPrograms.wordCountToCsv(
                WordCountData.TEXT, resultPath, FileSystem.WriteMode.NO_OVERWRITE);
    }

    @Test
    public void testPathWriteModeMillis() throws Exception {
        OutputFormatTestPrograms.wordCountToCsv(
                WordCountData.TEXT, resultPath, FileSystem.WriteMode.NO_OVERWRITE);
    }

    @Test
    public void testPathWriteModeMillisDelimiter() throws Exception {
        OutputFormatTestPrograms.wordCountToCsv(
                WordCountData.TEXT, resultPath, FileSystem.WriteMode.NO_OVERWRITE, "\n", ",");
    }

    @Test
    public void failPathWriteMode() throws Exception {
        OutputFormatTestPrograms.wordCountToCsv(WordCountData.TEXT, resultPath);
        try {
            OutputFormatTestPrograms.wordCountToCsv(
                    WordCountData.TEXT, resultPath, FileSystem.WriteMode.NO_OVERWRITE);
            fail("File should exist.");
        } catch (Exception e) {
            assertTrue(findThrowableWithMessage(e, "File already exists").isPresent());
        }
    }

    @Test
    public void failPathWriteModeMillis() throws Exception {
        OutputFormatTestPrograms.wordCountToCsv(WordCountData.TEXT, resultPath);
        try {
            OutputFormatTestPrograms.wordCountToCsv(
                    WordCountData.TEXT, resultPath, FileSystem.WriteMode.NO_OVERWRITE);
            fail("File should exist");
        } catch (Exception e) {
            assertTrue(findThrowableWithMessage(e, "File already exists").isPresent());
        }
    }

    @Test
    public void failPathWriteModeMillisDelimiter() throws Exception {
        OutputFormatTestPrograms.wordCountToCsv(WordCountData.TEXT, resultPath);
        try {
            OutputFormatTestPrograms.wordCountToCsv(
                    WordCountData.TEXT, resultPath, FileSystem.WriteMode.NO_OVERWRITE, "\n", ",");
            fail("File should exist.");
        } catch (Exception e) {
            assertTrue(findThrowableWithMessage(e, "File already exists").isPresent());
        }
    }

    @After
    public void closeFile() throws Exception {
        compareResultsByLinesInMemory(
                WordCountData.STREAMING_COUNTS_AS_TUPLES.replaceAll("[\\\\(\\\\)]", ""),
                resultPath);
    }
}
