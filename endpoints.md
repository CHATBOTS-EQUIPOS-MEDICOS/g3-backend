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

### 1.7 Solicitar Código de Recuperación de Contraseña

Permite al usuario solicitar un código OTP para recuperar su contraseña. Envía un correo con un código de 6 caracteres alfanuméricos en mayúsculas válido por 30 minutos.

- **URL:** `/api/auth/recovery/request`
- **Método HTTP:** `POST`
- **Rol requerido:** Público (sin autenticación).
- **Content-Type:** `application/json`

#### Cuerpo de la Petición (JSON)

| Campo | Tipo | Requerido | Descripción |
| :--- | :--- | :--- | :--- |
| `email` | String | Sí | Correo electrónico del usuario (formato válido con `@` y `.`). |

##### Ejemplo de Cuerpo de Petición
```json
{
  "email": "juan.perez@example.com"
}
```

#### Respuestas

- **`200 OK` (Solicitud Procesada):**
  ```json
  {
    "message": "Código de recuperación enviado al correo electrónico."
  }
  ```

- **`400 Bad Request` (Usuario no registrado o inactivo):**
  - Ejemplo si el correo no está registrado:
    ```json
    {
      "error": "El correo electrónico no está registrado."
    }
    ```

---

### 1.8 Verificar Código de Recuperación

Permite verificar que el código OTP alfanumérico enviado por correo sea correcto y no haya expirado. Si es válido, marca el código como verificado y retorna un `resetToken` temporal de tipo UUID.

- **URL:** `/api/auth/recovery/verify`
- **Método HTTP:** `POST`
- **Rol requerido:** Público (sin autenticación).
- **Content-Type:** `application/json`

#### Cuerpo de la Petición (JSON)

| Campo | Tipo | Requerido | Descripción |
| :--- | :--- | :--- | :--- |
| `email` | String | Sí | Correo electrónico del usuario. |
| `code` | String | Sí | Código OTP de 6 caracteres alfanuméricos (no es sensible a mayúsculas/minúsculas). |

##### Ejemplo de Cuerpo de Petición
```json
{
  "email": "juan.perez@example.com",
  "code": "abc123"
}
```

#### Respuestas

- **`200 OK` (Código Verificado):**
  ```json
  {
    "resetToken": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
    "message": "Código verificado exitosamente."
  }
  ```

- **`400 Bad Request` (Código inválido, usado o expirado):**
  ```json
  {
    "error": "El código de verificación ha expirado."
  }
  ```

---

### 1.9 Restablecer Contraseña con Token

Permite al usuario cambiar su contraseña utilizando el `resetToken` obtenido en el paso de verificación. El token se invalida tras su uso.

- **URL:** `/api/auth/recovery/reset`
- **Método HTTP:** `POST`
- **Rol requerido:** Público (sin autenticación).
- **Content-Type:** `application/json`

#### Cuerpo de la Petición (JSON)

| Campo | Tipo | Requerido | Descripción |
| :--- | :--- | :--- | :--- |
| `resetToken` | String (UUID) | Sí | Token obtenido de la validación del código. |
| `newPassword` | String | Sí | Nueva contraseña (entre 6 y 50 caracteres, con números, mayúsculas y minúsculas). |

##### Ejemplo de Cuerpo de Petición
```json
{
  "resetToken": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
  "newPassword": "NewSecurePassword456"
}
```

#### Respuestas

- **`200 OK` (Reinicio Exitoso):**
  ```json
  {
    "message": "Contraseña restablecida exitosamente."
  }
  ```

- **`400 Bad Request` (Token inválido, expirado o ya usado):**
  ```json
  {
    "error": "El token de restablecimiento ya fue utilizado."
  }
  ```

---

### 1.10 [ADMIN] Listar Todos los Usuarios

Permite al administrador obtener la lista detallada de todos los usuarios registrados en el sistema, ordenados por fecha de creación de manera descendente (los más recientes primero).

- **URL:** `/api/admin/users`
- **Método HTTP:** `GET`
- **Rol requerido:** `ADMIN`

#### Respuestas

