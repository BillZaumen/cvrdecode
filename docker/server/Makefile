
all: ../server.jar

../server.jar: server.mf *.java webpage.html
	(cd .. ; make libs)
	mkdir -p classes
	javac -d classes -classpath \
		classes:/usr/share/bzdev/libbzdev.jar:../../cvrdecode.jar \
		*.java
	jar cmf server.mf ../server.jar -C classes . webpage.html
