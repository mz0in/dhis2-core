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

import static org.hisp.dhis.web.WebClient.Body;
import static org.hisp.dhis.web.WebClient.ContentType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.geojson.GeoJsonObject;
import org.geojson.Polygon;
import org.hisp.dhis.feedback.ErrorCode;
import org.hisp.dhis.jsontree.JsonList;
import org.hisp.dhis.jsontree.JsonMixed;
import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.jsontree.JsonValue;
import org.hisp.dhis.web.HttpStatus;
import org.hisp.dhis.web.WebClient;
import org.hisp.dhis.webapi.DhisControllerConvenienceTest;
import org.hisp.dhis.webapi.json.domain.JsonAttributeValue;
import org.hisp.dhis.webapi.json.domain.JsonErrorReport;
import org.hisp.dhis.webapi.json.domain.JsonIdentifiableObject;
import org.hisp.dhis.webapi.json.domain.JsonImportSummary;
import org.hisp.dhis.webapi.json.domain.JsonProgram;
import org.hisp.dhis.webapi.json.domain.JsonWebMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the {@link org.hisp.dhis.webapi.controller.metadata.MetadataImportExportController} using
 * (mocked) REST requests.
 *
 * @author Jan Bernitt
 */
class MetadataImportExportControllerTest extends DhisControllerConvenienceTest {
  @Test
  void testPostJsonMetadata() {
    assertWebMessage(
        "OK",
        200,
        "OK",
        null,
        POST(
                "/38/metadata",
                "{'organisationUnits':[{'name':'My Unit', 'shortName':'OU1', 'openingDate': '2020-01-01'}]}")
            .content(HttpStatus.OK));
  }

  @Test
  void testPostJsonMetadata_Empty() {
    assertWebMessage("OK", 200, "OK", null, POST("/38/metadata", "{}").content(HttpStatus.OK));
  }

  @Test
  void testPostJsonMetadata_Pre38() {
    JsonObject report =
        POST(
                "/37/metadata",
                "{'organisationUnits':[{'name':'My Unit', 'shortName':'OU1', 'openingDate': '2020-01-01'}]}")
            .content(HttpStatus.OK);
    assertEquals("OK", report.getString("status").string());
  }

  @Test
  void testPostCsvMetadata() {
    assertWebMessage(
        "OK",
        200,
        "OK",
        null,
        POST("/38/metadata?classKey=ORGANISATION_UNIT", Body(","), ContentType("application/csv"))
            .content(HttpStatus.OK));
  }

  @Test
  void testPostCsvMetadata_Pre38() {
    JsonObject report =
        POST("/37/metadata?classKey=ORGANISATION_UNIT", Body(","), ContentType("application/csv"))
            .content(HttpStatus.OK);
    assertEquals("OK", report.getString("status").string());
  }

  @Test
  void testPostGmlMetadata() {
    assertWebMessage(
        "OK",
        200,
        "OK",
        null,
        POST("/38/metadata/gml", Body("<metadata></metadata>"), ContentType("application/xml"))
            .content(HttpStatus.OK));
  }

  @Test
  void testPostGmlMetadata_Pre38() {
    JsonObject report =
        POST("/37/metadata/gml", Body("<metadata></metadata>"), ContentType("application/xml"))
            .content(HttpStatus.OK);
    assertEquals("OK", report.getString("status").string());
    assertEquals("ImportReport", report.getString("responseType").string());
  }

  @Test
  void testPostProgramStageWithoutProgram() {
    JsonWebMessage message =
        POST("/metadata/", "{'programStages':[{'name':'test programStage'}]}")
            .content(HttpStatus.CONFLICT)
            .as(JsonWebMessage.class);
    JsonImportSummary response = message.get("response", JsonImportSummary.class);
    assertEquals(
        1, response.getTypeReports().get(0).getObjectReports().get(0).getErrorReports().size());
    assertEquals(
        ErrorCode.E4053,
        response
            .getTypeReports()
            .get(0)
            .getObjectReports()
            .get(0)
            .getErrorReports()
            .get(0)
            .getErrorCode());
  }

