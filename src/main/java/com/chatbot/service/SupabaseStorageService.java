package com.chatbot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
@Slf4j
public class SupabaseStorageService {

    private final String supabaseUrl;
    private final String serviceRoleKey;
    private final HttpClient httpClient;

    public SupabaseStorageService(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.service-role-key}") String serviceRoleKey) {
        this.supabaseUrl = supabaseUrl;
        this.serviceRoleKey = serviceRoleKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Sube los bytes del archivo a Supabase Storage en el bucket 'documents'.
     */
    public void uploadFile(String path, byte[] fileData, String contentType) {
        String url = String.format("%s/storage/v1/object/documents/%s", supabaseUrl, path);
        log.info("Subiendo archivo a Supabase Storage: {}", url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + serviceRoleKey)
                .header("Content-Type", contentType)
                .header("x-upsert", "true") // Permite sobrescribir si el archivo ya existe
                .POST(HttpRequest.BodyPublishers.ofByteArray(fileData))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Error al subir archivo a Supabase Storage. Status: {}, Response: {}", 
                        response.statusCode(), response.body());
                throw new RuntimeException("Error al subir archivo a Supabase Storage: Status " + response.statusCode());
            }
            log.info("Archivo subido con éxito al path de almacenamiento: {}", path);
        } catch (Exception e) {
            log.error("Excepción al subir archivo a Supabase Storage", e);
            throw new RuntimeException("Error al subir archivo a Supabase Storage", e);
        }
    }

    /**
     * Descarga los bytes del archivo desde el bucket 'documents' de Supabase Storage.
     */
    public byte[] downloadFile(String path) {
        String url = String.format("%s/storage/v1/object/documents/%s", supabaseUrl, path);
        log.info("Descargando archivo desde Supabase Storage: {}", url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + serviceRoleKey)
                .GET()
                .build();

        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Error al descargar archivo de Supabase Storage. Status: {}", response.statusCode());
                throw new RuntimeException("Error al descargar archivo: Status " + response.statusCode());
            }
            return response.body();
        } catch (Exception e) {
            log.error("Excepción al descargar archivo de Supabase Storage", e);
            throw new RuntimeException("Error al descargar archivo de Supabase Storage", e);
        }
    }

    /**
     * Elimina un archivo del bucket 'documents' de Supabase Storage.
     */
    public void deleteFile(String path) {
        String url = String.format("%s/storage/v1/object/documents/%s", supabaseUrl, path);
        log.info("Eliminando archivo de Supabase Storage: {}", url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + serviceRoleKey)
                .DELETE()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            // Si el archivo ya no existe (404 o 400), lo consideramos exitoso
            if (response.statusCode() != 200 && response.statusCode() != 404 && response.statusCode() != 400) {
                log.error("Error al eliminar archivo de Supabase Storage. Status: {}, Response: {}", 
                        response.statusCode(), response.body());
                throw new RuntimeException("Error al eliminar archivo: Status " + response.statusCode());
            }
            log.info("Archivo eliminado con éxito del almacenamiento: {}", path);
        } catch (Exception e) {
            log.error("Excepción al eliminar archivo de Supabase Storage", e);
            throw new RuntimeException("Error al eliminar archivo de Supabase Storage", e);
        }
    }
}
