package jvstm.test.point.runners;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.Callable;

import junit.framework.Assert;
import jvstm.ActiveTransactionsRecord;
import jvstm.Transaction;
import jvstm.VBox;
import jvstm.test.point.core.Point;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RunMultipleThreadsInLoop {

  private static final Random rand = new Random();
  private static final Logger logger = LoggerFactory.getLogger(RunMultipleThreadsInLoop.class);

  public static <T extends Number> void performTest(
      final int nrOfThreads,
      final int nrOfIterations,
      final Point<T> p ) throws InterruptedException, IOException{
    final Thread[] threads = new Thread[nrOfThreads];
    final long coordsSum = p.getX().longValue() + p.getY().longValue();
    for (int i = 0; i < threads.length; i++) {
      threads[i] = new Thread(){@Override public void run() {
        workerThread(nrOfIterations, p, coordsSum);
        logger.info("Thread {} finish!!!", Thread.currentThread().getId());
      }};
    }
    for (int i = 0; i < threads.length; i++) {
      threads[i].start();
    }
    for (int i = 0; i < threads.length; i++) {
      threads[i].join();
      logger.info("Thread {} release join!!!", threads[i].getId());
    }
    Thread.sleep(600);
    logger.info("Number of reversions = {}", ActiveTransactionsRecord.nrOfReversions);
    logger.info("Number of tries = {}", ActiveTransactionsRecord.nrOfTries);
    logger.info("Object is {}", ((VBox)p).body == null? "COMPACT" : "EXTENDED");
    long currSum = p.getX().longValue() + p.getY().longValue();
    Assert.assertEquals("Final verification: ", coordsSum, currSum);
  }
  public static <T extends Number> void workerThread(
      final int nrOfIterations,
      final Point<T> p,
      final long coordsSum
  ){
    for (int j = 0; j < nrOfIterations; j++) {
      try {
        final int idx = j;
        final String trxKind = "RW";
        String res = Transaction.doIt(new Callable<String>() {
          public String call() throws Exception {
            long x = p.getX().longValue();
            long y = p.getY().longValue();
            int valueToAdd = rand.nextInt(10) - 5;
            p.setX(x + valueToAdd);
            p.setY(y - valueToAdd);
            String res = Thread.currentThread().getName() + ": [x=" + x + ", y=" + y + "] ===> " + "[x=" + (x+valueToAdd) + ", y=" + (y-valueToAdd) + "]";
            return res;
          }
        });
        // logger.info("iteration {} - {} - has read: {}", j, trxKind, res);
        long currSum = Transaction.doIt(new Callable<Long>(){ public Long call() throws Exception {
            long x = p.getX().longValue();
            long y = p.getY().longValue();
            return x + y;
        }}, true);
        Assert.assertEquals(String.format("iter: %d", idx), coordsSum, currSum);
      } catch (Exception e) {
        e.printStackTrace();
        break;
      }
    }
  }

}
