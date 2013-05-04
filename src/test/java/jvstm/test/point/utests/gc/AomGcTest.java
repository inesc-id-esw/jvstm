package jvstm.test.point.utests.gc;

import junit.framework.Assert;
import jvstm.ActiveTransactionsRecord;
import jvstm.Transaction;
import jvstm.VBox;
import jvstm.gc.TxContext;
import jvstm.test.point.core.Point;
import jvstm.test.point.core.PointFactory;
import jvstm.test.point.core.PointFields;

import org.junit.Before;
import org.junit.Test;


/**
 * This is an abstract class that defines the entry points for several unit tests
 * that checks the correct functioning of the versioned boxes GC.
 * For each specialization of Point<T> we should provide the corresponding factory and
 * the unit test, which inherits from this class TestPoint<T> passing to the constructor
 * the concrete PointFactory<T> instance.
 *
 * !!!! This test must run with the GC disabled:
 *   -Djvstm.aom.reversion.disabled=true
 *
 * @author Fernando Miguel Carvalho
 */
public abstract class AomGcTest<T extends Number>{

    protected final PointFactory<T> pointFac;

    public AomGcTest(PointFactory<T> pointFac) {
        this.pointFac = pointFac;
    }
    
    @Before
    public void setUp(){
        TxContext currentCtx = Transaction.allTxContexts;
        while (currentCtx != null) {
            currentCtx.inCommitAndBegin = false;
            currentCtx.oldestRequiredVersion = null;
            currentCtx = currentCtx.next();
        }
        Transaction.gcTask.runGc();
    }

    @Test
    public void testTwoReversions(){
        int trxNumber = Transaction.mostRecentCommittedRecord.transactionNumber;
        int nrOfTries =  ActiveTransactionsRecord.nrOfTries;
        int nrOfReversions =  ActiveTransactionsRecord.nrOfReversions;
        Point<T> p = pointFac.make(7, 9);
        PointFields<T> fields = new PointFields<T>((Class<T>) p.getClass());

        // The factory instantiates a Point object and the
        // constructor does not invoke any barrier, so the
        // Point p must remain in the compact layout.
        Assert.assertSame(null, ((VBox) p).body);

        // The following update will create an Inevitable transaction
        // to perform the write operation in the Point coordinates and
        // it will be extended.
        p.setX(p.getX().intValue() + 1); // the get/set properties perform STM barriers.
        Assert.assertNotSame(null, ((VBox) p).body);
        Assert.assertEquals(++trxNumber, ((VBox) p).body.version);
        Assert.assertEquals(0, ((VBox) p).body.next.version);
        Assert.assertEquals(8, p.getX().longValue()); // the body contains the new value
        Assert.assertEquals(7, fields.getX(p).longValue()); // the field contains the original value
        Assert.assertEquals(9, p.getY().longValue()); // the body contains the old value
        Assert.assertEquals(9, fields.getY(p).longValue()); // the field contains the original value

        // After running the GC the Point object should be reverted.
        // And the values of the most recent body will be copied to
        // the standard fields of the Point object.
        Transaction.gcTask.runGc();
        Assert.assertSame(null, ((VBox) p).body);
        Assert.assertEquals(8, p.getX().longValue()); // the STM barrier reads the object in-place
        Assert.assertEquals(8, fields.getX(p).longValue()); // the field contains the new value
        Assert.assertEquals(9, p.getY().longValue());
        Assert.assertEquals(9, fields.getY(p).longValue());

        // The following update is made inside an explicit transaction.
        Transaction.begin();
        p.setY(p.getY().intValue() + 1); // the get/set properties perform STM barriers.
        Transaction.commit();
        Assert.assertEquals(++trxNumber, ((VBox) p).body.version);
        Assert.assertEquals(0, ((VBox) p).body.next.version);
        Assert.assertEquals(8, p.getX().longValue()); // the body contains the old value
        Assert.assertEquals(8, fields.getX(p).longValue()); // the field contains the old value
        Assert.assertEquals(10, p.getY().longValue()); // the body contains the new value
        Assert.assertEquals(9, fields.getY(p).longValue()); // the field contains the original value

        // After running the GC the Point object should be reverted.
        // And the values of the most recent body will be copied to
        // the standard fields of the Point object.
        Transaction.gcTask.runGc();
        Assert.assertSame(null, ((VBox) p).body);
        Assert.assertEquals(8, p.getX().longValue()); // the STM barrier reads the object in-place
        Assert.assertEquals(8, fields.getX(p).longValue()); // the field contains the new value
        Assert.assertEquals(10, p.getY().longValue());
        Assert.assertEquals(10, fields.getY(p).longValue());

        // Check the number of reversions
        Assert.assertEquals(nrOfTries + 2, ActiveTransactionsRecord.nrOfTries);
        Assert.assertEquals(nrOfReversions + 2, ActiveTransactionsRecord.nrOfReversions);
    }

