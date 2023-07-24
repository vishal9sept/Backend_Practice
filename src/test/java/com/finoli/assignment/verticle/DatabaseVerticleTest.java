package com.finoli.assignment.verticle;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(MockitoExtension.class)
@ExtendWith(VertxExtension.class)
public class DatabaseVerticleTest {

    void testCreateUserWithAddress(Vertx vertx, VertxTestContext testContext) {

        WebClient client = WebClient.create(vertx);

        JsonObject user = new JsonObject();
        user.put("name", "john");
        user.put("email", "john@mail.com");
        user.put("gender", "MALE");
        user.put("status", "ACTIVE");
        user.put("timestamp", "24-07-2023");

        JsonArray address = new JsonArray();
        JsonObject a1 = new JsonObject();
        JsonObject a2 = new JsonObject();

        a1.put("add_type", "HOME");
        a1.put("city", "Kolkata");
        a1.put("state", "WB");

        a2.put("add_type", "OFFICE");
        a2.put("city", "Bangalore");
        a2.put("state", "Karnataka");

        address.add(a1);
        address.add(a2);

        JsonObject requestBody = new JsonObject();
        requestBody.put("user", user);
        requestBody.put("address", address);

        Future<HttpResponse<Buffer>> response = client.post(8888,"localhost","/users")
                .putHeader("Content-Type", "application/json")
                .sendJsonObject(requestBody);

        response.onComplete(ar -> {
            if(ar.succeeded()){
                HttpResponse<Buffer> res = ar.result();
                testContext.verify(() -> {
                    Assertions.assertEquals(201, res.statusCode());
                    JsonObject responseBody = res.bodyAsJsonObject();
                    Assertions.assertEquals(43, responseBody.getInteger("id"));
                    Assertions.assertEquals("john", responseBody.getString("name"));
                    Assertions.assertEquals("john@mail.com", responseBody.getString("email"));
                    Assertions.assertEquals("MALE", responseBody.getString("gender"));
                    Assertions.assertEquals("ACTIVE", responseBody.getString("status"));
                    Assertions.assertEquals("24-07-2023", requestBody.getString("timestamp"));
                });
            }
            else{
                testContext.failNow(ar.cause().getMessage());
            }
            testContext.completeNow();
        });
    }

}
