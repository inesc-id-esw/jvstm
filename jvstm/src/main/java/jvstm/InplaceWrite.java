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
