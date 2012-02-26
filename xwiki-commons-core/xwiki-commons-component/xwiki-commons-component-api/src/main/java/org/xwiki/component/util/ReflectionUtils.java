/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.component.util;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Various Reflection utilities.
 * 
 * @version $Id$
 * @since 2.1RC1
 */
/**
 * @version $Id$
 */
public final class ReflectionUtils
{
    /**
     * Utility class.
     */
    private ReflectionUtils()
    {
        // Utility class
    }

    /**
     * @param clazz the class for which to return all fields
     * @return all fields declared by the passed class and its superclasses
     */
    public static Collection<Field> getAllFields(Class< ? > clazz)
    {
        // Note: use a linked hash map to keep the same order as the one used to declare the fields.
        Map<String, Field> fields = new LinkedHashMap<String, Field>();
        Class< ? > targetClass = clazz;
        while (targetClass != null) {
            Field[] targetClassFields;
            try {
                targetClassFields = targetClass.getDeclaredFields();
            } catch (NoClassDefFoundError e) {
                // Provide a better exception message to more easily debug component loading issue.
                // Specifically with this error message we'll known which component failed to be initialized.
                throw new NoClassDefFoundError("Failed to get fields for class [" + targetClass.getName()
                    + "] because the class [" + e.getMessage() + "] couldn't be found in the ClassLoader.");
            }

            for (Field field : targetClassFields) {
                // Make sure that if the same field is declared in a class and its superclass
                // only the field used in the class will be returned. Note that we need to do
                // this check since the Field object doesn't implement the equals method using
                // the field name.
                if (!fields.containsKey(field.getName())) {
                    fields.put(field.getName(), field);
                }
            }
            targetClass = targetClass.getSuperclass();
        }
        return fields.values();
    }

    /**
     * @param clazz the class for which to return all fields
     * @param fieldName the name of the field to get
     * @return the field specified from either the passed class or its superclasses
     * @exception NoSuchFieldException if the field doesn't exist in the class or superclasses
     */
    public static Field getField(Class< ? > clazz, String fieldName) throws NoSuchFieldException
    {
        Field resultField = null;
        Class< ? > targetClass = clazz;
        while (targetClass != null) {
            try {
                resultField = targetClass.getDeclaredField(fieldName);
                break;
            } catch (NoSuchFieldException e) {
                // Look in superclass
                targetClass = targetClass.getSuperclass();
            }
        }

        if (resultField == null) {
            throw new NoSuchFieldException("No field named [" + fieldName + "] in class [" + clazz.getName()
                + "] or superclasses");
        }

        return resultField;
    }

    /**
     * Extract the main class from the passed {@link Type}.
     * 
     * @param type the generic {@link Type}
     * @return the main Class of the generic {@link Type}
     * @since 4.0M1
     */
    public static Class getTypeClass(Type type)
    {
        Class typeClassClass;
        if (type instanceof Class) {
            typeClassClass = (Class) type;
        } else if (type instanceof ParameterizedType) {
            typeClassClass = (Class) ((ParameterizedType) type).getRawType();
        } else {
            typeClassClass = null;
        }

        return typeClassClass;
    }

    /**
     * Sets a value to a field using reflection even if the field is private.
     * 
     * @param instanceContainingField the object containing the field
     * @param fieldName the name of the field in the object
     * @param fieldValue the value to set for the provided field
     */
    public static void setFieldValue(Object instanceContainingField, String fieldName, Object fieldValue)
    {
        // Find the class containing the field to set
        Class< ? > targetClass = instanceContainingField.getClass();
        while (targetClass != null) {
            for (Field field : targetClass.getDeclaredFields()) {
                if (field.getName().equalsIgnoreCase(fieldName)) {
                    try {
                        boolean isAccessible = field.isAccessible();
                        try {
                            field.setAccessible(true);
                            field.set(instanceContainingField, fieldValue);
                        } finally {
                            field.setAccessible(isAccessible);
                        }
                    } catch (Exception e) {
                        // This shouldn't happen but if it does then the Component manager will not function properly
                        // and we need to abort. It probably means the Java security manager has been configured to
                        // prevent accessing private fields.
                        throw new RuntimeException("Failed to set field [" + fieldName + "] in instance of ["
                            + instanceContainingField.getClass().getName() + "]. The Java Security Manager has "
                            + "probably been configured to prevent settting private field values. XWiki requires "
                            + "this ability to work.", e);
                    }
                    return;
                }
            }
            targetClass = targetClass.getSuperclass();
        }
    }

