/*
 * Copyright (C) 2020 Graylog, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program. If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */
package org.graylog.testing.completebackend.apis;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.restassured.path.json.JsonPath;
import org.graylog2.rest.models.users.requests.PermissionEditRequest;

import java.util.List;
import java.util.Set;

import static io.restassured.RestAssured.given;

public class Users implements GraylogRestApi {

    private final GraylogApis api;

    public Users(GraylogApis api) {
        this.api = api;
    }

    public record User(@JsonProperty("username") String username,
                       @JsonProperty("password") String password,
                       @JsonProperty("first_name") String firstName,
                       @JsonProperty("last_name") String lastName,
                       @JsonProperty("email") String email,
                       @JsonProperty("service_account") boolean serviceAccount,
                       @JsonProperty("session_timeout_ms") long sessionTimeoutMs,
                       @JsonProperty("timezone") String timezone,
                       @JsonProperty("roles") List<String> roles,
                       @JsonProperty("permissions") List<String> permissions
    ) {
    }

    public static final User LOCAL_ADMIN = new User("admin", "admin", "Admin", "Admin", "admin@graylog", false, 30_0000, "UTC", List.of(), List.of());
    public static final User JOHN_DOE = new User("john.doe", "asdfgh", "John", "Doe", "john@graylog", false, 30_0000, "Europe/Vienna", List.of(), List.of());
    public static final User REPORT_MANAGER = new User("don.glow", "hrblfrsch", "Don", "Glow", "don@graylog", false, 30_0000, "Europe/Vienna", List.of(), List.of());
    public static final User ANOTHER_SIMPLE_USER = new User("pow.poe", "grbldrbl", "Pow", "poe", "pow@graylog", false, 30_0000, "Europe/Vienna", List.of(), List.of());

    public JsonPath createUser(User user) {
        given()
                .spec(api.requestSpecification())
                .when()
                .body(user)
                .post("/users")
                .then()
                .log().ifError()
                .statusCode(201);

        return getUserInfo(user.username);
    }

    public JsonPath addUserToRole(User user, String role) {
        given()
                .spec(api.requestSpecification())
                .when()
                .put("/roles/" + role + "/members/" + user.username)
                .then()
                .log().ifError()
                .statusCode(204);

        return getUserInfo(user.username);
    }

    public JsonPath setPermissions(User user, Set<String> permissions) {
        given()
                .spec(api.requestSpecification())
                .when()
                .body(PermissionEditRequest.create(permissions.stream().toList()))
                .put("/users/" + user.username + "/permissions")
                .then()
                .log().ifError()
                .statusCode(204);

        return getUserInfo(user.username);
    }

    public JsonPath getUserInfo(String username) {
        return given()
                .spec(api.requestSpecification())
                .when()
                .get("/users/" + username)
                .then()
                .log().ifError()
                .statusCode(200)
                .extract().jsonPath();
    }
}
