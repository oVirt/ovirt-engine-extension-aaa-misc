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
package org.ovirt.engineextensions.aaa.misc.mapping;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import javax.servlet.http.*;

import org.slf4j.*;

import org.ovirt.engine.api.extensions.*;
import org.ovirt.engine.api.extensions.aaa.*;

import org.ovirt.engineextensions.aaa.misc.*;

public class MappingExtension implements Extension {
    
    private enum ChangeCase {
        TOLOWER {
            @Override
            String transform(String value) {
                return value.toLowerCase();
            }
        },
        TOUPPER {
            @Override
            String transform(String value) {
                return value.toUpperCase();
            }
        },
        KEEP {
            @Override
            String transform(String value) {
                return value;
            }
        };
        abstract String transform(String value);
    }

    private interface Format {
        String format(String s);
    }

    private class VoidFormat implements Format {
        public String format(String s) {
            return s;
        }
    }

    private class RegExFormat implements Format {
        private final Pattern pattern;
        private final String replacement;
        private final boolean mustMatch;
        private final ChangeCase changecase;

        public RegExFormat(Properties props, String prefix, ChangeCase changecase) {
            pattern = Pattern.compile(props.getProperty(prefix + "pattern", "^.*$"));
            replacement = props.getProperty(prefix + "replacement", "$0");
            mustMatch = Boolean.valueOf(props.getProperty(prefix + "mustMatch", "false"));
            this.changecase = changecase;
        }

        public String format(String s) {
            String ret = s;
            Matcher matcher = pattern.matcher(s);
            if (!matcher.matches()) {
                if (mustMatch) {
                    throw new RuntimeException(
                        String.format(
                            "Input '%s' does not matches pattern '%s'",
                            s,
                            pattern
                        )
                    );
                }
            } else {
                ret = matcher.replaceFirst(replacement);
            }
            return changecase.transform(ret);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(MappingExtension.class);

    private Format mapUser;
    private Format mapAuthRecord;

    @Override
    public void invoke(ExtMap input, ExtMap output) {
        try {
            if (input.get(Base.InvokeKeys.COMMAND).equals(Base.InvokeCommands.LOAD)) {
                doLoad(input, output);
            } else if (input.get(Base.InvokeKeys.COMMAND).equals(Base.InvokeCommands.INITIALIZE)) {
                doInit(input, output);
            } else if (input.get(Base.InvokeKeys.COMMAND).equals(Base.InvokeCommands.TERMINATE)) {
                doTerminate(input, output);
            } else if (input.get(Base.InvokeKeys.COMMAND).equals(Mapping.InvokeCommands.MAP_USER)) {
                doMapUser(input, output);
            } else if (input.get(Base.InvokeKeys.COMMAND).equals(Mapping.InvokeCommands.MAP_AUTH_RECORD)) {
                doMapAuthRecord(input, output);
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

        mapUser = createFormat(configuration, "config.mapUser.");
        mapAuthRecord = createFormat(configuration, "config.mapAuthRecord.");
    }

    private void doInit(ExtMap input, ExtMap output) throws Exception {
    }

    private void doTerminate(ExtMap input, ExtMap output) throws IOException {
    }

    private void doMapUser(ExtMap input, ExtMap output) throws Exception {

        log.debug("doMapUser Entry {}", input);

        output.put(
            Mapping.InvokeKeys.USER,
            mapUser.format(input.<String>get(Mapping.InvokeKeys.USER))
        );

        log.debug("doMapUser Return {}", output);
    }

    private void doMapAuthRecord(ExtMap input, ExtMap output) throws Exception {

        log.debug("doMapAuthRecord Entry {}", input);

        ExtMap authRecord = new ExtMap(
            input.<ExtMap>get(Authn.InvokeKeys.AUTH_RECORD)
        );
        authRecord.put(
            Authn.AuthRecord.PRINCIPAL,
            mapAuthRecord.format(authRecord.<String>get(Authn.AuthRecord.PRINCIPAL))
        );
        output.put(
            Authn.InvokeKeys.AUTH_RECORD,
            authRecord
        );

        log.debug("doMapAuthRecord Return {}", output);
    }

    private Format createFormat(Properties props, String prefix) {
        Format ret = null;
        String type = props.getProperty(prefix + "type", "void");
        String newcase = props.getProperty(prefix + "case", "").toLowerCase();
        ChangeCase changecase;
        if("lower".equals(newcase)) {
            changecase = ChangeCase.TOLOWER;
        } else if ("upper".equals(newcase)){
            changecase = ChangeCase.TOUPPER;
        } else {
            changecase = ChangeCase.KEEP;
        }
        if ("void".equals(type)) {
            ret = new VoidFormat();
        } else if ("regex".equals(type)) {
            ret = new RegExFormat(props, prefix + type + ".", changecase);
        } else {
            new IllegalArgumentException(
                String.format(
                    "Invalid conversion type '%s'",
                    type
                )
            );
        }
        return ret;
    }

}

// vim: expandtab tabstop=4 shiftwidth=4
