package org.rogmann.jsmud;

import java.security.PrivilegedAction;

/**
 * Dummy-implementation of some JVM-methods.
 */
public class MockMethods {

    /**
     * Don't allow anyone to instantiate a MockMethods
     */
    private MockMethods() { }

    /**
     * Execute an action as in java.security.AccessControlContext but without checks.
     * @param <T> return-type
     * @param action action to be executed
     * @return return-value
     */
    public static <T> T doPrivileged(PrivilegedAction<T> action) {
    	return action.run();
    }

}
