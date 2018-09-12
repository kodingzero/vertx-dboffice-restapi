package exa.vertx.util;

import io.vertx.core.json.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Config {
    public static JsonObject fromFile(String file){

        try {
            return new JsonObject(new String(Files.readAllBytes(Paths.get(file))));
        } catch (IOException e) {
            throw new RuntimeException("Could not read file " + file, e);
        }
    }
}
