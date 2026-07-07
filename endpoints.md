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

### 1.4 Obtener Perfil del Usuario Logueado

Obtiene la información del perfil del usuario autenticado actualmente a partir de su token JWT.

- **URL:** `/api/users/me`
- **Método HTTP:** `GET`
- **Rol requerido:** Cualquier usuario autenticado.

#### Respuestas

- **`200 OK` (Consulta Exitosa):**
  ```json
  {
    "email": "juan.perez@example.com",
    "fullName": "Juan Pérez",
    "role": "CLIENT"
  }
  ```

- **`400 Bad Request` (Usuario desactivado o no encontrado):**
  ```json
  {
    "error": "El usuario está desactivado."
  }
  ```

- **`401 Unauthorized` (Token inválido, expirado o ausente):**
  Retorna un cuerpo vacío con el estado `401`.

---

### 1.5 Actualizar Perfil de Usuario

Permite al usuario autenticado actualizar su nombre completo y/o su contraseña. Si se desea cambiar la contraseña, se debe proveer la contraseña actual para verificar la identidad.

- **URL:** `/api/users/me`
- **Método HTTP:** `PUT`
- **Rol requerido:** Cualquier usuario autenticado.
- **Content-Type:** `application/json`

#### Cuerpo de la Petición (JSON)

| Campo         | Tipo   | Requerido | Descripción                                                                                           |
| :------------ | :----- | :-------- | :---------------------------------------------------------------------------------------------------- |
| `fullName`    | String | No        | Nuevo nombre completo del usuario (entre 3 y 100 caracteres).                                         |
| `oldPassword` | String | No        | Contraseña actual (requerida únicamente si se incluye `newPassword`).                                 |
| `newPassword` | String | No        | Nueva contraseña (entre 6 y 50 caracteres, debe incluir al menos un número, una mayúscula y minúscula).|

##### Ejemplo de Cuerpo de Petición (Actualizar Nombre)
```json
{
  "fullName": "Juan Carlos Pérez"
}
```

##### Ejemplo de Cuerpo de Petición (Actualizar Contraseña)
```json
{
  "oldPassword": "securePassword123",
  "newPassword": "NewSecurePassword456"
}
```

#### Respuestas

- **`200 OK` (Actualización Exitosa):**
  ```json
  {
    "email": "juan.perez@example.com",
    "fullName": "Juan Carlos Pérez",
    "role": "CLIENT"
  }
  ```

- **`400 Bad Request` (Errores de Validación / Contraseña Incorrecta):**
  - Ejemplo si la contraseña actual no coincide:
    ```json
    {
      "error": "La contraseña actual es incorrecta."
    }
    ```
  - Ejemplo si la nueva contraseña no cumple con los requisitos de seguridad:
    ```json
    {
      "error": "La nueva contraseña debe contener al menos un número, una letra mayúscula y una letra minúscula."
    }
    ```

---

### 1.6 Desactivar Cuenta de Usuario

Desactiva la cuenta del usuario autenticado (baja lógica). Cambia su estado a inactivo, registra la fecha de baja, y modifica su correo electrónico de forma genérica agregando la palabra `disabled` antes de la extensión del dominio para liberar el correo original.

- **URL:** `/api/users/me`
- **Método HTTP:** `DELETE`
- **Rol requerido:** Cualquier usuario autenticado.

#### Respuestas

- **`200 OK` (Desactivación Exitosa):**
  ```json
  {
    "message": "Cuenta desactivada exitosamente."
  }
  ```