- **`200 OK` (Consulta Exitosa):**
  ```json
  [
    {
      "id": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
      "fullName": "Juan Pérez",
      "email": "juan.perez@example.com",
      "role": "CLIENT",
      "active": true,
      "fechaBaja": null,
      "createdAt": "2026-07-08T10:00:00.000",
      "updatedAt": "2026-07-08T10:00:00.000"
    },
    {
      "id": "9c0d1e2f-3a4b-5c6d-a1b2-c3d4e5f67a8b",
      "fullName": "Administrator",
      "email": "admin@example.com",
      "role": "ADMIN",
      "active": true,
      "fechaBaja": null,
      "createdAt": "2026-07-01T08:00:00.000",
      "updatedAt": "2026-07-01T08:00:00.000"
    }
  ]
  ```

- **`403 Forbidden` (Rol insuficiente):**
  Retorna error si el usuario no posee el rol `ADMIN`.

---

### 1.11 [ADMIN] Crear Usuario

Permite al administrador registrar un nuevo usuario con cualquier rol en el sistema.

- **URL:** `/api/admin/users`
- **Método HTTP:** `POST`
- **Rol requerido:** `ADMIN`
- **Content-Type:** `application/json`

#### Cuerpo de la Petición (JSON)

| Campo | Tipo | Requerido | Descripción |
| :--- | :--- | :--- | :--- |
| `fullName` | String | Sí | Nombre completo (entre 3 y 100 caracteres). |
| `email` | String | Sí | Correo electrónico único con formato válido (requiere `@` y `.`). |
| `password` | String | Sí | Contraseña (entre 6 y 50 caracteres). |
| `role` | String | Sí | Rol del usuario (`ADMIN` o `CLIENT`). |

##### Ejemplo de Cuerpo de Petición
```json
{
  "fullName": "Soporte Técnico",
  "email": "soporte@example.com",
  "password": "securePassword123",
  "role": "CLIENT"
}
```

#### Respuestas

- **`200 OK` (Creación Exitosa):**
  ```json
  {
    "id": "e837f694-df7a-4c28-97e0-911a7a0de3d4",
    "fullName": "Soporte Técnico",
    "email": "soporte@example.com",
    "role": "CLIENT",
    "active": true,
    "fechaBaja": null,
    "createdAt": "2026-07-08T10:15:00.000",
    "updatedAt": "2026-07-08T10:15:00.000"
  }
  ```

- **`400 Bad Request` (Error de Validación o Correo Duplicado):**
  ```json
  {
    "error": "El correo ya está registrado."
  }
  ```

---

### 1.12 [ADMIN] Editar Usuario

Permite al administrador actualizar la información de cualquier usuario registrado en el sistema de manera parcial.

- **URL:** `/api/admin/users/{id}`
- **Método HTTP:** `PUT`
- **Rol requerido:** `ADMIN`
- **Content-Type:** `application/json`

#### Cuerpo de la Petición (JSON)
Todos los campos son opcionales.

| Campo | Tipo | Descripción |
| :--- | :--- | :--- |
| `fullName` | String | Nombre completo (entre 3 y 100 caracteres). |
| `email` | String | Correo electrónico con formato válido (requiere `@` y `.`). |
| `password` | String | Nueva contraseña (entre 6 y 50 caracteres, debe incluir al menos un número, una mayúscula y una minúscula). |
| `role` | String | Rol del usuario (`ADMIN` o `CLIENT`). |
| `active` | Boolean | Estado de la cuenta. |

> [!WARNING]
> **Autoprotección:** Un administrador no puede degradar su propio rol (de `ADMIN` a `CLIENT`) ni desactivar su propia cuenta.

#### Respuestas

- **`200 OK` (Actualización Exitosa):**
  ```json
  {
    "id": "e837f694-df7a-4c28-97e0-911a7a0de3d4",
    "fullName": "Soporte Técnico Actualizado",
    "email": "soporte.nuevo@example.com",
    "role": "ADMIN",
    "active": true,
    "fechaBaja": null,
    "createdAt": "2026-07-08T10:15:00.000",
    "updatedAt": "2026-07-08T10:20:00.000"
  }
  ```

- **`400 Bad Request` (Errores de validación o auto-degradación):**
  ```json
  {
    "error": "No puedes cambiar tu propio rol de administrador."
  }
  ```