  @Test
  void testPostProgramStageWithProgram() {
    POST(
            "/metadata/",
            "{'programs':[{'name':'test program', 'id':'VoZMWi7rBgj', 'shortName':'test program','programType':'WITH_REGISTRATION','programStages':[{'id':'VoZMWi7rBgf'}] }],'programStages':[{'id':'VoZMWi7rBgf','name':'test programStage'}]}")
        .content(HttpStatus.OK);
    assertEquals(
        "VoZMWi7rBgj",
        GET("/programStages/{id}", "VoZMWi7rBgf").content().getString("program.id").string());
    assertEquals(
        "VoZMWi7rBgf",
        GET("/programs/{id}", "VoZMWi7rBgj").content().getString("programStages[0].id").string());
  }

  @Test
  void testGetWithIeqFilter() {
    POST(
            "/metadata/",
            "{'programs':[{'name':'Test Program', 'id':'VoZMWi7rBgj', 'shortName':'test program','programType':'WITH_REGISTRATION', 'version':'5'}]}")
        .content(HttpStatus.OK);

    assertEquals(
        "Test Program",
        GET("/metadata?programs=true&filter=name:ieq:test program")
            .content()
            .getList("programs", JsonProgram.class)
            .get(0)
            .getName());
  }

  @Test
  void testGetWithIeqFilterNonString() {
    POST(
            "/metadata/",
            "{'programs':[{'name':'Test Program', 'id':'VoZMWi7rBgj', 'shortName':'test program','programType':'WITH_REGISTRATION', 'version':'5'}]}")
        .content(HttpStatus.OK);

    JsonWebMessage response =
        GET("/metadata?programs=true&filter=version:ieq:5")
            .content(HttpStatus.Series.CLIENT_ERROR)
            .as(JsonWebMessage.class);

    assertEquals(
        "Value `5` of type `Integer` is not supported by this operator.", response.getMessage());
    assertEquals(409, response.getHttpStatusCode());
    assertEquals("Conflict", response.getHttpStatus());
  }

  @Test
  void testPostValidGeoJsonAttribute() throws IOException {
    POST(
            "/metadata",
            "{\"organisationUnits\": [ {\"id\":\"rXnqqH2Pu6N\",\"name\": \"My Unit 2\",\"shortName\": \"OU2\",\"openingDate\": \"2020-01-01\","
                + "\"attributeValues\": [{\"value\":  \"{\\\"type\\\": \\\"Polygon\\\","
                + "\\\"coordinates\\\":  [[[100,0],[101,0],[101,1],[100,1],[100,0]]] }\","
                + "\"attribute\": {\"id\": \"RRH9IFiZZYN\"}}]}],"
                + "\"attributes\":[{\"id\":\"RRH9IFiZZYN\",\"valueType\":\"GEOJSON\",\"organisationUnitAttribute\":true,\"name\":\"testgeojson\"}]}")
        .content(HttpStatus.OK);

    JsonIdentifiableObject organisationUnit =
        GET("/organisationUnits/{id}", "rXnqqH2Pu6N").content().asA(JsonIdentifiableObject.class);

    assertEquals(1, organisationUnit.getAttributeValues().size());
    JsonAttributeValue attributeValue = organisationUnit.getAttributeValues().get(0);
    GeoJsonObject geoJSON =
        new ObjectMapper().readValue(attributeValue.getValue(), GeoJsonObject.class);
    assertTrue(geoJSON instanceof Polygon);
    Polygon polygon = (Polygon) geoJSON;
    assertEquals(100, polygon.getCoordinates().get(0).get(0).getLongitude());
  }

