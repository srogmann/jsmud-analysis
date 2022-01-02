package org.rogmann.jsmud.vm;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.List;

import org.rogmann.jsmud.datatypes.Tag;
import org.rogmann.jsmud.datatypes.VMArrayRegion;
import org.rogmann.jsmud.datatypes.VMClassID;
import org.rogmann.jsmud.datatypes.VMClassLoaderID;
import org.rogmann.jsmud.datatypes.VMDataField;
import org.rogmann.jsmud.datatypes.VMFieldID;
import org.rogmann.jsmud.datatypes.VMInterfaceID;
import org.rogmann.jsmud.datatypes.VMMethodID;
import org.rogmann.jsmud.datatypes.VMObjectID;
import org.rogmann.jsmud.datatypes.VMObjectOrExceptionID;
import org.rogmann.jsmud.datatypes.VMReferenceTypeID;
import org.rogmann.jsmud.datatypes.VMStringID;
import org.rogmann.jsmud.datatypes.VMTaggedObjectId;
import org.rogmann.jsmud.datatypes.VMThreadGroupID;
import org.rogmann.jsmud.datatypes.VMThreadID;
import org.rogmann.jsmud.datatypes.VMValue;
import org.rogmann.jsmud.debugger.SlotRequest;
import org.rogmann.jsmud.debugger.SlotValue;
import org.rogmann.jsmud.debugger.SourceFileRequester;
import org.rogmann.jsmud.replydata.LineTable;
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
	 * Gets the visitor of the current thread.
	 * @return visitor
	 */
	JvmExecutionVisitor getCurrentVisitor();

	/**
	 * Gets the invocation-handler
	 * @return invocation-handler
	 */
	JvmInvocationHandler getInvocationHandler();

	/**
	 * Registers a thread and the corresponding thread-group.
	 * @param thread thread
	 * @return <code>true</code> if the thread wasn't registered yet
	 */
	boolean registerThread(final Thread thread);

	/**
	 * Registers a thread and the corresponding thread-group.
	 * @param thread thread
	 * @param parentVisitor visitor of parent-thread or <code>null</code>
	 * @return <code>true</code> if the thread wasn't registered yet
	 */
	boolean registerThread(final Thread thread, final JvmExecutionVisitor parentVisitor);

	/**
	 * Removes a thread and the corresponding thread-group.
	 * @param thread thread
	 */
	void unregisterThread(final Thread thread);

	/**
	 * Creates a string in the JVM.
	 * @param utf8 String 
	 * @return String-Id
	 */
	VMStringID createString(String utf8);

	/**
	 * Loads a class.
	 * The context-class is used to determine the class-loader to be used.
	 * @param className qualified class-name, e.g. "java.lang.String"
	 * @param ctxClass context-class, i.e. a class which knows the class to be loaded
	 * @return loaded class
	 * @throws ClassNotFoundException if the class can't be found
	 */
	Class<?> loadClass(String className, final Class<?> ctxClass) throws ClassNotFoundException;

	/**
	 * Redefines a class.
	 * @param refType reference-type of the class
	 * @param classUntilNow class until now
	 * @param aClassbytes new class-file
	 */
	void redefineClass(final VMReferenceTypeID refType, final Class<?> classUntilNow, final byte[] aClassbytes);

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
	 * Gets the thread-id of a thread.
	 * @param thread thread
	 * @return thread-id or <code>null</code>
	 */
	VMThreadID getThreadId(Thread thread);

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
	 * Gets the default class-loader.
	 * @return class-loader
	 */
	ClassLoader getClassLoader();

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
	 * Sets static values in a class.
	 * @param clazz class
	 * @param aFields fields (may be in a super-class)
	 * @param aValues values
	 */
	void setClassStaticValues(Class<?> clazz, RefFieldBean[] aFields, VMDataField[] aValues);

	/**
	 * Gets a line-code-table of a method.
	 * @param clazz class of method
	 * @param method method
	 * @param refType ref-id of class
	 * @param methodID id of method
	 * @return line-code-table
	 */
	LineTable getLineTable(Class<?> clazz, Executable method, VMReferenceTypeID refType, VMMethodID methodID);

	/**
	 * Gets a list of interfaces of the given class.
	 * @param classRef given class
	 * @return list of interface-ids
	 */
	List<VMInterfaceID> getClassInterfaces(Class<?> classRef);

	/**
	 * Gets a list of fields of the given class.
	 * @param classRef given class
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
	 * Gets the id of a method.
	 * @param method method
	 * @return method-id
	 */
	VMMethodID getMethodId(Executable method);

	/**
	 * Disables garbage collection for the given object.
	 * @param cObjectId object-id
	 * @param vmObject object
	 */
	void disableCollection(VMObjectID cObjectId, Object vmObject);

	/**
	 * Enables garbage collection for the given object.
	 * @param cObjectId object-id
	 */
	void enableCollection(VMObjectID cObjectId);

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
	 * Sets values of elements of an array.
	 * @param objArray array
	 * @param tag type of array-elements
	 * @param firstIndex first index
	 * @param values values to be set
	 */
	void setArrayValues(Object objArray, Tag tag, int firstIndex, VMDataField[] values);

	/**
	 * Gets the VM-tag of an object-value.
	 * @param typeValue type of object
	 * @return VM-tag
	 */
	Tag getVMTag(final Class<?> typeValue);

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
	 * @return <code>true</code> if no error occurred
	 */
	boolean setVariableValues(MethodFrame methodFrame, List<SlotValue> slotVariables);

	/**
	 * Creates an object-instance.
	 * @param clazz class of the wanted object
	 * @param thread thread to be used
	 * @param constructor constructor to be used
	 * @param argValues arguments of the constructor
	 * @return new object or exception
	 */
	VMObjectOrExceptionID createNewInstance(Class<?> clazz, Thread thread, Constructor<?> constructor,
			List<VMValue> argValues);

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
	 * @param cThreadId thread-id of thread
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

	/**
	 * Counts the number of instances for the corresponding reference types.
	 * <p>The current implementation is not very fast and should be used for small simulated heaps.</p>
	 * @param aRefTypes reference types
	 * @return number of instances
	 */
	long[] getInstanceCounts(VMReferenceTypeID[] aRefTypes);

	/**
	 * Gets the instances of a given class.
	 * @param classRefType reference-type
	 * @param maxInstances maximum number of instances (0 = all instances)
	 * @return instance of this type
	 */
	List<VMTaggedObjectId> getInstances(Class<?> classRefType, int maxInstances);

	/**
	 * Requests the generation of a source-file containing pseudo-bytecode.
	 * The source-file will be registered for the use of its line-numbers while debugging.
	 * @param clazz class
	 * @param sourceFileRequester source-file-requester
	 * @throws IOException in case of an IOException
	 */
	void generateSourceFile(final Class<?> clazz, final SourceFileRequester sourceFileRequester) throws IOException;

	/**
	 * Gets the extension-attribute of a reference-type (see JSR-045).
	 * @param classRef class
	 * @return extension-attribute
	 */
	String getExtensionAttribute(Object classRef);

}
