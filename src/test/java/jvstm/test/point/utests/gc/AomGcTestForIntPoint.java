package jvstm.test.point.utests.gc;

import jvstm.test.point.impl.AomIntPointFactory;

/**
 * !!!! This test must run with the GC disabled:
 *   -Djvstm.aom.reversion.disabled=true
 */
public class AomGcTestForIntPoint extends AomGcTest<Integer>{

    public AomGcTestForIntPoint() {
        super(new AomIntPointFactory());
    }

}
