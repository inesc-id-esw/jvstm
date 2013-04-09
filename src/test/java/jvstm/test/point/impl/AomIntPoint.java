package jvstm.test.point.impl;

import jvstm.AomBarriers;

import jvstm.ReadWriteTransaction;
import jvstm.Transaction;
import jvstm.UtilUnsafe;
import jvstm.VBoxAom;
import jvstm.test.point.core.Point;

public class AomIntPoint extends VBoxAom<AomIntPoint> implements Point<Integer>{
    /*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*
     *------------     AOM INFRA-STRUCTURE      -----------------*
     *~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
    private static final long x__ADDRESS__;
    private static final long y__ADDRESS__;
    static{
        try {
            x__ADDRESS__ = UtilUnsafe.UNSAFE.objectFieldOffset(AomIntPoint.class.getDeclaredField("x"));
            y__ADDRESS__ = UtilUnsafe.UNSAFE.objectFieldOffset(AomIntPoint.class.getDeclaredField("y"));
        } catch (SecurityException e) {
            throw new RuntimeException(e);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    /*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     *---------------------- FIELDS   ---------------------
     *~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
    int x, y;

    /*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     *----------------------  CTOR   ----------------------
     *~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
    public AomIntPoint(int x, int y) {
        this.x = x;
        this.y = y;
    }
    /*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     *------------------ Point interface   ----------------
     *~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
    @Override
    public Integer getX() {
        return AomBarriers.get(Transaction.current(), this).x;
    }

    @Override
    public Integer getY() {
        return AomBarriers.get(Transaction.current(), this).y;
    }

    @Override
    public void setX(Number x) {
        AomBarriers.put((ReadWriteTransaction) Transaction.current(), this, x.intValue(), x__ADDRESS__);
    }

    @Override
    public void setY(Number y) {
        AomBarriers.put((ReadWriteTransaction)Transaction.current(), this, (y == null? 0: y.intValue()), y__ADDRESS__);
    }
    @Override
    public AomIntPoint replicate() {
        AomIntPoint res;
        try {
            res = (AomIntPoint)UtilUnsafe.UNSAFE.allocateInstance(AomIntPoint__FIELDS__.class);
            res.x = this.x;
            res.y = this.y;
            return res;
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        }

    }
    @Override
    public void toCompactLayout(AomIntPoint from) {
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
class AomIntPoint__FIELDS__ extends AomIntPoint{
    public AomIntPoint__FIELDS__(int x, int y) {
        super(x, y);
    }

    @Override
    public Integer getX() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Integer getY() {
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
