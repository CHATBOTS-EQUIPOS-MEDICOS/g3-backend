package com.chatbot.service;

import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class EmbeddingService {

    /**
     * Genera un vector normalizado de 1536 dimensiones de manera determinista 
     * a partir del contenido de texto utilizando un generador de números aleatorios 
     * sembrado con el hash del texto.
     */
    public String generateEmbedding(String text) {
        int dimension = 1536;
        float[] vector = new float[dimension];
        
        long seed = text != null ? text.hashCode() : 42;
        Random random = new Random(seed);
        
        for (int i = 0; i < dimension; i++) {
            vector[i] = random.nextFloat() * 2 - 1; // Valores entre -1.0 y 1.0
        }
        
        // Normalizar el vector para que la distancia del coseno funcione correctamente
        double sumSquare = 0;
        for (float v : vector) {
            sumSquare += v * v;
        }
        double norm = Math.sqrt(sumSquare);
        
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < dimension; i++) {
            sb.append(vector[i] / norm);
            if (i < dimension - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
