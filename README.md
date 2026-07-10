# Backend Chatbot RAG - Soporte Técnico de Máquinas

Este es el backend desarrollado en Java con Spring Boot para el chatbot de soporte técnico, conectado a una base de datos en Supabase que almacena tanto información de usuarios como vectores/embeddings para el sistema RAG (Retrieval-Augmented Generation).

---

## 🛠️ Tecnologías Utilizadas

- **Java 21**
- **Spring Boot 4.x** (Spring Data JPA)
- **PostgreSQL / pgvector** (Alojado en Supabase)
- **Flyway** (Gestión automática de migraciones de base de datos)
- **Dotenv Java** (Carga de variables de entorno desde archivo `.env`)
- **Apache PDFBox** (Procesamiento y extracción de texto de PDFs)

---

## ⚙️ Configuración del Entorno

La conexión a la base de datos se realiza a través del **Connection Pooler (Supavisor)** de Supabase en lugar de la conexión directa, para asegurar compatibilidad con redes IPv4 (dado que las conexiones directas de Supabase son IPv6-only).

### 1. Variables de Entorno (`.env`)

Crea un archivo `.env` en la raíz del proyecto (puedes tomar como base el archivo `.env.example`) y completa las variables de conexión con las credenciales de tu base de datos:

```env

# Configuración del Connection Pooler (Transaction Mode)
DB_HOST=aws-1-us-east-2.pooler.supabase.com
DB_PORT=6543
DB_NAME=postgres
DB_USER=postgres.lghsnanouiqxomqynbce
DB_PASSWORD=TU_CONTRASEÑA_DE_SUPABASE

# API Key de Gemini
GEMINI_API_KEY=TU_API_KEY_DE_GEMINI
```

_Nota: Asegúrate de que tu dirección de `DB_USER` tenga el formato `usuario.[REF_DE_PROYECTO]` para que el pooler pueda identificar tu base de datos._

---

## 🗄️ Base de Datos y Estructura RAG

El proyecto utiliza **Flyway** para estructurar la base de datos automáticamente al arrancar la aplicación.

### Migraciones de Base de Datos

- **`V1__create_vector_embeddings.sql`**: Habilita la extensión `vector` y crea la tabla `document_chunks`.
- **`V2__alter_embedding_dimensions.sql`**: Modifica la dimensión de los vectores de embeddings a 768 para compatibilidad con Gemini.
- **`V3__create_documents_table.sql`**: Crea la tabla `documents` para almacenar los metadatos de los archivos PDF.
- **`V4__alter_documents_use_storage.sql`**: Modifica la tabla para usar el bucket de Supabase Storage y lo crea automáticamente.

---

## 🚀 Arquitectura de Ingesta Asíncrona (Event-Driven)

Cuando un usuario sube un archivo PDF al backend, se activa el siguiente flujo desacoplado:

1. El **`DocumentController`** recibe el archivo y lo almacena inmediatamente en la tabla `documents` con estado `PROCESSING`.
2. El sistema publica un evento **`DocumentIngestedEvent`** y retorna una respuesta `202 Accepted` al cliente.
3. El **`DocumentEventListener`** intercepta el evento de manera asíncrona (`@Async`) y procesa el archivo PDF:
   - Extrae el texto utilizando **`PdfService`** (Apache PDFBox).
   - Divide el texto en fragmentos (chunks) usando un algoritmo de ventana deslizante.
   - Genera los embeddings correspondientes utilizando **`GeminiService`** (API de Gemini).
   - Guarda cada fragmento en la tabla `document_chunks`, vinculando el `document_id` en el campo `metadata` JSONB.
   - Actualiza el estado del documento a `COMPLETED` (o `FAILED` si ocurre algún error).
4. Cuando un documento es eliminado con un `DELETE`, se publica un **`DocumentDeletedEvent`**, el cual elimina asíncronamente todos los fragmentos y vectores de la tabla `document_chunks` que estén asociados a dicho documento mediante sus metadatos, además de eliminar el archivo físico en el bucket de Supabase Storage.

---

## 💬 Soporte en Vivo por WebSocket y Derivación

El sistema cuenta con un mecanismo de derivación a administrador o soporte técnico cuando el chatbot no puede resolver las dudas del usuario tras **3 fallos**.

### 1. Lógica de Detección de Fallos (Handoff a Humano)
Cada vez que el usuario realiza una pregunta y el modelo de IA responde, el backend analiza el historial de la conversación en base de datos.
Si el contador de respuestas fallidas de la IA (mensajes que contienen textos de fallback como `"no se encuentra en los manuales"`, `"no se encuentra en el contexto"`, o `"Lo siento, la respuesta"`) alcanza o supera **3**, el flag `suggestAdmin` se establece en `true` dentro del DTO `ChatAnswer`:

```java
public record ChatAnswer(
    String answer,
    List<SourceSnippet> sources,
    boolean suggestAdmin // Indica si se debe sugerir redirigir a un admin
) {}
```

### 2. Flujo de Derivación desde el Frontend
Cuando el frontend detecta que la respuesta del chatbot contiene `"suggestAdmin": true`:
1. **Mostrar Opción:** Se le ofrece al usuario un botón o diálogo: *¿Deseas hablar con soporte técnico/administrador en vivo?*.
2. **Crear Sesión de Soporte:** Si el usuario acepta, el cliente realiza una petición POST a `/api/support/request`. Esto genera una sesión con estado `WAITING` (o `ACTIVE` asignada a un administrador por defecto) y devuelve el ID único del soporte.
3. **Conexión WebSocket:** El cliente es redirigido a una nueva interfaz de chat conectándose a `ws://localhost:8080/ws/support`.

### 3. Arquitectura del WebSocket para Soporte en Vivo
El sistema utiliza la infraestructura de WebSockets de Spring Boot:
* **Endpoint de conexión:** `ws://localhost:8080/ws/support`
* **Autenticación:** El interceptor `JwtHandshakeInterceptor` valida el token JWT del usuario durante el handshake inicial.
* **Controlador WebSocket (`SupportWebSocketHandler`):**
  * **Mensajes del Cliente:** Valida que el cliente tenga una sesión activa, guarda el mensaje en la base de datos (`senderType = USER`) y lo retransmite en tiempo real al administrador asignado.
  * **Mensajes del Administrador:** Requiere el `sessionId`, valida que esté asignado a dicha sesión, almacena el mensaje (`senderType = ADMIN`) y lo retransmite al cliente.

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

El servidor web iniciará por defecto en el puerto `8080`.