- **`400 Bad Request` (Usuario ya inactivo o no encontrado):**
  ```json
  {
    "error": "El usuario ya se encuentra desactivado."
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

Realiza una consulta al chatbot fundamentada en los manuales cargados. Soporta consultas de texto puro, imágenes puras o texto más imagen combinados. Genera embeddings semánticos a partir de la pregunta del usuario o de la interpretación visual de la imagen enviada, busca en la base de datos local los fragmentos de manuales más similares usando similitud de coseno, y utiliza Gemini (`gemini-2.5-flash`) para responder fundamentado **únicamente** en dichos fragmentos.

- **URL:** `/api/chat/ask`
- **Método HTTP:** `POST`
- **Rol requerido:** Público / Permitido sin autenticación (disponible para clientes y público general)
- **Content-Type:** `multipart/form-data`

#### Cuerpo de la Petición (form-data)

| Campo      | Tipo       | Requerido | Descripción                                                                              |
| :--------- | :--------- | :-------- | :--------------------------------------------------------------------------------------- |
| `question` | String     | No\*      | Consulta del usuario sobre funcionamiento, errores o calibración de los equipos médicos. |
| `file`     | File (Img) | No\*      | Archivo de imagen de la máquina/problema (Formatos permitidos: PNG, JPG, JPEG, WEBP).    |

> \* Nota: Al menos uno de los dos campos (`question` o `file`) debe estar presente en la petición.

##### Ejemplo de Petición (Postman Form-data)
* `question` (Text): "¿Qué significa esta luz roja en la pantalla?"
* `file` (File): `alarma_ventilador.png` (Archivo seleccionado desde disco)

#### Respuestas

- **`200 OK` (Consulta Exitosa):**

  ```json
  {
    "answer": "La luz roja parpadeante indica una alarma de alta presión en el circuito del respirador, la cual no debe exceder los 60 cmH2O...",
    "sources": [
      {
        "documentName": "manual_respirador_model_x.pdf",
        "chunkIndex": 12,
        "snippet": "La presión máxima permitida del circuito del respirador no debe exceder los 60 cmH2O..."
      }
    ]
  }
  ```

- **`400 Bad Request` (Parámetros Vacíos o Formato de Imagen Inválido):**
  - Si faltan ambos parámetros:
    ```json
    {
      "error": "The question or image file must be provided."
    }
    ```
  - Si el formato de imagen no es válido:
    ```json
    {
      "error": "Formato de imagen no permitido. Solo se permiten PNG, JPG, JPEG y WEBP."
    }
    ```

- **`500 Internal Server Error` (Fallo en el Servicio/API de Gemini o lectura de archivo):**
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

Obtiene el historial completo de mensajes y respuestas de una conversación específica. El sistema valida que la conversación pertenezca al usuario autenticado. Si el usuario envió imágenes en sus consultas, la respuesta del backend incluirá los datos de la imagen codificada en Base64.

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
      "imageBase64": null,
      "imageMimeType": null,
      "sources": null,
      "createdAt": "2026-07-06T10:05:20"
    },
    {
      "id": "c1f77d3b-ae29-4b21-a185-32e65c589b21",
      "role": "USER",
      "content": "[Imagen enviada]",
      "imageBase64": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk...",
      "imageMimeType": "image/png",
      "sources": null,
      "createdAt": "2026-07-06T10:06:10"
    },
    {
      "id": "d1a63cde-f1b2-4d2c-8ab5-f12b2a75908e",
      "role": "MODEL",
      "content": "La luz de alarma roja del monitor indica un fallo de alimentación...",
      "imageBase64": null,
      "imageMimeType": null,
      "sources": [
        {
          "documentName": "monitor_multiparametrico.pdf",
          "chunkIndex": 3,
          "snippet": "En caso de fallo de alimentación, el LED rojo parpadeará alternamente..."
        }
      ],
      "createdAt": "2026-07-06T10:06:15"
    }
  ]
  ```

---

### 4.4 Preguntar en una Sesión de Chat

Envía una pregunta dentro de una sesión de chat existente, permitiendo opcionalmente adjuntar una imagen. Guarda la pregunta, la imagen (si se provee) y la respuesta del chatbot (con sus fuentes y fragmentos citados) en la base de datos asociada a la sesión.

Si el título actual de la sesión es "Nueva Conversación", el sistema actualizará automáticamente el título a la pregunta del usuario (truncado a 40 caracteres) o a "Consulta con Imagen" si sólo envió un archivo.

- **URL:** `/api/chat/sessions/{sessionId}/ask`
- **Método HTTP:** `POST`
- **Rol requerido:** Usuario autenticado
- **Content-Type:** `multipart/form-data`

#### Parámetros

| Parámetro   | Ubicación | Tipo | Requerido | Descripción                             |
| :---------- | :-------- | :--- | :-------- | :-------------------------------------- |
| `sessionId` | Path      | UUID | Sí        | Identificador único de la conversación. |

#### Cuerpo de la Petición (form-data)

| Campo      | Tipo       | Requerido | Descripción                                                                              |
| :--------- | :--------- | :-------- | :--------------------------------------------------------------------------------------- |
| `question` | String     | No\*      | Pregunta del usuario sobre el chat.                                                      |
| `file`     | File (Img) | No\*      | Archivo de imagen de la máquina/problema (Formatos permitidos: PNG, JPG, JPEG, WEBP).    |

> \* Nota: Al menos uno de los dos campos (`question` o `file`) debe estar presente en la petición.

