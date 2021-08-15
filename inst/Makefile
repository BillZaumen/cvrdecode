
all: cvrdecode.jar
	rm -rf TBUILD
	mkdir TBUILD
	javac -d TBUILD Installer.java
	(cd .. ; git log -64 ) > TBUILD/changelog
	echo >> TBUILD/changelog
	echo --- Earlier changes not shown --- >> TBUILD/changelog
	cp cvrdecode.jar TBUILD/cvrdecode.jar
	cp /usr/share/java/libbzdev-base.jar TBUILD/libbzdev-base.jar
	cp /usr/share/java/libbzdev-desktop.jar TBUILD/libbzdev-desktop.jar
	cp /usr/share/java/core.jar TBUILD/core.jar
	cp /usr/share/java/javase.jar TBUILD/javase.jar
	cp ../cvrdecode.1 TBUILD/cvrdecode.1
	cp ../copyright TBUILD/copyright
	cp ../cvrdecode.desktop TBUILD/cvrdecode.desktop
	gzip -9 < TBUILD/cvrdecode.1 > TBUILD/cvrdecode.1.gz
	groff -T html ../cvrdecode.1 > TBUILD/cvrdecode.html
	mkdir -p icons/hicolor/scalable/apps
	cp ../cvrdecode.svg icons/hicolor/scalable/apps/cvrdecode.svg
	mkdir -p icons/hicolor
	for i in 512 256 128 96 72 64 48 32 24 16 8 ; do \
	mkdir -p icons/hicolor/$${i}x$${i}/apps ; \
	inkscape -w $$i \
	  --export-filename=icons/hicolor/$${i}x$${i}/apps/cvrdecode.png \
	  ../cvrdecode.svg ; done
	(zfile=`pwd`/TBUILD/share.zip ; zip -r -q $$zfile icons)
	rm -r icons
	cp finish.txt TBUILD/finish.txt
	echo '#!/bin/sh' > TBUILD/cvrdecode.sh
	echo 'JAVA -jar CVRDECODEDIR/cvrdecode.jar "$$@"' >> TBUILD/cvrdecode.sh
	echo '@JAVA -jar CVRDECODEDIR/cvrdecode.jar %*' > TBUILD/cvrdecode.cmd
	echo '@JAVAW -jar CVRDECODEDIR/cvrdecode.jar %*' > TBUILD/cvrdecodew.cmd
	jar cfm cvrdecode-install.jar install.mf -C TBUILD .

cvrdecode.jar: ../classes/CaVaxRecDecoder.class manifest.mf
	jar cfm cvrdecode.jar manifest.mf  -C ../classes .

../classes/CaVaxRecDecoder.class: ../CaVaxRecDecoder.java ../cvrdecode.svg
	(cd .. ; make cvrdecode.jar)

clean:
	rm -rf TBUILD cvrdecode.jar

superclean: clean
	rm -f cvrdecode-install.jar