  @Test
  void testPostInValidGeoJsonAttribute() {
    JsonWebMessage message =
        POST(
                "/metadata",
                "{\"organisationUnits\": [ {\"id\":\"rXnqqH2Pu6N\",\"name\": \"My Unit 2\",\"shortName\": \"OU2\",\"openingDate\": \"2020-01-01\","
                    + "\"attributeValues\": [{\"value\":  \"{\\\"type\\\": \\\"Polygon\\\"}\","
                    + "\"attribute\": {\"id\": \"RRH9IFiZZYN\"}}]}],"
                    + "\"attributes\":[{\"id\":\"RRH9IFiZZYN\",\"valueType\":\"GEOJSON\",\"organisationUnitAttribute\":true,\"name\":\"testgeojson\"}]}")
            .content(HttpStatus.CONFLICT)
            .as(JsonWebMessage.class);
    assertNotNull(
        message.find(JsonErrorReport.class, report -> report.getErrorCode() == ErrorCode.E6004));
  }

  /** Import OptionSet with two Options, sort orders are 2 and 3. */
  @Test
  void testImportOptionSetWithOptions() {
    POST(
            "/metadata",
            "{\"optionSets\":\n"
                + "    [{\"name\": \"Device category\",\"id\": \"RHqFlB1Wm4d\",\"version\": 2,\"valueType\": \"TEXT\",\"options\":[{\"id\": \"Uh4HvjK6zg3\"},{\"id\": \"BQMei56UBl6\"}]}],\n"
                + "\"options\":\n"
                + "    [{\"code\": \"Vaccine freezer\",\"name\": \"Vaccine freezer\",\"id\": \"BQMei56UBl6\",\"sortOrder\": 5,\"optionSet\":{\"id\": \"RHqFlB1Wm4d\"}},\n"
                + "    {\"code\": \"Icelined refrigerator\",\"name\": \"Icelined refrigerator\",\"id\": \"Uh4HvjK6zg3\",\"optionSet\":{\"id\": \"RHqFlB1Wm4d\"}}]}")
        .content(HttpStatus.OK);

    JsonObject response =
        GET("/optionSets/{uid}?fields=options[id,sortOrder]", "RHqFlB1Wm4d").content();

    assertEquals(2, response.getObject("options").size());
    assertEquals(0, response.getNumber("options[0].sortOrder").intValue());
    assertEquals(1, response.getNumber("options[1].sortOrder").intValue());
    assertEquals("Uh4HvjK6zg3", response.getString("options[0].id").string());
    assertEquals("BQMei56UBl6", response.getString("options[1].id").string());
  }

  /** Import OptionSet with two Options, one has sortOrder and the other doesn't */
  @Test
  void testImportOptionSetWithOptionsOneSortOrder() {
    POST(
            "/metadata",
            "{\"optionSets\":\n"
                + "    [{\"name\": \"Device category\",\"id\": \"RHqFlB1Wm4d\",\"version\": 2,\"valueType\": \"TEXT\",\"options\":[{\"id\": \"Uh4HvjK6zg3\"},{\"id\": \"BQMei56UBl6\"}]}],\n"
                + "\"options\":\n"
                + "    [{\"code\": \"Vaccine freezer\",\"name\": \"Vaccine freezer\",\"id\": \"BQMei56UBl6\",\"optionSet\":{\"id\": \"RHqFlB1Wm4d\"}},\n"
                + "    {\"code\": \"Icelined refrigerator\",\"name\": \"Icelined refrigerator\",\"id\": \"Uh4HvjK6zg3\",\"sortOrder\": 3,\"optionSet\":{\"id\": \"RHqFlB1Wm4d\"}}]}")
        .content(HttpStatus.OK);

    JsonObject response =
        GET("/optionSets/{uid}?fields=options[id,sortOrder]", "RHqFlB1Wm4d").content();

    assertEquals(2, response.getObject("options").size());
    assertNotNull(response.get("options[0].sortOrder"));
    assertNotNull(response.get("options[1].sortOrder"));
  }

