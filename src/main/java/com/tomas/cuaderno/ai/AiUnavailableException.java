package com.tomas.cuaderno.ai;

public class AiUnavailableException extends RuntimeException {
    public AiUnavailableException() { super("La sugerencia automática no está disponible ahora"); }
}
