#!/bin/sh

rm simple/*.class
javac -cp ../../target/jvstm-1.0-SNAPSHOT.jar:. simple/SimpleParallelTest.java
java -cp ../../target/jvstm-1.0-SNAPSHOT.jar:. simple.SimpleParallelTest
javac -cp ../../target/jvstm-1.0-SNAPSHOT.jar:. simple/SimpleDisjointTest.java
java -cp ../../target/jvstm-1.0-SNAPSHOT.jar:. simple.SimpleDisjointTest
rm simple/*.class
