package org.rogmann.jsmud.vm;

import java.util.Arrays;

import org.rogmann.jsmud.log.Logger;
import org.rogmann.jsmud.log.LoggerFactory;

/**
 * Operand stack.
 * The maximum size can be increased via pushInto, a hack to store INVOKEDYNAMIC-arguments. 
 */
public class OperandStack {
	/** logger */
	private static final Logger LOG = LoggerFactory.getLogger(JvmInvocationHandlerReflection.class);

	/** maximum length of a displayed value */
	private static final int MAX_LEN_VALUE = Integer.getInteger(OperandStack.class.getName() + ".maxLenValue", 250).intValue();

	/** <code>true</code> if the whole stack (including unused values) should be displayed (useful in case of debugging the simulator itself) */
	private static final boolean SHOW_FULL_STACK = Boolean.getBoolean(OperandStack.class.getName() + ".showFullStack");

	private Object[] stack;
	
	/** size of resize because of pushInto */
	private int resize;
	
	/** index in stack */
	private int idx;

	/**
	 * Constructor
	 * @param maxSize maximum size
	 */
	public OperandStack(final int maxSize) {
		stack = new Object[maxSize];
		idx = -1;
	}
	
	/**
	 * Gets and removes the element on top.
	 * @return element
	 */
	public Object pop() {
		assert idx >= 0 : "pop at empty stack";
		return stack[idx--];
	}

	/**
	 * Gets and removes the element at given index from top.
	 * @param index (0 = top, 1 = element after top, ...)
	 * @return element
	 */
	public Object pop(final int index) {
		assert idx >= index : "pop(" + index + ") at too small stack";
		final Object oStack = stack[idx - index];
		System.arraycopy(stack, idx - index + 1, stack, idx - index, index);
		idx--;
		return oStack;
	}

	/**
	 * Puts an element on top.
	 * @param o element
	 */
	public void push(final Object o) {
		assert idx < stack.length - 1 : "push at full stack";
		try {
			stack[++idx] = o;
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new RuntimeException("push-error, " + toString(), e);
		}
	}

	/**
	 * Pushes an array of elements into the stack and resizes.
	 * @param offset offset in the stack (0 = top)
	 * @param objects elements to be inserted
	 */
	public void pushAndResize(int offset, Object[] objects) {
		final int objLen = objects.length;
		if (objLen > resize) {
			// We have to resize the stack.
			final int newLength = stack.length + objLen - resize;
			if (LOG.isDebugEnabled()) {
				LOG.debug(String.format("Resize stack: %d -> %d",
						Integer.valueOf(stack.length), Integer.valueOf(newLength)));
			}
			stack = Arrays.copyOfRange(stack, 0, newLength);
			resize = objLen;
		}
		int stackOffset = idx + 1 - offset;
		if (offset > 0) {
			System.arraycopy(stack, stackOffset, stack, stackOffset + objLen, offset);
		}
		System.arraycopy(objects, 0, stack, stackOffset, objLen);
		idx += objLen;
	}

	/**
	 * Peeks at the element on top.
	 * @return element
	 */
	public Object peek() {
		assert idx >= 0 : "peek at empty stack";
		assert idx < stack.length : "stack-index too large";
		return stack[idx];
	}
	
	/**
	 * Peeks at an element from top with index.
	 * @param index 0 = element on top, 1 = next element, ...
	 * @return element
	 */
	public Object peek(final int index) {
		assert idx - index >= 0 : "stack-index minus index " + index + " too low";
		assert idx - index < stack.length : "stack-index minus index " + index + " too large";
		return stack[idx - index];
	}

	/**
	 * Clears the stack.
	 */
	public void clear() {
		idx = -1;
	}

	/**
	 * Replaces an element from top with index.
	 * @param index 0 = element on top, 1 = next element, ...
	 * @param obj object to be set at the given index
	 */
	public void peek(final int index, final Object obj) {
		assert idx - index >= 0 : "stack-index minus index " + index + " too low";
		assert idx - index < stack.length : "stack-index minus index " + index + " too large";
		stack[idx - index] = obj;
	}

