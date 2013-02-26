package jvstm.test.point.utests.gc;

import jvstm.test.point.impl.AomIntPointFactory;
import jvstm.test.point.impl.AomIntegerPointFactory;

/**
 * !!!! This test must run with the GC disabled:
 *   -Djvstm.aom.reversion.disabled=true
 */
public class AomGcTestForIntegerPoint extends AomGcTest<Integer>{

    public AomGcTestForIntegerPoint() {
	super(new AomIntegerPointFactory());
    }
    
}
