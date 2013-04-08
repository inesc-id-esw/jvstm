package jvstm.test.point.core;

import junit.framework.TestCase;
import jvstm.Transaction;
import jvstm.test.point.runners.RunMultipleThreadsInLoop;
import jvstm.test.point.runners.RunRwWithRo;
import jvstm.test.point.runners.RunRwWithRw;
import jvstm.test.point.runners.RunRwWithRwConflictDisjointFields;
import jvstm.test.point.runners.RunRwWithRwConflictSameFields;
import jvstm.test.point.runners.RunRwWithRwNoWaitAllExtendedObjects;
import jvstm.test.point.runners.RunSingleThread;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * This is an abstract class that defines the entry points for several unit tests performing
 * transactions and operations over a Point<T> instance.
 * For each specialization of Point<T> we should provide the corresponding factory and
 * the unit test, which inherits from this class TestPoint<T> passing to the constructor
 * the concrete PointFactory<T> instance.
 *
 * @author Fernando Miguel Carvalho
 */
public abstract class TestPoint<T extends Number> extends TestCase{
    protected final PointFactory<T> pointFac;

    public TestPoint(PointFactory<T> pointFac) {
        this.pointFac = pointFac;
    }
    @Before
    public void setUp(){
        if(Transaction.current() != null)
            Transaction.abort();
        // Transaction.mostRecentCommittedRecord.clean(); ????? NullPointerExcpetion

        // Transaction.suspend();
        // Assert.assertEquals(0, TopLevelCounter.getRunning());
    }
    @After
    public void tearDown(){
        // Assert.assertEquals(0, TopLevelCounter.getRunning());
    }
    @Test
    public void testRunSingleThread() throws Exception{
        RunSingleThread.performTest(pointFac.make(7, 8));
    }
    @Test
    public void testRwWithRo() throws Exception{
        RunRwWithRo.performTest(pointFac.make(7, 8));
    }
    @Test
    public void testRunRwWithRw() throws Exception{
        RunRwWithRw.performTest(pointFac.make(7, 8));
    }
    @Test
    public void testRwWithRwConflictDisjointFields() throws Exception{
        RunRwWithRwConflictDisjointFields.performTest(pointFac.make(7, 8));
    }
    @Test
    public void testRwWithRwConflictSameFields() throws Exception{
        RunRwWithRwConflictSameFields.performTest(pointFac.make(7, 8));
    }
    @Test
    public void testRwWithRwNoWaitAllExtendedObjects() throws Exception{
        RunRwWithRwNoWaitAllExtendedObjects.performTest(pointFac.make(7, 8));
    }
    @Test
    public void testRunMultipleThreadsInLoop() throws Exception{
        RunMultipleThreadsInLoop.performTest(8, 1024, pointFac.make(7, 8));
    }
}
