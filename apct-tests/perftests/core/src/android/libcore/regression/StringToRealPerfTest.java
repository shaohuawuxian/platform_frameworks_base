/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.libcore.regression;

import androidx.benchmark.BenchmarkState;
import androidx.benchmark.junit4.BenchmarkRule;

import androidx.test.filters.LargeTest;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collection;

@RunWith(JUnitParamsRunner.class)
@LargeTest
public class StringToRealPerfTest {
    @Rule public BenchmarkRule mBenchmarkRule = new BenchmarkRule();

    public static Collection<Object[]> getData() {
        return Arrays.asList(
                new Object[][] {
                    {"NaN"},
                    {"-1"},
                    {"0"},
                    {"1"},
                    {"1.2"},
                    {"-123.45"},
                    {"-123.45e8"},
                    {"-123.45e36"}
                });
    }

    @Test
    @Parameters(method = "getData")
    public void timeFloat_parseFloat(String string) {
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            Float.parseFloat(string);
        }
    }

    @Test
    @Parameters(method = "getData")
    public void timeDouble_parseDouble(String string) {
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            Double.parseDouble(string);
        }
    }
}
