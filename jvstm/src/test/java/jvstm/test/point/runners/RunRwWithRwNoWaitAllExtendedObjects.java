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

public class RunRwWithRwNoWaitAllExtendedObjects{
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
	Assert.assertEquals(initY, p.getY().longValue());
	p.setY(update(initY));
	Assert.assertEquals(update(initY), p.getY().longValue());
	SuspendedTransaction r2Token = rwTrx2.suspendTx();
	//
	// ThreadLocal -> rwTrx1 
	//
	rwTrx1.resume(r1Token);
	long currX = p.getX().longValue();
	Assert.assertEquals(initX, currX);
	p.setX(update(initX));
	Assert.assertEquals(update(initX), p.getX().longValue());
	r1Token = rwTrx1.suspendTx();
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
	rwTrx2.resume(r2Token );
	Transaction.commit();
	//
	// Main thread starts a new transaction
	//
	currX = p.getX().longValue();
	currY = p.getY().longValue();
	Assert.assertEquals(initX, currX);
	Assert.assertEquals(update(initY), currY);
	//
	// ThreadLocal -> rwTrx1
	//
	rwTrx1.resume(r1Token );
	currX = p.getX().longValue();
	Assert.assertEquals(update(initX), currX);
	try{
	    Transaction.commit(); // The commit succeeds with conflict detection granularity of word-level 
	    Assert.assertTrue(false); // Fields with different VBoxes will not conflict
	}catch(CommitException e){
	    Assert.assertTrue(true); 
	    rwTrx1.abortTx();
	}
	//
	// Main thread starts a new transaction
	//
	Assert.assertEquals(initX, p.getX().longValue());
	Assert.assertEquals(update(initY), p.getY().longValue());
    }
    private static long update(long src){
	return (src*4+6)/2;
    } 

}
