# InnovaTech - Sistema de Microservicios (Backend)

Este proyecto implementa una arquitectura de microservicios robusta para la gestión de Ventas y Despachos.

## 🚀 Tecnologías Utilizadas
* **Java 17 & Spring Boot**: Framework principal.
* **PostgreSQL**: Base de datos relacional.
* **Docker & Docker Compose**: Contenedorización y orquestación.
* **Amazon ECR**: Registro de imágenes privado.
* **GitHub Actions**: Pipeline de CI/CD automatizado.

## 🛠️ Infraestructura y Despliegue
El despliegue se realiza en una instancia **AWS EC2** (Amazon Linux 2023). Cada vez que se realiza un push a la rama `deploy`:
1. GitHub Actions construye la imagen optimizada (Multi-stage build).
2. La imagen se etiqueta y se sube a **AWS ECR**.
3. Se ejecuta un despliegue automático vía SSH en la instancia de producción.

## 📡 Endpoints Principales
* **Ventas**: Port 8081
* **Despachos**: Port 8082
