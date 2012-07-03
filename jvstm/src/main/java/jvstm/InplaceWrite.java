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

import static jvstm.UtilUnsafe.UNSAFE;

import java.lang.reflect.Field;


public class InplaceWrite<T> {

    // --- Setup to use Unsafe
    private static final long ownerOffset;
    static { // <clinit>
	Field f = null;
	try {
	    f = InplaceWrite.class.getDeclaredField("orec");
	} catch (java.lang.NoSuchFieldException e) {
	    throw new RuntimeException(e);
	}
	ownerOffset = UNSAFE.objectFieldOffset(f);
    }

    public OwnershipRecord orec;
    public T tempValue;
    public InplaceWrite<T> next;

    public InplaceWrite() {
	this.orec = OwnershipRecord.DEFAULT_COMMITTED_OWNER;
	this.tempValue = null;
	this.next = null;
    }

    public InplaceWrite(OwnershipRecord owner, T tempValue, InplaceWrite<T> next) {
	this.orec = owner;
	this.tempValue = tempValue;
	this.next = next;
    }

    protected boolean CASowner(OwnershipRecord prevOrec, OwnershipRecord newOrec) {
	return UNSAFE.compareAndSwapObject(this, ownerOffset, prevOrec, newOrec);
    }

}
