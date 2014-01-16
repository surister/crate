/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed any another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package org.cratedb.core;

import com.google.common.base.Splitter;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtils {
    private static final Splitter SPLITTER = Splitter.on('.');
    private static final Joiner JOINER = Joiner.on('.');
    private static final Pattern PATTERN = Pattern.compile("\\['([^\\]])*'\\]");
    private static final Pattern SQL_PATTERN = Pattern.compile("(.+?)(?:\\['([^\\]])*'\\])+");

    public static String dottedToSqlPath(String dottedPath) {
        Iterable<String> splitted = SPLITTER.split(dottedPath);
        Iterator<String> iter = splitted.iterator();
        StringBuilder builder = new StringBuilder(iter.next());
        while (iter.hasNext()) {
            builder.append("['").append(iter.next()).append("']");
        }
        return builder.toString();
    }

    public static String sqlToDottedPath(String sqlPath) {
        if (!SQL_PATTERN.matcher(sqlPath).find()) { return sqlPath; }
        List<String> s = new ArrayList<>();
        int idx = sqlPath.indexOf('[');
        s.add(sqlPath.substring(0, idx));
        Matcher m = PATTERN.matcher(sqlPath);
        while (m.find(idx)) {
            String group = m.group(1);
            if (group == null) { group = ""; }
            s.add(group);
            idx = m.end();
        }

        return JOINER.join(s);
    }
}
