# Innovatech Backend — Despliegue en AWS ECS Fargate

Backend del proyecto Innovatech, compuesto por dos microservicios REST desarrollados con Spring Boot que gestionan ventas y despachos. Desplegado en AWS ECS Fargate como parte de la Evaluación Parcial 3 (ISY1101 - Introducción a Herramientas DevOps, DuocUC).

## Servicios

| Servicio | Descripción | Puerto interno | Ruta base | Imagen ECR |
|---|---|---|---|---|
| Ventas API | Gestiona ventas, fechas de compra, dirección, valor y estado de despacho generado. | 8082 | `/api/v1/ventas` | `innovatech-ventas` |
| Despachos API | Gestiona despachos, fecha de despacho, camión, intentos, compra asociada y estado despachado. | 8081 | `/api/v1/despachos` | `innovatech-despachos` |

## Stack técnico

- Java 17, Spring Boot 3.4.4
- Spring Web, Spring Data JPA, Spring Validation
- MySQL 8.0 (Amazon RDS)
- Maven, Springdoc OpenAPI / Swagger UI
- Docker, AWS ECS Fargate

## Arquitectura desplegada en AWS

```
                         Internet
                            │
                            ▼
                ┌───────────────────────┐
                │   Application Load     │
                │   Balancer (ALB)       │
                │  innovatech-alb        │
                └───────────┬───────────┘
            ┌────────────────┼────────────────┐
       listener:80      listener:8082     listener:8081
            │                 │                 │
            ▼                 ▼                 ▼
     ┌─────────────┐   ┌─────────────┐   ┌─────────────┐
     │ tg-frontend │   │  tg-ventas  │   │tg-despachos │
     └──────┬──────┘   └──────┬──────┘   └──────┬──────┘
            │                 │                 │
            ▼                 ▼                 ▼
     ┌─────────────┐   ┌─────────────┐   ┌─────────────┐
     │ frontend-svc│   │ ventas-svc  │   │despachos-svc│
     │  (ECS Fargate)  │ (ECS Fargate)│   │(ECS Fargate)│
     └─────────────┘   └──────┬──────┘   └──────┬──────┘
                               │                 │
                               └────────┬────────┘
                                        ▼
                              ┌──────────────────┐
                              │  Amazon RDS MySQL  │
                              │  innovatech-mysql  │
                              └──────────────────┘
```

Clúster: `innovatech-cluster` (ECS Fargate, región `us-east-1`).
Cada servicio corre en subredes públicas con IP pública asignada (necesario en este entorno de AWS Academy Learner Lab, que no dispone de NAT Gateway, para permitir el pull de imágenes desde ECR).

### Decisión de arquitectura: backend expuesto vía ALB

El frontend (React, ejecutado en el navegador del usuario) necesita llamar directamente a las APIs REST. Dado que el navegador corre fuera de la VPC, se optó por exponer los backends a través de listeners adicionales del mismo ALB (puertos 8081 y 8082), en vez de un proxy reverso interno. Esta decisión prioriza velocidad de implementación y facilidad de validación en la defensa técnica; en un entorno productivo se recomendaría un API Gateway o un proxy reverso para no exponer los microservicios directamente.

### Comunicación con Cloud Map / Service Connect

Se intentó inicialmente implementar DNS interno mediante AWS Cloud Map (`servicediscovery:CreatePrivateDnsNamespace` / `CreateHttpNamespace`), pero ambas operaciones fueron rechazadas por política de AWS Academy Learner Lab (`AccessDeniedException`). Se documentó el intento y se optó por el enrutamiento vía ALB como alternativa funcionalmente equivalente para los fines de esta evaluación.

## Variables de entorno (Task Definition ECS)

| Variable | Descripción | Origen |
|---|---|---|
| `SERVER_PORT` | Puerto interno de Spring Boot (Tomcat) | Variable de entorno fija en Task Definition |
| `DB_ENDPOINT` | Endpoint de RDS MySQL | Variable de entorno fija en Task Definition |
| `DB_PORT` | Puerto de MySQL (3306) | Variable de entorno fija en Task Definition |
| `DB_NAME` | Nombre de la base de datos (`app_db`) | Variable de entorno fija en Task Definition |
| `DB_USERNAME` | Usuario de base de datos | Variable de entorno fija en Task Definition |
| `DB_PASSWORD` | Password de base de datos | **AWS SSM Parameter Store** (`/innovatech/db-password`, SecureString), inyectado vía bloque `secrets` de la Task Definition |

La gestión de credenciales mediante SSM Parameter Store (en lugar de variables de entorno en texto plano) evita exponer el password en la definición de la tarea o en los logs de despliegue.

## Pipeline CI/CD (GitHub Actions)

Cada microservicio tiene su propio workflow en `.github/workflows/`, disparado por push a la rama `deploy`:

- `deploy-back.yml` → build, push a ECR (`innovatech-ventas`) y `force-new-deployment` en `ventas-svc`.
- `deploy-despachos.yml` → build, push a ECR (`innovatech-despachos`) y `force-new-deployment` en `despachos-svc`.

Flujo: `checkout → configure AWS credentials → login ECR → docker build/push → ecs update-service --force-new-deployment → ecs wait services-stable`.

El paso final (`ecs wait services-stable`) garantiza que el pipeline no se reporte como exitoso hasta que el nuevo despliegue esté completamente estable y los health checks del ALB lo confirmen como saludable.

### Secrets de GitHub Actions requeridos

```
AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
AWS_SESSION_TOKEN
```

Estas credenciales corresponden a las credenciales temporales de AWS Academy Learner Lab y deben renovarse periódicamente (expiran cada pocas horas), actualizando los Secrets del repositorio.

## Autoscaling

Política de Target Tracking configurada en ambos servicios (`ventas-svc`, `despachos-svc`):

- Métrica: `ECSServiceAverageCPUUtilization`
- Umbral objetivo: **50% CPU**
- Mín. tareas: 1, Máx. tareas: 3
- Scale-out cooldown: 60s, Scale-in cooldown: 120s

**Justificación del umbral de 50%:** dado que el arranque de cada instancia de Spring Boot toma aproximadamente 80 segundos (carga de Hibernate/JPA y establecimiento del pool de conexiones HikariCP), un umbral del 50% deja margen suficiente de reacción ante picos de tráfico antes de que la CPU se sature por completo, evitando degradación de latencia mientras la nueva tarea termina de inicializarse.

Se validó el comportamiento mediante simulación de carga (múltiples requests concurrentes vía PowerShell jobs), confirmando que CloudWatch registró 3 datapoints consecutivos por sobre el umbral, lo cual disparó la alarma `TargetTracking-...-AlarmHigh` y escaló `ventas-svc` de 1 a 2 tareas activas, ambas saludables en el ALB tras su arranque.

## Logs y observabilidad

Todos los contenedores envían logs a CloudWatch Logs bajo el log group `/ecs/innovatech`, con prefijos de stream por servicio (`ventas/`, `despachos/`, `frontend/`).

## Problemas encontrados durante el despliegue y solución

| Problema | Causa | Solución |
|---|---|---|
| Tareas no podían escribir logs y morían inmediatamente | El log group `/ecs/innovatech` no existía al momento de crear los servicios | Se creó el log group antes de iniciar los servicios (`aws logs create-log-group`) |
| ALB devolvía 504 Gateway Timeout en los backends | El Security Group de las tareas ECS solo permitía tráfico desde el ALB en el puerto 80, no en 8081/8082 | Se agregaron reglas de ingress específicas para los puertos 8081 y 8082 desde el SG del ALB |
| Health checks fallaban pese a que la aplicación funcionaba correctamente | El `health-check-grace-period-seconds` inicial (0-90s) era insuficiente frente al tiempo real de arranque de Spring Boot (~80s) | Se aumentó el grace period a 180s y se relajó el `unhealthy-threshold-count` del target group a 10 intentos con intervalos de 20s |
| Inconsistencia de puertos internos entre Ventas y Despachos | Ventas no tenía `server.port` fijo (usaba el default 8080 de Spring Boot); Despachos sí lo tenía fijo en 8081 | Se forzó `SERVER_PORT` como variable de entorno explícita en ambas Task Definitions (8082 para Ventas, 8081 para Despachos), alineado con la documentación del proyecto |
| `CreatePrivateDnsNamespace`/`CreateHttpNamespace` de Cloud Map rechazados | Restricción de política IAM en AWS Academy Learner Lab | Se optó por exponer los backends vía listeners adicionales del mismo ALB en lugar de DNS interno |

## Ejecución local (desarrollo)

```bash
cd back-Ventas_SpringBoot/Springboot-API-REST
export DB_ENDPOINT=localhost
export DB_PORT=3306
export DB_NAME=app_db
export DB_USERNAME=app_user
export DB_PASSWORD=change_me
./mvnw spring-boot:run
```

```bash
cd back-Despachos_SpringBoot/Springboot-API-REST-DESPACHO
export DB_ENDPOINT=localhost
export DB_PORT=3306
export DB_NAME=app_db
export DB_USERNAME=app_user
export DB_PASSWORD=change_me
./mvnw spring-boot:run
```

## Documentación Swagger

```
http://<ALB_DNS>:8082/swagger-ui.html   (Ventas)
http://<ALB_DNS>:8081/swagger-ui.html   (Despachos)
```

## Flujo Git

Ver `GIT_FLOW.md`. Resumen: `main` (estable) → `develop` (integración) → `deploy` (dispara el pipeline CI/CD hacia ECS).
