VERSION = 1.0

LIBS = libbzdev-base.jar libbzdev-ejws.jar libbzdev.jar cvrdecode.jar \
	javase.jar core.jar

server.jar: $(LIBS) server/server.mf server/Server.java server/webpage.html \
		server/Adapter.java 
	(cd server ; make)

docker: server.jar
	docker build --tag wtzbzdev/cvrdecode:$(VERSION) .

#
# Version to push if the latest version is being updated.
# must be logged into Docker Hub.
#
#
docker-release: server.jar
	docker build --tag wtzbzdev/cvrdecode:$(VERSION) \
		--tag wtzbzdev/cvrdecode .
	docker push wtzbzdev/cvrdecode:$(VERSION)
	docker push wtzbzdev/cvrdecode

start:
	docker run --publish 80:80 --detach --name cvrdecode \
		wtzbzdev/cvrdecode:$(VERSION)

start-traced:
	docker run --publish 80:80 --env TRACE=true --detach --name cvrdecode \
		wtzbzdev/cvrdecode:$(VERSION)

stop:
	docker stop cvrdecode
	docker rm cvrdecode

libbzdev-base.jar: /usr/share/java/libbzdev-base.jar
	cp $< $@

libbzdev-ejws.jar: /usr/share/java/libbzdev-ejws.jar
	cp $< $@

libbzdev.jar: /usr/share/java/libbzdev.jar
	cp $< $@

cvrdecode.jar: ../cvrdecode.jar
	cp $< $@

javase.jar: /usr/share/java/javase.jar
	cp $< $@

core.jar: /usr/share/java/core.jar
	cp $< $@


libs: $(LIBS)

clean:
	rm $(LIBS) server.jar
	(cd server; make clean)
