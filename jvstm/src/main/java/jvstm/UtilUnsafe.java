/*
 * JVSTM: a Java library for Software Transactional Memory
 * Copyright (C) 2005 INESC-ID Software Engineering Group
 * http://www.esw.inesc-id.pt
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * Author's contact:
 * INESC-ID Software Engineering Group
 * Rua Alves Redol 9
 * 1000 - 029 Lisboa
 * Portugal
 */
package jvstm;

import java.lang.reflect.Field;

/**
 * Simple class to obtain access to the {@link Unsafe} object.
 */
public final class UtilUnsafe {
    public static final sun.misc.Unsafe UNSAFE = getUnsafe();

    @SuppressWarnings("unchecked")
    private static <UNSAFE> UNSAFE getUnsafe() {
        Object theUnsafe = null;
        Exception exception = null;

        try {
            Class<?> uc = Class.forName("sun.misc.Unsafe");
            Field f = uc.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            theUnsafe = f.get(uc);
        } catch (Exception e) { exception = e; }

        if (theUnsafe == null) throw new Error("Could not obtain access to sun.misc.Unsafe", exception);
        return (UNSAFE) theUnsafe;
    }

    private UtilUnsafe() { }

    public static long objectFieldOffset(Class<?> klass, String fieldName){
        Field f = null;
        try {
            f = klass.getDeclaredField(fieldName);
        } catch (java.lang.NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        return UNSAFE.objectFieldOffset(f);

    }

}
