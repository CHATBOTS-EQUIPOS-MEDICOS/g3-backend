# API Endpoints - Backend RAG de Equipos Médicos con Gemini

Este documento describe la especificación de los endpoints del backend en Spring Boot para autenticación, gestión de manuales médicos, procesamiento vectorial (embeddings) y consultas mediante RAG (Retrieval-Augmented Generation) con Gemini.

## Base URL

```text
http://localhost:8080
```

> [!NOTE]
> Todos los endpoints tienen CORS configurado con `@CrossOrigin` (permitiendo orígenes específicos como `http://localhost:4200` en autenticación y `*` en otros controladores) para permitir su fácil consumo desde cualquier aplicación frontend.

---

## 1. Autenticación y Usuarios

Todos los endpoints de autenticación tienen el prefijo `/api/auth`.

### 1.1 Registrar Usuario

Permite registrar un nuevo usuario en el sistema.

- **URL:** `/api/auth/register`
- **Método HTTP:** `POST`
- **Content-Type:** `application/json`

#### Cuerpo de la Petición (JSON)

| Campo      | Tipo   | Requerido | Descripción                                                    |
| :--------- | :----- | :-------- | :------------------------------------------------------------- |
| `fullName` | String | Sí        | Nombre completo del usuario (entre 3 y 100 caracteres).        |
| `email`    | String | Sí        | Correo electrónico único con formato válido.                   |
| `password` | String | Sí        | Contraseña de acceso (entre 6 y 50 caracteres).                |
| `role`     | String | No        | Rol del usuario (`ADMIN` o `CLIENT`). Por defecto es `CLIENT`. |

##### Ejemplo de Cuerpo de Petición

```json
{
  "fullName": "Juan Pérez",
  "email": "juan.perez@example.com",
  "password": "securepassword123",
  "role": "CLIENT"
}
```

#### Respuestas

- **`200 OK` (Registro Exitoso):**

  ```json
  {
    "fullName": "Juan Pérez",
    "email": "juan.perez@example.com",
    "role": "CLIENT",
    "message": "Usuario registrado exitosamente."
  }
  ```

- **`400 Bad Request` (Error de Validación o Correo Duplicado):**
  - Ejemplo si el correo ya existe:
    ```json
    {
      "error": "El correo ya está registrado."
    }
    ```
  - Ejemplo de errores de validación de campos:
    ```json
    {
      "fullName": "El nombre completo es requerido.",
      "password": "La contraseña debe tener entre 6 y 50 caracteres."
    }
    ```

---

### 1.2 Iniciar Sesión (Login)

Autentica al usuario y genera una cookie HTTP-only con el token JWT.

- **URL:** `/api/auth/login`
- **Método HTTP:** `POST`
- **Content-Type:** `application/json`

#### Cuerpo de la Petición (JSON)

| Campo      | Tipo   | Requerido | Descripción                     |
| :--------- | :----- | :-------- | :------------------------------ |
| `email`    | String | Sí        | Correo electrónico del usuario. |
| `password` | String | Sí        | Contraseña del usuario.         |

##### Ejemplo de Cuerpo de Petición

```json
{
  "email": "juan.perez@example.com",
  "password": "securepassword123"
}
```

#### Respuestas

- **`200 OK` (Inicio de Sesión Exitoso):**
  - _Headers:_ Incluye una cookie HTTP-only llamada `token` con el JWT del usuario (expira en 24 horas).
  - _Body:_
    ```json
    {
      "fullName": "Juan Pérez",
      "email": "juan.perez@example.com",
      "role": "CLIENT",
      "message": "Sesión iniciada correctamente."
    }
    ```

- **`400 Bad Request` (Credenciales Incorrectas):**
  ```json
  {
    "error": "Credenciales incorrectas."
  }
  ```

---

### 1.3 Cerrar Sesión (Logout)

Invalida la sesión del usuario eliminando la cookie de autenticación.

- **URL:** `/api/auth/logout`
- **Método HTTP:** `POST`

#### Respuestas

- **`200 OK` (Cierre de Sesión Exitoso):**
  - _Headers:_ Limpia la cookie `token` (establece su valor en `null` y expiración en `0`).
  - _Body:_
    ```json
    {
      "message": "Sesión cerrada correctamente."
    }
    ```