    /**
     * Extract the last generic type from the passed field. For example {@code private List&lt;A, B&gt; field} would
     * return the {@code B} class.
     * 
     * @param field the field from which to extract the generic type
     * @return the class of the last generic type or null if the field doesn't have a generic type
     */
    public static Class< ? > getLastGenericFieldType(Field field)
    {
        return getTypeClass(getLastFieldGenericArgument(field));
    }

    /**
     * Extract the last generic type from the passed field. For example {@code private List&lt;A, B&gt; field} would
     * return the {@code B} class.
     * 
     * @param field the field from which to extract the generic type
     * @return the type of the last generic type or null if the field doesn't have a generic type
     * @since 4.0M1
     */
    public static Type getLastFieldGenericArgument(Field field)
    {
        return getLastTypeGenericArgument(field.getGenericType());
    }

    /**
     * Extract the last generic type from the passed Type. For example {@code private List&lt;A, B&gt; field} would
     * return the {@code B} class.
     * 
     * @param type the type from which to extract the generic type
     * @return the type of the last generic type or null if the field doesn't have a generic type
     * @since 4.0M1
     */
    public static Type getLastTypeGenericArgument(Type type)
    {
        if (type instanceof ParameterizedType) {
            ParameterizedType pType = (ParameterizedType) type;
            Type[] types = pType.getActualTypeArguments();
            if (types.length > 0) {
                return types[types.length - 1];
            }
        }

        return null;
    }

    /**
     * Extract the last generic type from the passed class. For example
     * {@code public Class MyClass implements FilterClass&lt;A, B&gt;, SomeOtherClass&lt;C&gt;} will return {@code B}.
     * 
     * @param clazz the class to extract from
     * @param filterClass the class of the generic type we're looking for
     * @return the last generic type from the interfaces of the passed class, filtered by the passed filter class
     */
    public static Class< ? > getLastGenericClassType(Class clazz, Class filterClass)
    {
        Type type = getGenericClassType(clazz, filterClass);

        if (type instanceof ParameterizedType) {
            ParameterizedType pType = (ParameterizedType) type;
            if (filterClass.isAssignableFrom((Class) pType.getRawType())) {
                Type[] actualTypes = pType.getActualTypeArguments();
                if (actualTypes.length > 0 && actualTypes[actualTypes.length - 1] instanceof Class) {
                    return (Class) actualTypes[actualTypes.length - 1];
                }
            }
        }

        return null;
    }

    /**
     * Extract the real Type from the passed class. For example
     * {@code public Class MyClass implements FilterClass&lt;A, B&gt;, SomeOtherClass&lt;C&gt;} will return
     * {@code FilterClass&lt;A, B&gt;, SomeOtherClass&lt;C&gt;}.
     * 
     * @param clazz the class to extract from
     * @param filterClass the class of the generic type we're looking for
     * @return the real Type from the interfaces of the passed class, filtered by the passed filter class
     * @since 4.0M1
     */
    public static Type getGenericClassType(Class clazz, Class filterClass)
    {
        // Get all interfaces implemented and find the one that's a Provider with a Generic type
        for (Type type : clazz.getGenericInterfaces()) {
            if (type == filterClass) {
                return type;
            } else if (type instanceof ParameterizedType) {
                ParameterizedType pType = (ParameterizedType) type;
                if (filterClass.isAssignableFrom((Class) pType.getRawType())) {
                    return type;
                }
            }
        }

        return null;
    }
}