	/**
	 * Replace uninitialized classes with the new initialized instance.
	 * @param uninstType uninitialized instance
	 * @param instanceInit initialized instance
	 */
	public void replaceUninitialized(UninitializedInstance uninstType, Object instanceInit) {
		final long id = uninstType.getId();
		for (int i = 0; i <= idx; i++) {
			final Object obj = stack[i];
			if (obj instanceof UninitializedInstance) {
				final UninitializedInstance loopUI = (UninitializedInstance) obj;
				if (loopUI.getId() == id) {
					stack[i] = instanceInit;
				}
			}
		}
	}

	/**
	 * Removes n elements from stack
	 * @param n number of elements
	 */
	public void remove(int n) {
		assert idx - n >= -1 : "can not remove " + n + " of " + (idx + 1);
		idx -= n;
	}

	/**
	 * Gets the number of elements on the stack.
	 * @return size
	 */
	public int size() {
		return idx + 1;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		if (idx + 1 > stack.length) {
			throw new IllegalStateException(String.format("stack invalid: length=%d, idx=%d",
					Integer.valueOf(stack.length), Integer.valueOf(idx)));
		}
		final StringBuilder sbTypes = new StringBuilder(20);
		final int lenTypes = SHOW_FULL_STACK ? stack.length : idx + 1;
		for (int i = 0; i < lenTypes; i++) {
			if (i > 0) {
				sbTypes.append(", ");
			}
			final Object so = stack[i];
			if (so == null) {
				sbTypes.append(so);
			}
			else {
				final String simpleName = so.getClass().getSimpleName();
				if (simpleName.length() > 0) {
					sbTypes.append(simpleName);
				}
				else {
					// e.g. class net.sf.cglib.core.internal.LoadingCache$2
					sbTypes.append(so.getClass());
				}
			}
		}
		return String.format("stack: currLen=%d, maxLen=%d, types=[%s], values=%s",
				Integer.valueOf(idx + 1), Integer.valueOf(stack.length),
				sbTypes, toString(stack, idx)
		);
	}

	/**
	 * Dumps an array of objects.
	 * Long values are shortened.
	 * Values after the last used index are shortened.
	 * Backslash, linefeed and carriage-return are escaped.
	 * @param aObjects array
	 * @param lastIdx last used index (-1 if none is used)
	 * @return display-string
	 */
	public static Object toString(Object[] aObjects, final int lastIdx) {
		final StringBuilder sb = new StringBuilder(50);
		sb.append('[');
		final int len = aObjects.length;
		final int maxLenUnusedValue = SHOW_FULL_STACK ? MAX_LEN_VALUE : 3;
		final int lenShown = SHOW_FULL_STACK ? len : lastIdx + 1;
		for (int i = 0; i < lenShown; i++) {
			final Object object = aObjects[i];
			if (i > 0) {
				sb.append(", ");
			}
			try {
				final String sObject = (object != null) ? object.toString() : "null";
				final int maxLen = (i <= lastIdx) ? MAX_LEN_VALUE : maxLenUnusedValue;
				for (int j = 0; j < Math.min(sObject.length(), maxLen); j++) {
					final char c = sObject.charAt(j);
					if (c == '\\') {
						sb.append("\\\\");
					}
					else if (c == '\r') {
						sb.append("\\r");
					}
					else if (c == '\n') {
						sb.append("\\n");
					}
					else {
						sb.append(c);
					}
				}
				if (sObject.length() > maxLen) {
					sb.append("...");
				}
			} catch (Exception e) {
				sb.append(String.format("%s(0x%x)[%s]", (object != null) ? object.getClass() : null,
						Integer.valueOf(System.identityHashCode(object)), e.toString()));
			}
		}
		sb.append(']');
		return sb.toString();
	}

}
