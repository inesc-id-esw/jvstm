package jvstm.test.point.impl;

import jvstm.test.point.core.Point;
import jvstm.test.point.core.PointFactory;

public class AomShortPointFactory implements PointFactory<Short>{

    @Override
    public Point<Short> make(Number x, Number y) {
	return new AomShortPoint(x.shortValue(), y.shortValue());
    }

}