---

## 2. Gestión de Documentos

Todos los endpoints de documentos tienen el prefijo `/api/documents`.

> [!IMPORTANT]
> Los endpoints de modificación y eliminación de documentos (Carga y Eliminación) requieren obligatoriamente el rol de **`ADMIN`**. Los endpoints de consulta (Listar y Descargar) están disponibles para cualquier usuario **autenticado**.

### 2.1 Cargar y Procesar Manual PDF

Inicia la carga de un manual médico en formato PDF. El backend almacena el archivo en Supabase Storage e inicia la ingesta y procesamiento asíncrono para generar los fragmentos semánticos y embeddings.

- **URL:** `/api/documents/upload`
- **Método HTTP:** `POST`
- **Rol requerido:** `ADMIN`
- **Content-Type:** `multipart/form-data`

#### Parámetros de la Petición

| Parámetro | Tipo       | Requerido | Descripción                                         |
| :-------- | :--------- | :-------- | :-------------------------------------------------- |
| `file`    | File (PDF) | Sí        | Archivo PDF del manual médico que se desea indexar. |

#### Respuestas

- **`202 Accepted` (Ingesta Iniciada):**

  ```json
  {
    "id": "e837f694-df7a-4c28-97e0-911a7a0de3d4",
    "name": "manual_respirador_model_x.pdf",
    "contentType": "application/pdf",
    "sizeBytes": 1048576,
    "status": "PROCESSING",
    "storagePath": "documents/e837f694-df7a-4c28-97e0-911a7a0de3d4_manual_respirador_model_x.pdf",
    "createdAt": "2026-07-03T17:15:30.123",
    "updatedAt": "2026-07-03T17:15:30.123"
  }
  ```

- **`400 Bad Request` (Archivo Vacío):**
  Retorna un cuerpo vacío si el archivo no contiene datos.

- **`415 Unsupported Media Type` (Formato No Válido):**
  Retorna si el archivo enviado no tiene la cabecera `Content-Type` igual a `application/pdf`.

---

### 2.2 Listar Documentos

Obtiene una lista de todos los manuales y documentos registrados en el sistema.

- **URL:** `/api/documents`
- **Método HTTP:** `GET`
- **Rol requerido:** Cualquier usuario autenticado

#### Respuestas

- **`200 OK`:**
  ```json
  [
    {
      "id": "e837f694-df7a-4c28-97e0-911a7a0de3d4",
      "name": "manual_respirador_model_x.pdf",
      "contentType": "application/pdf",
      "sizeBytes": 1048576,
      "status": "COMPLETED",
      "storagePath": "documents/e837f694-df7a-4c28-97e0-911a7a0de3d4_manual_respirador_model_x.pdf",
      "createdAt": "2026-07-03T17:15:30",
      "updatedAt": "2026-07-03T17:15:45"
    }
  ]
  ```

---

### 2.3 Descargar Documento

Descarga los bytes reales de un manual almacenado en el almacenamiento de Supabase.

- **URL:** `/api/documents/{id}/download`
- **Método HTTP:** `GET`
- **Rol requerido:** Cualquier usuario autenticado

#### Parámetros

| Parámetro | Ubicación | Tipo    | Requerido | Descripción                                                                                         |
| :-------- | :-------- | :------ | :-------- | :-------------------------------------------------------------------------------------------------- |
| `id`      | Path      | UUID    | Sí        | Identificador único del documento.                                                                  |
| `inline`  | Query     | Boolean | No        | Determina si el archivo se visualiza en el navegador (`true`) o se descarga (`false`, por defecto). |

#### Respuestas

- **`200 OK`:**
  - _Headers:_ `Content-Type: application/pdf`, `Content-Disposition: attachment; filename="nombre.pdf"` (o `inline;` según el parámetro).
  - _Body:_ Bytes binarios del archivo PDF.

---

### 2.4 Eliminar Documento

Elimina el registro de un manual del sistema, liberando también los recursos relacionados.

- **URL:** `/api/documents/{id}`
- **Método HTTP:** `DELETE`
- **Rol requerido:** `ADMIN`

#### Parámetros

