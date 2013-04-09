package arraytree;

import java.lang.reflect.InvocationTargetException;

public abstract class JVSTMTest<T extends Comparable<T>> {

    protected long lastTime;

    public void createTopLevels(Thread[] topLevels, Class... clazzes) {
	for (int i = 0; i < topLevels.length; i++) {
	    try {
		topLevels[i] = (Thread) clazzes[i % clazzes.length].getConstructors()[0].newInstance(this);
	    } catch (InstantiationException e) {
		e.printStackTrace();
	    } catch (IllegalAccessException e) {
		e.printStackTrace();
	    } catch (IllegalArgumentException e) {
		e.printStackTrace();
	    } catch (SecurityException e) {
		e.printStackTrace();
	    } catch (InvocationTargetException e) {
		e.printStackTrace();
	    }
	}
    }

    public void startTopLevels(Thread[] topLevels) {
	for (int i = 0; i < topLevels.length; i++) {
	    topLevels[i].start();
	}
    }

    public void joinTopLevels(Thread[] topLevels) throws InterruptedException {
	for (int i = 0; i < topLevels.length; i++) {
	    topLevels[i].join();
	}
    }

    public abstract void before() throws Exception;

    public abstract void execute() throws Exception;

    public abstract void after();

    public abstract T obtainResult();

    public abstract T expectedValue();

    public boolean test() throws Exception {
	before();
	long startTime = System.nanoTime();
	execute();
	long totalTime = System.nanoTime() - startTime;
	after();
	final T expectedValue = expectedValue();
	final T obtainedValue = obtainResult();
	lastTime = totalTime / 1000000;
	System.err.println("Counter: " + expectedValue + " [Expected]");
	System.err.println("Counter: " + obtainedValue + " in " + lastTime + " ms ");
	return expectedValue.equals(obtainedValue);
    }
}
