package jvstm.test.point.impl;

import jvstm.AomBarriers;

import jvstm.ReadWriteTransaction;
import jvstm.Transaction;
import jvstm.UtilUnsafe;
import jvstm.VBoxAom;
import jvstm.test.point.core.Point;

public class AomDoublePoint extends VBoxAom<AomDoublePoint> implements Point<Double>{
    /*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*
     *------------     AOM INFRA-STRUCTURE      -----------------*
     *~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
    private static final long x__ADDRESS__;
    private static final long y__ADDRESS__;
    static{
        try {
            x__ADDRESS__ = UtilUnsafe.UNSAFE.objectFieldOffset(AomDoublePoint.class.getDeclaredField("x"));
            y__ADDRESS__ = UtilUnsafe.UNSAFE.objectFieldOffset(AomDoublePoint.class.getDeclaredField("y"));
        } catch (SecurityException e) {
            throw new RuntimeException(e);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    /*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     *---------------------- FIELDS   ---------------------
     *~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
    double x, y;

    /*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     *----------------------  CTOR   ----------------------
     *~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
    public AomDoublePoint(double x, double y) {
        this.x = x;
        this.y = y;
    }
    /*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     *------------------ Point interface   ----------------
     *~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
    @Override
    public Double getX() {
        return AomBarriers.get(Transaction.current(), this).x;
    }

    @Override
    public Double getY() {
        return AomBarriers.get(Transaction.current(), this).y;
    }

    @Override
    public void setX(Number x) {
        AomBarriers.put((ReadWriteTransaction) Transaction.current(), this, x.doubleValue(), x__ADDRESS__);
    }

    @Override
    public void setY(Number y) {
        AomBarriers.put((ReadWriteTransaction)Transaction.current(), this, (y == null? 0: y.doubleValue()), y__ADDRESS__);
    }
    @Override
    public AomDoublePoint replicate() {
        AomDoublePoint res;
        try {
            res = (AomDoublePoint)UtilUnsafe.UNSAFE.allocateInstance(AomDoublePoint__FIELDS__.class);
            res.x = this.x;
            res.y = this.y;
            return res;
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        }

    }
    @Override
    public void toCompactLayout(AomDoublePoint from) {
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
class AomDoublePoint__FIELDS__ extends AomDoublePoint{
    public AomDoublePoint__FIELDS__(double x, double y) {
        super(x, y);
    }

    @Override
    public Double getX() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Double getY() {
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
