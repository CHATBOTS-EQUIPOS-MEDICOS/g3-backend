# Backend Chatbot RAG - Soporte Técnico de Máquinas

Este es el backend desarrollado en Java con Spring Boot para el chatbot de soporte técnico, conectado a una base de datos en Supabase que almacena tanto información de usuarios como vectores/embeddings para el sistema RAG (Retrieval-Augmented Generation).

---

## 🛠️ Tecnologías Utilizadas

- **Java 21**
- **Spring Boot 4.x** (Spring Data JPA)
- **PostgreSQL / pgvector** (Alojado en Supabase)
- **Flyway** (Gestión automática de migraciones de base de datos)
- **Dotenv Java** (Carga de variables de entorno desde archivo `.env`)

---

## ⚙️ Configuración del Entorno

La conexión a la base de datos se realiza a través del **Connection Pooler (Supavisor)** de Supabase en lugar de la conexión directa, para asegurar compatibilidad con redes IPv4 (dado que las conexiones directas de Supabase son IPv6-only).

### 1. Variables de Entorno (`.env`)

Crea un archivo `.env` en la raíz del proyecto (puedes tomar como base el archivo `.env.example`) y completa las variables de conexión con las credenciales de tu base de datos:

```env
NEXT_PUBLIC_SUPABASE_URL=https://lghsnanouiqxomqynbce.supabase.co
NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=sb_publishable_iZePSfzVScpfjSNSFEblSA_ld2yDao5

# Configuración del Connection Pooler (Transaction Mode)
DB_HOST=aws-1-us-east-2.pooler.supabase.com
DB_PORT=6543
DB_NAME=postgres
DB_USER=postgres.lghsnanouiqxomqynbce
DB_PASSWORD=TU_CONTRASEÑA_DE_SUPABASE
```

*Nota: Asegúrate de que tu dirección de `DB_USER` tenga el formato `usuario.[REF_DE_PROYECTO]` para que el pooler pueda identificar tu base de datos.*

---

## 🗄️ Base de Datos y Vectores (RAG)

El proyecto utiliza **Flyway** para estructurar la base de datos automáticamente al arrancar la aplicación.

### Migraciones de Base de Datos
El archivo de migración inicial se encuentra en `src/main/resources/db/migration/V1__create_vector_embeddings.sql` y realiza las siguientes tareas:
1. **Habilitación de la extensión `vector`** de Postgres.
2. **Creación de la tabla `public.document_chunks`** para almacenar el contenido del documento, metadatos en formato JSON y un vector de 1536 dimensiones (optimizado para modelos de OpenAI como `text-embedding-3-small`).
3. **Creación de un índice HNSW (`document_chunks_embedding_idx`)** utilizando la función de distancia de coseno (`vector_cosine_ops`) para lograr búsquedas de similitud veloces y escalables.

---

## 🚀 Ejecución del Proyecto

1. Asegúrate de compilar y descargar las dependencias necesarias:
   ```powershell
   ./mvnw clean compile
   ```

2. Ejecuta el servidor de desarrollo local:
   ```powershell
   ./mvnw spring-boot:run
   ```

El servidor web iniciará por defecto en el puerto `8080` e interactuará automáticamente con Supabase.
