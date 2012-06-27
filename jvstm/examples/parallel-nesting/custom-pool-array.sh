#!/bin/sh

rm arraytree/*.class
javac -cp ../../target/jvstm-1.0-SNAPSHOT.jar:. arraytree/CustomPoolVArray.java
java -cp ../../target/jvstm-1.0-SNAPSHOT.jar:. arraytree.CustomPoolVArray loadTreeBalancedToBottom
rm arraytree/*.class
