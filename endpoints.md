# API Endpoints - Backend RAG de Equipos Médicos con Gemini

Este documento describe la especificación de los endpoints del backend en Spring Boot para la carga de manuales médicos, procesamiento vectorial (embeddings) y consultas mediante RAG (Retrieval-Augmented Generation) con Gemini.

## Base URL
```text
http://localhost:8080
```

> [!NOTE]
> Todos los endpoints tienen CORS configurado con `@CrossOrigin(origins = "*")` para permitir su fácil consumo desde cualquier aplicación frontend.

---

## 1. Cargar y Procesar Manuales PDF

Procesa un manual médico en formato PDF, extrae su texto, lo divide en fragmentos semánticos, calcula sus vectores (embeddings) con Gemini (`gemini-embedding-001`) y los almacena en la base de datos vectorial local basada en SQLite.

* **URL:** `/api/documents/upload`
* **Método HTTP:** `POST`
* **Content-Type:** `multipart/form-data`

### Parámetros de la Petición

| Parámetro | Tipo | Requerido | Descripción |
| :--- | :--- | :--- | :--- |
| `file` | File (PDF) | Sí | El archivo PDF del manual médico que se desea indexar en el sistema. |

### Ejemplo de Petición (curl)
```bash
curl -X POST -F "file=@/ruta/al/manual_respirador_model_x.pdf" http://localhost:8080/api/documents/upload
```

### Ejemplo de Respuesta Exitosa (`200 OK`)
```json
{
  "message": "Documento procesado e indexado correctamente.",
  "filename": "manual_respirador_model_x.pdf",
  "chunksCount": 42
}
```

### Ejemplo de Respuesta de Error (`400 Bad Request` o `500 Internal Server Error`)
```json
{
  "error": "Only PDF files are supported."
}
```

---

## 2. Consultar al Chatbot (Estilo RAG)

Realiza una consulta semántica al chatbot. Genera el embedding de la pregunta del usuario, busca en SQLite los fragmentos de manuales más similares usando similitud de coseno, inyecta dicho contexto como fuente y utiliza Gemini (`gemini-2.5-flash`) bajo estrictas instrucciones de no inventar respuestas para contestar fundamentado **únicamente** en los manuales cargados.

* **URL:** `/api/chat/ask`
* **Método HTTP:** `POST`
* **Content-Type:** `application/json`

### Cuerpo de la Petición (JSON)

| Campo | Tipo | Requerido | Descripción |
| :--- | :--- | :--- | :--- |
| `question` | String | Sí | La consulta o pregunta del usuario sobre el funcionamiento, errores o calibración de los equipos médicos. |

### Ejemplo de Cuerpo de Petición
```json
{
  "question": "¿Cuál es la presión máxima permitida en el circuito del respirador?"
}
```

### Ejemplo de Respuesta Exitosa (`200 OK`)
```json
{
  "answer": "La presión máxima permitida en el circuito del respirador es de 60 cmH2O. Si se supera este valor, se activará de inmediato la alarma de alta presión y el sistema abrirá la válvula de seguridad exhalatoria.",
  "sources": [
    {
      "documentName": "manual_respirador_model_x.pdf",
      "chunkIndex": 12,
      "snippet": "El circuito de paciente está diseñado para operar con un rango seguro de 0 a 50 cmH2O. La presión máxima permitida del circuito del respirador no debe exceder los 60 cmH2O..."
    },
    {
      "documentName": "manual_respirador_model_x.pdf",
      "chunkIndex": 13,
      "snippet": "...En caso de sobrepresión mayor a 60 cmH2O, la válvula exhalatoria se abre de forma automática liberando el exceso de flujo al exterior..."
    }
  ]
}
```

### Ejemplo de Respuesta cuando la Información NO existe en los Manuales
```json
{
  "answer": "Lo siento, la respuesta a esa pregunta no se encuentra en los manuales de equipos médicos disponibles.",
  "sources": []
}
```

### Ejemplo de Respuesta si NO hay manuales cargados aún
```json
{
  "answer": "No hay manuales cargados en el sistema. Por favor, sube archivos PDF de manuales de equipos médicos para comenzar a chatear.",
  "sources": []
}
```
