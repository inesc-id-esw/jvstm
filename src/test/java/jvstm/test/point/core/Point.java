package jvstm.test.point.core;


public interface Point<T extends Number>{
  T getX();
  T getY();
  void setX(Number x);
  void setY(Number y);
}
