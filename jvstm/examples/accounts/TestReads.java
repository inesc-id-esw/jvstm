import java.util.Random;

public class TestReads {

    Account ac[];

    TestReads(int numAccounts, String kind) {
        ac = new Account[numAccounts];

        for (int i = 0; i < numAccounts; i++) {
            if (kind.equals("V"))
                ac[i] = new VAccount(10);
            else if (kind.equals("P"))
                ac[i] = new PAccount(10);
            else
                ac[i] = new SAccount(10);
        }
    }


    static void mySleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
        }        
    }


    public static void main(String[] args) {
        final int numAccounts = Integer.parseInt(args[0]);
        final int numThreads = Integer.parseInt(args[1]);
        final int numLoops = Integer.parseInt(args[2]);
        final TestReads ti = new TestReads(numAccounts, args[3]);

        Transaction.commit();

        for (int i = 0; i < numThreads; i++) {
            final int tnum = i;
            new Thread() {
                public void run() {
                    long sum = 0;
                    for (int i = 0; i < 1000; i++) {
                        for (int j = 0; j < numLoops; j++) {
                            for (int k = 0; k < numAccounts; k++) {
                                sum += ti.ac[k].getBalance();                                
                            }
                        }
                    }
                    System.out.println("#" + tnum + " Balance: " + sum);
                }
            }.start();
        }
        
    }
}
