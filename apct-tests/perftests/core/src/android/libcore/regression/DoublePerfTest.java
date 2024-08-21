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
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class DoublePerfTest {
    @Rule public BenchmarkRule mBenchmarkRule = new BenchmarkRule();

    private double mD = 1.2;
    private long mL = 4608083138725491507L;

    @Test
    public void timeDoubleToLongBits() {
        long result = 123;
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            result = Double.doubleToLongBits(mD);
        }
        if (result != mL) {
            throw new RuntimeException(Long.toString(result));
        }
    }

    @Test
    public void timeDoubleToRawLongBits() {
        long result = 123;
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            result = Double.doubleToRawLongBits(mD);
        }
        if (result != mL) {
            throw new RuntimeException(Long.toString(result));
        }
    }

    @Test
    public void timeLongBitsToDouble() {
        double result = 123.0;
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            result = Double.longBitsToDouble(mL);
        }
        if (result != mD) {
            throw new RuntimeException(Double.toString(result) + " "
                    + Double.doubleToRawLongBits(result));
        }
    }
}
