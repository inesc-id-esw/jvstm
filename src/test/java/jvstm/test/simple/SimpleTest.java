package jvstm.test.simple;
import static junit.framework.Assert.assertNotNull;
import jvstm.Atomic;

import org.junit.Test;

public class SimpleTest {

    @Test
    public void testAtomicAnnotationIsApplied() {
        runAtomicMethod();
    }

    @Atomic
    private void runAtomicMethod() {
        // if running within an atomic there is already a transaction
        assertNotNull(jvstm.Transaction.current());
    }

}
