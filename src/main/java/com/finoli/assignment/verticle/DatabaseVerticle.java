package com.finoli.assignment.verticle;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgConnection;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

public class DatabaseVerticle extends AbstractVerticle {

    private final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    private PgPool pool;
    private EventBus rabbitMQBus;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {

        System.out.println("Inside DB Verticle Start Method : ");

        PgConnectOptions connectOptions = new PgConnectOptions()
                .setPort(5432)
                .setHost("localhost")
                .setDatabase("finoli")
                .setUser("postgres")
                .setPassword("Prajwal@123");

        // Pool options
        PoolOptions poolOptions = new PoolOptions().setMaxSize(5);

        pool = PgPool.pool(vertx, connectOptions, poolOptions);

        vertx.eventBus().consumer("eventBus.add", this::eventBusHandler);

        rabbitMQBus = vertx.eventBus();

        startPromise.complete();

    }

    private Future<PgConnection> getConnection() {
        Promise<PgConnection> promise = Promise.promise();
        pool.getConnection(res -> {
            if (res.succeeded()) {
                promise.complete((PgConnection) res.result());
            } else {
                promise.fail(res.cause());
            }
        });
        return promise.future();
    }

    public <T> void eventBusHandler(Message<T> message) {
        JsonObject requestBody = (JsonObject) message.body();
        String type = requestBody.getString("type");
        if (type != null) {
            switch (type) {
                case "post":
                    createUser(message);
                    break;
                case "get":
                    getAllUsers(message);
                    break;
                case "put":
                    updateUser(message);
                    break;
                case "postAddress":
                    postAddress(message);
                    break;
                case "getAddress":
                    getAddressByUser(message);
                    break;
                case "postUserWithAddress":
                    createUserWithAddress(message);
                    break;
                case "userWithAddByID":
                    getUserAddressByUserID(message);
                    break;
            }
        }
    }

    // Find user by ID for updating User
    private Future<Boolean> findUserById(Integer id) {

        Promise<Boolean> promise = Promise.promise();
        String sql = "SELECT COUNT(*) AS count FROM user_info WHERE id = $1";

        getConnection().compose(connection -> {
            connection.preparedQuery(sql).execute(Tuple.of(id), res -> {
                if (res.succeeded()) {
                    int count = res.result().iterator().next().getInteger("count");
                    System.out.println("Inside FindUserBy ID ---Count ==:" + " " + count);
                    promise.complete(count > 0);
                } else {
                    promise.fail(res.cause());
                }
                // Close the database connection
                connection.close();
            });

            return promise.future();
        });

        return promise.future();
    }

    // For Find user by Email
    private Future<Boolean> checkUserExists(String email) {
        Promise<Boolean> promise = Promise.promise();
        String sql = "SELECT COUNT(*) AS count FROM user_info WHERE email = $1";

        getConnection().compose(connection -> {
            connection.preparedQuery(sql).execute(Tuple.of(email), res -> {
                if (res.succeeded()) {
                    int count = res.result().iterator().next().getInteger("count");
                    promise.complete(count > 0);
                } else {
                    promise.fail(res.cause());
                }
                // Close the database connection
                connection.close();
            });

            return promise.future();
        });

        return promise.future();
    }

