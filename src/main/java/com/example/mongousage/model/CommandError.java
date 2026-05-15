package com.example.mongousage.model;

public record CommandError(String scope, String command, String message) {
}
