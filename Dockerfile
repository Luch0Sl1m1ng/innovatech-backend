# Etapa 1: Compilación
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app

# Copiamos absolutamente todo el contenido de la carpeta del microservicio
COPY . .

# TRUCO: Buscamos dónde está el pom.xml y ejecutamos el build ahí mismo
RUN mvn clean package -DskipTests -f $(find . -name "pom.xml" | head -n 1)

# Etapa 2: Ejecución
FROM amazoncorretto:17-alpine-jdk
WORKDIR /app

# Buscamos el .jar generado (este comando lo encuentra donde sea que Maven lo haya dejado)
RUN apk add --no-cache findutils
COPY --from=build /app/**/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
