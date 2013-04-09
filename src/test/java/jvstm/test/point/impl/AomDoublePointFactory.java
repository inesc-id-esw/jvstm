package jvstm.test.point.impl;

import jvstm.test.point.core.Point;
import jvstm.test.point.core.PointFactory;

public class AomDoublePointFactory implements PointFactory<Double>{

    @Override
    public Point<Double> make(Number x, Number y) {
        return new AomDoublePoint(x.doubleValue(), y.doubleValue());
    }

}
