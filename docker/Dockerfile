FROM eclipse-temurin:17.0.10_7-jre-alpine

COPY . /usr/app

WORKDIR /usr/app

EXPOSE 80/tcp

ENV BACKLOG=30 NTHREADS=50 TRACE=false IPADDR=wildcard PORT=80

CMD ["java", "-jar", "server.jar"]
