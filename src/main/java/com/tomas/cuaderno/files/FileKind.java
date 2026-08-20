package com.tomas.cuaderno.files;

import com.fasterxml.jackson.annotation.JsonValue;

public enum FileKind {
    IMAGE, DOCUMENT, VIDEO, AUDIO, OTHER;
    @JsonValue public String jsonValue() { return name().toLowerCase(); }
    public static FileKind fromValue(String value) { for (FileKind kind : values()) if (kind.name().equalsIgnoreCase(value)) return kind; throw new IllegalArgumentException("Unknown file kind: " + value); }
}
