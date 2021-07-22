package org.rogmann.jsmud;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.List;

import org.rogmann.jsmud.datatypes.VMArrayRegion;
import org.rogmann.jsmud.datatypes.VMClassID;
import org.rogmann.jsmud.datatypes.VMClassLoaderID;
import org.rogmann.jsmud.datatypes.VMDataField;
import org.rogmann.jsmud.datatypes.VMFieldID;
import org.rogmann.jsmud.datatypes.VMInterfaceID;
import org.rogmann.jsmud.datatypes.VMMethodID;
import org.rogmann.jsmud.datatypes.VMObjectID;
import org.rogmann.jsmud.datatypes.VMReferenceTypeID;
import org.rogmann.jsmud.datatypes.VMStringID;
import org.rogmann.jsmud.datatypes.VMTaggedObjectId;
import org.rogmann.jsmud.datatypes.VMThreadGroupID;
import org.rogmann.jsmud.datatypes.VMThreadID;
import org.rogmann.jsmud.datatypes.VMValue;
import org.rogmann.jsmud.debugger.SlotRequest;
import org.rogmann.jsmud.debugger.SlotValue;
import org.rogmann.jsmud.replydata.LineCodeIndex;
import org.rogmann.jsmud.replydata.RefFieldBean;
import org.rogmann.jsmud.replydata.RefFrameBean;
import org.rogmann.jsmud.replydata.RefMethodBean;
import org.rogmann.jsmud.replydata.RefTypeBean;
import org.rogmann.jsmud.replydata.VariableSlot;

/**
 * Interface of a VM.
 */
public interface VM {

	/**
	 * Creates a string in the JVM.
	 * @param utf8 String 
	 * @return String-Id
	 */
	VMStringID createString(String utf8);

	/**
	 * Gets the loaded classes by signature
	 * @param signature signature
	 * @return list of classes
	 */
	List<RefTypeBean> getClassesBySignature(final String signature);

	/**
	 * Gets reference-types for all classes currently loaded by the VM.
	 * @return list of all classes
	 */
	List<RefTypeBean> getAllClassesWithGeneric();

	/**
	 * Gets the ref-type-bean of a class.
	 * @param objClass class
	 * @return ref-type-bean
	 */
	RefTypeBean getClassRefTypeBean(Class<? extends Object> objClass);

	/**
	 * Gets the Thread-Id of the current thread.
	 * @return current thread-id
	 */
	VMThreadID getCurrentThreadId();
	
	/**
	 * Gets the thread-group-id of a thread
	 * @param thread thread
	 * @return thread-group-id
	 */
	VMThreadGroupID getCurrentThreadGroupId(Thread thread);

	/**
	 * Gets the class-loader of a class.
	 * @param classRef class
	 * @return class-loader-id
	 */
	VMClassLoaderID getClassLoader(Class<?> classRef);

	/**
	 * Gets the id of the super-class of a class.
	 * @param classId class-id
	 * @return id of super-class
	 */
	VMClassID getSuperClass(VMClassID classId);

	/**
	 * Gets a line-code-table of a method.
	 * @param clazz class of method
	 * @param method method
	 * @param refType ref-id of class
	 * @param methodID id of method
	 * @return line-code-table
	 */
	List<LineCodeIndex> getLineTable(Class<?> clazz, Executable method, VMReferenceTypeID refType, VMMethodID methodID);

	/**
	 * Gets a list of interfaces of the given class.
	 * @param classRef given class
	 * @return list of interface-ids
	 */
	List<VMInterfaceID> getClassInterfaces(Class<?> classRef);

	/**
	 * Gets a list of fields of the given class.
	 * @param clazz given class
	 * @return list of field-ref-beans
	 */
	List<RefFieldBean> getFieldsWithGeneric(Class<?> classRef);

	/**
	 * Gets a field-ref-bean of a given field.
	 * @param fieldID ref-id of field
	 * @return field-ref-bean or <code>null</code>
	 */
	RefFieldBean getRefFieldBean(VMFieldID fieldID);

	/**
	 * Gets a list of methods of the given class.
	 * @param clazz given class
	 * @return list of method-ref-beans
	 */
	List<RefMethodBean> getMethodsWithGeneric(final Class<?> clazz);

