package jvstm.test.jwormbench;

import jwormbench.core.INode;
import jwormbench.factories.INodeFactory;


public class JvstmBenchNodeFactory implements INodeFactory{
    public INode make(int initValue){
	return new BenchWorldNodeAom(initValue);
    }
}
