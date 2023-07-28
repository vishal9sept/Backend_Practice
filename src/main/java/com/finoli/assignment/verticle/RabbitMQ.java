package com.finoli.assignment.verticle;

import io.vertx.amqp.AmqpClient;
import io.vertx.amqp.AmqpClientOptions;
import io.vertx.amqp.AmqpConnection;
import io.vertx.amqp.AmqpMessage;
import io.vertx.amqp.AmqpReceiver;
import io.vertx.amqp.AmqpSender;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;

public class RabbitMQ extends AbstractVerticle {

    private final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    private AmqpConnection connection;
    private AmqpSender sender = null;
    private AmqpReceiver receiver = null;

    @Override
    public void start(Promise<Void> startPromise) {

        logger.info("inside start of RabbitMQ Verticle");

        // JsonObject config = config();

        // JsonObject amqpConfig = config.getJsonObject("amqp");

        // String host = amqpConfig.getString("host");
        // Integer port = amqpConfig.getInteger("port");
        // String user = amqpConfig.getString("username");
        // String password = amqpConfig.getString("password");

        AmqpClientOptions amqpOpt = new AmqpClientOptions()
                .setHost("localhost")
                .setPort(5672)
                .setUsername("amqp_user")
                .setPassword("amqp_pw")
                .setHeartbeat(30000)
                .setConnectTimeout(2000);

        AmqpClient client = AmqpClient.create(vertx, amqpOpt);

        client.connect()
                .onComplete(ar -> {
                    if (ar.failed()) {
                        logger.error("Failed to establish AMQP connection : " + ar.result());
                        startPromise.fail(ar.cause());
                    } else {
                        logger.debug("AMQP Connection established Successfully !!!");
                        connection = ar.result();

                        Promise<Void> senderPromise = Promise.promise();

                        createSenderMethod(senderPromise);

                        senderPromise.future()
                                .onSuccess(s -> {
                                    logger.info("RabbitMQ Processing Verticle Deployed");
                                    startPromise.complete();
                                })
                                .onFailure(err -> {
                                    logger.error("Failed to deploy RabbitMQ Processing Verticle: " +
                                            err.getMessage());
                                    startPromise.fail(err);
                                });
                    }
                });

        vertx.eventBus().consumer("rabbitMQ", message -> {
            JsonObject json = (JsonObject) message.body();
            logger.info("Inside EventBus Consumer Method :");
            String jsonString = json.encode();
            publishMessage(jsonString);
        });
    }

    private void createSenderMethod(Promise<Void> promise) {
        connection.createSender("/exchange/amq.topic")
                .onSuccess(sender -> {
                    this.sender = sender;
                    logger.debug("AMQP Sender created successfully");
                    promise.complete();
                })
                .onFailure(err -> {
                    logger.error("Failed to create AMQP Sender: " + err.getMessage());
                    promise.fail(err);
                });
    }

    private void publishMessage(String message) {
        if (sender != null) {
            AmqpMessage amqpMessage = AmqpMessage.create()
                    .address("/exchange/amq.topic")
                    .subject("user.create")
                    .withBody(message)
                    .build();
            System.out.println(sender.toString() + " " + amqpMessage.bodyAsString());

            try {
                sender.exceptionHandler(err -> {
                    System.out.println("Exception at 103" + " " + err.getMessage());
                });

                sender.sendWithAck(amqpMessage).onComplete(acked -> {
                    if (acked.succeeded()) {
                        System.out.println("AMQP Message Published Successfully");
                    } else {
                        System.out.println("AMQP Message Publish Failed ");
                    }
                });
            } catch (Exception e) {
                System.out.println("Exception at Line-114" + " " + e.getMessage());
            }
        } else {
            logger.warn("AMQP Sender is not initialized. Cannot publish message.");
        }
    }

    @Override
    public void stop() {
        if (sender != null) {
            sender.close();
        }
        if (receiver != null) {
            receiver.close();
        }
        if (connection != null) {
            connection.close();
        }
        logger.info("RabbitMQ Processing Verticle stopped");
    }

}