---

### 1.13 [ADMIN] Desactivar Usuario (Baja Lógica)

Permite al administrador realizar la baja lógica de un usuario. Esto cambia su estado `active` a `false`, registra la `fechaBaja` y modifica el correo añadiendo `"disabled"` (ejemplo: `soporte@exampledisabled.com`) para liberar el correo original.

- **URL:** `/api/admin/users/{id}`
- **Método HTTP:** `DELETE`
- **Rol requerido:** `ADMIN`

> [!WARNING]
> Un administrador no puede desactivar su propia cuenta.

#### Respuestas

- **`200 OK` (Desactivación Exitosa):**
  ```json
  {
    "message": "Usuario desactivado exitosamente."
  }
  ```

- **`400 Bad Request` (Auto-desactivación o usuario no encontrado):**
  ```json
  {
    "error": "No puedes desactivar tu propia cuenta de administrador."
  }
  ```

---

### 1.14 [ADMIN] Activar Usuario (Alta Lógica)

Permite al administrador reactivar un usuario anteriormente desactivado. Cambia su estado `active` a `true`, limpia `fechaBaja` a `null`, y restaura el correo electrónico original (eliminando el sufijo `"disabled"`) si el correo no está siendo usado por otra cuenta activa.

- **URL:** `/api/admin/users/{id}/activate`
- **Método HTTP:** `POST`
- **Rol requerido:** `ADMIN`

#### Respuestas

- **`200 OK` (Reactivación Exitosa):**
  ```json
  {
    "id": "e837f694-df7a-4c28-97e0-911a7a0de3d4",
    "fullName": "Soporte Técnico Actualizado",
    "email": "soporte@example.com",
    "role": "ADMIN",
    "active": true,
    "fechaBaja": null,
    "createdAt": "2026-07-08T10:15:00.000",
    "updatedAt": "2026-07-08T10:30:00.000"
  }
  ```

