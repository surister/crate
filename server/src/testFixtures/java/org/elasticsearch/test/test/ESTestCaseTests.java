/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.test.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.RandomObjects;

public class ESTestCaseTests extends ESTestCase {

    public void testShuffleMap() throws IOException {
        BytesReference source = RandomObjects.randomSource(random(), 5);
        try (XContentParser parser = createParser(JsonXContent.JSON_XCONTENT, source)) {
            LinkedHashMap<String, Object> initialMap = (LinkedHashMap<String, Object>)parser.mapOrdered();

            Set<List<String>> distinctKeys = new HashSet<>();
            for (int i = 0; i < 10; i++) {
                LinkedHashMap<String, Object> shuffledMap = shuffleMap(initialMap, Collections.emptySet());
                assertThat(shuffledMap).as("both maps should contain the same mappings").isEqualTo(initialMap);
                List<String> shuffledKeys = new ArrayList<>(shuffledMap.keySet());
                distinctKeys.add(shuffledKeys);
            }
            //out of 10 shuffling runs we expect to have at least more than 1 distinct output.
            //This is to make sure that we actually do the shuffling
            assertThat(distinctKeys).hasSizeGreaterThan(1);
        }
    }

    public void testShuffleXContentExcludeFields() throws IOException {
        XContentType xContentType = randomFrom(XContentType.values());
        try (XContentBuilder builder = XContentBuilder.builder(xContentType.xContent())) {
            builder.startObject();
            {
                builder.field("field1", "value1");
                builder.field("field2", "value2");
                {
                    builder.startObject("object1");
                    {
                        builder.field("inner1", "value1");
                        builder.field("inner2", "value2");
                        builder.field("inner3", "value3");
                    }
                    builder.endObject();
                }
                {
                    builder.startObject("object2");
                    {
                        builder.field("inner4", "value4");
                        builder.field("inner5", "value5");
                        builder.field("inner6", "value6");
                    }
                    builder.endObject();
                }
            }
            builder.endObject();
            BytesReference bytes = BytesReference.bytes(builder);
            LinkedHashMap<String, Object> initialMap;
            try (XContentParser parser = createParser(xContentType.xContent(), bytes)) {
                initialMap = (LinkedHashMap<String, Object>)parser.mapOrdered();
            }

            List<String> expectedInnerKeys1 = Arrays.asList("inner1", "inner2", "inner3");
            Set<List<String>> distinctTopLevelKeys = new HashSet<>();
            Set<List<String>> distinctInnerKeys2 = new HashSet<>();
            for (int i = 0; i < 10; i++) {
                try (XContentParser parser = createParser(xContentType.xContent(), bytes)) {
                    try (XContentBuilder shuffledBuilder = shuffleXContent(parser, randomBoolean(), "object1")) {
                        try (XContentParser shuffledParser = createParser(shuffledBuilder)) {
                            Map<String, Object> shuffledMap = shuffledParser.mapOrdered();
                            assertThat(shuffledMap).as("both maps should contain the same mappings").isEqualTo(initialMap);
                            List<String> shuffledKeys = new ArrayList<>(shuffledMap.keySet());
                            distinctTopLevelKeys.add(shuffledKeys);
                            @SuppressWarnings("unchecked")
                            Map<String, Object> innerMap1 = (Map<String, Object>)shuffledMap.get("object1");
                            List<String> actualInnerKeys1 = new ArrayList<>(innerMap1.keySet());
                            assertThat(actualInnerKeys1).as("object1 should have been left untouched").isEqualTo(expectedInnerKeys1);
                            @SuppressWarnings("unchecked")
                            Map<String, Object> innerMap2 = (Map<String, Object>)shuffledMap.get("object2");
                            List<String> actualInnerKeys2 = new ArrayList<>(innerMap2.keySet());
                            distinctInnerKeys2.add(actualInnerKeys2);
                        }
                    }
                }
            }

            //out of 10 shuffling runs we expect to have at least more than 1 distinct output for both top level keys and inner object2
            assertThat(distinctTopLevelKeys).hasSizeGreaterThan(1);
            assertThat(distinctInnerKeys2).hasSizeGreaterThan(1);
        }
    }

    public void testRandomUniqueNotUnique() {
        assertThat(randomUnique(() -> 1, 10)).hasSize(1);
    }

    public void testRandomUniqueTotallyUnique() {
        AtomicInteger i = new AtomicInteger();
        assertThat(randomUnique(i::incrementAndGet, 100)).hasSize(100);
    }

    public void testRandomUniqueNormalUsageAlwayMoreThanOne() {
        assertThat(randomUnique(() -> randomAlphaOfLengthBetween(1, 20), 10)).hasSizeGreaterThan(0);
    }

    public void testRandomValueOtherThan() {
        // "normal" way of calling where the value is not null
        int bad = randomInt();
        assertThat((int) randomValueOtherThan(bad, ESTestCase::randomInt)).isNotEqualTo(bad);

        /*
         * "funny" way of calling where the value is null. This once
         * had a unique behavior but at this point `null` acts just
         * like any other value.
         */
        Supplier<Object> usuallyNull = () -> usually() ? null : randomInt();
        assertThat(randomValueOtherThan(null, usuallyNull)).isNotNull();
    }

    public void testWorkerSystemProperty() {
        assumeTrue(
            "requires running tests with Maven",
            System.getProperty(ESTestCase.TEST_WORKER_SYS_PROPERTY) != null);

        assertThat(ESTestCase.TEST_WORKER_VM_ID).isNotEqualTo(ESTestCase.DEFAULT_TEST_WORKER_ID);
    }

    public void testBasePortMaven() {
        assumeTrue(
            "requires running tests with Maven",
            System.getProperty(ESTestCase.TEST_WORKER_SYS_PROPERTY) != null);
        // Maven worker IDs are 1 based
        assertThat(ESTestCase.getBasePort()).isNotEqualTo(10300);
    }

    public void testBasePortIDE() {
        assumeTrue(
            "requires running tests without maven/forkCount",
            System.getProperty(ESTestCase.TEST_WORKER_SYS_PROPERTY) == null);
        assertThat(ESTestCase.getBasePort()).isEqualTo(10300);
    }
}
