package com.finoli.assignment;

import com.finoli.assignment.verticle.DatabaseVerticle;
import com.finoli.assignment.verticle.HttpVerticle;

import io.vertx.core.Vertx;

public class Application {
  public static void main(String[] args) {

    Vertx vertx = Vertx.vertx();

    vertx.deployVerticle(new HttpVerticle());
    vertx.deployVerticle(new DatabaseVerticle());
    // vertx.deployVerticle(new UserVerticle());

  }
}
