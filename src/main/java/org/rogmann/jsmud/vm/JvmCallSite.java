package org.rogmann.jsmud.vm;

/**
 * Contains the bootstrap-method of a INVOKEDYNAMIC-class.
 * An instance of this class is placed on the stack to simulate an INVOKEDYNAMIC-call.
 */
public class JvmCallSite {

	/** owner-class (calling-class) */
	private String lambdaOwner;
	/** name of lambda-method */
	private String lambdaName;
	/** signature of lambda-method */
	private String lambdaDesc;

	/**
	 * Constructor
	 * @param lambdaOwner owner-class (calling-class)
	 * @param lambdaName name of lambda-method
	 * @param lambdaDesc signature of lambda-method
	 */
	public JvmCallSite(String lambdaOwner, String lambdaName, String lambdaDesc) {
		this.lambdaOwner = lambdaOwner.replace('/', '.');
		this.lambdaName = lambdaName;
		this.lambdaDesc = lambdaDesc;
	}

	/**
	 * Gets the owner-class (calling-class)
	 * @return full qualified class-name
	 */
	public String getLambdaOwner() {
		return lambdaOwner;
	}

	/**
	 * Gets the name of the lambda-method.
	 * @return method-name
	 */
	public String getLambdaName() {
		return lambdaName;
	}

	/**
	 * Gets the signature of the lambda-method.
	 * @return method-signature
	 */
	public String getLambdaDesc() {
		return lambdaDesc;
	}

}