  /** Import OptionSet with two Options, both doesn't have sortOrder */
  @Test
  void testImportOptionSetWithOptionsNoSortOrder() {
    POST(
            "/metadata",
            "{\"optionSets\":\n"
                + "    [{\"name\": \"Device category\",\"id\": \"RHqFlB1Wm4d\",\"version\": 2,\"valueType\": \"TEXT\",\"options\":[{\"id\": \"BQMei56UBl6\"},{\"id\": \"Uh4HvjK6zg3\"}]}],\n"
                + "\"options\":\n"
                + "    [{\"code\": \"Vaccine freezer\",\"name\": \"Vaccine freezer\",\"id\": \"BQMei56UBl6\",\"optionSet\":{\"id\": \"RHqFlB1Wm4d\"}},\n"
                + "    {\"code\": \"Icelined refrigerator\",\"name\": \"Icelined refrigerator\",\"id\": \"Uh4HvjK6zg3\",\"optionSet\":{\"id\": \"RHqFlB1Wm4d\"}}]}")
        .content(HttpStatus.OK);

    JsonObject response =
        GET("/optionSets/{uid}?fields=options[id,sortOrder]", "RHqFlB1Wm4d").content();

    assertEquals(2, response.getObject("options").size());
    assertEquals(0, response.getNumber("options[0].sortOrder").intValue());
    assertEquals("BQMei56UBl6", response.getString("options[0].id").string());
    assertNotNull(response.get("options[1].sortOrder"));
    assertEquals("Uh4HvjK6zg3", response.getString("options[1].id").string());
    assertEquals(1, response.getNumber("options[1].sortOrder").intValue());

    POST(
            "/metadata",
            "{\"optionSets\":\n"
                + "    [{\"name\": \"Device category\",\"id\": \"RHqFlB1Wm4d\",\"version\": 2,\"valueType\": \"TEXT\",\"options\":[{\"id\": \"Uh4HvjK6zg3\"},{\"id\": \"BQMei56UBl6\"}]}],\n"
                + "\"options\":\n"
                + "    [{\"code\": \"Icelined refrigerator\",\"name\": \"Icelined refrigerator\",\"id\": \"Uh4HvjK6zg3\",\"optionSet\":{\"id\": \"RHqFlB1Wm4d\"}},\n"
                + "    {\"code\": \"Vaccine freezer\",\"name\": \"Vaccine freezer\",\"id\": \"BQMei56UBl6\",\"optionSet\":{\"id\": \"RHqFlB1Wm4d\"}}]}")
        .content(HttpStatus.OK);

    response = GET("/optionSets/{uid}?fields=options[id,sortOrder]", "RHqFlB1Wm4d").content();
    assertEquals("Uh4HvjK6zg3", response.getString("options[0].id").string());
    assertEquals("BQMei56UBl6", response.getString("options[1].id").string());
  }

  /** Import OptionSet with two Options, both have same sortOrder */
  @Test
  void testImportOptionSetWithOptionsDuplicateSortOrder() {
    POST(
            "/metadata",
            "{\"optionSets\":\n"
                + "    [{\"name\": \"Device category\",\"id\": \"RHqFlB1Wm4d\",\"version\": 2,\"valueType\": \"TEXT\",\"options\":[{\"id\": \"Uh4HvjK6zg3\"},{\"id\": \"BQMei56UBl6\"}]}],\n"
                + "\"options\":\n"
                + "    [{\"code\": \"Vaccine freezer\",\"name\": \"Vaccine freezer\",\"sortOrder\": 2,\"id\": \"BQMei56UBl6\",\"optionSet\":{\"id\": \"RHqFlB1Wm4d\"}},\n"
                + "    {\"code\": \"Icelined refrigerator\",\"name\": \"Icelined refrigerator\",\"sortOrder\": 2,\"id\": \"Uh4HvjK6zg3\",\"optionSet\":{\"id\": \"RHqFlB1Wm4d\"}}]}")
        .content(HttpStatus.OK);

    JsonObject response =
        GET("/optionSets/{uid}?fields=options[id,sortOrder]", "RHqFlB1Wm4d").content();

    assertEquals(2, response.getObject("options").size());
    assertNotNull(response.get("options[0].sortOrder"));
    assertNotNull(response.get("options[1].sortOrder"));
  }