    // GET
    public <T> void getAllUsers(Message<T> message) {

        System.out.println("Inside GEt users Method ");

        JsonObject input = (JsonObject) message.body();
        // JsonObject userJson = input.getJsonObject("inputJson");
        // String nameQuery = input.getString("name");

        // Filtering ---------------------------------------------------

        System.out.println("Message : " + message.body());

        String nameQuery = null;
        String genderQuery = null;
        String statusQuery = null;

        JsonArray nameArray = input.getJsonArray("name");
        JsonArray genderArray = input.getJsonArray("gender");
        JsonArray statusArray = input.getJsonArray("status");

        if (nameArray != null && nameArray.size() > 0)
            nameQuery = nameArray.getString(0);
        if (genderArray != null && genderArray.size() > 0)
            genderQuery = genderArray.getString(0);
        if (statusArray != null && statusArray.size() > 0)
            statusQuery = statusArray.getString(0);

        System.out.println(nameQuery + "  " + genderQuery + " " + statusQuery);

        StringBuilder queryBuilder = new StringBuilder("SELECT * FROM user_info ORDER By id");

        if (nameQuery != null) {
            queryBuilder.append(" WHERE name = '").append(nameQuery).append("'");
        }
        if (genderQuery != null) {
            if (nameQuery != null) {
                queryBuilder.append(" AND gender ='").append(genderQuery).append("'");
            } else {
                queryBuilder.append(" WHERE gender ='").append(genderQuery).append("'");
            }
        }

        if (statusQuery != null) {
            if (nameQuery != null || genderQuery != null)
                queryBuilder.append(" AND status ='").append(statusQuery).append("'");
            else
                queryBuilder.append(" WHERE status = '").append(statusQuery).append("'");
        }
        // ------------------------------------------------------------------------------------------------------------

        System.out.println(queryBuilder.toString());

        getConnection().compose(connection -> {

            Promise<RowSet<Row>> promise = Promise.promise();

            connection.query(queryBuilder.toString()).execute(res -> {
                if (res.succeeded()) {
                    RowSet<Row> rows = res.result();
                    promise.complete(rows);
                } else {
                    promise.fail(res.cause());
                }
                connection.close(); // Closed the DB connection
            });

            return promise.future();
        }).onSuccess(rows -> {
            List<JsonObject> usersList = new ArrayList<>();

            for (Row row : rows) {
                Integer id = row.getInteger("id");
                String name = row.getString("name");
                String email = row.getString("email");
                String gender = row.getString("gender");
                String status = row.getString("status");
                String timestamp = row.getString("timestamp");

                JsonObject userJson = new JsonObject()
                        .put("id", id)
                        .put("name", name)
                        .put("email", email)
                        .put("gender", gender)
                        .put("status", status)
                        .put("timestamp", timestamp);

                usersList.add(userJson);
            }

            JsonObject responseJson = new JsonObject().put("users", usersList);
            rabbitMQBus.publish("rabbitMQ", responseJson);
            logger.debug("Message published from -> getAllUsers() : " + responseJson.encodePrettily());
            message.reply(responseJson);
        }).onFailure(error -> {
            JsonObject errObj = new JsonObject().put("error", error.getMessage());
            message.reply(errObj);
        });
    }

    // POST
    public <T> void createUser(Message<T> message) {

        System.out.print("Inside POST Method : " + message.body().toString());

        JsonObject input = (JsonObject) message.body();
        JsonObject userJson = input.getJsonObject("requestBody");

        // Check if the user with the same email already exists
        String email = userJson.getString("email");
        checkUserExists(email).compose(exists -> {
            if (exists) {
                // User with the same email already exists, return an error response
                JsonObject errObj = new JsonObject()
                        .put("error", "User with the same email already exists");
                message.reply(errObj);
                return Future.failedFuture("User with the same email already exists");
            } else {
                // User does not exist, proceed with creating the new user
                Tuple userTuple = Tuple.of(
                        userJson.getString("name"),
                        email,
                        userJson.getString("gender").toUpperCase(),
                        userJson.getString("status").toUpperCase(),
                        LocalDateTime.now().toLocalDate().toString());

                // inserting the new user into the database
                return getConnection().compose(connection -> {
                    Promise<JsonObject> promise = Promise.promise();

                    String sql = "INSERT INTO user_info (name, email, gender, status, timestamp) VALUES ($1, $2, $3, $4, $5)";

                    connection.preparedQuery(sql).execute(userTuple, res2 -> {
                        if (res2.succeeded()) {
                            promise.complete(userJson);
                        } else {
                            promise.fail(res2.cause());
                        }
                        // Close the database connection
                        connection.close();
                    });

                    return promise.future();
                }).onSuccess(response -> {
                    JsonObject responseJson = new JsonObject()
                            .put("User Added Successfully with Name : ", userJson.getString("name"));

                    rabbitMQBus.publish("rabbitMQ", responseJson);
                    logger.debug("Message published from -> getAllUsers() : " + responseJson.encodePrettily());
                    message.reply(responseJson);
                }).onFailure(error -> {
                    JsonObject errObj = new JsonObject();
                    errObj.put("error", error.getMessage());
                    message.reply(errObj);
                });
            }
        });
    }