##### Ejemplo de Petición (Postman Form-data)
* `question` (Text): "¿Cómo se soluciona este código de error?"
* `file` (File): `codigo_error_monitor.jpg` (Archivo seleccionado desde disco)

#### Respuestas

- **`200 OK` (Respuesta Generada y Guardada):**
  ```json
  {
    "answer": "El código de error en la pantalla indica una batería baja. Deberá conectar el monitor multiparamétrico a la red eléctrica...",
    "sources": [
      {
        "documentName": "monitor_multiparametrico.pdf",
        "chunkIndex": 3,
        "snippet": "Cuando la batería interna está críticamente baja, se mostrará el error en pantalla..."
      }
    ]
  }
  ```

- **`400 Bad Request` (Pregunta o Archivo Vacíos / Imagen Inválida):**
  - Si faltan ambos parámetros:
    ```json
    {
      "error": "The question or image file must be provided."
    }
    ```
  - Si el formato de imagen no es válido:
    ```json
    {
      "error": "Formato de imagen no permitido. Solo se permiten PNG, JPG, JPEG y WEBP."
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

---

## 5. Soporte Técnico en Vivo y WebSockets

Esta sección describe la API REST y la especificación de comunicación en tiempo real (WebSockets) para el chat de soporte técnico directo entre clientes y administradores.

### 5.1 Solicitar Soporte Técnico (Cliente)

Crea una sesión de chat de soporte en vivo y la asigna directamente al administrador por defecto del sistema. La sesión se inicia en estado `ACTIVE`.

- **URL:** `/api/support/request`
- **Método HTTP:** `POST`
- **Rol requerido:** Usuario autenticado

#### Respuestas

- **`200 OK` (Sesión Creada y Asignada):**
  ```json
  {
    "id": "a90df23a-f3c8-47c0-a7d5-865f04a60124",
    "userId": "d74e0d7c-86e5-42cf-9d41-b0e6e713600f",
    "clientName": "Juan Pérez",
    "supportId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "supportName": "Administrator",
    "status": "ACTIVE",
    "createdAt": "2026-07-07T11:32:00",
    "assignedAt": "2026-07-07T11:32:00",
    "closedAt": null
  }
  ```

---

### 5.2 Obtener Sesión de Soporte Activa (Cliente)

Obtiene la sesión de soporte activa o en espera del cliente autenticado.

- **URL:** `/api/support/sessions/active`
- **Método HTTP:** `GET`
- **Rol requerido:** Usuario autenticado

#### Respuestas

- **`200 OK` (Sesión Activa Encontrada):**
  ```json
  {
    "id": "a90df23a-f3c8-47c0-a7d5-865f04a60124",
    "userId": "d74e0d7c-86e5-42cf-9d41-b0e6e713600f",
    "clientName": "Juan Pérez",
    "supportId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "supportName": "Administrator",
    "status": "ACTIVE",
    "createdAt": "2026-07-07T11:32:00",
    "assignedAt": "2026-07-07T11:32:00",
    "closedAt": null
  }
  ```

- **`204 No Content` (No hay sesión activa):**
  Retorna estado `204` si el cliente no posee sesiones de soporte abiertas o activas.

---

### 5.3 Obtener Mensajes de una Sesión

Obtiene el historial completo de mensajes en orden cronológico de una conversación de soporte.

- **URL:** `/api/support/sessions/{sessionId}/messages`
- **Método HTTP:** `GET`
- **Rol requerido:** Usuario autenticado

#### Respuestas

- **`200 OK`:**
  ```json
  [
    {
      "id": "c1da3d5e-ef12-42da-91bc-ab34e56c1234",
      "sessionId": "a90df23a-f3c8-47c0-a7d5-865f04a60124",
      "senderId": "d74e0d7c-86e5-42cf-9d41-b0e6e713600f",
      "senderType": "USER",
      "content": "Hola, necesito ayuda técnica con el respirador",
      "createdAt": "2026-07-07T11:33:10"
    },
    {
      "id": "df23a41b-ca34-45ba-bc12-cd5ef7890aef",
      "sessionId": "a90df23a-f3c8-47c0-a7d5-865f04a60124",
      "senderId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
      "senderType": "ADMIN",
      "content": "Buenas tardes Juan, ¿en qué te puedo asistir?",
      "createdAt": "2026-07-07T11:33:45"
    }
  ]
  ```

---

### 5.4 Finalizar Sesión de Soporte

Cierra y marca una sesión de soporte como resuelta. Bloquea el envío de más mensajes.

- **URL:** `/api/support/sessions/{sessionId}/close`
- **Método HTTP:** `POST`
- **Rol requerido:** Usuario autenticado

#### Respuestas

- **`200 OK`:**
  ```json
  {
    "id": "a90df23a-f3c8-47c0-a7d5-865f04a60124",
    "userId": "d74e0d7c-86e5-42cf-9d41-b0e6e713600f",
    "clientName": "Juan Pérez",
    "supportId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "supportName": "Administrator",
    "status": "RESOLVED",
    "createdAt": "2026-07-07T11:32:00",
    "assignedAt": "2026-07-07T11:32:00",
    "closedAt": "2026-07-07T11:35:10"
  }
  ```

---

### 5.5 Obtener Chats Activos del Administrador (Admin)

Obtiene una lista de todas las sesiones de soporte activas que están asignadas al administrador logueado en ese momento.

- **URL:** `/api/support/sessions/admin/active`
- **Método HTTP:** `GET`
- **Rol requerido:** Administrador (`ADMIN`)

#### Respuestas

- **`200 OK`:**
  ```json
  [
    {
      "id": "a90df23a-f3c8-47c0-a7d5-865f04a60124",
      "userId": "d74e0d7c-86e5-42cf-9d41-b0e6e713600f",
      "clientName": "Juan Pérez",
      "supportId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
      "supportName": "Administrator",
      "status": "ACTIVE",
      "createdAt": "2026-07-07T11:32:00",
      "assignedAt": "2026-07-07T11:32:00",
      "closedAt": null
    }
  ]
  ```

---

### 5.6 Obtener Historial de Chats Cerrados del Administrador (Admin)

Obtiene una lista de todas las sesiones de soporte finalizadas/cerradas (estados `RESOLVED` y `EXPIRED`) que estuvieron asignadas al administrador logueado.

- **URL:** `/api/support/sessions/admin/history`
- **Método HTTP:** `GET`
- **Rol requerido:** Administrador (`ADMIN`)

#### Respuestas

- **`200 OK`:**
  ```json
  [
    {
      "id": "a90df23a-f3c8-47c0-a7d5-865f04a60124",
      "userId": "d74e0d7c-86e5-42cf-9d41-b0e6e713600f",
      "clientName": "Juan Pérez",
      "supportId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
      "supportName": "Administrator",
      "status": "RESOLVED",
      "createdAt": "2026-07-07T11:32:00",
      "assignedAt": "2026-07-07T11:32:00",
      "closedAt": "2026-07-07T11:35:10"
    }
  ]
  ```

---

### 5.7 Conectar al Chat de Soporte vía WebSocket

Establece una conexión en tiempo real bidireccional para chatear.

- **URL de Conexión:** `ws://localhost:8080/ws/support`
- **Autenticación (Handshake):** Requiere un token JWT válido. Puede enviarse como cookie `token` o como parámetro de consulta de URL `?token=TU_JWT_TOKEN`.