- **`400 Bad Request` (Usuario ya activo o correo original duplicado):**
  ```json
  {
    "error": "El usuario ya se encuentra activo."
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

### 2.5 Habilitar Documento

Cambia el estado de un documento a habilitado (`enabled = true`), de manera que sus fragmentos se vuelvan a utilizar en las búsquedas semánticas del chatbot.

- **URL:** `/api/documents/{id}/enable`
- **Método HTTP:** `POST`
- **Rol requerido:** `ADMIN`

#### Parámetros

| Parámetro | Ubicación | Tipo | Requerido | Descripción                                     |
| :-------- | :-------- | :--- | :-------- | :---------------------------------------------- |
| `id`      | Path      | UUID | Sí        | Identificador único del documento a habilitar.  |

#### Respuestas

- **`200 OK`:**
  Retorna el objeto documento actualizado con `enabled: true`.

  ```json
  {
    "id": "e837f694-df7a-4c28-97e0-911a7a0de3d4",
    "name": "manual_respirador_model_x.pdf",
    "contentType": "application/pdf",
    "sizeBytes": 1048576,
    "status": "COMPLETED",
    "storagePath": "documents/e837f694-df7a-4c28-97e0-911a7a0de3d4_manual_respirador_model_x.pdf",
    "enabled": true,
    "createdAt": "2026-07-03T17:15:30.123",
    "updatedAt": "2026-07-20T12:05:00"
  }
  ```

---

### 2.6 Deshabilitar Documento

Cambia el estado de un documento a deshabilitado (`enabled = false`), impidiendo que el chatbot utilice la información de este documento para responder a las consultas.

- **URL:** `/api/documents/{id}/disable`
- **Método HTTP:** `POST`
- **Rol requerido:** `ADMIN`

#### Parámetros

| Parámetro | Ubicación | Tipo | Requerido | Descripción                                        |
| :-------- | :-------- | :--- | :-------- | :------------------------------------------------- |
| `id`      | Path      | UUID | Sí        | Identificador único del documento a deshabilitar.  |

#### Respuestas

- **`200 OK`:**
  Retorna el objeto documento actualizado con `enabled: false`.

  ```json
  {
    "id": "e837f694-df7a-4c28-97e0-911a7a0de3d4",
    "name": "manual_respirador_model_x.pdf",
    "contentType": "application/pdf",
    "sizeBytes": 1048576,
    "status": "COMPLETED",
    "storagePath": "documents/e837f694-df7a-4c28-97e0-911a7a0de3d4_manual_respirador_model_x.pdf",
    "enabled": false,
    "createdAt": "2026-07-03T17:15:30.123",
    "updatedAt": "2026-07-20T12:05:00"
  }
  ```

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
    "updatedAt": "2026-07-06T10:05:00",
    "isClosed": false,
    "closedAt": null
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
      "updatedAt": "2026-07-06T10:05:25",
      "isClosed": false,
      "closedAt": null
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
      "createdAt": "2026-07-06T10:05:20",
      "liked": null
    },
    {
      "id": "c1f77d3b-ae29-4b21-a185-32e65c589b21",
      "role": "USER",
      "content": "[Imagen enviada]",
      "imageBase64": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk...",
      "imageMimeType": "image/png",
      "sources": null,
      "createdAt": "2026-07-06T10:06:10",
      "liked": null
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
      "createdAt": "2026-07-06T10:06:15",
      "liked": true
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

- **`500 Internal Server Error` (Sesión cerrada):**
  - Si se intenta consultar dentro de una sesión cerrada:
    ```json
    {
      "error": "Failed to answer the question in session: No se pueden enviar mensajes a una sesión de chat cerrada."
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

### 4.6 Cerrar Sesión de Chat

Permite al usuario marcar una sesión de chat como cerrada/inactiva. Una vez cerrada, no se pueden enviar más mensajes (preguntas) a dicha sesión. El sistema valida que la sesión exista y que pertenezca al usuario autenticado.

- **URL:** `/api/chat/sessions/{sessionId}/close`
- **Método HTTP:** `POST`
- **Rol requerido:** Usuario autenticado

#### Parámetros

| Parámetro   | Ubicación | Tipo | Requerido | Descripción                                     |
| :---------- | :-------- | :--- | :-------- | :---------------------------------------------- |
| `sessionId` | Path      | UUID | Sí        | Identificador único de la sesión a cerrar.      |

#### Respuestas

- **`200 OK` (Sesión Cerrada Exitosamente):**
  ```json
  {
    "id": "3c0b89ea-2f22-4a0b-9dcf-f25b29dbf0a2",
    "title": "Mantenimiento Preventivo D100",
    "createdAt": "2026-07-06T10:05:00",
    "updatedAt": "2026-07-06T10:05:25",
    "isClosed": true,
    "closedAt": "2026-07-17T11:55:00"
  }
  ```

- **`400 Bad Request` (Sesión no encontrada o no pertenece al usuario):**
  ```json
  {
    "error": "Sesión de chat no encontrada o no pertenece al usuario."
  }
  ```

---

### 4.7 Reaccionar con Like a un Mensaje

Registra una calificación positiva (`liked` = `true`) para un mensaje específico. Valida que el mensaje exista, pertenezca a una sesión del usuario autenticado, y que haya sido generado por la IA (rol `MODEL`).

- **URL:** `/api/chat/messages/{messageId}/like`
- **Método HTTP:** `POST`
- **Rol requerido:** Usuario autenticado

#### Parámetros

| Parámetro   | Ubicación | Tipo | Requerido | Descripción                                     |
| :---------- | :-------- | :--- | :-------- | :---------------------------------------------- |
| `messageId` | Path      | UUID | Sí        | Identificador único del mensaje a calificar.    |

#### Respuestas

- **`200 OK` (Mensaje Calificado Exitosamente):**
  ```json
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
    "createdAt": "2026-07-06T10:06:15",
    "liked": true
  }
  ```

- **`400 Bad Request` (Mensaje no encontrado, no pertenece al usuario, o no es un mensaje de la IA):**
  Retorna un cuerpo vacío con el estado `400`.

---

### 4.8 Reaccionar con Dislike a un Mensaje

Registra una calificación negativa (`liked` = `false`) para un mensaje específico. Valida que el mensaje exista, pertenezca a una sesión del usuario autenticado, y que haya sido generado por la IA (rol `MODEL`).

- **URL:** `/api/chat/messages/{messageId}/dislike`
- **Método HTTP:** `POST`
- **Rol requerido:** Usuario autenticado

#### Parámetros

| Parámetro   | Ubicación | Tipo | Requerido | Descripción                                     |
| :---------- | :-------- | :--- | :-------- | :---------------------------------------------- |
| `messageId` | Path      | UUID | Sí        | Identificador único del mensaje a calificar.    |

#### Respuestas

- **`200 OK` (Mensaje Calificado Exitosamente):**
  ```json
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
    "createdAt": "2026-07-06T10:06:15",
    "liked": false
  }
  ```

- **`400 Bad Request` (Mensaje no encontrado, no pertenece al usuario, o no es un mensaje de la IA):**
  Retorna un cuerpo vacío con el estado `400`.

---

## 5. Soporte Técnico en Vivo y WebSockets

Esta sección describe la API REST y la especificación de comunicación en tiempo real (WebSockets) para el chat de soporte técnico directo entre clientes y técnicos (empleados), y el acceso a transcripciones para administradores.

### 5.1 Solicitar Soporte Técnico (Cliente)

Crea una sesión de chat de soporte en vivo. Solo los usuarios con rol **`CLIENT`** pueden solicitar soporte técnico. La sesión se inicia en estado **`WAITING`** sin ningún técnico asignado (`support` = `null`). Genera un resumen del problema mediante inteligencia artificial (Gemini) basándose en las últimas interacciones del usuario con el chatbot.

Adicionalmente, esta acción notifica inmediatamente a todos los técnicos conectados a través de la alerta de WebSocket `NEW_WAITING_SESSION`.

- **URL:** `/api/support/request`
- **Método HTTP:** `POST`
- **Rol requerido:** `CLIENT`

#### Respuestas

- **`200 OK` (Sesión Creada en Espera):**
  ```json
  {
    "id": "a90df23a-f3c8-47c0-a7d5-865f04a60124",
    "userId": "d74e0d7c-86e5-42cf-9d41-b0e6e713600f",
    "clientName": "Juan Pérez",
    "supportId": null,
    "supportName": null,
    "status": "WAITING",
    "createdAt": "2026-07-07T11:32:00",
    "assignedAt": null,
    "closedAt": null,
    "summary": "El usuario solicita asistencia técnica para configurar su respirador ya que no enciende."
  }
  ```

- **`403 Forbidden` (Acceso denegado a roles incorrectos):**
  ```json
  {
    "error": "Su rol en el sistema (TECHNICIAN) no puede solicitar soporte técnico."
  }
  ```

---

### 5.2 Obtener Sesión de Soporte Activa (Cliente)

Obtiene la sesión de soporte activa o en espera (`WAITING`, `ACTIVE`, `PENDING_USER`) del cliente autenticado.

- **URL:** `/api/support/sessions/active`
- **Método HTTP:** `GET`
- **Rol requerido:** `CLIENT` (o cualquier usuario autenticado)

#### Respuestas

- **`200 OK` (Sesión Activa Encontrada):**
  ```json
  {
    "id": "a90df23a-f3c8-47c0-a7d5-865f04a60124",
    "userId": "d74e0d7c-86e5-42cf-9d41-b0e6e713600f",
    "clientName": "Juan Pérez",
    "supportId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "supportName": "Técnico Especialista",
    "status": "ACTIVE",
    "createdAt": "2026-07-07T11:32:00",
    "assignedAt": "2026-07-07T11:32:15",
    "closedAt": null,
    "summary": "El usuario solicita asistencia técnica para configurar su respirador ya que no enciende."
  }
  ```

- **`204 No Content` (No hay sesión activa):**
  Retorna estado `204` si el cliente no posee sesiones de soporte abiertas o activas.

---

### 5.3 Obtener Mensajes de una Sesión

Obtiene el historial completo de mensajes en orden cronológico de una conversación de soporte, bajo las siguientes restricciones de visibilidad:
- **`CLIENT`**: Puede ver los mensajes únicamente si la sesión le pertenece.
- **`TECHNICIAN`**: Puede ver los mensajes únicamente si la sesión está **`ACTIVE`** y está asignado a ella. No puede ver mensajes de sesiones finalizadas/cerradas.
- **`ADMIN`**: Puede ver los mensajes de cualquier sesión (activa o cerrada) para auditoría e historial.

- **URL:** `/api/support/sessions/{sessionId}/messages`
- **Método HTTP:** `GET`
- **Rol requerido:** Usuario autenticado con los permisos correspondientes.

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
      "senderType": "TECHNICIAN",
      "content": "Buenas tardes Juan, ¿en qué te puedo asistir?",
      "createdAt": "2026-07-07T11:33:45"
    }
  ]
  ```

- **`403 Forbidden` (Acceso denegado, p. ej., técnico intentando acceder a una sesión cerrada):**
  ```json
  {
    "error": "No tienes permiso para ver los mensajes de esta sesión. Los técnicos no pueden ver el historial de conversaciones de sesiones cerradas."
  }
  ```

---

### 5.4 Aceptar Sesión de Soporte (Técnico)

Permite a un técnico aceptar una sesión de soporte que se encuentra en espera (`WAITING`). La petición se gestiona a través de una cola FIFO concurrente con bloqueo pesimista en base de datos.
- Valida que el técnico tenga un número de sesiones activas simultáneas menor al límite máximo configurado (por defecto, `3`).
- Cambia el estado a `ACTIVE` y asocia al técnico.

- **URL:** `/api/support/sessions/{sessionId}/accept`
- **Método HTTP:** `POST`
- **Rol requerido:** `TECHNICIAN`

#### Respuestas

- **`200 OK` (Solicitud Encolada):**
  ```json
  {
    "message": "Solicitud de aceptación encolada. Procesando..."
  }
  ```

---

### 5.5 Finalizar Sesión de Soporte

Cierra y marca una sesión de soporte como resuelta. Esta acción envía automáticamente al cliente una notificación en tiempo real de tipo `SYSTEM_MESSAGE` indicando que el chat ha sido finalizado, seguido de la alerta de cierre `SESSION_CLOSED`. Bloquea el envío de más mensajes.

- **URL:** `/api/support/sessions/{sessionId}/close`
- **Método HTTP:** `POST`
- **Rol requerido:** Técnico asignado (`TECHNICIAN`)

#### Respuestas

- **`200 OK` (Sesión Cerrada con Éxito):**
  ```json
  {
    "id": "a90df23a-f3c8-47c0-a7d5-865f04a60124",
    "userId": "d74e0d7c-86e5-42cf-9d41-b0e6e713600f",
    "clientName": "Juan Pérez",
    "supportId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "supportName": "Técnico Especialista",
    "status": "RESOLVED",
    "createdAt": "2026-07-07T11:32:00",
    "assignedAt": "2026-07-07T11:32:30",
    "closedAt": "2026-07-07T11:35:10",
    "summary": "El usuario solicita asistencia técnica para configurar su respirador ya que no enciende."
  }
  ```

---

### 5.6 Listar Sesiones en Espera (Técnico)

Obtiene la lista de todas las sesiones de soporte que se encuentran actualmente esperando asignación (estado `WAITING`), incluyendo el resumen de IA.

- **URL:** `/api/support/sessions/waiting`
- **Método HTTP:** `GET`
- **Rol requerido:** `TECHNICIAN`

#### Respuestas

- **`200 OK`:**
  ```json
  [
    {
      "id": "a90df23a-f3c8-47c0-a7d5-865f04a60124",
      "userId": "d74e0d7c-86e5-42cf-9d41-b0e6e713600f",
      "clientName": "Juan Pérez",
      "supportId": null,
      "supportName": null,
      "status": "WAITING",
      "createdAt": "2026-07-07T11:32:00",
      "assignedAt": null,
      "closedAt": null,
      "summary": "El usuario solicita asistencia técnica para configurar su respirador ya que no enciende."
    }
  ]
  ```

---

### 5.7 Obtener Chats Activos del Técnico (Técnico)

Obtiene una lista de todas las sesiones de soporte activas (`ACTIVE`) que están asignadas al técnico logueado.

- **URL:** `/api/support/sessions/technician/active`
- **Método HTTP:** `GET`
- **Rol requerido:** Técnico (`TECHNICIAN`)

#### Respuestas

- **`200 OK`:**
  ```json
  [
    {
      "id": "a90df23a-f3c8-47c0-a7d5-865f04a60124",
      "userId": "d74e0d7c-86e5-42cf-9d41-b0e6e713600f",
      "clientName": "Juan Pérez",
      "supportId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
      "supportName": "Técnico Especialista",
      "status": "ACTIVE",
      "createdAt": "2026-07-07T11:32:00",
      "assignedAt": "2026-07-07T11:32:30",
      "closedAt": null,
      "summary": "El usuario solicita asistencia técnica para configurar su respirador ya que no enciende."
    }
  ]
  ```

---

### 5.8 Obtener Historial de Chats Cerrados del Técnico (Técnico)

Obtiene una lista de todas las sesiones de soporte finalizadas/cerradas (estados `RESOLVED` y `EXPIRED`) asignadas al técnico logueado (solo incluye los metadatos de sesión, no permite ver las conversaciones).

- **URL:** `/api/support/sessions/technician/history`
- **Método HTTP:** `GET`
- **Rol requerido:** Técnico (`TECHNICIAN`)

#### Respuestas

- **`200 OK`:**
  ```json
  [
    {
      "id": "a90df23a-f3c8-47c0-a7d5-865f04a60124",
      "userId": "d74e0d7c-86e5-42cf-9d41-b0e6e713600f",
      "clientName": "Juan Pérez",
      "supportId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
      "supportName": "Técnico Especialista",
      "status": "RESOLVED",
      "createdAt": "2026-07-07T11:32:00",
      "assignedAt": "2026-07-07T11:32:30",
      "closedAt": "2026-07-07T11:35:10",
      "summary": "El usuario solicita asistencia técnica para configurar su respirador ya que no enciende."
    }
  ]
  ```

---

### 5.9 Obtener Historial de Todos los Chats Cerrados (Administrador)

Obtiene una lista de todas las sesiones de soporte cerradas en el sistema (estados `RESOLVED` y `EXPIRED`), permitiendo auditorías para administradores.

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
      "supportName": "Técnico Especialista",
      "status": "RESOLVED",
      "createdAt": "2026-07-07T11:32:00",
      "assignedAt": "2026-07-07T11:32:30",
      "closedAt": "2026-07-07T11:35:10",
      "summary": "El usuario solicita asistencia técnica para configurar su respirador ya que no enciende."
    }
  ]
  ```

