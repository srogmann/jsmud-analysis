package org.rogmann.jsmud.debugger;

import org.rogmann.jsmud.vm.JvmException;

/**
 * This exception is thrown when a clinit-method (static initializer)
 * should be invoke while processing debugger-packages.
 * 
 * <p>Example: Invoking of $$EnhancerByCGLIB$$3d7dcd21.hashCode triggers a clinit-method.</p>
 */
public class JvmClinitWhileDebuggingException extends JvmException {

	/** serialization-id */
	private static final long serialVersionUID = 20220203L;

	/**
	 * Constructor
	 * @param message message-text
	 */
	public JvmClinitWhileDebuggingException(final String message) {
		super(message);
	}

}
