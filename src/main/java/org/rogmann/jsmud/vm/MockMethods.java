package org.rogmann.jsmud.vm;

import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

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

    /**
     * Execute an action as in java.security.AccessControlContext but without checks.
     * @param <T> return-type
     * @param action action to be executed
     * @return return-value
     * @throws PrivilegedActionException in case of an exception
     */
    public static <T> T doPrivileged(PrivilegedExceptionAction<T> action) throws PrivilegedActionException {
    	try {
			return action.run();
    	}
    	catch (RuntimeException e) {
    		throw e;
		}
    	catch (Exception e) {
			throw new PrivilegedActionException(e);
		}
    }

}
