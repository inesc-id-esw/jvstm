package jvstm;

public abstract class VBoxBody<E> {
    public volatile int version = -1;
    public E value;
    
    public abstract VBoxBody<E> getBody(int maxVersion);

    public abstract void setPrevious(VBoxBody<E> previous);

    public abstract void clearPrevious();
}
