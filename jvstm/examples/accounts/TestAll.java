import java.util.Random;

public class TestAll {

    Account ac[];
    int count = 0;

    TestAll(int numAccounts, String kind) {
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

    synchronized void finished() {
        count++;
    }

    static void mySleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
        }        
    }


    public static void main(String[] args) {
        final String kind = args[3];
        final boolean doCommits = (kind.equals("V"));
        final int numAccounts = Integer.parseInt(args[0]);
        final int numThreads = Integer.parseInt(args[1]);
        final int numTotal = Integer.parseInt(args[2]);

        Transaction.begin();
        final TestAll ti = new TestAll(numAccounts, kind);
        Transaction.commit();

        final Stats stats = new Stats(args[3], numThreads, numTotal, numAccounts);

        Thread threads[] = new Thread[numThreads];

        for (int i = 0; i < numThreads; i++) {
            final int tnum = i;
            threads[i] = new Thread() {
                public void run() {
                    //Transaction.beginReadOnly();
                    Transaction.begin();
                    long sum = 0;
                    long loops = (numTotal / numAccounts) / numThreads;
                    long countDown = loops/100;
                    for (int i = 0; i < loops; i++) {
                        for (int k = 0; k < numAccounts; k++) {
                            sum += ti.ac[k].getBalance();
                        }
                        countDown--;
                        if (countDown == 0) {
                            //if (doCommits) Transaction.checkpoint();
                            countDown = loops/100;
                        }   
                    }
                    //System.out.println("#" + tnum + " Balance: " + sum);
                    //stats.statIt(start, System.currentTimeMillis());
                    Transaction.commit();
                    ti.finished();
                }
            };
        }

        final long start = System.currentTimeMillis();

        Thread changer = new Thread() {
                public void run() {
                    Transaction.begin();
                    Random rnd = new Random();
                    while (ti.count < numThreads) {
                        int from = rnd.nextInt(numAccounts);
                        long amount = Math.min(10, ti.ac[from].getBalance());
                        ti.ac[from].withdraw(amount);
                        ti.ac[numAccounts - from - 1].deposit(amount);
                        Transaction.checkpoint();
                        mySleep(200);
                    }
                    Transaction.commit();
                    System.out.println(kind 
                                       + "\t" + numThreads 
                                       + "\t" + numAccounts 
                                       + "\t" + (System.currentTimeMillis() - start));
                }
            };

        changer.start();

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
        System.out.println(kind 
                           + "\t" + numThreads 
                           + "\t" + numAccounts 
                           + "\t" + (System.currentTimeMillis() - start));
    }

    static class Stats {
        String kind;
        int numThreads;
        long numOps;
        int numAccounts;

        Stats(String kind, int numThreads, long numOps, int numAccounts) {
            this.kind = kind;
            this.numThreads = numThreads;
            this.numOps = numOps;
            this.numAccounts = numAccounts;
        }

        long min = -1;
        long max = -1;
        long sum = 0;
        int count = 0;
        
        synchronized void statIt(long start, long end) {
            long diff = end - start;
            if (min == -1) {
                min = diff;
                max = diff;
            } else {
                min = Math.min(min, diff);
                max = Math.max(max, diff);                
            }
            
            sum += diff;
            count++;
        }

        void print() {
            System.out.println(kind 
                               + "\t" + numThreads 
                               + "\t" + count 
                               + "\t" + numAccounts 
                               + "\t" + min 
                               + "\t" + max 
                               + "\t" + sum
                               + "\t" + ((sum*1000/count) / numOps));
        }
    }
}