---

### 5.10 Conectar al Chat de Soporte vía WebSocket

Establece una conexión en tiempo real bidireccional para chatear.

- **URL de Conexión:** `ws://localhost:8080/ws/support`
- **Autenticación (Handshake):** Requiere un token JWT válido. Puede enviarse como cookie `token` o como parámetro de consulta de URL `?token=TU_JWT_TOKEN`.

#### Protocolo de Mensajería JSON (Eventos y Respuestas)

##### 5.10.1 Solicitar Soporte vía WebSocket (`REQUEST_SUPPORT`)
El cliente (`CLIENT`) puede iniciar una solicitud de soporte técnico. El servidor responderá con el evento `SESSION_STATUS` de forma inmediata.
```json
{
  "type": "REQUEST_SUPPORT"
}
```

##### 5.10.2 Aceptar Soporte vía WebSocket (`ACCEPT_SUPPORT`)
Un técnico (`TECHNICIAN`) puede aceptar y reclamar un chat de soporte en espera.
```json
{
  "type": "ACCEPT_SUPPORT",
  "sessionId": "a90df23a-f3c8-47c0-a7d5-865f04a60124"
}
```

##### 5.10.3 Cerrar Soporte vía WebSocket (`CLOSE_SUPPORT`)
El técnico asignado (`TECHNICIAN`) puede dar por finalizada la sesión de soporte de forma directa.
```json
{
  "type": "CLOSE_SUPPORT",
  "sessionId": "a90df23a-f3c8-47c0-a7d5-865f04a60124"
}
```

