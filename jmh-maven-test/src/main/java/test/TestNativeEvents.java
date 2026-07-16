package test;

import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;
import jdk.internal.misc.Unsafe;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.lang.reflect.*;

public class TestNativeEvents {
    private static final Unsafe UNSAFE;
    
    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== Testing JFR Native Memory Events ===\n");
        
        // List available event types
        System.out.println("1. Available event types containing 'Native' or 'Memory':");
        List<EventType> nativeTypes = new ArrayList<>();
        for (EventType type : EventType.getEventTypes()) {
            String name = type.getName();
            if (name.contains("Native") || name.contains("Memory")) {
                nativeTypes.add(type);
                System.out.println("   - " + name);
            }
        }
        System.out.println("   Total: " + nativeTypes.size() + " events\n");
        
        // Test if JavaNativeAllocationEvent exists
        System.out.println("2. Checking for specific event classes:");
        String[] eventClasses = {
            "jdk.internal.event.JavaNativeAllocationEvent",
            "jdk.internal.event.JavaNativeFreeEvent", 
            "jdk.internal.event.JavaNativeReallocateEvent"
        };
        for (String className : eventClasses) {
            try {
                Class<?> cls = Class.forName(className);
                System.out.println("   " + className + " - FOUND");
            } catch (ClassNotFoundException e) {
                System.out.println("   " + className + " - NOT FOUND");
            }
        }
        System.out.println();
        
        // Start a recording
        System.out.println("3. Starting JFR recording...");
        Recording r = new Recording();
        File outputFile = new File("/tmp/test_recording.jfr");
        if (outputFile.exists()) outputFile.delete();
        r.start();
        System.out.println("   Recording started\n");
        
        // Allocate memory with events enabled
        System.out.println("4. Allocating native memory with events enabled...");
        System.setProperty("jdk.jfr.event.NativeMemoryAllocation", "true");
        
        long addr = 0;
        int size = 1024;
        
        // Try to trigger allocation event
        for (int i = 0; i < 1000; i++) {
            addr = UNSAFE.allocateMemory(size);
            if (addr != 0) {
                UNSAFE.setMemory(addr, size, (byte) 0);
            }
        }
        System.out.println("   Allocated 1000 blocks of " + size + " bytes each");
        System.out.println("   First address: 0x" + Long.toHexString(addr) + "\n");
        
        // Wait for events
        System.out.println("5. Waiting for events to be recorded...");
        Thread.sleep(2000);
        
        // Stop recording
        System.out.println("6. Stopping recording...");
        r.stop();
        r.dump(outputFile);
        System.out.println("   Recording saved to: " + outputFile.getAbsolutePath());
        System.out.println("   File size: " + outputFile.length() + " bytes\n");
        
        // Parse the recording
        System.out.println("7. Analyzing recording...");
        System.out.println("   (Use jfr print to view events: jfr print --events 'jdk.JavaNative*' " + outputFile + ")");
        
        System.out.println("\n=== Test Complete ===");
    }
}
