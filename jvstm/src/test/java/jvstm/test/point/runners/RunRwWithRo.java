package jvstm.test.point.runners;

import junit.framework.Assert;

import jvstm.SuspendedTransaction;
import jvstm.Transaction;
import jvstm.test.point.core.Point;

public class RunRwWithRo{
  public static <T extends Number> void performTest(final Point<T> p) throws Exception{
    final long initX = p.getX().longValue();
    final long initY = p.getY().longValue();
    Transaction roTrx = Transaction.begin(true);
    SuspendedTransaction roTrxToken = roTrx.suspendTx();
    //
    // ThreadLocal -> rw
    //
    Transaction rwTrx = Transaction.begin(false);
    Assert.assertEquals(initX, p.getX().longValue());
    Assert.assertEquals(initY, p.getY().longValue());
    p.setX(update(initX));
    p.setY(update(initY));
    Assert.assertEquals(update(initX), p.getX().longValue());
    Assert.assertEquals(update(initY), p.getY().longValue());
    SuspendedTransaction rwTrxToken =  rwTrx.suspendTx();
    //
    // ThreadLocal -> ro
    //
    Transaction.resume(roTrxToken );
    long currX = p.getX().longValue();
    Assert.assertEquals(initX, currX);
    Assert.assertEquals(initY, p.getY().longValue());
    roTrxToken = roTrx.suspendTx();
    //
    // Main thread starts a new transaction
    //
    Assert.assertEquals(initX, p.getX().longValue());
    Assert.assertEquals(initY, p.getY().longValue());
    //
    // ThreadLocal -> rw
    //
    rwTrx.resume(rwTrxToken );
    Transaction.commit();
    //
    // ThreadLocal -> ro
    //
    roTrx.resume(roTrxToken );
    Assert.assertEquals(initX, p.getX().longValue());
    Assert.assertEquals(initY, p.getY().longValue());
    Transaction.commit();
    //
    // Main thread starts a new transaction
    //
    Assert.assertEquals(update(initX), p.getX().longValue());
    Assert.assertEquals(update(initY), p.getY().longValue());
  }
  private static long update(long src){
    return (src*4+6)/2;
  }

}