| Parámetro | Ubicación | Tipo | Requerido | Descripción                                   |
| :-------- | :-------- | :--- | :-------- | :-------------------------------------------- |
| `id`      | Path      | UUID | Sí        | Identificador único del documento a eliminar. |

#### Respuestas

- **`204 No Content`:**
  El documento y sus fragmentos asociados han sido eliminados de manera exitosa.

---

## 3. Consultar al Chatbot (Estilo RAG)

### 3.1 Consultar al Chatbot

Realiza una consulta semántica al chatbot. Genera el embedding de la pregunta, busca en la base de datos local los fragmentos más similares usando similitud de coseno, inyecta dicho contexto como fuente y utiliza Gemini (`gemini-2.5-flash`) para responder fundamentado **únicamente** en los manuales cargados.

- **URL:** `/api/chat/ask`
- **Método HTTP:** `POST`
- **Rol requerido:** Público / Permitido sin autenticación (disponible para clientes y público general)
- **Content-Type:** `application/json`

#### Cuerpo de la Petición (JSON)

| Campo      | Tipo   | Requerido | Descripción                                                                              |
| :--------- | :----- | :-------- | :--------------------------------------------------------------------------------------- |
| `question` | String | Sí        | Consulta del usuario sobre funcionamiento, errores o calibración de los equipos médicos. |

##### Ejemplo de Cuerpo de Petición

```json
{
  "question": "¿Cuál es la presión máxima permitida en el circuito del respirador?"
}
```

#### Respuestas

- **`200 OK` (Consulta Exitosa):**

  ```json
  {
    "answer": "La presión máxima permitida en el circuito del respirador es de 60 cmH2O. Si se supera este valor, se activará la alarma de alta presión.",
    "sources": [
      {
        "documentName": "manual_respirador_model_x.pdf",
        "chunkIndex": 12,
        "snippet": "La presión máxima permitida del circuito del respirador no debe exceder los 60 cmH2O..."
      }
    ]
  }
  ```

- **`400 Bad Request` (Pregunta Vacía):**

  ```json
  {
    "error": "The question field must not be empty."
  }
  ```

- **`500 Internal Server Error` (Fallo en el Servicio/API de Gemini):**
  ```json
  {
    "error": "Failed to answer the question: <detalle_del_error>"
  }
  ```

---

## 4. Historial de Chats (Sesiones y Conversaciones)

Todos estos endpoints requieren que el usuario esté autenticado. El sistema extraerá el ID del usuario directamente desde el token JWT (cookie `token` o cabecera `Authorization`).

### 4.1 Crear Sesión de Chat

Crea una nueva conversación para el usuario autenticado.

- **URL:** `/api/chat/sessions`
- **Método HTTP:** `POST`
- **Rol requerido:** Usuario autenticado
- **Content-Type:** `application/json`

#### Cuerpo de la Petición (JSON)

| Campo   | Tipo   | Requerido | Descripción                                                                           |
| :------ | :----- | :-------- | :------------------------------------------------------------------------------------ |
| `title` | String | No        | Título personalizado para el chat. Si se omite, se guarda como "Nueva Conversación". |

##### Ejemplo de Cuerpo de Petición
```json
{
  "title": "Mantenimiento Preventivo D100"
}
```

#### Respuestas

- **`200 OK` (Sesión Creada):**
  ```json
  {
    "id": "3c0b89ea-2f22-4a0b-9dcf-f25b29dbf0a2",
    "title": "Mantenimiento Preventivo D100",
    "createdAt": "2026-07-06T10:05:00",
    "updatedAt": "2026-07-06T10:05:00"
  }
  ```

---

### 4.2 Listar Sesiones de Chat

Obtiene la lista de todas las sesiones de chat iniciadas por el usuario autenticado, ordenadas por la fecha de actualización más reciente.

- **URL:** `/api/chat/sessions`
- **Método HTTP:** `GET`
- **Rol requerido:** Usuario autenticado

#### Respuestas

- **`200 OK`:**
  ```json
  [
    {
      "id": "3c0b89ea-2f22-4a0b-9dcf-f25b29dbf0a2",
      "title": "Mantenimiento Preventivo D100",
      "createdAt": "2026-07-06T10:05:00",
      "updatedAt": "2026-07-06T10:05:25"
    }
  ]
  ```

