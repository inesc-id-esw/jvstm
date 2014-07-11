package simple;

import java.util.ArrayList;
import java.util.List;

import jvstm.CommitException;
import jvstm.ParallelTask;
import jvstm.Transaction;
import jvstm.VBox;

/**
 * Test the following scenario
 * A writes x=1
 * A spawns B
 * B spawns C e D
 * C writes x=2
 * C reads y=0
 * D writes y=1
 * D commits
 * C aborts
 * at this point, another top level transaction Z tries to write to x and acquires ownership of the VBox
 * when in fact the ownership belonged to A
 * 
 * 
 * @author nmld
 *
 */
public class AbortedParallelTxTest {

	protected static final VBox<Integer> x = new VBox<Integer>(0);
	protected static final VBox<Integer> y = new VBox<Integer>(0);

	public static class TopLevelA extends Thread {

		public void run() {
			Transaction.begin(false);
			try {
				int valX = x.get();
				x.put(valX + 1);

				List<ParallelTask<Void>> tasks = new ArrayList<ParallelTask<Void>>();
				tasks.add(new NestedB());
				Transaction.current().manageNestedParallelTxs(tasks);

				Transaction.commit();
				
				throw new AssertionError("TopLevelA should not commit because of Z");
				
			} catch (CommitException ce) {
				Transaction.abort();
			} 
		}
	}

	public static class NestedB extends ParallelTask<Void> {

		@Override
		public Void execute() throws Throwable {
			List<ParallelTask<Void>> tasks = new ArrayList<ParallelTask<Void>>();
			tasks.add(new NestedC());
			tasks.add(new NestedD());
			Transaction.current().manageNestedParallelTxs(tasks);
			return null;
		}

	}

	public static class NestedC extends ParallelTask<Void> {

		private boolean isRepeating = false;

		@Override
		public Void execute() throws Throwable {
			if (isRepeating) {
				Thread.sleep(3000);
			}

			int valX = x.get();
			x.put(valX + 1);
			int valY = y.get();

			if (valX != 1) {
				throw new AssertionError("NestedC read valX != 1: " + valX);
			}
			if (!isRepeating && valY != 0) {
				throw new AssertionError("NestedC was not yet aborted, yet it read valY != 0: " + valY);
			}
			if (isRepeating && valY != 1) {
				throw new AssertionError("NestedC was already aborted, yet it read valY != 1: " + valY);
			}

			isRepeating = true;

			Thread.sleep(750);

			return null;
		}

	}

	public static class NestedD extends ParallelTask<Void> {

		@Override
		public Void execute() throws Throwable {
			Thread.sleep(500);

			int valY = y.get();
			y.put(valY + 1);

			if (valY != 0) {
				throw new AssertionError("NestedD read valY != 0: " + valY);
			}

			return null;
		}

	}

	public static class TopLevelZ extends Thread {

		public void run() {
			try {

				Transaction.begin(false);
				Thread.sleep(2000);

				int valX = x.get();
				x.put(valX + 1);
				Transaction.commit();

			} catch (InterruptedException e) {
			}
		}

	}

	public static void main(String[] args) throws InterruptedException {
		TopLevelA A = new TopLevelA();
		TopLevelZ Z = new TopLevelZ();
		A.start();
		Z.start();
		A.join();
		Z.join();
		if (x.get() != 1) {
			throw new AssertionError("Check in the end verified that x != 1");
		}
		if (y.get() != 0) {
			throw new AssertionError("Check in the end verified that y != 0");
		}
		
		System.out.println("Successful test");
	}

}