    @Test
    public void testOneReversion(){
        int trxNumber = Transaction.mostRecentCommittedRecord.transactionNumber;
        int nrOfTries =  ActiveTransactionsRecord.nrOfTries;
        int nrOfReversions =  ActiveTransactionsRecord.nrOfReversions;
        Point<T> p = pointFac.make(7, 9);
        PointFields<T> fields = new PointFields<T>((Class<T>) p.getClass());

        // The factory instantiates a Point object and the
        // constructor does not invoke any barrier, so the
        // Point p must remain in the compact layout.
        Assert.assertSame(null, ((VBox) p).body);

        // The following update is made inside an explicit transaction.
        Transaction.begin();
        p.setY(p.getY().intValue() + 1); // the get/set properties perform STM barriers.
        Transaction.commit();
        Assert.assertNotSame(null, ((VBox) p).body); // object extended
        Assert.assertEquals(++trxNumber, ((VBox) p).body.version); // first version 2
        Assert.assertEquals(0, ((VBox) p).body.next.version); // the last one 0
        Assert.assertEquals(7, p.getX().longValue()); // the body contains the original value
        Assert.assertEquals(7, fields.getX(p).longValue()); // the field contains the original value
        Assert.assertEquals(10, p.getY().longValue()); // the body contains the new value
        Assert.assertEquals(9, fields.getY(p).longValue()); // the field contains the original value

        // The following update will create an Inevitable transaction
        // to perform the write operation in the Point coordinates and
        // it will be extended.
        p.setX(p.getX().intValue() + 1); // the get/set properties perform STM barriers.
        Assert.assertNotSame(null, ((VBox) p).body);
        Assert.assertEquals(++trxNumber, ((VBox) p).body.version);
        Assert.assertEquals(trxNumber-1, ((VBox) p).body.next.version);
        Assert.assertEquals(0, ((VBox) p).body.next.next.version);
        Assert.assertEquals(8, p.getX().longValue()); // the body contains the new value
        Assert.assertEquals(7, fields.getX(p).longValue()); // the field contains the original value
        Assert.assertEquals(10, p.getY().longValue()); // the body contains the new value
        Assert.assertEquals(9, fields.getY(p).longValue()); // the field contains the original value


        // After running the GC the Point object should be reverted.
        // And the values of the most recent body will be copied to
        // the standard fields of the Point object.
        Transaction.gcTask.runGc();
        Assert.assertSame(null, ((VBox) p).body);
        Assert.assertEquals(8, p.getX().longValue()); // the STM barrier reads the object in-place
        Assert.assertEquals(8, fields.getX(p).longValue()); // the field contains the new value
        Assert.assertEquals(10, p.getY().longValue());
        Assert.assertEquals(10, fields.getY(p).longValue());

        // Check the number of reversions
        Assert.assertEquals(nrOfTries + 1, ActiveTransactionsRecord.nrOfTries);
        Assert.assertEquals(nrOfReversions + 1, ActiveTransactionsRecord.nrOfReversions);
    }


    @Test
    public void testMultiplePoints(){
        int trxNumber = Transaction.mostRecentCommittedRecord.transactionNumber;
        int nrOfTries =  ActiveTransactionsRecord.nrOfTries;
        int nrOfReversions =  ActiveTransactionsRecord.nrOfReversions;
        Point<T>[] p = new Point[13];
        for (int i = 0; i < p.length; i++) {
            p[i] = pointFac.make(7, 9);
        }
        PointFields<T> fields = new PointFields<T>((Class<T>) pointFac.make(7, 9).getClass());


        // The factory instantiates a Point object and the
        // constructor does not invoke any barrier, so the
        // Point p must remain in the compact layout.
        for (int i = 0; i < p.length; i++) {
            Assert.assertSame(null, ((VBox) p[i]).body);
        }

        // The following update is made inside an explicit transaction.
        Transaction.begin();
        for (int i = 0; i < p.length; i++) {
            p[i].setY(p[i].getY().intValue() + 1); // the get/set properties perform STM barriers.
        }
        Transaction.commit();
        ++trxNumber;
        for (int i = 0; i < p.length; i++) {
            Assert.assertNotSame(null, ((VBox) p[i]).body); // object extended
            Assert.assertEquals(trxNumber, ((VBox) p[i]).body.version); // first version 2
            Assert.assertEquals(0, ((VBox) p[i]).body.next.version); // the last one 0
            Assert.assertEquals(7, p[i].getX().longValue()); // the body contains the original value
            Assert.assertEquals(7, fields.getX(p[i]).longValue()); // the field contains the original value
            Assert.assertEquals(10, p[i].getY().longValue()); // the body contains the new value
            Assert.assertEquals(9, fields.getY(p[i]).longValue()); // the field contains the original value
        }

        // After running the GC the Point object should be reverted.
        // And the values of the most recent body will be copied to
        // the standard fields of the Point object.
        Transaction.gcTask.runGc();
        for (int i = 0; i < p.length; i++) {
            Assert.assertSame(null, ((VBox) p[i]).body);
            Assert.assertEquals(7, p[i].getX().longValue()); // the STM barrier reads the object in-place
            Assert.assertEquals(7, fields.getX(p[i]).longValue()); // the field contains the new value
            Assert.assertEquals(10, p[i].getY().longValue());
            Assert.assertEquals(10, fields.getY(p[i]).longValue());
        }

        // Check the number of reversions
        Assert.assertEquals(nrOfTries + 13, ActiveTransactionsRecord.nrOfTries);
        Assert.assertEquals(nrOfReversions + 13, ActiveTransactionsRecord.nrOfReversions);
    }
}
