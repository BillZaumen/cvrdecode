#!/bin/sh

JLDIR=/usr/share/java
CP1=$JLDIR/libbzdev-base.jar:$JLDIR/libbzdev-desktop.jar
CP=$CP1:$JLDIR/core.jar:$JLDIR/javase.jar

java -classpath /usr/share/cvrdecode/cvrdecode.jar:$CP CaVaxRecDecoder "$@"
