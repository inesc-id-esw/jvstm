package jvstm.test.point.core;

import java.lang.reflect.Field;

/**
 * This is an utility class to read transactional object fields in-place
 * and avoiding the STM Barriers.
 * Typically the transactional classes properties use barriers, but for 
 * unit tests purpose we may want to access the fields in-place. 
 */
public class PointFields<T extends Number> {
    
    private final Field fieldX, fieldY;
    
    public PointFields(Class c) {
	try {
	    this.fieldX = c.getDeclaredField("x");
	    this.fieldY = c.getDeclaredField("y");
	    this.fieldX.setAccessible(true);
	    this.fieldY.setAccessible(true);
	} catch (NoSuchFieldException e) {
	    throw new RuntimeException(e);
	} catch (SecurityException e) {
	    throw new RuntimeException(e);
	}
    }

    public T getX(Point<T> p){
	try {
	    return (T) fieldX.get(p);
	} catch (IllegalArgumentException e) {
	    throw new RuntimeException(e);
	} catch (IllegalAccessException e) {
	    throw new RuntimeException(e);
	}
    }
    
    public T getY(Point<T> p){
	try {
	    return (T) fieldY.get(p);
	} catch (IllegalArgumentException e) {
	    throw new RuntimeException(e);
	} catch (IllegalAccessException e) {
	    throw new RuntimeException(e);
	}
    }
    
    public void setX(Point<T> p, Number val){
	try {
	    fieldX.set(p, val);
	} catch (IllegalArgumentException e) {
	    throw new RuntimeException(e);
	} catch (IllegalAccessException e) {
	    throw new RuntimeException(e);
	}
    }
    
    public void setY(Point<T> p, Number val){
	try {
	    fieldY.set(p, val);
	} catch (IllegalArgumentException e) {
	    throw new RuntimeException(e);
	} catch (IllegalAccessException e) {
	    throw new RuntimeException(e);
	}
    }

}
