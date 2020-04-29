# we will use openjdk 8 with alpine as it is a very small linux distro
FROM openjdk:8-jre-alpine3.9

# copy the packaged jar file into our docker image
COPY target/scala-2.12/oauth20-provider.jar /oauth20-provider.jar

COPY dependencies/server.jks /

# set the startup command to execute the jar
CMD ["java", "-jar", "/oauth20-provider.jar"]