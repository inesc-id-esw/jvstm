#!/bin/sh

rm simple/*.class
javac -cp ../../target/jvstm-1.0-SNAPSHOT.jar:. simple/SimpleParallelAnnotationsTest.java
javac -cp ../../target/jvstm-1.0-SNAPSHOT.jar:. simple/SimpleDisjointAnnotationsTest.java
java -cp ../../target/jvstm-1.0-SNAPSHOT.jar:../../lib/asm-debug-all-4.0.jar jvstm.atomic.ProcessAtomicAnnotations simple
java -cp ../../target/jvstm-1.0-SNAPSHOT.jar:../../lib/asm-debug-all-4.0.jar jvstm.atomic.ProcessParNestAnnotations simple
java -cp ../../target/jvstm-1.0-SNAPSHOT.jar:. simple.SimpleParallelAnnotationsTest
java -cp ../../target/jvstm-1.0-SNAPSHOT.jar:. simple.SimpleDisjointAnnotationsTest
rm simple/*.class
