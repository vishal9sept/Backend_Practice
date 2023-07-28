package com.finoli.assignment;

import com.finoli.assignment.verticle.DatabaseVerticle;
import com.finoli.assignment.verticle.HttpVerticle;
import com.finoli.assignment.verticle.RabbitMQ;

import io.vertx.core.Vertx;

public class Application {
  public static void main(String[] args) {

    Vertx vertx = Vertx.vertx();

    vertx.deployVerticle(new DatabaseVerticle(),ar -> {
      if(ar.succeeded()){
        System.out.println("Succesfully Deployed Database Verticle : ");
        vertx.deployVerticle(new RabbitMQ());
      }
      else{
        System.out.println("Unable to deploy DatabaseVerticle :");
      }
    });
    vertx.deployVerticle(new HttpVerticle());
    

  }
}
