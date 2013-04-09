import java.util.Random;
import jvstm.*;

public class TestTransfers {

    private static Random RND = new Random();

    Account ac[];
    Counter counter;
    int restarts = 0;

    TestTransfers(int numAccounts) {
        //counter = new Counter();
        ac = new Account[numAccounts];
        
        for (int i = 0; i < numAccounts; i++) {
            ac[i] = new VAccount(10);
        }
    }

    void transTransferAmount() {
        while (true) {
            Transaction tx = Transaction.begin();
            try {
                transferAmount();
                tx.commit();
                tx = null;
                return;
            } catch (CommitException ce) {
                tx.abort();
                tx = null;
                countRestart();
            } finally {
                if (tx != null) {
                    tx.abort();
                }
            }
        }
    }

    synchronized void countRestart() {
        restarts++;
    }

    void transferAmount() {
        Account acc1 = ac[RND.nextInt(ac.length)];
        Account acc2 = ac[RND.nextInt(ac.length)];

        long value = acc1.getBalance() / 2;

        acc1.withdraw(value);
        acc2.deposit(value);
        counter.inc();
        mySleep(0, 10);
    }

    static void mySleep(long millis, int nanos) {
        try {
            Thread.sleep(millis, nanos);
        } catch (InterruptedException ie) {
        }        
    }


    public static void main(String[] args) {
        final int numAccounts = Integer.parseInt(args[0]);
        final int numThreads = Integer.parseInt(args[1]);
        final int numTotal = Integer.parseInt(args[2]);

        Transaction.begin();
        final TestTransfers ti = new TestTransfers(numAccounts);
        Transaction.commit();

        Thread threads[] = new Thread[numThreads];

        for (int i = 0; i < numThreads; i++) {
            threads[i] = new Thread() {
                public void run() {
                    long loops = numTotal / numThreads;
                    for (int i = 0; i < loops; i++) {
                        ti.transTransferAmount();
                    }
                }
            };
        }

        final long start = System.currentTimeMillis();

        for (int i = 0; i < numThreads; i++) {
            threads[i].start();
        }        
        for (int i = 0; i < numThreads; i++) {
            try {
                threads[i].join();
            } catch (Throwable t) {
                throw new Error("erro");
            }
        }        
        System.out.println("TestTransfers\t" + numThreads 
                           + "\t" + numAccounts 
                           + "\t" + (System.currentTimeMillis() - start));
        System.out.println("Restarts = " + ti.restarts);
        System.out.println("Counter = " + ti.counter.getCount());
    }
}
