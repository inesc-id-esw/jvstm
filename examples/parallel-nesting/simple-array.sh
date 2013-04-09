#!/bin/sh

rm simple/*.class
javac -cp ../../target/jvstm-1.0-SNAPSHOT.jar:. simple/SimpleVArraysParallelTest.java
java -cp ../../target/jvstm-1.0-SNAPSHOT.jar:. simple.SimpleVArraysParallelTest
rm simple/*.class
