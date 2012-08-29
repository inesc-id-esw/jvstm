import jvstm.CommitException;
import jvstm.PerTxBox;
import jvstm.Transaction;
import jvstm.VBox;


public class CounterPerTxBox {

	private VBox<Long> count = new VBox<Long>(0L);
	private PerTxBox<Long> countPerTxBox = new PerTxBox<Long>(0L) {
		@Override
		public void commit(Long value) {
			count.put(count.get() + value);
		}
	};

	public long getCount() {
		return count.get() + countPerTxBox.get();
	}

	public void inc() {
		countPerTxBox.put(countPerTxBox.get() + 1);
	}

	protected static final CounterPerTxBox counter = new CounterPerTxBox();
	protected static final int INCREMENTS = 100;
	protected static final int WORKERS = 8;

	public static void main(String[] args) throws InterruptedException {
		Thread[] workers = new Thread[WORKERS];
		for (int i = 0; i < WORKERS; i++) {
			workers[i] = new Worker();
			workers[i].start();
		}
		for (int i = 0; i < WORKERS; i++) {
			workers[i].join();
		}
		System.out.println("Expected: " + (WORKERS * INCREMENTS) + " got: " + counter.getCount());
	}

	protected static class Worker extends Thread {
		public void run() {
			for (int i = 0; i < INCREMENTS; i++) {
				while (true) {
					try {
						Transaction.begin();
						counter.inc();
						Transaction.commit();
						break;
					} catch (CommitException ce) {

					}
				}
			}
		}
	}

}
