/*
 * Copyright 2012-2018 Red Hat Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */
package org.ovirt.engine.extension.aaa.misc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.ovirt.engine.api.extensions.ExtKey;
import org.ovirt.engine.api.extensions.ExtMap;
import org.ovirt.engine.api.extensions.aaa.Authz;

public class QueryExecutor {
    private AtomicLong nextOpaque = new AtomicLong(1);
    private Map<String, ExtMap> searchQueries = new HashMap<>();

    public static class SearchContext {
        /** can be principal or group */
        public static final ExtKey IS_PRINCIPAL = new ExtKey("AAA_MISC_IS_PRINCIPAL",
                Boolean.class,
                "a3c7adec-d161-11e8-9e1e-5254008c7d02");
        public static final ExtKey QUERY_STRING = new ExtKey("AAA_MISC_QUERY_STRING",
                String.class,
                "aad9fcb6-d161-11e8-a234-5254008c7d02");
    }

    public String openQuery(ExtMap filter) {
        String opaque = Long.toString(nextOpaque.getAndIncrement());

        searchQueries.put(opaque, filter);

        return opaque;
    }

    public Collection<ExtMap> executeQuery(String opaque) {
        ExtMap filter = searchQueries.get(opaque);

        Collection<ExtMap> result = null;

        if (filter != null) {
            result = new ArrayList<>();
            boolean isPrincipal = filter.get(SearchContext.IS_PRINCIPAL, Boolean.class);
            if (isPrincipal) {
                result.add(getPrincipalRecord(filter));
            } else {
                result.add(getGroupRecord(filter));
            }
        }
        searchQueries.remove(opaque);
        return result;
    }

    public ExtMap buildPrincipalRecord(Map<String, String> headers, String nameArg, String groupsArg) {
        ExtMap principalRecord = new ExtMap();
        principalRecord.mput(
                Authz.PrincipalRecord.NAMESPACE,
                "*"
        ).mput(
                Authz.PrincipalRecord.PRINCIPAL,
                headers.get(nameArg)
        ).mput(
                Authz.PrincipalRecord.NAME,
                headers.get(nameArg)
        ).mput(
                Authz.PrincipalRecord.LAST_NAME,
                ""
        ).mput(
                Authz.PrincipalRecord.GROUPS,
                buildPrincipalRecordGroups(headers, groupsArg)
        );
        return principalRecord;
    }

    public Collection<ExtMap> buildPrincipalRecordGroups(Map<String, String> headers, String groupsArg) {
        List<String> groupNames;
        if (headers.containsKey(groupsArg)) {
            groupNames = Arrays.asList(headers.get(groupsArg).split(","));
        } else {
            return Collections.emptyList();
        }

        LinkedList<ExtMap> groups = new LinkedList<>();
        for (String groupName : groupNames) {
            groupName = groupName.replaceFirst("^/", "");
            ExtMap group = new ExtMap();
            group.mput(
                    Authz.GroupRecord.GROUPS,
                    new LinkedList<>()
            ).mput(
                    Authz.GroupRecord.NAMESPACE,
                    "*"
            ).mput(
                    Authz.GroupRecord.NAME,
                    groupName
            ).mput(
                    Authz.GroupRecord.ID,
                    groupName
            );
            groups.add(group);
        }
        return groups;
    }

    private ExtMap getPrincipalRecord(ExtMap opaque) {
        String name = opaque.get(SearchContext.QUERY_STRING, String.class);
        if (name.trim().length() > 0) {
            return new ExtMap().mput(
                    Authz.PrincipalRecord.GROUPS,
                    Collections.emptyList()
                    ).mput(
                            Authz.PrincipalRecord.TITLE,
                            ""
                    ).mput(
                            Authz.PrincipalRecord.NAMESPACE,
                            "*"
                    ).mput(
                            Authz.PrincipalRecord.NAME,
                            name
                    ).mput(
                            Authz.PrincipalRecord.DEPARTMENT,
                            ""
                    ).mput(
                            Authz.PrincipalRecord.PRINCIPAL,
                            name
                    ).mput(
                            Authz.PrincipalRecord.EMAIL,
                            ""
                    ).mput(
                            Authz.PrincipalRecord.LAST_NAME,
                            ""
                    ).mput(
                            Authz.PrincipalRecord.DISPLAY_NAME,
                            ""
                    ).mput(
                            Authz.PrincipalRecord.FIRST_NAME,
                            name
                    ).mput(
                            Authz.PrincipalRecord.ID,
                            UUID.randomUUID().toString()
                    );
        }
        return new ExtMap();
    }

    private ExtMap getGroupRecord(ExtMap opaque) {
        String name = opaque.get(SearchContext.QUERY_STRING, String.class);
        if (name.trim().length() > 0) {
            return new ExtMap().mput(
                    Authz.PrincipalRecord.NAMESPACE,
                    "*"
                    ).mput(
                            Authz.GroupRecord.NAME,
                            name
                    ).mput(
                            Authz.GroupRecord.ID,
                            UUID.randomUUID().toString()
                    );
        }
        return new ExtMap();
    }
}
