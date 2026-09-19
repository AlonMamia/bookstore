package com.alon.bookstore.user;

import lombok.Data;

import java.time.LocalDate;

@Data
public class UserDtoOut {

    private String email;

    private String role;

    private String firstName;

    private String lastName;

    private LocalDate dateOfBirth;
}
