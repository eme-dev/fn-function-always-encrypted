package com.empresa.security.poc.model;

public final class EncryptedEnvelope {

    private final String ciphertext;
    private final String wrappedDek;
    private final String iv;

    public EncryptedEnvelope(String ciphertext, String wrappedDek, String iv) {
        this.ciphertext = ciphertext;
        this.wrappedDek = wrappedDek;
        this.iv = iv;
    }

    public String getCiphertext() {
        return ciphertext;
    }

    public String getWrappedDek() {
        return wrappedDek;
    }

    public String getIv() {
        return iv;
    }
}
