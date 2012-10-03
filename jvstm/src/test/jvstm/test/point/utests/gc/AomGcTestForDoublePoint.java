package jvstm.test.point.utests.gc;

import jvstm.test.point.impl.AomIntPointFactory;

/**
 * !!!! This test must run with the GC disabled:
 *   -Djvstm.aom.gc.disabled=true
 */
public class AomGcTestForDoublePoint extends AomGcTest<Integer>{

    public AomGcTestForDoublePoint() {
	super(new AomIntPointFactory());
    }
    
}
