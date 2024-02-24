/*
 * Licensed to Crate.io GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.analyze;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.Test;

import io.crate.execution.engine.collect.sources.SysTableRegistry;
import io.crate.metadata.cluster.DDLClusterStateService;
import io.crate.role.Role;
import io.crate.role.RoleManagerService;
import io.crate.test.integration.CrateDummyClusterServiceUnitTest;
import io.crate.testing.SQLExecutor;

public class CreateUserMappingAnalyzerTest extends CrateDummyClusterServiceUnitTest {

    @Test
    public void test_cannot_create_user_mapping_for_unknown_user() {
        var e = SQLExecutor
            .builder(clusterService)
            .setUserManager(
                new RoleManagerService(
                    null,
                    null,
                    null,
                    null,
                    mock(SysTableRegistry.class),
                    () -> List.of(Role.CRATE_USER),
                    new DDLClusterStateService())
            )
            .build();
        assertThatThrownBy(() -> e.analyze("CREATE USER MAPPING FOR user1 SERVER pg"))
            .isExactlyInstanceOf(IllegalArgumentException.class)
            .hasMessage("Cannot create a user mapping for an unknown user: 'user1'");
    }
}
