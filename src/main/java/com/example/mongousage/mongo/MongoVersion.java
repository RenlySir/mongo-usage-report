package com.example.mongousage.mongo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record MongoVersion(int major, int minor, int patch) {
    private static final Pattern VERSION = Pattern.compile("^v?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?.*$");

    public static MongoVersion parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("MongoDB version is required. Supported major versions: 4, 5, 6, 7.");
        }
        Matcher matcher = VERSION.matcher(value.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid MongoDB version: " + value);
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        int patch = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
        if (major < 4 || major > 7) {
            throw new IllegalArgumentException("Unsupported MongoDB version: " + value + ". Supported major versions: 4, 5, 6, 7.");
        }
        return new MongoVersion(major, minor, patch);
    }

    public boolean atLeast(int requiredMajor, int requiredMinor) {
        if (major != requiredMajor) {
            return major > requiredMajor;
        }
        return minor >= requiredMinor;
    }

    public boolean atLeast(int requiredMajor, int requiredMinor, int requiredPatch) {
        if (major != requiredMajor) {
            return major > requiredMajor;
        }
        if (minor != requiredMinor) {
            return minor > requiredMinor;
        }
        return patch >= requiredPatch;
    }

    public String display() {
        return major + "." + minor + "." + patch;
    }
}
