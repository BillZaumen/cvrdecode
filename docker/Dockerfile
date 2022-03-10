FROM openjdk:11.0.14-jre

COPY . /usr/app

WORKDIR /usr/app

EXPOSE 80/tcp

ENV BACKLOG=30 NTHREADS=50 TRACE=false IPADDR=wildcard PORT=80

CMD ["java", "-jar", "server.jar"]
