#!/bin/sh

rm arraytree/*.class
javac -cp ../../target/jvstm-1.0-SNAPSHOT.jar:. arraytree/CustomPool.java
java -cp ../../target/jvstm-1.0-SNAPSHOT.jar:. arraytree.CustomPool loadTreeBalancedToBottom
rm arraytree/*.class
