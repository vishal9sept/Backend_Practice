package com.finoli.assignment.entity;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

  private Integer id;
  private String name;
  private String email;
  private String gender;
  private String status;
  private LocalDateTime timestamp;

  public User(String name, String email, String gender, String status, LocalDateTime timestamp) {
    this.setName(name);
    this.setEmail(email);
    this.setGender(gender);
    this.setStatus(status);
    this.setTimestamp(timestamp);
  }

}
