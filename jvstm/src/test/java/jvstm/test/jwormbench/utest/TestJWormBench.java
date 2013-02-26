package jvstm.test.jwormbench.utest;

import jvstm.test.jwormbench.RunJWormBench;

import org.junit.Test;

public class TestJWormBench {

    @Test
    public void testJWormBench1() throws InterruptedException{
	RunJWormBench.performTest(512, 4, 21);
    }
    @Test
    public void testJWormBench2() throws InterruptedException{
	RunJWormBench.performTest(512, 4, 22);
    }
    @Test
    public void testJWormBench3() throws InterruptedException{
	RunJWormBench.performTest(512, 4, 23);
    }
    @Test
    public void testJWormBench4() throws InterruptedException{
	RunJWormBench.performTest(512, 4, 51);
    }
    @Test
    public void testJWormBench5() throws InterruptedException{
	RunJWormBench.performTest(512, 4, 52);
    }
    @Test
    public void testJWormBench6() throws InterruptedException{
	RunJWormBench.performTest(512, 4, 53);
    }
}
