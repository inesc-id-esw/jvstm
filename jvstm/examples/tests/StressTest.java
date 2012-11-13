import jvstm.*;
import pt.ist.esw.atomicannotation.Atomic;
import java.util.Random;
import java.util.concurrent.*;

public class StressTest {

    private VBox<Long>[] nums;

    public StressTest(int num) {
        nums = new VBox[num];
        for (int i = 0; i < num; i++) {
            nums[i] = new VBox<Long>(0L);
        }
    }

    void start(int numThreads) {
        Executor executor = Executors.newFixedThreadPool(numThreads);
        for (int i = 0; i < numThreads; i++) {
            executor.execute(new Worker());
        }
    }
    

    class Worker implements Runnable {
        private Random rnd = new Random();

        public void run() {
            while (true) {
                long total = sumAll();

//                 if (total == 1000) {
//                     System.out.println("Found one 1000!!!");
//                 }
                
                changeOne(total < 1000);
            }
        }

        @Atomic long sumAll() {
            long sum = 0;
            for (int i = 0; i < nums.length; i++) {
                sum += nums[i].get();
            }
            return sum;
        }
        
        @Atomic void changeOne(boolean inc) {
            int pos = rnd.nextInt(nums.length);

            long val = nums[pos].get() + (inc ? 1 : -1);
            nums[pos].put(val);
        }
    }
    

    public static void main(String[] args) throws Exception {
        Thread.sleep(5000);
        System.out.println("Will start now...");
        new StressTest(Integer.parseInt(args[0])).start(Integer.parseInt(args[1]));
    }
}
