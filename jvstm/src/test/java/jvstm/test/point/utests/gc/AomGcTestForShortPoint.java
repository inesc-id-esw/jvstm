package jvstm.test.point.utests.gc;

import jvstm.test.point.impl.AomShortPointFactory;

/**
 * !!!! This test must run with the GC disabled:
 *   -Djvstm.aom.reversion.disabled=true
 */
public class AomGcTestForShortPoint extends AomGcTest<Short>{

    public AomGcTestForShortPoint() {
	super(new AomShortPointFactory());
    }
    
}
