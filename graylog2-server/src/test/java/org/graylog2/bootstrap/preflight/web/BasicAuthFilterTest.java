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
package org.graylog2.bootstrap.preflight.web;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.HttpStatus;
import org.assertj.core.api.Assertions;
import org.glassfish.jersey.server.ContainerRequest;
import jakarta.annotation.Nonnull;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

class BasicAuthFilterTest {


    @Test
    void testLoginPath() throws IOException {
        final BasicAuthFilter filter = new BasicAuthFilter("admin", DigestUtils.sha256Hex("admin"), "junit-test", URI.create("/login"));
        final ContainerRequest request = mockRequest("login", null, null);
        filter.filter(request);
        final ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        Mockito.verify(request, never()).abortWith(captor.capture());
    }

    @Test
    void testMissingCredentials() throws IOException {
        final BasicAuthFilter filter = new BasicAuthFilter("admin", DigestUtils.sha256Hex("admin"), "junit-test", URI.create("/login"));
        final ContainerRequest request = mockRequest("/", null, null);
        filter.filter(request);
        final ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        Mockito.verify(request, times(1)).abortWith(captor.capture());
        final Response response = captor.getValue();
        Assertions.assertThat(response.getStatusInfo().getStatusCode()).isEqualTo(HttpStatus.SC_MOVED_TEMPORARILY);
        Assertions.assertThat(response.getEntity()).isEqualTo("You cannot access this resource, missing or invalid authorization header!");
    }

    @Test
    void testInvalidCredentials() throws IOException {
        final BasicAuthFilter filter = new BasicAuthFilter("admin", DigestUtils.sha256Hex("admin"), "junit-test", URI.create("/login"));
        final ContainerRequest request = mockRequest("/", "admin", "admin1");
        filter.filter(request);
        final ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        Mockito.verify(request, times(1)).abortWith(captor.capture());
        final Response response = captor.getValue();
        Assertions.assertThat(response.getStatusInfo().getStatusCode()).isEqualTo(HttpStatus.SC_MOVED_TEMPORARILY);
        Assertions.assertThat(response.getEntity()).isEqualTo("You cannot access this resource, missing or invalid authorization header!");
    }

    @Test
    void testMissingPassword() throws IOException {
        final BasicAuthFilter filter = new BasicAuthFilter("admin", DigestUtils.sha256Hex("admin"), "junit-test", URI.create("/login"));
        final ContainerRequest request = mockRequest("/", "admin", "");
        filter.filter(request);
        final ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        Mockito.verify(request, times(1)).abortWith(captor.capture());
        final Response response = captor.getValue();
        Assertions.assertThat(response.getStatusInfo().getStatusCode()).isEqualTo(HttpStatus.SC_MOVED_TEMPORARILY);
        Assertions.assertThat(response.getEntity()).isEqualTo("You cannot access this resource, missing or invalid authorization header!");
    }

    @Test
    void testCorrectCredentials() throws IOException {
        final BasicAuthFilter filter = new BasicAuthFilter("admin", DigestUtils.sha256Hex("admin"), "junit-test", URI.create("/login"));
        final ContainerRequest request = mockRequest("/", "admin", "admin");
        filter.filter(request);
        Mockito.verify(request, Mockito.never()).abortWith(Mockito.any());
    }

    private static ContainerRequest mockRequest(String path, String username, String password) {
        final ContainerRequest request = mock(ContainerRequest.class);

        final ExtendedUriInfo uriInfo = mock(ExtendedUriInfo.class);
        Mockito.when(uriInfo.getPath()).thenReturn(path);
        Mockito.when(request.getUriInfo()).thenReturn(uriInfo);

        final MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();
        if (username != null && password != null) {
            headers.put("Authorization", Collections.singletonList("Basic " + base64(username, password)));
        }
        Mockito.when(request.getHeaders()).thenReturn(headers);
        return request;
    }

    @Nonnull
    private static String base64(String username, String password) {
        final String value = username + ":" + password;
        return new String(Base64.getEncoder().encode(value.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    }
}
