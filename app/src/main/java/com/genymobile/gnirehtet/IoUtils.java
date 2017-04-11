package com.genymobile.gnirehtet;

import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class IoUtils {

    private IoUtils() {
        // not instantiable
    }

    /**
     * Set {@code fd} to be blocking or non-blocking, according to the state of {@code blocking}.
     *
     * @param fd       the file descriptor
     * @param blocking the target blocking mode
     * @throws IOException if an I/O problem occurs
     */
    public static void setBlocking(FileDescriptor fd, boolean blocking) throws IOException {
        // calls libcore.io.IoUtils.setBlocking(FileDescriptor, boolean) using reflection
        // <https://android.googlesource.com/platform/libcore/+/30c669166d86d0bd133edfb67909665fb41d29b6/luni/src/main/java/libcore/io/IoUtils.java#89>
        try {
            Class<?> cls = Class.forName("libcore.io.IoUtils");
            Method setBlocking = cls.getDeclaredMethod("setBlocking", FileDescriptor.class, boolean.class);
            setBlocking.invoke(null, fd, blocking);
            // cannot multi-catch on API < 19
        } catch (ClassNotFoundException e) {
            throw new UnsupportedOperationException("Cannot call libcore.io.IoUtils.setBlocking()", e);
        } catch (NoSuchMethodException e) {
            throw new UnsupportedOperationException("Cannot call libcore.io.IoUtils.setBlocking()", e);
        } catch (IllegalAccessException e) {
            throw new UnsupportedOperationException("Cannot call libcore.io.IoUtils.setBlocking()", e);
        } catch (InvocationTargetException e) {
            throw new UnsupportedOperationException("Cannot call libcore.io.IoUtils.setBlocking()", e);
        }
    }
}