    // PUT
    public <T> void updateUser(Message<T> message) {

        System.out.print("Inside PUT Method : " + message.body().toString());

        JsonObject input = (JsonObject) message.body();
        JsonObject userJson = input.getJsonObject("requestBody");

        System.out.println("////****\\\\"+ userJson);

        // Extract the userId from the request body
        Integer Id = userJson.getInteger("id");

        // Check if the user with the specified userId exists before proceeding with the
        // update
        findUserById(Id).compose(existingUser -> {
            if (existingUser) {
                // User exists, proceed with the update
                Tuple userTuple = Tuple.of(
                        userJson.getString("name"),
                        userJson.getString("email"),
                        userJson.getString("gender").toUpperCase(),
                        userJson.getString("status").toUpperCase(),
                        LocalDateTime.now().toLocalDate().toString(),
                        Id // Use userId to identify the user for the update
                );

                return getConnection().compose(connection -> {
                    Promise<Void> promise = Promise.promise();

                    String sql = "UPDATE user_info SET name = $1, email = $2, gender = $3, status = $4, timestamp = $5 WHERE id = $6";

                    connection.preparedQuery(sql).execute(userTuple, res -> {
                        if (res.succeeded()) {
                            promise.complete();
                        } else {
                            promise.fail(res.cause());
                        }
                        // Close the database connection
                        connection.close();
                    });

                    return promise.future();
                }).onSuccess(response -> {
                    JsonObject responseJson = new JsonObject()
                            .put("User Updated Successfully with userId: ", Id);

                    rabbitMQBus.publish("rabbitMQ", responseJson);
                    logger.debug("Message published from -> updateUser() : " + responseJson.encodePrettily());
                    message.reply(responseJson);
                }).onFailure(error -> {
                    JsonObject errObj = new JsonObject();
                    errObj.put("error", error.getMessage());
                    message.reply(errObj);
                });
            } else {
                // User with the specified userId does not exist, reply with an error
                JsonObject errObj = new JsonObject()
                        .put("error", "User with the specified userId does not exist");
                message.reply(errObj);
                return Future.failedFuture("User with the specified userId does not exist");
            }
        });

    }

    // Post For Address
    public <T> Future<JsonObject> postAddress(Message<T> message) {

        System.out.println("Inside address : " + message.body().toString());
        JsonObject input = (JsonObject) message.body();
        JsonObject userJson = input.getJsonObject("requestBody");

        String sql = "INSERT INTO user_address (user_id, add_type, city, state) VALUES ($1, $2, $3, $4)";

        Tuple tuple = Tuple.of(userJson.getInteger("user_id"),
                userJson.getString("add_type"),
                userJson.getString("city"),
                userJson.getString("state"));

        return getConnection().compose(connection -> {
            Promise<JsonObject> promise = Promise.promise();

            connection.preparedQuery(sql).execute(tuple, res2 -> {
                if (res2.succeeded()) {
                    promise.complete(userJson);
                } else {
                    promise.fail(res2.cause());
                }
                // Close the database connection
                connection.close();
            });

            return promise.future();
        }).onSuccess(response -> {
            JsonObject responseJson = new JsonObject()
                    .put("Address Added Successfully for user_ID : ", userJson.getString("user_id"));
            message.reply(responseJson);
        }).onFailure(error -> {
            JsonObject errObj = new JsonObject();
            errObj.put("error", error.getMessage());
            message.reply(errObj);
        });

    }