#### Protocolo de Mensajería JSON

##### 5.6.6.1 Enviar Mensaje (Cliente)
Envía un mensaje a la sesión activa del cliente.
```json
{
  "type": "MESSAGE",
  "content": "Hola, necesito asistencia."
}
```

##### 5.6.6.2 Enviar Mensaje (Administrador)
Envía un mensaje a un cliente específico indicando el `sessionId`.
```json
{
  "type": "MESSAGE",
  "sessionId": "a90df23a-f3c8-47c0-a7d5-865f04a60124",
  "content": "Hola, ¿en qué te puedo ayudar?"
}
```

##### 5.6.6.3 Mensaje Recibido (Ambas Partes)
El servidor reenvía el mensaje en tiempo real con este formato:
```json
{
  "type": "MESSAGE",
  "id": "UUID_DEL_MENSAJE",
  "sessionId": "UUID_DE_LA_SESION",
  "senderId": "UUID_DEL_EMISOR",
  "senderType": "USER", // o "ADMIN"
  "content": "Contenido del mensaje",
  "createdAt": "2026-07-07T11:33:10"
}
```

##### 5.6.6.4 Notificación de Cierre de Sesión
El servidor avisa que el chat fue cerrado por la otra parte o API REST.
```json
{
  "type": "SESSION_CLOSED",
  "sessionId": "UUID_DE_LA_SESION"
}
```

##### 5.6.6.5 Mensaje de Error
El servidor responde con este formato en caso de infracciones (como chatear sin una sesión activa o sobre una sesión cerrada).
```json
{
  "type": "ERROR",
  "message": "Error: La sesión de soporte está cerrada y no permite enviar más mensajes."
}
```


