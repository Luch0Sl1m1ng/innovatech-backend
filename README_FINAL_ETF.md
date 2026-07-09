# Innovatech Chile — Backend (Ventas + Despachos)

Backend de la plataforma Innovatech Chile: dos microservicios en Spring Boot (Ventas y Despachos) desplegados en AWS ECS Fargate, con pipeline CI/CD automatizado y entorno de desarrollo local orquestado con Docker Compose.

## Arquitectura

- **Ventas** (Spring Boot, puerto 8082) — `back-Ventas_SpringBoot/Springboot-API-REST`
- **Despachos** (Spring Boot, puerto 8081) — `back-Despachos_SpringBoot/Springboot-API-REST-DESPACHO`
- **Base de datos:** Amazon RDS MySQL 8.0 en AWS / MySQL 8.0 en contenedor para desarrollo local
- **Orquestación en la nube:** AWS ECS Fargate, balanceado por un Application Load Balancer (`innovatech-alb`)
- **CI/CD:** GitHub Actions — un workflow por microservicio, disparado en cada push a la rama `deploy`

## Ejecutar el entorno completo en local (Docker Compose)

Requiere tener clonado también el repositorio [`innovatech-frontend`](https://github.com/Luch0Sl1m1ng/innovatech-frontend) como carpeta hermana de este repo:

```
proyectos/
  innovatech-backend/     <- este repo
  innovatech-frontend/
```

Pasos:

```bash
cp .env.example .env
docker compose up --build
```

Esto levanta 4 contenedores en una red Docker dedicada:

| Servicio | Puerto local | Descripción |
|---|---|---|
| `mysql` | 3306 | MySQL 8.0, con healthcheck antes de iniciar los microservicios |
| `ventas` | 8082 | Microservicio de Ventas (Swagger en `/swagger-ui.html`) |
| `despachos` | 8081 | Microservicio de Despachos (Swagger en `/swagger-ui.html`) |
| `frontend` | 80 | Dashboard React servido por Nginx |

Las credenciales de la base de datos se configuran en `.env` (nunca se suben a Git — ver `.gitignore`). `.env.example` documenta las variables necesarias.

## Tests automatizados

Cada microservicio incluye tests unitarios ejecutados automáticamente en el pipeline antes del build y deploy (`mvn test`), usando una base de datos H2 en memoria (`src/test/resources/application.properties`) para no depender de conectividad real a RDS durante la ejecución en GitHub Actions.

Para correr los tests en local:
```bash
cd back-Ventas_SpringBoot/Springboot-API-REST
mvn test
```

## Pipeline CI/CD

Cada push a la rama `deploy` dispara el workflow correspondiente (`.github/workflows/deploy-back.yml` para Ventas, `deploy-despachos.yml` para Despachos), que ejecuta:

1. Checkout del código
2. Set up JDK 17
3. **Run tests** (`mvn -B test`)
4. Configuración de credenciales AWS
5. Login a Amazon ECR
6. Build y push de la imagen Docker
7. Force new deployment en ECS
8. Espera a que el servicio se estabilice (`ecs wait services-stable`)

## Seguridad

- Imágenes de runtime minimalistas (`amazoncorretto:17-alpine-jdk`), build multietapa que descarta herramientas de compilación y código fuente de la imagen final
- Puertos mínimos expuestos (solo 8081/8082, uno por microservicio)
- Credenciales de AWS y de base de datos gestionadas vía GitHub Secrets / AWS Systems Manager Parameter Store, nunca en el código fuente
- Security Groups de mínimo privilegio en AWS (`alb-tienda` público, `ecs-tienda` solo acepta tráfico del ALB)

## Integrantes

Luis Alejandro Rojas Gil — RUT 27.204.304-3
Tomás Andrés González Borje — RUT 19.277.589-2

Asignatura ISY1101 — Introducción a Herramientas DevOps — DuocUC 2026
