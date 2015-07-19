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
package org.ovirt.engineextensions.aaa.misc.http;

import java.io.*;
import java.util.*;
import javax.servlet.http.*;

import org.slf4j.*;

import org.ovirt.engine.api.extensions.*;
import org.ovirt.engine.api.extensions.aaa.*;

import org.ovirt.engineextensions.aaa.misc.*;

public class AuthnExtension implements Extension {

    private enum Artifact {
        PRINCIPAL(
            new Format() {
                public String getPrincipal(HttpServletRequest request, String arg) {
                    return request.getUserPrincipal().getName();
                }
            }
        ),
        REMOTE_USER(
            new Format() {
                public String getPrincipal(HttpServletRequest request, String arg) {
                    return request.getRemoteUser();
                }
            }
        ),
        HEADER(
            new Format() {
                public String getPrincipal(HttpServletRequest request, String arg) {
                    return request.getHeader(arg);
                }
            }
        ),
        ATTRIBUTE(
            new Format() {
                public String getPrincipal(HttpServletRequest request, String arg) {
                    return request.getAttribute(arg).toString();
                }
            }
        ),
        ENVIRONMENT(
            new Format() {
                public String getPrincipal(HttpServletRequest request, String arg) {
                    return System.getenv(arg);
                }
            }
        );

        private interface Format {
            String getPrincipal(HttpServletRequest request, String arg);
        }

        private final Format format;

        private Artifact(Format format) {
            this.format = format;
        }

        public String getPrincipal(HttpServletRequest request, String arg) {
            return format.getPrincipal(request, arg);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(AuthnExtension.class);

    private Artifact artifact;
    private String artifactArg;

    @Override
    public void invoke(ExtMap input, ExtMap output) {
        try {
            if (input.get(Base.InvokeKeys.COMMAND).equals(Base.InvokeCommands.LOAD)) {
                doLoad(input, output);
            } else if (input.get(Base.InvokeKeys.COMMAND).equals(Base.InvokeCommands.INITIALIZE)) {
                doInit(input, output);
            } else if (input.get(Base.InvokeKeys.COMMAND).equals(Base.InvokeCommands.TERMINATE)) {
                doTerminate(input, output);
            } else if (input.get(Base.InvokeKeys.COMMAND).equals(Authn.InvokeCommands.AUTHENTICATE_NEGOTIATE)) {
                doAuthenticateNegotiate(input, output);
            } else {
                output.put(Base.InvokeKeys.RESULT, Base.InvokeResult.UNSUPPORTED);
            }
            output.putIfAbsent(Base.InvokeKeys.RESULT, Base.InvokeResult.SUCCESS);
        } catch (Exception e) {
            log.debug("Exception", e);
            output.mput(
                Base.InvokeKeys.RESULT, Base.InvokeResult.FAILED
            ).mput(
                Base.InvokeKeys.MESSAGE, e.getMessage()
            );
        }
    }

    private void doLoad(ExtMap input, ExtMap output) throws Exception {
        ExtMap context = input.<ExtMap> get(
            Base.InvokeKeys.CONTEXT
        );
        Properties configuration = context.<Properties> get(
            Base.ContextKeys.CONFIGURATION
        );
        context.mput(
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
            "aaa.misc.http.authn"
        ).mput(
            Authn.ContextKeys.CAPABILITIES,
            (
                Authn.Capabilities.AUTHENTICATE_NEGOTIATE_NON_INTERACTIVE |
                0
            )
        );

        artifact = Artifact.valueOf(configuration.getProperty("config.artifact.name", Artifact.PRINCIPAL.toString()));
        artifactArg = configuration.getProperty("config.artifact.arg");
    }

    private void doInit(ExtMap input, ExtMap output) throws Exception {
    }

    private void doTerminate(ExtMap input, ExtMap output) throws IOException {
    }

    private void doAuthenticateNegotiate(ExtMap input, ExtMap output) throws Exception {

        log.debug("doAuthenticateNegotiate Entry");

        HttpServletRequest request = input.<HttpServletRequest>get(Authn.InvokeKeys.HTTP_SERVLET_REQUEST);
        dumpRequest(request);

        String principal = artifact.getPrincipal(request, artifactArg);
        if (principal == null) {
            output.mput(
                Authn.InvokeKeys.RESULT,
                Authn.AuthResult.NEGOTIATION_UNAUTHORIZED
            );
        } else {
            output.mput(
                Authn.InvokeKeys.RESULT,
                Authn.AuthResult.SUCCESS
            ).mput(
                Authn.InvokeKeys.PRINCIPAL,
                principal
            ).mput(
                Authn.InvokeKeys.AUTH_RECORD,
                new ExtMap().mput(
                    Authn.AuthRecord.PRINCIPAL,
                    principal
                )
            );
        }

        log.debug("doAuthenticateNegotiate Return {}", output);
    }

    @SuppressWarnings("unchecked")
    private void dumpRequest(HttpServletRequest request) {
        if (log.isTraceEnabled()) {
            log.trace("Secure: {}", request.isSecure());
            log.trace("AuthType: {}", request.getAuthType());
            log.trace("Principal: {}", request.getUserPrincipal());
            log.trace("RemoteUser: {}", request.getRemoteUser());
            for (String attribute : Collections.list((Enumeration<String>)request.getAttributeNames())) {
                log.trace("Attribute: {}: {}", attribute, request.getAttribute(attribute));
            }
            for (String header : Collections.list((Enumeration<String>)request.getHeaderNames())) {
                int i=0;
                for (String value : Collections.list((Enumeration<String>)request.getHeaders(header))) {
                    log.trace("Header: {}:{} {}", header, i++, value);
                }
            }
        }
    }

}

// vim: expandtab tabstop=4 shiftwidth=4
