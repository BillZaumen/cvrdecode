FROM eclipse-temurin:11.0.18_10-jre-jammy

COPY . /usr/app

WORKDIR /usr/app

EXPOSE 80/tcp

ENV BACKLOG=30 NTHREADS=50 TRACE=false IPADDR=wildcard PORT=80

CMD ["java", "-jar", "server.jar"]
