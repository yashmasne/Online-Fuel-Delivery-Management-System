FROM tomcat:10.1-jdk17-temurin AS build
WORKDIR /src
COPY src ./src
RUN mkdir -p /app/WEB-INF/classes /app/WEB-INF/lib \
 && cp -r src/main/webapp/. /app/ \
 && javac --release 17 -nowarn \
      -cp /usr/local/tomcat/lib/servlet-api.jar \
      -d /app/WEB-INF/classes \
      $(find src/main/java -name '*.java') \
 && curl -fsSL -o /app/WEB-INF/lib/mysql-connector-j-9.6.0.jar \
      https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/9.6.0/mysql-connector-j-9.6.0.jar

FROM tomcat:10.1-jdk17-temurin

# Install MariaDB for self-contained zero-config deployment
RUN apt-get update \
 && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends mariadb-server \
 && rm -rf /var/lib/apt/lists/*

RUN rm -rf /usr/local/tomcat/webapps/*
COPY --from=build /app /usr/local/tomcat/webapps/ROOT
COPY --from=build /app /usr/local/tomcat/webapps/Online_Fuel_Delivery_Management_System
COPY fuel_delivery.sql /app/fuel_delivery.sql
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

EXPOSE 8080
ENTRYPOINT ["/entrypoint.sh"]