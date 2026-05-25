# Etapa 1: Compilación
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app

# Copiamos el pom y TODO el contenido de la carpeta donde estemos
COPY . .

# Compilamos saltando los tests
RUN mvn clean package -DskipTests

# Etapa 2: Ejecución
FROM amazoncorretto:17-alpine-jdk
WORKDIR /app

# Buscamos el jar generado en la carpeta target y lo copiamos
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
