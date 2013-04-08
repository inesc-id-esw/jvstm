package jvstm.test.point.runners;

import java.util.concurrent.Executors;

import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;
import jvstm.CommitException;
import jvstm.SuspendedTransaction;
import jvstm.Transaction;
import jvstm.test.point.core.Point;

public class RunRwWithRwConflictSameFields{
  private static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);

  public static <T extends Number> void performTest(final Point<T> p) throws Exception{
    p.setX(13);
    p.setY(14);
    final long initX = p.getX().longValue();
    final long initY = p.getY().longValue();
    final Transaction rwTrx1 = Transaction.begin(false);
    SuspendedTransaction r1Token = rwTrx1.suspendTx();
    //
    // ThreadLocal -> rwTrx2
    //
    final Transaction rwTrx2 = Transaction.begin(false);
    Assert.assertEquals(initX, p.getX().longValue());
    Assert.assertEquals(initY, p.getY().longValue());
    p.setX(update(initX));
    p.setY(update(initY));
    Assert.assertEquals(update(initX), p.getX().longValue());
    Assert.assertEquals(update(initY), p.getY().longValue());
    final SuspendedTransaction r2Token = rwTrx2.suspendTx();
    //
    // ThreadLocal -> rwTrx1 -> this transaction will exchange
    // the values of x and y.
    //
    rwTrx1.resume(r1Token);
    long currX = p.getX().longValue();
    Assert.assertEquals(initX, currX);
    Assert.assertEquals(initY, p.getY().longValue());
    p.setX(initY);
    p.setY(initX);
    Assert.assertEquals(initY, p.getX().longValue());
    Assert.assertEquals(initX, p.getY().longValue());
    final SuspendedTransaction r1TokenB = rwTrx1.suspendTx();
    //
    // Main thread starts a new transaction
    //
    currX = p.getX().longValue();
    long currY = p.getY().longValue();
    Assert.assertEquals(initX, currX);
    Assert.assertEquals(initY, currY);
    //
    // ThreadLocal -> rwTrx2
    //
    final Future<?> fT2 = executor.submit(new Runnable() { public void run() {
      rwTrx2.resume(r2Token );
      Transaction.commit();
    }});
    //
    // ThreadLocal -> rwTrx1
    //
    Future<?> fT1 = executor.schedule(new Runnable() { public void run() {
      rwTrx1.resume(r1TokenB);
      try {
        Transaction.commit();
      } catch (CommitException e) {
        Assert.assertTrue(true);
        rwTrx1.abortTx();
        //
        // ThreadLocal -> rwTrx1 -> restart a new transaction
        // that will try to exchange the values of x and y.
        //
        try {fT2.get();}
        catch (Exception e2) {throw new RuntimeException(e2);}
        Transaction.begin(false);
        long currX = p.getX().longValue();
        long currY = p.getY().longValue();
        Assert.assertEquals(update(initX), currX);
        Assert.assertEquals(update(initY), currY);
        p.setX(update(initY));
        p.setY(update(initX));
        Assert.assertEquals(update(initY), p.getX().longValue());
        Assert.assertEquals(update(initX), p.getY().longValue());
        Transaction.commit();
        //
        // Main thread starts a new transaction
        //
        Assert.assertEquals(update(initY), p.getX().longValue());
        Assert.assertEquals(update(initX), p.getY().longValue());
        return;
      }
      Assert.assertTrue(false);
    }}, 1000, TimeUnit.MILLISECONDS);
    fT2.get();
    fT1.get();
    //
    // Main thread starts a new transaction
    //
    currX = p.getX().longValue();
    currY = p.getY().longValue();
    Assert.assertEquals(update(initY), p.getX().longValue());
    Assert.assertEquals(update(initX), p.getY().longValue());
  }
  private static long update(long src){
    return (src*4+6)/2;
  }

}
