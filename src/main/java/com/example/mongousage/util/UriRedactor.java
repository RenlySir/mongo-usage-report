package com.example.mongousage.util;

public final class UriRedactor {
    private UriRedactor() {
    }

    public static String redact(String uri) {
        if (uri == null) {
            return "";
        }
        return uri.replaceFirst("(mongodb(?:\\+srv)?://[^:/@]+):([^@]+)@", "$1:****@");
    }
}