---

### 4.3 Listar Mensajes de una Sesión

Obtiene el historial completo de mensajes y respuestas de una conversación específica. El sistema valida que la conversación pertenezca al usuario autenticado.

- **URL:** `/api/chat/sessions/{sessionId}/messages`
- **Método HTTP:** `GET`
- **Rol requerido:** Usuario autenticado

#### Parámetros

| Parámetro   | Ubicación | Tipo | Requerido | Descripción                             |
| :---------- | :-------- | :--- | :-------- | :-------------------------------------- |
| `sessionId` | Path      | UUID | Sí        | Identificador único de la conversación. |

#### Respuestas

- **`200 OK`:**
  ```json
  [
    {
      "id": "bfa4429e-cd56-42d4-a0fb-4050b13cf0ea",
      "role": "USER",
      "content": "¿Cómo se calibra la pantalla del monitor multiparamétrico?",
      "sources": null,
      "createdAt": "2026-07-06T10:05:20"
    },
    {
      "id": "d1a63cde-f1b2-4d2c-8ab5-f12b2a75908e",
      "role": "MODEL",
      "content": "Para calibrar la pantalla, ingrese al menú de servicio manteniendo presionado el botón 'Menú' durante 3 segundos...",
      "sources": [
        {
          "documentName": "monitor_multiparametrico.pdf",
          "chunkIndex": 3,
          "snippet": "Para calibrar la pantalla, acceda al menú de servicio técnico..."
        }
      ],
      "createdAt": "2026-07-06T10:05:25"
    }
  ]
  ```

---

### 4.4 Preguntar en una Sesión de Chat

Envía una pregunta dentro de una sesión de chat existente. Guarda la pregunta y la respuesta del chatbot (con sus fuentes y fragmentos citados) en la base de datos asociada a la sesión.

Si el título actual de la sesión es "Nueva Conversación", el sistema actualizará automáticamente el título al texto de esta primera pregunta (truncado a 40 caracteres).

- **URL:** `/api/chat/sessions/{sessionId}/ask`
- **Método HTTP:** `POST`
- **Rol requerido:** Usuario autenticado
- **Content-Type:** `application/json`

#### Parámetros

| Parámetro   | Ubicación | Tipo | Requerido | Descripción                             |
| :---------- | :-------- | :--- | :-------- | :-------------------------------------- |
| `sessionId` | Path      | UUID | Sí        | Identificador único de la conversación. |

#### Cuerpo de la Petición (JSON)

| Campo      | Tipo   | Requerido | Descripción                         |
| :--------- | :----- | :-------- | :---------------------------------- |
| `question` | String | Sí        | Pregunta del usuario sobre el chat. |

##### Ejemplo de Cuerpo de Petición
```json
{
  "question": "¿Cómo se calibra la pantalla del monitor multiparamétrico?"
}
```

#### Respuestas

- **`200 OK` (Respuesta Generada y Guardada):**
  ```json
  {
    "answer": "Para calibrar la pantalla, ingrese al menú de servicio manteniendo presionado el botón 'Menú' durante 3 segundos...",
    "sources": [
      {
        "documentName": "monitor_multiparametrico.pdf",
        "chunkIndex": 3,
        "snippet": "Para calibrar la pantalla, acceda al menú de servicio técnico..."
      }
    ]
  }
  ```

- **`400 Bad Request` (Pregunta Vacía):**
  ```json
  {
    "error": "The question field must not be empty."
  }
  ```

---

### 4.5 Eliminar Sesión de Chat

Elimina una sesión de chat específica y todos sus mensajes asociados en cascada.

- **URL:** `/api/chat/sessions/{sessionId}`
- **Método HTTP:** `DELETE`
- **Rol requerido:** Usuario autenticado

#### Parámetros

| Parámetro   | Ubicación | Tipo | Requerido | Descripción                                     |
| :---------- | :-------- | :--- | :-------- | :---------------------------------------------- |
| `sessionId` | Path      | UUID | Sí        | Identificador único de la sesión a eliminar.    |

#### Respuestas

- **`204 No Content`:**
  La sesión de chat e historial de mensajes asociados se han eliminado correctamente de la base de datos.

