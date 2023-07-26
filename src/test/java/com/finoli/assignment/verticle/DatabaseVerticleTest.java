package com.finoli.assignment.verticle;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@TestInstance(Lifecycle.PER_CLASS)
@ExtendWith(MockitoExtension.class)
@ExtendWith(VertxExtension.class)
public class DatabaseVerticleTest {

    @BeforeAll
    void deploy_verticle(Vertx vertx, VertxTestContext testContext) {
        vertx.deployVerticle(new HttpVerticle(), testContext.succeedingThenComplete());
        vertx.deployVerticle(new DatabaseVerticle(), testContext.succeedingThenComplete());
    }

    @Test
    @DisplayName("Create User Test")
    void testCreateUserWithAddress(Vertx vertx, VertxTestContext testContext) {

        WebClient client = WebClient.create(vertx);

        JsonObject user = new JsonObject();
        user.put("name", "Sean");
        user.put("email", "sean@mail.com");
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

        // JsonObject requestBody = new JsonObject();
        // requestBody.put("user", user);
        user.put("address", address);

        Future<HttpResponse<Buffer>> response = client.post(8888, "localhost", "/api/userWithAdd")
                .putHeader("Content-Type", "application/json")
                .sendJsonObject(user);

        response.onComplete(ar -> {
            if (ar.succeeded()) {
                HttpResponse<Buffer> res = ar.result();
                testContext.verify(() -> {
                    Assertions.assertEquals(201, res.statusCode());
                    JsonObject responseBody = res.bodyAsJsonObject();
                    JsonObject responseJson = responseBody.getJsonObject("User added with address : ----> ");
                    System.out.println("Inside Test : " + responseJson);
                    // Assertions.assertEquals(43, requestJson.getInteger("id"));
                    Assertions.assertEquals("Sean", responseJson.getString("name"));
                    Assertions.assertEquals("sean@mail.com", responseJson.getString("email"));
                    Assertions.assertEquals("MALE", responseJson.getString("gender"));
                    Assertions.assertEquals("ACTIVE", responseJson.getString("status"));
                    Assertions.assertEquals("24-07-2023", responseJson.getString("timestamp"));
                });
            } else {
                testContext.failNow(ar.cause().getMessage());
            }
            testContext.completeNow();
        });
    }

    @Test
    @DisplayName("Get All Users")
    void testGetAllUserWithAddress(Vertx vertx, VertxTestContext testContext) {

        WebClient client = WebClient.create(vertx);

        JsonObject jsonObject = new JsonObject();

        Future<HttpResponse<Buffer>> tableData = client.get(8888, "localhost", "/api/userWithAddByID?id=3")
                .putHeader("Content-Type", "application/json")
                .sendJsonObject(jsonObject);

        tableData.onComplete(res -> {
            if (res.succeeded()) {
                HttpResponse<Buffer> response = res.result();
                testContext.verify(() -> {
                    Assertions.assertEquals(201, response.statusCode());
                    JsonObject responseJson = response.bodyAsJsonObject();
                    Assertions.assertEquals(3, responseJson.getInteger("id"));
                    Assertions.assertEquals("Harry", responseJson.getString("name"));
                    Assertions.assertEquals("harry@mail.com", responseJson.getString("email"));
                    Assertions.assertEquals("MALE", responseJson.getString("gender"));
                    Assertions.assertEquals("INACTIVE", responseJson.getString("status"));
                    Assertions.assertEquals("2023-07-12", responseJson.getString("timestamp"));

                    JsonArray addArray = responseJson.getJsonArray("address");
                    JsonObject a1 = addArray.getJsonObject(0);
                    Assertions.assertEquals(1, a1.getInteger("address_id"));
                    Assertions.assertEquals(3, a1.getInteger("user_id"));
                    Assertions.assertEquals("HOME", a1.getString("add_type"));
                    Assertions.assertEquals("Kolkata", a1.getString("city"));
                    Assertions.assertEquals("West Bengal", a1.getString("state"));

                    JsonObject a2 = addArray.getJsonObject(1);
                    Assertions.assertEquals(4, a2.getInteger("address_id"));
                    Assertions.assertEquals(3, a2.getInteger("user_id"));
                    Assertions.assertEquals("OFFICE", a2.getString("add_type"));
                    Assertions.assertEquals("Kalimpong", a2.getString("city"));
                    Assertions.assertEquals("West Bengal", a2.getString("state"));
                });
            } else {
                testContext.failNow(res.cause());
            }
            testContext.completeNow();
        });
    }

    @Test
    @DisplayName("Update Users Test")
    void testUpdateUser(Vertx vertx, VertxTestContext testContext) {

        WebClient client = WebClient.create(vertx);

        JsonObject requestBody = new JsonObject();
        requestBody.put("id", 36);
        requestBody.put("name", "Lokesh");
        requestBody.put("email", "lokesh@mail.com");
        requestBody.put("gender", "MALE");
        requestBody.put("status", "ACTIVE");

        // Perform the HTTP request
        Future<HttpResponse<Buffer>> tableData = client.put(8888, "localhost", "/api/users")
                .putHeader("Content-Type", "application/json")
                .sendJsonObject(requestBody);

        // Verify the response
        tableData.onComplete(ar -> {
            if (ar.succeeded()) {
                HttpResponse<Buffer> response = ar.result();
                testContext.verify(() -> {
                    Assertions.assertEquals(201, response.statusCode());
                    JsonObject responseJson = response.bodyAsJsonObject();
                    Assertions.assertEquals(36, responseJson.getInteger("User Updated Successfully with userId: "));
                });
            } else {
                testContext.failNow(ar.cause());
            }
            testContext.completeNow();
        });

    }

    @AfterAll
    @DisplayName("Check that verticle is still there and then Close It")
    void lastChecks(Vertx vertx) {

        assertThat(vertx.deploymentIDs())
        .isNotEmpty()
        .hasSize(1);

        vertx.close();
    }
}
