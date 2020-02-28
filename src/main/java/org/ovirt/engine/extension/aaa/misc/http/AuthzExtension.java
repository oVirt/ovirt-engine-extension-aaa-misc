/*
 * Copyright 2012-2014 Red Hat Inc.
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
package org.ovirt.engine.extension.aaa.misc.http;

import static org.ovirt.engine.api.extensions.aaa.Authz.QueryFilterOperator;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

import org.ovirt.engine.api.extensions.Base;
import org.ovirt.engine.api.extensions.ExtKey;
import org.ovirt.engine.api.extensions.ExtMap;
import org.ovirt.engine.api.extensions.ExtUUID;
import org.ovirt.engine.api.extensions.Extension;
import org.ovirt.engine.api.extensions.aaa.Authz;
import org.ovirt.engine.extension.aaa.misc.Config;
import org.ovirt.engine.extension.aaa.misc.QueryExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthzExtension implements Extension {
    private static final Logger LOG = LoggerFactory.getLogger(AuthzExtension.class);
    private static final int MAX_FILTER_SIZE = 100; // Maximum field nest level.

    private static final Logger log = LoggerFactory.getLogger(AuthzExtension.class);

    private String nameArg;
    private String groupsArg;
    private QueryExecutor queryExecutor;

    // The variables below need to be removed once extensions-api is released
    /**
      * HttpServletRequest.
      * Used for negotiate authentication.
      */
    public static final ExtKey HTTP_SERVLET_REQUEST = new ExtKey("AAA_AUTHN_HTTP_SERVLET_REQUEST", Object.class, "e1cd5eb2-8f63-4617-bcd4-9863bbc788d7");
    /**
     * Used for Fetch Principal for external SSO authentication where the claim set is included in the parameters
     */
    public static final ExtKey HTTP_SERVLET_REQUEST_PARAMS = new ExtKey("AAA_AUTHN_REQUEST_PARAMS", Object.class, "a02e573f-d928-4c78-bf40-fc932ee2a645");

    @Override
    public void invoke(ExtMap input, ExtMap output) {
        try {
            if (input.get(Base.InvokeKeys.COMMAND).equals(Base.InvokeCommands.LOAD)) {
                doLoad(input, output);
            } else if (input.get(Base.InvokeKeys.COMMAND).equals(Base.InvokeCommands.INITIALIZE)) {
                doInit(input, output);
            } else if (input.get(Base.InvokeKeys.COMMAND).equals(Base.InvokeCommands.TERMINATE)) {
                // Nothing to do.
            } else if (input.get(Base.InvokeKeys.COMMAND).equals(Authz.InvokeCommands.FETCH_PRINCIPAL_RECORD)) {
                doFetchPrincipalRecord(input, output);
            } else if (input.get(Base.InvokeKeys.COMMAND).equals(Authz.InvokeCommands.QUERY_OPEN)) {
                doQueryOpen(input, output);
            } else if (input.get(Base.InvokeKeys.COMMAND).equals(Authz.InvokeCommands.QUERY_EXECUTE)) {
                doQueryExecute(input, output);
            } else if (input.get(Base.InvokeKeys.COMMAND).equals(Authz.InvokeCommands.QUERY_CLOSE)) {
                doQueryClose(input, output);
            } else {
                output.put(Base.InvokeKeys.RESULT, Base.InvokeResult.UNSUPPORTED);
                throw new IllegalArgumentException();
            }
            output.put(Base.InvokeKeys.RESULT, Base.InvokeResult.SUCCESS);
            output.put(Authz.InvokeKeys.STATUS, Authz.Status.SUCCESS);
        } catch (Throwable e) {
            LOG.error(
                    "Unexpected Exception invoking: {}",
                    input.<ExtUUID> get(Base.InvokeKeys.COMMAND)
                    );
            LOG.debug(
                    "Exception:",
                    e
                    );
            output.putIfAbsent(Base.InvokeKeys.RESULT, Base.InvokeResult.FAILED);
            output.put(Authz.InvokeKeys.STATUS, Authz.Status.GENERAL_ERROR);
            output.put(Base.InvokeKeys.MESSAGE, e.getMessage());
        }
    }

    /**
     * Loads extension instance. Extension should configure its information within the context during this command. No
     * operation that may fail or change system state should be carried out at this stage.
     */
    private void doLoad(ExtMap input, ExtMap output) throws Exception {
        ExtMap context = input.<ExtMap> get(
                Base.InvokeKeys.CONTEXT
                );
        Properties configuration = context.<Properties> get(
                Base.ContextKeys.CONFIGURATION
                );

        context.mput(
                Authz.ContextKeys.AVAILABLE_NAMESPACES,
                Arrays.asList("*")
                ).mput(
                        Authz.ContextKeys.CAPABILITIES,
                        Authz.Capabilities.RECURSIVE_GROUP_RESOLUTION
                ).mput(
                        Authz.ContextKeys.QUERY_MAX_FILTER_SIZE,
                        MAX_FILTER_SIZE
                ).mput(
                        Base.ContextKeys.AUTHOR,
                        "The oVirt Project"
                ).mput(
                        Base.ContextKeys.LICENSE,
                        "ASL 2.0"
                ).mput(
                        Base.ContextKeys.HOME_URL,
                        "http://www.ovirt.org"
                ).mput(
                        Base.ContextKeys.VERSION,
                        Config.PACKAGE_VERSION
                ).mput(
                        Base.ContextKeys.EXTENSION_NOTES,
                        String.format(
                                "Display name: %s",
                                Config.PACKAGE_DISPLAY_NAME
                                )
                ).mput(
                        Base.ContextKeys.BUILD_INTERFACE_VERSION,
                        Base.INTERFACE_VERSION_CURRENT
                ).mput(
                        Base.ContextKeys.EXTENSION_NAME,
                        "aaa.misc.http.authz"
                );

        nameArg = configuration.getProperty("config.artifact.name.arg");
        groupsArg = configuration.getProperty("config.artifact.groups.arg");
    }

    private void doInit(ExtMap input, ExtMap output) throws SQLException, IOException {
        queryExecutor = new QueryExecutor();
    }

    private void doFetchPrincipalRecord(ExtMap input, ExtMap output) throws SQLException, IOException {
        // Fix after extensions-api is released
        // HttpServletRequest request = input.get(Authz.InvokeKeys.HTTP_SERVLET_REQUEST);
        HttpServletRequest request = input.get(HTTP_SERVLET_REQUEST);
        dumpRequest(request);

        Map<String, String> headers =
                request == null ?
        // Fix after extensions-api is released
        //              input.<Map<String, String>> get(Authz.InvokeKeys.HTTP_SERVLET_REQUEST_PARAMS) :
                        input.<Map<String, String>> get(HTTP_SERVLET_REQUEST_PARAMS) :
                        getHeaders(request);

        output.mput(
                Authz.InvokeKeys.STATUS,
                Authz.Status.SUCCESS
                ).mput(
                        Base.InvokeKeys.RESULT,
                        Base.InvokeResult.SUCCESS
                ).mput(
                        Authz.InvokeKeys.PRINCIPAL_RECORD,
                        queryExecutor.buildPrincipalRecord(headers, nameArg, groupsArg)
                );
    }

    private Map<String, String> getHeaders(HttpServletRequest req) {
        Map<String, String> params = new HashMap<>();
        Enumeration<String> headerNames = req.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            params.put(headerName, req.getHeader(headerName));
        }
        return params;
    }

    @SuppressWarnings("unchecked")
    private void dumpRequest(HttpServletRequest request) {
        if (request != null && log.isTraceEnabled()) {
            log.trace("Secure: {}", request.isSecure());
            log.trace("AuthType: {}", request.getAuthType());
            log.trace("Principal: {}", request.getUserPrincipal());
            log.trace("RemoteUser: {}", request.getRemoteUser());
            for (String attribute : Collections.list(request.getAttributeNames())) {
                log.trace("Attribute: {}: {}", attribute, request.getAttribute(attribute));
            }
            for (String header : Collections.list(request.getHeaderNames())) {
                int i = 0;
                for (String value : Collections.list(request.getHeaders(header))) {
                    log.trace("Header: {}:{} {}", header, i++, value);
                }
            }
        }
    }

    private void doQueryOpen(ExtMap input, ExtMap output) {
        try {
            output.mput(
                    Authz.InvokeKeys.QUERY_OPAQUE,
                    queryExecutor.openQuery(new ExtMap().mput(
                            QueryExecutor.SearchContext.IS_PRINCIPAL,
                            input.get(Authz.InvokeKeys.QUERY_ENTITY, ExtUUID.class)
                                    .equals(Authz.QueryEntity.PRINCIPAL)
                            ).mput(
                                    QueryExecutor.SearchContext.QUERY_STRING,
                                    getQueryString(input.get(Authz.InvokeKeys.QUERY_FILTER, ExtMap.class))
                            )
                    )).mput(Base.InvokeKeys.RESULT, Base.InvokeResult.SUCCESS);
        } catch (IllegalArgumentException e) {
            output.mput(Authz.InvokeKeys.STATUS, Authz.Status.GENERAL_ERROR)
                    .mput(Base.InvokeKeys.RESULT, Base.InvokeResult.FAILED)
                    .mput(Base.InvokeKeys.MESSAGE, e.getMessage());
        }
    }

    private String getQueryString(ExtMap filter) {
        ExtKey key = filter.get(Authz.QueryFilterRecord.KEY, ExtKey.class);
        int opCode = filter.get(Authz.QueryFilterRecord.OPERATOR, Integer.class);

        if (opCode == QueryFilterOperator.EQ) {
            String val = filter.get(key, String.class);
            return !val.endsWith("*") ? val : "";
        } else {
            Collection<ExtMap> filters = filter.get(Authz.QueryFilterRecord.FILTER);
            for (ExtMap map : filters) {
                return getQueryString(map);
            }
        }

        return "";
    }

    private void doQueryExecute(ExtMap input, ExtMap output) throws SQLException, IOException {
        output.mput(
                Authz.InvokeKeys.QUERY_RESULT,
                queryExecutor.executeQuery(input.get(Authz.InvokeKeys.QUERY_OPAQUE, String.class))
                );
    }

    private void doQueryClose(ExtMap input, ExtMap output) throws SQLException {
        // empty
    }

}
