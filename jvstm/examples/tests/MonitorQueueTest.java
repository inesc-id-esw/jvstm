import jvstm.*;

public class MonitorQueueTest {

    static class Suicidal implements Runnable {
        public void run() {
            Transaction.begin();
            // die...
        }
    }

    static class Sleeper implements Runnable {
        private long howMuch;

        Sleeper(long howMuch) {
            this.howMuch = howMuch;
        }

        @Atomic public void run() {
            sleep(howMuch);
        }
    }
    
    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            // nop
        }
    }

    public static void main(String[] args) {
        System.out.println("Will start now...");
        new Thread(new Sleeper(1000)).start();
        new Thread(new Suicidal()).start();
        sleep(1000);
        new Thread(new Sleeper(200000)).start();
        new Thread(new Sleeper(1000)).start();
    }
}
