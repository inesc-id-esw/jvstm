#!/bin/sh

rm simple/*.class
javac -cp ../../target/jvstm-1.0-SNAPSHOT.jar:. simple/SimpleParallelTest.java
java -cp ../../target/jvstm-1.0-SNAPSHOT.jar:. simple.SimpleParallelTest
javac -cp ../../target/jvstm-1.0-SNAPSHOT.jar:. simple/SimpleUnsafeTest.java
java -cp ../../target/jvstm-1.0-SNAPSHOT.jar:. simple.SimpleUnsafeTest
rm simple/*.class
