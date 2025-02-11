/*
 * Copyright (c) 2004-2022, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.hisp.dhis.webapi.controller;

import static org.hisp.dhis.utils.Assertions.assertGreaterOrEqual;
import static org.hisp.dhis.utils.Assertions.assertLessOrEqual;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.hisp.dhis.jsontree.JsonMixed;
import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.webapi.DhisControllerConvenienceTest;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;

/**
 * Tests the {@link org.hisp.dhis.webapi.openapi.OpenApiController} with Mock MVC tests.
 *
 * <p>The documents returned by the controller are generated "on-the-fly" and are not dependent on
 * any database input.
 *
 * @author Jan Bernitt
 */
class OpenApiControllerTest extends DhisControllerConvenienceTest {
  @Test
  void testGetOpenApiDocumentJson() {
    JsonObject doc =
        GET("/openapi/openapi.json?failOnNameClash=true&failOnInconsistency=true").content();
    assertTrue(doc.isObject());
    assertTrue(doc.getObject("components.schemas.PropertyNames_OrganisationUnit").isObject());
    assertGreaterOrEqual(150, doc.getObject("paths").size());
    assertGreaterOrEqual(0, doc.getObject("security[0].basicAuth").size());
    assertGreaterOrEqual(1, doc.getObject("components.securitySchemes").size());
    assertGreaterOrEqual(200, doc.getObject("components.schemas").size());
    assertGreaterOrEqual(200, doc.getObject("components.schemas").size());

    SwaggerParseResult result =
        new OpenAPIParser().readContents(doc.node().getDeclaration(), null, null);
    assertEquals(List.of(), result.getMessages(), "There should not be any errors");
  }

  @Test
  void testGetOpenApiDocument_PathFilter() {
    JsonObject doc = GET("/openapi/openapi.json?path=/users").content();
    assertTrue(doc.isObject());
    assertTrue(
        doc.getObject("paths")
            .has("/users/gist", "/users/invite", "/users/invites", "/users/sharing"));
    assertLessOrEqual(26, doc.getObject("paths").size());
    assertLessOrEqual(22, doc.getObject("components.schemas").size());
  }

  @Test
  void testGetOpenApiDocument_TagFilter() {
    JsonObject doc = GET("/openapi/openapi.json?tag=user").content();
    assertTrue(doc.isObject());
    assertTrue(
        doc.getObject("paths")
            .has("/users/gist", "/users/invite", "/users/invites", "/users/sharing"));
    assertLessOrEqual(147, doc.getObject("paths").size());
    assertLessOrEqual(60, doc.getObject("components.schemas").size());
  }

  @Test
  void testGetOpenApiDocument_DefaultValue() {
    // defaults in parameter objects (from Property analysis)
    JsonObject users = GET("/openapi/openapi.json?path=/users").content();
    JsonObject sharedParams = users.getObject("components.parameters");
    assertEquals(50, sharedParams.getNumber("{GistParams.pageSize}.schema.default").integer());
    assertEquals(
        "AND", sharedParams.getString("{GistParams.rootJunction}.schema.default").string());
    assertTrue(sharedParams.getBoolean("{GistParams.translate}.schema.default").booleanValue());

    // defaults in individual parameters (from endpoint method parameter analysis)
    JsonObject fileResources = GET("/openapi/openapi.json?path=/fileResources").content();
    JsonObject domain =
        fileResources.get("paths./fileResources/.post.parameters").asList(JsonObject.class).stream()
            .filter(p -> "domain".equals(p.getString("name").string()))
            .findFirst()
            .orElse(JsonMixed.of("{}"));
    assertEquals("DATA_VALUE", domain.getString("schema.default").string());

    JsonObject audits = GET("/openapi/openapi.json?path=/audits").content();
    JsonObject pageSize =
        audits
            .getArray("paths./audits/trackedEntityAttributeValue.get.parameters")
            .asList(JsonObject.class)
            .stream()
            .filter(p -> "pageSize".equals(p.getString("name").string()))
            .findFirst()
            .orElse(JsonMixed.of("{}"));
    assertEquals(50, pageSize.getNumber("schema.default").integer());
  }

  @Test
  void testGetOpenApiDocument_CodeGeneration() throws IOException {
    JsonObject doc = GET("/openapi/openapi.json?failOnNameClash=true").content();

    Path tmpFile = Files.createTempFile("openapi", ".json");
    Files.writeString(tmpFile, doc.node().getDeclaration());

    CodegenConfigurator configurator =
        new CodegenConfigurator()
            .setInputSpec(tmpFile.toAbsolutePath().toString())
            .setGeneratorName("r");

    assertNotNull(
        new DefaultGenerator(true).opts(configurator.toClientOptInput()).generate(),
        "Like due to a query parameter which is complex, needs debugging to find out, insert breakpoint at RClientCodegen.constructExampleCode(RClientCodegen.java:950)");
  }
}
