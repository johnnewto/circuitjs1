package com.lushprojects.circuitjs1.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class TestFixtures {

    private TestFixtures() {
    }

    static String loadSfcr(String fileName) throws IOException {
        return loadResource("sfcr/" + fileName);
    }

    static String loadResource(String resourcePath) throws IOException {
        InputStream input = TestFixtures.class.getClassLoader().getResourceAsStream(resourcePath);
        if (input == null) {
            throw new IOException("Fixture resource not found: " + resourcePath);
        }
        try {
            return readAll(input);
        } finally {
            input.close();
        }
    }

    private static String readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), "UTF-8");
    }
}
