package org.rogmann.jsmud.test;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marker-annotation of a method which tests JSMUD.
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface JsmudTest {

	String description() default "junit-test of jsmud-analysis";
}