    // Get All Users and Their addresses
    public <T> void getAddressByUser(Message<T> message) {

        System.out.println("Inside GetAddress Method ");

        System.out.println("Message : " + message.body());

        StringBuilder queryBuilder = new StringBuilder(
                "SELECT * FROM user_info U LEFT JOIN user_address ON user_address.user_id = U.id ORDER BY U.id");

        System.out.println(queryBuilder.toString());

        getConnection().compose(connection -> {

            Promise<RowSet<Row>> promise = Promise.promise();

            connection.query(queryBuilder.toString()).execute(res -> {
                if (res.succeeded()) {
                    RowSet<Row> rows = res.result();
                    promise.complete(rows);
                } else {
                    promise.fail(res.cause());
                }
                connection.close(); // Closed the DB connection
            });

            return promise.future();
        }).onSuccess(rows -> {
            List<JsonObject> usersList = new ArrayList<>();
            Integer prevUserId = 0;
            for (Row row : rows) {

                System.out.println("Rows :::--->" + row.deepToString());
                Integer id = row.getInteger("id");
                String name = row.getString("name");
                String email = row.getString("email");
                String gender = row.getString("gender");
                String status = row.getString("status");
                String timestamp = row.getString("timestamp");

                JsonObject userJson = new JsonObject()
                        .put("id", id)
                        .put("name", name)
                        .put("email", email)
                        .put("gender", gender)
                        .put("status", status)
                        .put("timestamp", timestamp);

                // ------For Address
                JsonArray addressesArray = new JsonArray();
                if (id == prevUserId) {
                    for (int i = 0; i < 2; i++) {
                        JsonObject address = new JsonObject();
                        address.put("address_id", row.getInteger("address_id"));
                        address.put("user_id", row.getInteger("user_id"));
                        address.put("add_type", row.getString("add_type"));
                        address.put("city", row.getString("city"));
                        address.put("state", row.getString("state"));
                        addressesArray.add(address);
                    }
                } else {
                    JsonObject address = new JsonObject();
                    address.put("address_id", row.getInteger("address_id"));
                    address.put("user_id", row.getInteger("user_id"));
                    address.put("add_type", row.getString("add_type"));
                    address.put("city", row.getString("city"));
                    address.put("state", row.getString("state"));
                    addressesArray.add(address);
                }
                JsonObject response = new JsonObject();
                response.put("user", userJson);
                response.put("address", addressesArray);

                usersList.add(response);
                prevUserId = id; // Previous User ID
            }

            JsonObject responseJson = new JsonObject().put("users", usersList);
            rabbitMQBus.publish("rabbitMQ", responseJson);
            message.reply(responseJson);
        }).onFailure(error -> {
            JsonObject errObj = new JsonObject().put("error", error.getMessage());
            message.reply(errObj);
        });

    }

    // Get a User and his Addresses by User_ID.
    public <T> void getUserAddressByUserID(Message<T> message) {

        System.out.println("Inside GetUserAddressBy UserId Method : ");

        JsonObject input = (JsonObject) message.body();
        String userId = input.getString("id");

        System.out.println("userId : " + userId);

        String userQuery = "SELECT * from user_info WHERE id=" + userId;
        // String addQuery = "SELECT * FROM user_address where user_id=" + userId;

        getConnection().compose(connection -> {

            Promise<JsonObject> promise = Promise.promise();

            connection.query(userQuery).execute(res -> {

                if (res.succeeded()) {
                    RowSet<Row> rows = res.result();
                    // System.out.println(rows.iterator().next().deepToString());
                    Row row = rows.iterator().next();

                    JsonObject user = new JsonObject();
                    user.put("id", row.getInteger("id"));
                    user.put("name", row.getString("name"));
                    user.put("email", row.getString("email"));
                    user.put("gender", row.getString("gender"));
                    user.put("status", row.getString("status"));
                    user.put("timestamp", row.getString("timestamp"));

                    // JsonObject response = new JsonObject();
                    // response.put("user", user);
                    Future<JsonArray> addResponse = getUsersAddresses(connection, userId);
                    addResponse.onComplete(address -> {
                        System.out.println("API adress : " + address);
                        user.put("address", address.result());
                        // message.reply("");
                        rabbitMQBus.publish("rabbitMQ", user);
                        logger.debug("Message published from -> getUserAddressByUserID() : " + user.encodePrettily());
                        promise.complete(user);
                    });
                } else {
                    promise.fail(res.cause());
                }
                connection.close(); // Closed the DB connection
            });

            return promise.future();
        }).onSuccess(row -> {

            // List<JsonObject> usersList = new ArrayList<>();
            // System.out.println("Rows :::--->" + row);
            // JsonObject responseJson = new JsonObject().put("users", usersList);
            message.reply(row);

        }).onFailure(error -> {
            JsonObject errObj = new JsonObject().put("error", error.getMessage());
            message.reply(errObj);
        });

    }

