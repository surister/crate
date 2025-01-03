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

package io.crate.integrationtests;


import static io.crate.testing.Asserts.assertThat;

import org.elasticsearch.test.IntegTestCase;
import org.junit.Test;

import io.crate.testing.UseHashJoins;
import io.crate.testing.UseRandomizedOptimizerRules;

public class LookupJoinIntegrationTest extends IntegTestCase {

    @UseRandomizedOptimizerRules(0)
    @UseHashJoins(1)
    @Test
    public void test_lookup_join_with_two_collect_operators() throws Exception {
        execute("create table doc.t1 (id int) with(number_of_replicas=0)");
        execute("insert into doc.t1 (id) select b from generate_series(1,10000) a(b)");
        execute("create table doc.t2 (id int)");
        execute("insert into doc.t2 (id) select b from generate_series(1,100) a(b)");
        execute("refresh table doc.t1");
        execute("refresh table doc.t2");
        execute("analyze");
        waitNoPendingTasksOnAll();
        try (var session = sqlExecutor.newSession()) {
            execute("SET optimizer_equi_join_to_lookup_join = true", session);
            var query = "select t1.id, t2.id from doc.t1 join doc.t2 on t1.id = t2.id";
            execute("explain (costs false)" + query, session);
            assertThat(response).hasLines(
                "HashJoin[INNER | (id = id)]",
                "  ├ MultiPhase",
                "  │  └ Collect[doc.t1 | [id] | (id = ANY((doc.t2)))]",
                "  │  └ Collect[doc.t2 | [id] | true]",
                "  └ Collect[doc.t2 | [id] | true]"
            );
            execute(query, session);
            assertThat(response).hasRowCount(100);
        }
    }

    @UseRandomizedOptimizerRules(0)
    @Test
    public void test_drop_join_on_top_of_lookup_join() throws Exception {
        execute("create table doc.t1 (id int) with(number_of_replicas=0)");
        execute("insert into doc.t1 (id) select b from generate_series(1,10000) a(b)");
        execute("create table doc.t2 (id int)");
        execute("insert into doc.t2 (id) select b from generate_series(1,100) a(b)");
        execute("refresh table doc.t1");
        execute("refresh table doc.t2");
        execute("analyze");
        waitNoPendingTasksOnAll();
        try (var session = sqlExecutor.newSession()) {
            execute("SET optimizer_equi_join_to_lookup_join = true", session);
            var query = "select t1.id from doc.t1 join doc.t2 on t1.id = t2.id";
            execute("explain (costs false)" + query, session);
            assertThat(response).hasLines(
                "MultiPhase",
                "  └ Collect[doc.t1 | [id] | (id = ANY((doc.t2)))]",
                "  └ Collect[doc.t2 | [id] | true]"
            );
            execute(query, session);
            assertThat(response).hasRowCount(100);

            execute("SET enable_hashjoin=false");
            execute("explain (costs false)" + query, session);
            assertThat(response).hasLines(
                "MultiPhase",
                "  └ Collect[doc.t1 | [id] | (id = ANY((doc.t2)))]",
                "  └ Collect[doc.t2 | [id] | true]"
            );
            execute(query, session);
            assertThat(response).hasRowCount(100);
        }
    }

    @UseRandomizedOptimizerRules(0)
    @UseHashJoins(1)
    @Test
    public void test_lookup_join_with_subquery_on_the_smaller_side() throws Exception {
        execute("create table doc.t1 (id int) with(number_of_replicas=0)");
        execute("insert into doc.t1 (id) select b from generate_series(1,10000) a(b)");
        execute("create table doc.t2 (id int, name string)");
        execute("insert into doc.t2 (id, name) select b, (b%4)::TEXT from generate_series(1,100) a(b)");
        execute("refresh table doc.t1");
        execute("refresh table doc.t2");
        execute("analyze");
        waitNoPendingTasksOnAll();
        try (var session = sqlExecutor.newSession()) {
            execute("SET optimizer_equi_join_to_lookup_join = true", session);
            var query = "select count(name) from doc.t1 join (select name, id from doc.t2 where doc.t2.id > 0) x on t1.id = x.id";
            execute("explain (costs false)" + query, session);
            assertThat(response).hasLines(
                "HashAggregate[count(name)]",
                "  └ HashJoin[INNER | (id = id)]",
                "    ├ MultiPhase",
                "    │  └ Collect[doc.t1 | [id] | (id = ANY((x)))]",
                "    │  └ Rename[id] AS x",
                "    │    └ Filter[(id > 0)]",
                "    │      └ Collect[doc.t2 | [id] | true]",
                "    └ Rename[name, id] AS x",
                "      └ Collect[doc.t2 | [name, id] | (id > 0)]"
            );
            execute(query, session);
            assertThat(response).hasRows("100");
        }
    }

    @UseRandomizedOptimizerRules(0)
    @UseHashJoins(1)
    @Test
    public void test_lookup_join_with_subquery_on_the_larger_side() throws Exception {
        execute("create table doc.t1 (id int) with(number_of_replicas=0)");
        execute("insert into doc.t1 (id) select b from generate_series(1,10000) a(b)");
        execute("create table doc.t2 (id int, name string)");
        execute("insert into doc.t2 (id, name) select b, (b%4)::TEXT from generate_series(1,100) a(b)");
        execute("refresh table doc.t1");
        execute("refresh table doc.t2");
        execute("analyze");
        waitNoPendingTasksOnAll();
        try (var session = sqlExecutor.newSession()) {
            execute("SET optimizer_equi_join_to_lookup_join = true", session);
            var query = "select count(name) from (select name, id from doc.t2 where doc.t2.name = '1') x join doc.t1 on t1.id = x.id";
            execute("explain (costs false)" + query, session);
            assertThat(response).hasLines(
                "HashAggregate[count(name)]",
                "  └ Eval[name, id, id]",
                "    └ HashJoin[INNER | (id = id)]",
                "      ├ MultiPhase",
                "      │  └ Collect[doc.t1 | [id] | (id = ANY((x)))]",
                "      │  └ Eval[id]",
                "      │    └ Rename[name, id] AS x",
                "      │      └ Filter[(name = '1')]",
                "      │        └ Collect[doc.t2 | [name, id] | true]",
                "      └ Rename[name, id] AS x",
                "        └ Collect[doc.t2 | [name, id] | (name = '1')]");
            execute(query, session);
            assertThat(response).hasRows("25");
        }
    }
}
