/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1105 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.openjdk.bench.sun.misc;

import java.lang.reflect.Field;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import jdk.jfr.Recording;

import jdk.internal.misc.Unsafe;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Measures the performance impact of emitting JFR events for native memory
 * operations on {@code jdk.internal.misc.Unsafe}. The benchmark runs both
 * with JFR disabled (the default state) and with a {@link Recording} that
 * has the three native-memory events enabled, so the cost of the event
 * emission can be quantified.
 *
 * <p>Boundary cases tested:
 * <ul>
 *   <li>{@code allocateMemory(0)} - zero-size allocation</li>
 *   <li>{@code freeMemory(0)} - free null address</li>
 *   <li>{@code reallocateMemory(0, N)} - reallocate from null</li>
 *   <li>{@code reallocateMemory(addr, 0)} - reallocate to zero size</li>
 *   <li>{@code reallocateMemory(0, 0)} - reallocate from null to zero</li>
 * </ul>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(value = 3, jvmArgs = {"-Xms1g", "-Xmx1g", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"})
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@SuppressWarnings("removal")
public class UnsafeMemoryEvents {

    @Param({"0", "64", "1024"})
    public int size;

    /**
     * If {@code true}, a {@link Recording} is started that enables the three
     * Java native memory events. If {@code false}, no recording is active,
     * which measures the JFR-disabled (zero overhead) baseline.
     */
    @Param({"false", "true"})
    public boolean eventsEnabled;

    private static final Unsafe U;
    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            U = (Unsafe) f.get(null);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot access Unsafe", ex);
        }
    }

    private Recording recording;
    private Path recordingFile;
    private long lastAllocAddr;

    @Setup(Level.Trial)
    public void startRecording() throws IOException {
        if (eventsEnabled) {
            recording = new Recording();
            recording.enable("jdk.JavaNativeAllocation");
            recording.enable("jdk.JavaNativeFree");
            recording.enable("jdk.JavaNativeReallocate");
            recordingFile = Files.createTempFile("jfr-recording", ".jfr");
            recording.setToDisk(true);
            recording.start();
        }
    }

    @TearDown(Level.Trial)
    public void stopRecording() throws Exception {
        if (recording != null) {
            recording.stop();
            recording.close();
        }
        if (recordingFile != null) {
            Files.deleteIfExists(recordingFile);
        }
    }

    @Benchmark
    public long allocateAndFreeMemory() {
        if (size > 0) {
            long addr = U.allocateMemory(size);
            lastAllocAddr = addr;
            U.freeMemory(addr);
            return addr;
        } else {
            U.freeMemory(0);
            return 0;
        }
    }

    @Benchmark
    public long allocateMemory() {
        long addr = U.allocateMemory(size);
        lastAllocAddr = addr;
        return addr;
    }

    @Benchmark
    public void freeMemory() {
        if (size > 0) {
            long addr = U.allocateMemory(size);
            U.freeMemory(addr);
        } else {
            U.freeMemory(0);
        }
    }

    @Benchmark
    public long reallocateMemory() {
        if (size > 0) {
            long addr = U.allocateMemory(size);
            long reallocAddr = U.reallocateMemory(addr, size);
            lastAllocAddr = reallocAddr;
            U.freeMemory(reallocAddr);
            return reallocAddr;
        } else {
            long addr = U.allocateMemory(64);
            long reallocAddr = U.reallocateMemory(addr, 0);
            lastAllocAddr = reallocAddr;
            return reallocAddr;
        }
    }

    @Benchmark
    public long reallocateFromNull() {
        long reallocAddr = U.reallocateMemory(0, size);
        lastAllocAddr = reallocAddr;
        if (reallocAddr != 0) {
            U.freeMemory(reallocAddr);
        }
        return reallocAddr;
    }

    @Benchmark
    public long reallocateToZero() {
        long addr = U.allocateMemory(64);
        long reallocAddr = U.reallocateMemory(addr, 0);
        lastAllocAddr = reallocAddr;
        return reallocAddr;
    }

    @Benchmark
    public long reallocateNullToZero() {
        long reallocAddr = U.reallocateMemory(0, 0);
        lastAllocAddr = reallocAddr;
        return reallocAddr;
    }
}
