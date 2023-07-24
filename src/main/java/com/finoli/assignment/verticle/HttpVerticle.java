package com.finoli.assignment.verticle;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

public class HttpVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> startPromise) {

        System.out.println("Inside Start Method of HttpVerticle : ");

        HttpServer server = vertx.createHttpServer();
        Router router = Router.router(vertx);

        // Get the event bus instance
        EventBus eventBus = vertx.eventBus();

        // POST Method
        router.post("/api/users").handler(BodyHandler.create())
                .handler(routingContext -> myEventBus(routingContext, eventBus, "post"));

        // adding a route to handle GET requests to retrieve all users
        router.get("/api/users")
                .handler(routingContext -> myEventBus(routingContext, eventBus, "get"));

        router.put("/api/users").handler(BodyHandler.create())
                .handler(routingContext -> myEventBus(routingContext, eventBus, "put"));

        router.post("/api/address").handler(BodyHandler.create())
                .handler(routingContext -> myEventBus(routingContext, eventBus, "postAddress"));

        router.get("/api/getAddress").handler(routingContext -> myEventBus(routingContext, eventBus, "getAddress"));

        router.get("/api/userWithAddByID")
                .handler(routingContext -> myEventBus(routingContext, eventBus, "userWithAddByID"));

        router.post("/api/userWithAdd").handler(BodyHandler.create())
                .handler(routingContext -> myEventBus(routingContext, eventBus, "postUserWithAddress"));

        // starting the server
        server.requestHandler(router).listen(8888, asyncResult -> {
            if (asyncResult.succeeded())
                System.out.println("Server started on port 8888");
            else
                System.out.println("Failed to start server: " + asyncResult.cause());
        });

    }

    private static void myEventBus(RoutingContext routingContext, EventBus eventBus, String types) {

        System.out.println("*********Routing Contest***********" + routingContext.queryParams());
        JsonObject userJson;
        if (routingContext.body().asJsonObject() == null) {

            String type = types;

            userJson = new JsonObject();
            userJson.put("type", type);
            System.out.println("User_Id : "+routingContext.queryParam("id").get(0));

            userJson.put("id", routingContext.queryParam("id").get(0));
            // ---For filtering User data
            // userJson.put("name", routingContext.queryParam("name"));
            // userJson.put("gender", routingContext.queryParam("gender"));
            // userJson.put("status", routingContext.queryParam("status"));

        } else {

            JsonObject requestBody = routingContext.body().asJsonObject();

            System.out.println("requestBody : " + " " + requestBody);

            String type = types;
            try {
                userJson = new JsonObject();

                userJson.put("type", type);
                userJson.put("requestBody", requestBody);

            } catch (DecodeException e) {
                routingContext.response()
                        .setStatusCode(400)
                        .end("Bad Request: Invalid user data");
                return;
            }
        }

        eventBus.request("eventBus.add", userJson, reply -> {
            if (reply.succeeded()) {
                System.out.println("Inside EventBus Reply Succeeded : ");
                JsonObject jObject = (JsonObject) reply.result().body();
                routingContext.response()
                        .setStatusCode(201)
                        .putHeader("content-type", "application/json")
                        .end(jObject.encode());
            } else {
                System.out.println("Inside EventBus Reply Succeeded : ");
                routingContext.response()
                        .setStatusCode(500)
                        .end("Failed to create user: " + reply.cause().getMessage());
            }
        });

    }

}
