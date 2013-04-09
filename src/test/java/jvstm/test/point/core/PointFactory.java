package jvstm.test.point.core;

public interface PointFactory<T extends Number>{
  Point<T> make(Number paramInt1, Number paramInt2);
}