    // -----------------------------------------------------------------------------------------
    // Create User With Address array
    public <T> Future<JsonObject> createUserWithAddress(Message<T> message) {
        Promise<JsonObject> promise = Promise.promise();

        JsonObject input = (JsonObject) message.body();
        JsonObject requestBody = input.getJsonObject("requestBody");
        JsonObject userJson = requestBody;
        // JsonArray addressJson = requestBody.getJsonArray("address");
        JsonArray addressJson = userJson.getJsonArray("address");

        System.out.println("Inside UserWithAddress MEthod" + addressJson.toString());

        // inserting the new user into the database
        getConnection().compose(connection -> {

            return connection.begin().onSuccess(txn -> {
                Future<Integer> insertedUser = insertUser(connection, userJson);

                insertedUser.compose(res -> {
                    System.out.println("User_Id : " + res);
                    return insertAddress(connection, addressJson, res);
                }).onSuccess(res -> {
                    txn.commit().onSuccess(result -> {
                        promise.complete();
                        JsonObject response = new JsonObject();
                        response.put("User added with address : ----> ", requestBody);
                        rabbitMQBus.publish("rabbitMQ", response);

                        message.reply(response);
                    }).onFailure(error -> {
                        message.reply(error.getMessage());
                    });

                }).onFailure(err -> {
                    txn.rollback();
                    promise.fail(err.getMessage());
                    message.reply(err.getMessage());
                }).eventually(con -> connection.close());
            });

        });
        // .onSuccess(response -> {
        // JsonObject responseJson = new JsonObject()
        // .put("User Added With Address : ", requestBody);

        // message.reply(responseJson);

        // // promise.complete(responseJson);
        // }).onFailure(error -> {
        // JsonObject errObj = new JsonObject();
        // errObj.put("error", error.getMessage());
        // message.reply(errObj);
        // // promise.fail(errObj.toString());
        // });
        return promise.future();
    }

    private Future<Integer> insertUser(PgConnection connection, JsonObject userJson) {

        System.out.println("Inside -----> insertUser");

        Promise<Integer> promise = Promise.promise();

        String sql = "INSERT INTO user_info (name, email, gender, status, timestamp) " +
                "VALUES ($1, $2, $3, $4, $5) RETURNING id";

        connection.preparedQuery(sql).execute(Tuple.of(
                userJson.getString("name"),
                userJson.getString("email"),
                userJson.getString("gender").toUpperCase(),
                userJson.getString("status").toUpperCase(),
                LocalDateTime.now().toLocalDate().toString()), ar -> {
                    if (ar.succeeded()) {
                        Row row = ar.result().iterator().next();
                        Integer userId = row.getInteger("id");
                        promise.complete(userId);
                    } else {
                        promise.fail(ar.cause());
                    }
                });

        return promise.future();
    }

    private Future<Boolean> insertAddress(PgConnection connection, JsonArray addressJson, Integer userId) {

        System.out.println("Inside ---------> insertAddress");
        Promise<Boolean> promise = Promise.promise();
        String sql = "INSERT INTO user_address (user_id, add_type, city, state) VALUES ($1, $2, $3, $4)";

        // for (int i = 0; i < addressJson.size(); i++) {
        // System.out.println("---***---" +
        // addressJson.getJsonObject(i).getString("city"));
        // }

        // Create a list of futures to represent individual insert operations
        List<Future> insertFutures = new ArrayList<>();

        for (int i = 0; i < addressJson.size(); i++) {
            Tuple addressData = Tuple.of(
                    userId,
                    addressJson.getJsonObject(i).getString("add_type"),
                    addressJson.getJsonObject(i).getString("city"),
                    addressJson.getJsonObject(i).getString("state"));

            // Add the future representing the current insert operation
            insertFutures.add(
                    Future.future(fut -> connection.preparedQuery(sql).execute(addressData, ar -> {
                        if (ar.succeeded()) {
                            fut.complete();
                        } else {
                            fut.fail(ar.cause());
                        }
                    })));
        }

        // Use CompositeFuture to wait for all insert operations to complete
        CompositeFuture.all(insertFutures).onComplete(ar -> {
            if (ar.succeeded()) {
                promise.complete(true);
            } else {
                promise.fail(ar.cause());
            }
        });

        return promise.future();
    }

    private Future<JsonArray> getUsersAddresses(PgConnection connection, String userId) {
        Promise<JsonArray> promise = Promise.promise();

        String sql = "SELECT * FROM user_address WHERE user_id =" + userId;

        connection.query(sql).execute(ar -> {
            if (ar.succeeded()) {
                JsonArray addressesArray = new JsonArray();
                ar.result().forEach(row -> {
                    JsonObject address = new JsonObject();
                    address.put("address_id", row.getInteger("address_id"));
                    address.put("user_id", row.getInteger("user_id"));
                    address.put("add_type", row.getString("add_type"));
                    address.put("city", row.getString("city"));
                    address.put("state", row.getString("state"));
                    addressesArray.add(address);
                });
                promise.complete(addressesArray);
            } else {
                promise.fail(ar.cause());
            }
        });

        return promise.future();
    }

}
