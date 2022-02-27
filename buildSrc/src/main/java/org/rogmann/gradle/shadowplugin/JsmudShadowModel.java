package org.rogmann.gradle.shadowplugin;

/**
 * Configuration of JsmudShadowPlugin.
 */
public class JsmudShadowModel {

	/** Java-package of dependency, e.g. "org.objectweb.asm" */
	public String depPackage;
	/** Java-package of dependency, e.g. "org.rogmann.jsmud.shadow.asm" */
	public String depPackageShadow;

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder(50);
		sb.append(JsmudShadowModel.class.getSimpleName());
		sb.append('{');
		sb.append("depPackage='").append(depPackage).append('\'');
		sb.append(", ");
		sb.append("depPackageShadow=").append(depPackageShadow).append('\'');	
		sb.append('}');
		return sb.toString();
	}
	
}