  @Test
  void testImportOptionSetWithNoLinkOptions() {
    POST(
            "/metadata",
            "{\"optionSets\":\n"
                + "    [{\"name\": \"Device category\",\"id\": \"RHqFlB1Wm4d\",\"version\": 2,\"valueType\": \"TEXT\"}],\n"
                + "\"options\":\n"
                + "    [{\"code\": \"Vaccine freezer\",\"name\": \"Vaccine freezer\",\"sortOrder\": 2,\"id\": \"BQMei56UBl6\",\"optionSet\":{\"id\": \"RHqFlB1Wm4d\"}},\n"
                + "    {\"code\": \"Icelined refrigerator\",\"name\": \"Icelined refrigerator\",\"sortOrder\": 3,\"id\": \"Uh4HvjK6zg3\",\"optionSet\":{\"id\": \"RHqFlB1Wm4d\"}}]}")
        .content(HttpStatus.OK);

    JsonObject response =
        GET("/optionSets/{uid}?fields=options[id,sortOrder]", "RHqFlB1Wm4d").content();

    assertEquals(2, response.getObject("options").size());
    assertNotNull(response.get("options[0].sortOrder"));
    assertNotNull(response.get("options[1].sortOrder"));
  }

  @Test
  @DisplayName(
      "Should not include null objects in collection Category.categorycombos or CategoryCombo.categories after importing")
  void testImportCategoryComboAndCategory() {
    POST("/metadata", Body("metadata/category_and_categorycombo.json")).content(HttpStatus.OK);
    JsonMixed response = GET("/categories/{uid}?fields=id,categoryCombos", "IjOK1aXkjVO").content();
    JsonList<JsonObject> catCombos = response.getList("categoryCombos", JsonObject.class);
    assertNotNull(catCombos);
    assertFalse(catCombos.stream().anyMatch(JsonValue::isNull));

    response = GET("/categoryCombos/{uid}?fields=id,categoryCombos", "TIAbMD7ETV6").content();
    JsonList<JsonObject> categories = response.getList("categories", JsonObject.class);
    assertNotNull(categories);
    assertFalse(categories.stream().anyMatch(JsonValue::isNull));
  }

  @Test
  void testImportDashboardWithInvalidLayout_UpdateFlow() {
    JsonImportSummary createReport =
        POST("/metadata", WebClient.Body("dashboard/import_dashboard_with_valid_layout.json"))
            .content(HttpStatus.OK)
            .get("response")
            .as(JsonImportSummary.class);

    assertEquals("OK", createReport.getStatus());
    assertEquals(1, createReport.getStats().getCreated());
    assertEquals(0, createReport.getStats().getIgnored());
    assertEquals(0, createReport.getStats().getUpdated());
    assertEquals(1, createReport.getStats().getTotal());

    JsonImportSummary updateReport =
        POST("/metadata", WebClient.Body("dashboard/import_dashboard_with_invalid_layout.json"))
            .content(HttpStatus.CONFLICT)
            .get("response")
            .as(JsonImportSummary.class);

    assertEquals("ERROR", updateReport.getStatus());
    assertEquals(0, updateReport.getStats().getCreated());
    assertEquals(2, updateReport.getStats().getIgnored());
    assertEquals(0, updateReport.getStats().getUpdated());
    assertEquals(2, updateReport.getStats().getTotal());
  }

  @Test
  void testImportDashboardWithValidLayout_CreateFlow() {
    JsonImportSummary report =
        POST("/metadata", WebClient.Body("dashboard/import_dashboard_with_valid_layout.json"))
            .content(HttpStatus.OK)
            .get("response")
            .as(JsonImportSummary.class);

    assertEquals("OK", report.getStatus());
    assertEquals(1, report.getStats().getCreated());
    assertEquals(0, report.getStats().getIgnored());
    assertEquals(0, report.getStats().getUpdated());
    assertEquals(1, report.getStats().getTotal());
  }

