import java.util.Random;
import jvstm.Transaction;

public class TestIt {

    VAccount ac;

    TestIt() {
        ac = new VAccount(10);
    }


    static void mySleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
        }        
    }


    public static void main(String[] args) {
        final TestIt ti = new TestIt();
        Transaction.commit();

        final Random rnd = new Random();

        for (int i = 0; i < 10; i++) {
            final int tnum = i;
            new Thread() {
                public void run() {
                    for (int i = 0; i < 10; i++) {
                        boolean ok = false;
                        while (! ok) {
                            //mySleep(rnd.nextInt(10));
                            ti.ac.deposit(tnum);
                            try {
                                Transaction.commit();
                                ok = true;
                            } catch (Throwable e) {
                                Transaction.abort();
                            }                            
                        }
                        System.out.println("#" + tnum + " Balance: " + ti.ac.getBalance());
                    }
                    
                }
            }.start();
        }
        
    }
}
