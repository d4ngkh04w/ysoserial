package ysoserial.payloads.util;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

import sun.misc.Unsafe;
import sun.reflect.ReflectionFactory;

import com.nqzero.permit.Permit;

@SuppressWarnings ( "restriction" )
public class Reflections {

    public static void setAccessible(AccessibleObject member) {
        String versionStr = System.getProperty("java.version");
        int javaVersion = Integer.parseInt(versionStr.split("\\.")[0]);
        if (javaVersion < 12) {
          // quiet runtime warnings from JDK9+
          Permit.setAccessible(member);
        } else {
          // not possible to quiet runtime warnings anymore...
          // see https://bugs.openjdk.java.net/browse/JDK-8210522
          // to understand impact on Permit (i.e. it does not work
          // anymore with Java >= 12)
          member.setAccessible(true);
        }
    }

	public static Field getField(final Class<?> clazz, final String fieldName) {
        Field field = null;
	try {
	    field = clazz.getDeclaredField(fieldName);
	    setAccessible(field);
        }
        catch (NoSuchFieldException ex) {
            if (clazz.getSuperclass() != null)
                field = getField(clazz.getSuperclass(), fieldName);
        }
		return field;
	}

	public static void setFieldValue(final Object obj, final String fieldName, final Object value) throws Exception {
		final Field field = getField(obj.getClass(), fieldName);
		field.set(obj, value);
	}

	public static Object getFieldValue(final Object obj, final String fieldName) throws Exception {
		final Field field = getField(obj.getClass(), fieldName);
		return field.get(obj);
	}

	public static Constructor<?> getFirstCtor(final String name) throws Exception {
		final Constructor<?> ctor = Class.forName(name).getDeclaredConstructors()[0];
	    setAccessible(ctor);
	    return ctor;
	}

	public static Object newInstance(String className, Object ... args) throws Exception {
        return getFirstCtor(className).newInstance(args);
    }

    public static <T> T createWithoutConstructor ( Class<T> classToInstantiate )
            throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        return createWithConstructor(classToInstantiate, Object.class, new Class[0], new Object[0]);
    }

    @SuppressWarnings ( {"unchecked"} )
    public static <T> T createWithConstructor ( Class<T> classToInstantiate, Class<? super T> constructorClass, Class<?>[] consArgTypes, Object[] consArgs )
            throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        Constructor<? super T> objCons = constructorClass.getDeclaredConstructor(consArgTypes);
	    setAccessible(objCons);
        Constructor<?> sc = ReflectionFactory.getReflectionFactory().newConstructorForSerialization(classToInstantiate, objCons);
	    setAccessible(sc);
        return (T)sc.newInstance(consArgs);
    }

    public static Unsafe getUnsafe() throws Exception {
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (Unsafe) f.get(null);
    }

    /**
     * Sets a field value using sun.misc.Unsafe, bypassing all JVM type checks.
     * Required for Java 21+ where some fields were narrowed in type
     * (e.g. BadAttributeValueExpException.val changed from Object to String),
     * causing normal Field.set() to throw IllegalArgumentException even with
     * accessibility granted via --add-opens.
     */
    public static void unsafeSetFieldValue(final Object obj, final String fieldName, final Object value) throws Exception {
        final Field field = getField(obj.getClass(), fieldName);
        Unsafe unsafe = getUnsafe();
        unsafe.putObject(obj, unsafe.objectFieldOffset(field), value);
    }

}