  @Test
  void testImportDashboardWithInvalidLayout_CreateFlow() {
    JsonImportSummary report =
        POST("/metadata", WebClient.Body("dashboard/import_dashboard_with_invalid_layout.json"))
            .content(HttpStatus.CONFLICT)
            .get("response")
            .as(JsonImportSummary.class);

    assertEquals("ERROR", report.getStatus());
    assertEquals(0, report.getStats().getCreated());
    assertEquals(2, report.getStats().getIgnored());
    assertEquals(0, report.getStats().getUpdated());
    assertEquals(2, report.getStats().getTotal());
  }

  @Test
  @DisplayName("Export user metadata with skipSharing option returns expected fields")
  void exportUserWithSkipSharing() {
    // when users are exported including the skipSharing option
    JsonObject user =
        GET("/metadata.json?skipSharing=true&download=true&users=true")
            .content(HttpStatus.OK)
            .getArray("users")
            .getObject(0);

    // then the returned users should have the following fields present
    assertTrue(user.exists());
    assertTrue(user.getString("username").exists());
    assertTrue(user.getString("userRoles").exists());
  }

  @Test
  void testImportWithInvalidCreatedBy() {
    JsonMixed report =
        POST(
                "/metadata",
                "{\"optionSets\":\n"
                    + "    [{\"name\": \"Device category\",\"id\": \"RHqFlB1Wm4d\",\"version\": 2,\"valueType\": \"TEXT\",\"createdBy\": \"invalid\"}]}")
            .content(HttpStatus.OK);

    assertNotNull(report.get("response"));

    JsonMixed optionSet = GET("/optionSets/{uid}", "RHqFlB1Wm4d").content(HttpStatus.OK);
    assertTrue(optionSet.get("createdBy").exists());
  }

  @Test
  void testImportWithInvalidCreatedByAndSkipSharing() {
    JsonMixed report =
        POST(
                "/metadata?skipSharing=true",
                "{\"optionSets\":\n"
                    + "    [{\"name\": \"Device category\",\"id\": \"RHqFlB1Wm4d\",\"version\": 2,\"valueType\": \"TEXT\",\"createdBy\": \"invalid\"}]}")
            .content(HttpStatus.OK);

    assertNotNull(report.get("response"));

    JsonMixed optionSet = GET("/optionSets/{uid}", "RHqFlB1Wm4d").content(HttpStatus.OK);
    assertTrue(optionSet.get("createdBy").exists());
  }

  @Test
  @DisplayName(
      "Should return error in import report if deleting object is referenced by other object")
  void testDeleteWithException() {
    POST(
            "/metadata",
            """
             {'optionSets':
                 [{'name': 'Device category','id': 'RHqFlB1Wm4d','version': 2,'valueType': 'TEXT'}]
             ,'dataElements':
             [{'name':'test DataElement with OptionSet', 'shortName':'test DataElement', 'aggregationType':'SUM','domainType':'AGGREGATE','categoryCombo':{'id':'bjDvmb4bfuf'},'valueType':'NUMBER','optionSet':{'id':'RHqFlB1Wm4d'}
             }]}""")
        .content(HttpStatus.OK);
    JsonImportSummary report =
        POST(
                "/metadata?importStrategy=DELETE",
                """
                {'optionSets':
                [{'name': 'Device category','id': 'RHqFlB1Wm4d','version': 2,'valueType': 'TEXT'}]}""")
            .content(HttpStatus.CONFLICT)
            .get("response")
            .as(JsonImportSummary.class);
    assertEquals(0, report.getStats().getDeleted());
    assertEquals(1, report.getStats().getIgnored());
    assertEquals(
        "Object could not be deleted because it is associated with another object: DataElement",
        report
            .find(
                JsonErrorReport.class, errorReport -> errorReport.getErrorCode() == ErrorCode.E4030)
            .getMessage());
  }
}
