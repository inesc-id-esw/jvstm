package jvstm;

public abstract class VBoxBody<E> {
    public volatile int version = -1;
    public E value;
    
    public abstract VBoxBody<E> getBody(int maxVersion);

    public abstract void commit(VBoxBody<E> previous);
}
