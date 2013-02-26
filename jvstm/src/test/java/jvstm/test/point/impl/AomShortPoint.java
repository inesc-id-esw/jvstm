package jvstm.test.point.impl;

import jvstm.AomBarriers;

import jvstm.ReadWriteTransaction;
import jvstm.Transaction;
import jvstm.UtilUnsafe;
import jvstm.VBoxAom;
import jvstm.test.point.core.Point;

public class AomShortPoint extends VBoxAom<AomShortPoint> implements Point<Short>{
    /*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*
     *------------     AOM INFRA-STRUCTURE      -----------------*
     *~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
    private static final long x__ADDRESS__;
    private static final long y__ADDRESS__;
    static{
	try {
	    x__ADDRESS__ = UtilUnsafe.UNSAFE.objectFieldOffset(AomShortPoint.class.getDeclaredField("x"));
	    y__ADDRESS__ = UtilUnsafe.UNSAFE.objectFieldOffset(AomShortPoint.class.getDeclaredField("y"));
	} catch (SecurityException e) {
	    throw new RuntimeException(e);
	} catch (NoSuchFieldException e) {
	    throw new RuntimeException(e);
	}
    }
    /*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     *---------------------- FIELDS   ---------------------
     *~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
    short x, y;

    /*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     *----------------------  CTOR   ----------------------
     *~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
    public AomShortPoint(short x, short y) {
	this.x = x;
	this.y = y;
    }
    /*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     *------------------ Point interface   ----------------
     *~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
    @Override
    public Short getX() {
	return AomBarriers.get(Transaction.current(), this).x;
    }

    @Override
    public Short getY() {
	return AomBarriers.get(Transaction.current(), this).y;
    }

    @Override
    public void setX(Number x) {
	AomBarriers.put((ReadWriteTransaction) Transaction.current(), this, x.shortValue(), x__ADDRESS__);
    }

    @Override
    public void setY(Number y) {
	AomBarriers.put((ReadWriteTransaction)Transaction.current(), this, (y == null? 0: y.shortValue()), y__ADDRESS__);
    }
    @Override
    public AomShortPoint replicate() {
	AomShortPoint res;
	try {
	    res = (AomShortPoint)UtilUnsafe.UNSAFE.allocateInstance(AomShortPoint__FIELDS__.class);
	    res.x = this.x;
	    res.y = this.y;
	    return res;
	} catch (InstantiationException e) {
	    throw new RuntimeException(e);
	}
	
    }
    @Override
    public void toCompactLayout(AomShortPoint from) {
	this.x = from.x;
	this.y = from.y;
    }
}

/**
 * This is an auxiliary class that is automatically created by the AOM in the Deuce 
 * and serves as a state repository without behavior (operations).
 * The values stored in the versioned history are instances of this class preventing 
 * from any misuse.  
 */
class AomShortPoint__FIELDS__ extends AomShortPoint{
    public AomShortPoint__FIELDS__(short x, short y) {
	super(x, y);
    }

    @Override
    public Short getX() {
	throw new UnsupportedOperationException();
    }

    @Override
    public Short getY() {
	throw new UnsupportedOperationException();
    }

    @Override
    public void setX(Number x) {
	throw new UnsupportedOperationException();
    }

    @Override
    public void setY(Number y) {
	throw new UnsupportedOperationException();
    }
}