##### 5.10.4 Enviar Mensaje (Cliente)
Envía un mensaje a la sesión activa del cliente.
```json
{
  "type": "MESSAGE",
  "content": "Hola, necesito asistencia."
}
```

##### 5.10.5 Enviar Mensaje (Técnico)
Envía un mensaje indicando el `sessionId`.
```json
{
  "type": "MESSAGE",
  "sessionId": "a90df23a-f3c8-47c0-a7d5-865f04a60124",
  "content": "Hola, ¿en qué te puedo ayudar?"
}
```

##### 5.10.6 Mensaje Recibido (Ambas Partes)
El servidor reenvía el mensaje en tiempo real con este formato:
```json
{
  "type": "MESSAGE",
  "id": "UUID_DEL_MENSAJE",
  "sessionId": "UUID_DE_LA_SESION",
  "senderId": "UUID_DEL_EMISOR",
  "senderType": "USER", // o "TECHNICIAN"
  "content": "Contenido del mensaje",
  "createdAt": "2026-07-07T11:33:10"
}
```

##### 5.10.7 Alerta de Estado de la Sesión (`SESSION_STATUS`)
Notificación enviada al cliente confirmando el estado actual de su solicitud de soporte.
```json
{
  "type": "SESSION_STATUS",
  "sessionId": "UUID_DE_LA_SESION",
  "status": "WAITING" // o "ACTIVE", "RESOLVED"
}
```

