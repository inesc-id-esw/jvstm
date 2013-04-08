package jvstm.test.point.runners;

import java.util.concurrent.Executors;

import java.util.concurrent.ScheduledExecutorService;

import junit.framework.Assert;
import jvstm.CommitException;
import jvstm.SuspendedTransaction;
import jvstm.TopLevelTransaction;
import jvstm.Transaction;
import jvstm.test.point.core.Point;
import jvstm.test.point.impl.AomIntPoint;

public class RunRwWithRw{
    public static void main(String [] args) throws Exception{
        RunRwWithRw.performTest(new AomIntPoint(7, 8));
    }

  private static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);

  public static <T extends Number> void performTest(final Point<T> p) throws Exception{
    final long initX = p.getX().longValue();
    final long initY = p.getY().longValue();
    //
    // ThreadLocal -> rwTrx2
    //
    TopLevelTransaction rwTrx2 = (TopLevelTransaction) Transaction.begin(false);
    long t2readX = p.getX().longValue();
    Assert.assertEquals(initX, t2readX);
    SuspendedTransaction r2Token =  rwTrx2.suspendTx();
    //
    // ThreadLocal -> rwTrx1
    //
    TopLevelTransaction rwTrx1 = (TopLevelTransaction) Transaction.begin(false);
    long t1readX = p.getX().longValue();
    Assert.assertEquals(initX, t1readX);
    SuspendedTransaction r1Token = rwTrx1.suspendTx();
    //
    // ThreadLocal -> rwTrx2
    //
    rwTrx2.resume(r2Token );
    long t2readY = p.getY().longValue();
    Assert.assertEquals(initY, t2readY);
    r2Token  = rwTrx2.suspendTx();
    //
    // ThreadLocal -> rwTrx1
    //
    rwTrx1.resume(r1Token);
    long t1readY = p.getY().longValue();
    Assert.assertEquals(initY, t1readY);
    r1Token = rwTrx1.suspendTx();
    //
    // ThreadLocal -> rwTrx2
    //
    rwTrx2.resume(r2Token );
    p.setX(t2readX + 3);
    r2Token = rwTrx2.suspendTx();
    //
    // ThreadLocal -> rwTrx1
    //
    rwTrx1.resume(r1Token );
    p.setX(t1readX + 7);
    r1Token = rwTrx1.suspendTx();
    //
    // ThreadLocal -> rwTrx2
    //
    rwTrx2.resume(r2Token );
    p.setY(t2readY - 3);
    r2Token  = rwTrx2.suspendTx();
    //
    // ThreadLocal -> rwTrx1
    //
    rwTrx1.resume(r1Token );
    p.setY(t1readY - 7);
    r1Token = rwTrx1.suspendTx();
    //
    // ThreadLocal -> rwTrx2
    //
    rwTrx2.resume(r2Token );
    Transaction.commit();
    r2Token = rwTrx2.suspendTx();
    //
    // ThreadLocal -> rwTrx1
    //
    rwTrx1.resume(r1Token );
    try{
      Transaction.commit();
    }catch(CommitException e){
      Transaction.abort();
      rwTrx1 = (TopLevelTransaction) Transaction.begin(false);
      t1readX = p.getX().longValue();
      Assert.assertEquals(initX + 3, t1readX);
      t1readY = p.getY().longValue();
      Assert.assertEquals(initY - 3, t1readY);
      p.setX(t1readY + 7);
      p.setY(t1readY - 7);
      Transaction.commit();
      Assert.assertTrue(true);
      return;
    }
    Assert.assertTrue(false);
  }
}
