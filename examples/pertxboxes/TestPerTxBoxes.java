import jvstm.CommitException;
import jvstm.Transaction;
import jvstm.VBox;


public class TestPerTxBoxes {

	protected static final int INITIAL_X = 4;
	protected static final int INITIAL_Y = 2;

	protected static final VBox<Integer> normalVBox = new VBox<Integer>(0);
	protected static final SwapperPerTxBox swapper = new SwapperPerTxBox(INITIAL_X, INITIAL_Y);
	protected static final CounterPerTxBox counter = new CounterPerTxBox(0L);
	protected static final int OPERATIONS = 1000;
	protected static final int WORKERS = 4;

	public static void main(String[] args) throws InterruptedException {
		Thread[] workers = new Thread[WORKERS];
		for (int i = 0; i < WORKERS; i++) {
			workers[i] = new Worker();
			workers[i].start();
		}
		for (int i = 0; i < WORKERS; i++) {
			workers[i].join();
		}
		if (
				normalVBox.get() != (WORKERS * OPERATIONS) || 
				swapper.getX() != INITIAL_X || swapper.getY() != INITIAL_Y ||
				counter.getCount() != (WORKERS * OPERATIONS)
				) {
			System.out.println("Error: WORKERS * OPS: " + (WORKERS * OPERATIONS) + " vbox: " + normalVBox.get() + " counter " 
					+ counter.getCount() + " x: " + swapper.getX() + " y: " + swapper.getY());
		} else {
			System.out.println("Ok");
		}
	}

	protected static class Worker extends Thread {
		public void run() {
			for (int i = 0; i < OPERATIONS; i++) {
				while (true) {
					Transaction tx = null;
					try {
						tx = Transaction.begin();
						normalVBox.put(normalVBox.get() + 1);
						counter.inc();
						swapper.swapXY();
						Transaction.commit();
						tx = null;
						break;
					} catch (CommitException ce) {
						if (tx != null) {
							tx.abort();
						}
					}
				}
				//				int x, y = 0;
				//				Transaction.begin(true);
				//				x = swapper.getX();
				//				y = swapper.getY();
				//				Transaction.commit();
				//				if ((x == INITIAL_X && y == INITIAL_Y) || (x == INITIAL_Y && y == INITIAL_X)) {
				//					
				//				} else {
				//					System.out.println("Inconsistency after " + i + " iterations x: " + x + " y: " + y);
				//					System.exit(1);
				//				}
			}
		}
	}

}
