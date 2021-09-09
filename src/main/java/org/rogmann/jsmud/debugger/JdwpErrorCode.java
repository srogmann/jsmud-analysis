package org.rogmann.jsmud.debugger;

/**
 * jwdp-error-code.
 */
public enum JdwpErrorCode {

	/** No error has occurred. */
	NONE(0),
	/** Passed thread is null, is not a valid thread or has exited. */
	INVALID_THREAD(10),
	/** Thread group invalid. */
	INVALID_THREAD_GROUP(11),
	/** If the specified thread has not been suspended by an event. */
	THREAD_NOT_SUSPENDED(13),
	/** Thread already suspended. */
	THREAD_SUSPENDED(14),
	/** If this reference type has been unloaded and garbage collected. */
	INVALID_OBJECT(20),
	/** Invalid class. */
	INVALID_CLASS(21),
	/** Invalid method. */
	INVALID_METHODID(23),
	/** Invalid field. */
	INVALID_FIELDID(25),
	/** Invalid jFrameID. */
	INVALID_FRAMEID(30),
	/** Invalid slot. */
	INVALID_SLOT(35),
	/** The functionality is not implemented in this virtual machine. */
	NOT_IMPLEMENTED(99),
	/** The specified event type id is not recognized. */
	INVALID_EVENT_TYPE(102),
	/** Illegal Argument. */
	ILLEGAL_ARGUMENT(103),
	/** The virtual machine is not running. */
	VM_DEAD(112),
	/** Invalid Length. */
	INVALID_LENGTH(504),
	/** Invalid String. */
	INVALID_STRING(506),
	/** Invalid Array. */
	INVALID_ARRAY(508);
	
	/** error-code */
	private final short errorCode;
	
	/**
	 * Constructor
	 * @param errorCode error-code
	 */
	private JdwpErrorCode(final int errorCode) {
		this.errorCode = (short) errorCode;
	}
	
	/**
	 * Gets the error-code.
	 * @return error-code
	 */
	public short getErrorCode() {
		return errorCode;
	}
}