	/**
	 * Gets the values of fields of an object.
	 * In case of an error the size of the list of values is lesser than the number of field-ids 
	 * @param vmObject object
	 * @param listFields field-ids
	 * @return list of values
	 */
	List<VMValue> readObjectFieldValues(Object vmObject, List<VMFieldID> listFields);

	/**
	 * Sets the values of fields of an object.
	 * @param vmObject object
	 * @param listFields list of fields to be set
	 * @param listValues values to be set
	 */
	void setObjectValues(Object vmObject, List<RefFieldBean> listFields, List<VMDataField> listValues);

	/**
	 * Gets a region of an array.
	 * @param objArray array
	 * @param firstIndex first index
	 * @param length length of region
	 * @return array-region
	 */
	VMArrayRegion readArrayValues(Object objArray, int firstIndex, int length);

	/**
	 * Gets the VM-value of an object-value.
	 * @param typeValue type of object
	 * @param oValue value
	 * @return VM-value
	 */
	VMValue getVMValue(final Class<?> typeValue, final Object oValue);

	/**
	 * Gets the list of variable-slots of a method.
	 * @param method method
	 * @return slots
	 */
	List<VariableSlot> getVariableSlots(Method method);

	/**
	 * Gets the values of variables of a method-frame.
	 * @param methodFrame method-frame
	 * @param slotRequests requested variables
	 * @return values of variable, the list is too short in case of an invalid slot
	 */
	List<VMValue> getVariableValues(MethodFrame methodFrame, List<SlotRequest> slotRequests);

	/**
	 * Sets the values of variables of a method-frame.
	 * @param methodFrame method-frame
	 * @param slotVariables slots and new values
	 * @return <code>true</code> if no error occured
	 */
	boolean setVariableValues(MethodFrame methodFrame, List<SlotValue> slotVariables);

	/**
	 * Executes a method on an object.
	 * @param cObjectId object-instance
	 * @param classId class
	 * @param methodId method
	 * @param values method-arguments
	 * @return return value (may be null) and exception (may be null)
	 */
	VMDataField[] executeMethod(VMObjectID cObjectId, VMClassID classId, VMMethodID methodId, List<SlotValue> values);

	/**
	 * Gets a list of frames of the call-stack of a thread.
	 * @param cThreadId thread-id
	 * @param startFrame start-frame
	 * @param length number of frames or -1 (meaning all remaining)
	 * @return list of frames
	 */
	List<RefFrameBean> getThreadFrames(VMThreadID cThreadId, int startFrame, int length);

	/**
	 * Gets the VM-id of the this-object of the current frame.
	 * @param methodFrame method-frame
	 * @return tagged object-id
	 */
	VMTaggedObjectId getThisObject(MethodFrame methodFrame);

	/**
	 * Gets a JVM-object by oject-id.
	 * @param objectId VM-object-id
	 * @return object or <code>null</code> if unknown
	 */
	Object getVMObject(VMObjectID objectId);

	/**
	 * Gets the suspend-count of a thread.
	 * @param cThreadId
	 * @return suspend or <code>null</code> in case of a unknown thread
	 */
	Integer getSuspendCount(VMThreadID cThreadId);

	/**
	 * Suspends all threads in the VM.
	 */
	void suspend();

	/**
	 * Suspends a thread
	 * @param cThreadId thread-id
	 * @return <code>true</code> in case of a known-thread, <code>false</code> otherwise
	 */
	boolean suspendThread(VMThreadID cThreadId);

	/**
	 * Resumes all threads in the VM.
	 */
	void resume();

	/**
	 * Resumes a thread
	 * @param cThreadId thread-id
	 * @return <code>true</code> in case of a known-thread, <code>false</code> otherwise
	 */
	boolean resumeThread(VMThreadID cThreadId);

	/**
	 * Interrupts a thread.
	 * @param thread thread
	 */
	void interrupt(Thread thread);

	/**
	 * Gets the monitor-object if a thread is waiting, null-id otherwise.
	 * @param thread thread
	 * @return object-id or null-id 
	 */
	VMTaggedObjectId getCurrentContentedMonitor(Thread thread);

	/**
	 * Gets the monitor-objects entered by the given thread.
	 * @param thread thread
	 * @return list of monitor-objects
	 */
	List<VMTaggedObjectId> getOwnedMonitors(Thread thread);

}
