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
package org.hisp.dhis.config;

import java.util.Map;
import java.util.Properties;
import org.hisp.dhis.external.conf.DhisConfigurationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * @author Gintare Vilkelyte <vilkelyte.gintare@gmail.com>
 */
@Configuration
public class TestContainerPostgresConfig {
  /**
   * Refers to the {@code postgis/postgis:10-2.5-alpine} image which contains PostgreSQL 10 and
   * PostGIS 2.5.
   */
  private static final String POSTGRES_POSTGIS_VERSION = "10-2.5-alpine";

  private static final DockerImageName POSTGIS_IMAGE_NAME =
      DockerImageName.parse("postgis/postgis").asCompatibleSubstituteFor("postgres");
  private static final String POSTGRES_DATABASE_NAME = "dhis";
  private static final String POSTGRES_USERNAME = "dhis";
  private static final String POSTGRES_PASSWORD = "dhis";
  private static final PostgreSQLContainer<?> POSTGRES_CONTAINER;

  static {
    POSTGRES_CONTAINER =
        new PostgreSQLContainer<>(POSTGIS_IMAGE_NAME.withTag(POSTGRES_POSTGIS_VERSION))
            .withDatabaseName(POSTGRES_DATABASE_NAME)
            .withUsername(POSTGRES_USERNAME)
            .withPassword(POSTGRES_PASSWORD)
            .withInitScript("db/extensions.sql")
            .withTmpFs(Map.of("/testtmpfs", "rw"))
            .withEnv("LC_COLLATE", "C");

    POSTGRES_CONTAINER.start();
  }

  @Bean(name = "dhisConfigurationProvider")
  public DhisConfigurationProvider dhisConfigurationProvider() {

    PostgresDhisConfigurationProvider dhisConfigurationProvider =
        new PostgresDhisConfigurationProvider(getConfigurationFile());

    Properties properties = new Properties();
    properties.setProperty(
        "connection.dialect", "org.hisp.dhis.hibernate.dialect.DhisPostgresDialect");
    properties.setProperty("connection.driver_class", POSTGRES_CONTAINER.getDriverClassName());
    properties.setProperty("connection.url", POSTGRES_CONTAINER.getJdbcUrl());
    properties.setProperty("connection.username", POSTGRES_USERNAME);
    properties.setProperty("connection.password", POSTGRES_PASSWORD);
    dhisConfigurationProvider.addProperties(properties);

    return dhisConfigurationProvider;
  }

  protected String getConfigurationFile() {
    return "postgresTestConfig.conf";
  }
}
