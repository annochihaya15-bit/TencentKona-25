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
package jdk.jfr.event.runtime;

import static jdk.test.lib.Asserts.assertEquals;
import static jdk.test.lib.Asserts.assertTrue;

import java.util.ArrayList;
import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @run main/othervm jdk.jfr.event.runtime.TestNativeMemEvent
 */
public class TestNativeMemEvent {

    private static final String ALLOC_EVENT_NAME = EventNames.JavaNativeAllocation;
    private static final String FREE_EVENT_NAME = EventNames.JavaNativeFree;
    private static final String REALLOC_EVENT_NAME = EventNames.JavaNativeReallocate;

    private static final int INITIAL_SIZE = 100;
    private static final int RESIZED_SIZE = 1000;

    public static void main(String[] args) throws Throwable {
        long[] addresses = new long[2];

        try (Recording recording = new Recording()) {
            recording.enable(ALLOC_EVENT_NAME);
            recording.enable(FREE_EVENT_NAME);
            recording.enable(REALLOC_EVENT_NAME);
            recording.start();

            addresses = unsafeMemTest();

            recording.stop();

            List<RecordedEvent> events = Events.fromRecording(recording);
            Events.hasEvents(events);

            List<RecordedEvent> allocEvents = new ArrayList<>();
            List<RecordedEvent> freeEvents = new ArrayList<>();
            List<RecordedEvent> reallocEvents = new ArrayList<>();

            for (RecordedEvent event : events) {
                String name = event.getEventType().getName();
                if (ALLOC_EVENT_NAME.equals(name)) {
                    allocEvents.add(event);
                } else if (FREE_EVENT_NAME.equals(name)) {
                    freeEvents.add(event);
                } else if (REALLOC_EVENT_NAME.equals(name)) {
                    reallocEvents.add(event);
                }
            }

            assertTrue(allocEvents.size() >= 1,
                    "Expected at least one " + ALLOC_EVENT_NAME + " event, got " + allocEvents.size());
            assertTrue(reallocEvents.size() >= 1,
                    "Expected at least one " + REALLOC_EVENT_NAME + " event, got " + reallocEvents.size());
            assertTrue(freeEvents.size() >= 1,
                    "Expected at least one " + FREE_EVENT_NAME + " event, got " + freeEvents.size());

            for (RecordedEvent event : allocEvents) {
                long addr = Events.assertField(event, "addr").atLeast(1L).getValue();
                long allocationSize = Events.assertField(event, "allocationSize").atLeast((long) INITIAL_SIZE).getValue();
                assertEquals(addr, addresses[0]);
                assertTrue(allocationSize >= INITIAL_SIZE,
                        "allocationSize " + allocationSize + " < INITIAL_SIZE " + INITIAL_SIZE);
            }

            for (RecordedEvent event : reallocEvents) {
                long freeAddr = Events.assertField(event, "freeAddr").atLeast(1L).getValue();
                long allocAddr = Events.assertField(event, "allocAddr").atLeast(1L).getValue();
                long allocationSize = Events.assertField(event, "allocationSize").atLeast((long) RESIZED_SIZE).getValue();
                assertEquals(freeAddr, addresses[0]);
                assertEquals(allocAddr, addresses[1]);
                assertTrue(allocationSize >= RESIZED_SIZE,
                        "allocationSize " + allocationSize + " < RESIZED_SIZE " + RESIZED_SIZE);
            }

            for (RecordedEvent event : freeEvents) {
                long addr = Events.assertField(event, "addr").atLeast(1L).getValue();
                assertEquals(addr, addresses[1]);
            }
        }
    }

    @SuppressWarnings("removal")
    public static long[] unsafeMemTest() {
        long[] addrs = new long[2];
        jdk.internal.misc.Unsafe unsafe = jdk.internal.misc.Unsafe.getUnsafe();
        long addr = unsafe.allocateMemory(INITIAL_SIZE);
        addrs[0] = addr;
        long reallocAddr = unsafe.reallocateMemory(addr, RESIZED_SIZE);
        addrs[1] = reallocAddr;
        unsafe.freeMemory(reallocAddr);
        return addrs;
    }
}