##### 5.10.8 Alerta de Sesión Aceptada/Asignada (`SESSION_ACCEPTED`)
Enviada tanto al cliente como al técnico en tiempo real cuando un técnico acepta la sesión de soporte:
```json
{
  "type": "SESSION_ACCEPTED",
  "sessionId": "UUID_DE_LA_SESION",
  "supportId": "UUID_DEL_TECNICO",
  "supportName": "Nombre del Técnico"
}
```

##### 5.10.9 Notificación de Cierre de Sesión (`SESSION_CLOSED`)
El servidor avisa que la sesión fue cerrada de forma definitiva.
```json
{
  "type": "SESSION_CLOSED",
  "sessionId": "UUID_DE_LA_SESION"
}
```

##### 5.10.10 Respuestas Automáticas del Sistema (`SYSTEM_MESSAGE`)
Mensajes generados de forma automática por el backend para notificar eventos clave al cliente:
* **Si el cliente escribe en estado de espera (`WAITING`):**
  ```json
  {
    "type": "SYSTEM_MESSAGE",
    "sessionId": "UUID_DE_LA_SESION",
    "content": "Por favor, espera unos segundos a que un técnico acepte tu solicitud.",
    "createdAt": "2026-07-07T11:33:15"
  }
  ```
* **Si el técnico cierra la sesión de soporte:**
  ```json
  {
    "type": "SYSTEM_MESSAGE",
    "sessionId": "UUID_DE_LA_SESION",
    "content": "Su sesión de soporte ha sido finalizada.",
    "createdAt": "2026-07-07T11:35:10"
  }
  ```

##### 5.10.11 Alerta de Nueva Solicitud en Espera (`NEW_WAITING_SESSION`)
El servidor avisa a todos los técnicos en tiempo real que un cliente acaba de solicitar soporte técnico y se encuentra esperando un técnico asignado, adjuntando el resumen generado.
```json
{
  "type": "NEW_WAITING_SESSION",
  "sessionId": "UUID_DE_LA_SESION",
  "clientId": "UUID_DEL_CLIENTE",
  "clientName": "Nombre del Cliente",
  "summary": "El usuario solicita asistencia técnica para configurar su respirador ya que no enciende."
}
```

##### 5.10.12 Alerta de Sesión Reclamada (`SESSION_CLAIMED`)
El servidor avisa a todos los técnicos que una sesión de la lista de espera ya fue reclamada y debe removerse de la bandeja.
```json
{
  "type": "SESSION_CLAIMED",
  "sessionId": "UUID_DE_LA_SESION"
}
```

##### 5.10.13 Mensaje de Error
El servidor responde con este formato en caso de infracciones (como chatear sin una sesión activa o sobre una sesión cerrada).
```json
{
  "type": "ERROR",
  "message": "Error: La sesión de soporte está cerrada y no permite enviar más mensajes."
}
```


