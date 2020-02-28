# ====================================================================
# Copyright oVirt Authors
# SPDX-License-Identifier: Apache-2.0
# ====================================================================

#
# CUSTOMIZATION-BEGIN
#
PACKAGE_NAME=ovirt-engine-extension-aaa-misc
PACKAGE_VERSION=master
PACKAGE_DISPLAY_NAME=$(PACKAGE_NAME)-$(PACKAGE_VERSION)
PREFIX=/usr/local
LOCALSTATE_DIR=$(PREFIX)/var
BIN_DIR=$(PREFIX)/bin
SYSCONF_DIR=$(PREFIX)/etc
DATAROOT_DIR=$(PREFIX)/share
MAN_DIR=$(DATAROOT_DIR)/man
DOC_DIR=$(DATAROOT_DIR)/doc
DATA_DIR=$(DATAROOT_DIR)/$(PACKAGE_NAME)
JAVA_DIR=$(DATAROOT_DIR)/java
BUILD_FILE=tmp.built

#
# CUSTOMIZATION-END
#

# Don't use any of the bultin rules, in particular don't use the rule
# for .sh files, as that means that we can't generate .sh files from
# templates:
.SUFFIXES:
.SUFFIXES: .in

# Rule to generate files from templates:
.in:
	GENERATED_FILE_LIST=$$(echo "$(GENERATED)" | sed -e "s/ /\\\n/g"); \
	sed \
	-e "s|@MODULEPATH@|$(DATA_DIR)/modules|g" \
	-e "s|@PACKAGE_NAME@|$(PACKAGE_NAME)|g" \
	-e "s|@PACKAGE_VERSION@|$(PACKAGE_VERSION)|g" \
	-e "s|@PACKAGE_DISPLAY_NAME@|$(PACKAGE_DISPLAY_NAME)|g" \
	$< > $@

# List of files that will be generated from templates:
# Once add new template file, if required chmod, add it in generated-files target.
GENERATED = \
	packaging/etc/ovirt-engine/engine.conf.d/50-ovirt-engine-extension-aaa-misc.conf \
	src/main/resources/org/ovirt/engine/extension/aaa/misc/config.properties \
	$(NULL)


build: \
	$(BUILD_FILE) \
	$(NULL)

# support force run of maven
maven:
	mvn -Pdevenv install
	touch "$(BUILD_FILE)"

$(BUILD_FILE):
	$(MAKE) maven

clean:
	# Clean maven generated stuff:
	mvn clean
	rm -rf $(BUILD_FILE) tmp.dev.flist
	# Clean files generated from templates:
	rm -rf $$(echo $(GENERATED))


install: \
	install-layout \
	$(NULL)


# copy SOURCEDIR to TARGETDIR
# exclude EXCLUDEGEN a list of files to exclude with .in
# exclude EXCLUDE a list of files.
copy-recursive:
	( cd "$(SOURCEDIR)" && find . -type d -printf '%P\n' ) | while read d; do \
		install -d -m 755 "$(TARGETDIR)/$${d}"; \
	done
	( \
		cd "$(SOURCEDIR)" && find . -type f -printf '%P\n' | \
		while read f; do \
			exclude=false; \
			for x in $(EXCLUDE_GEN); do \
				if [ "$(SOURCEDIR)/$${f}" = "$${x}.in" ]; then \
					exclude=true; \
					break; \
				fi; \
			done; \
			for x in $(EXCLUDE); do \
				if [ "$(SOURCEDIR)/$${f}" = "$${x}" ]; then \
					exclude=true; \
					break; \
				fi; \
			done; \
			$${exclude} || echo "$${f}"; \
		done \
	) | while read f; do \
		src="$(SOURCEDIR)/$${f}"; \
		dst="$(TARGETDIR)/$${f}"; \
		[ -x "$${src}" ] && MASK=0755 || MASK=0644; \
		[ -n "$(DEV_FLIST)" ] && echo "$${dst}" | sed 's#^$(PREFIX)/##' >> "$(DEV_FLIST)"; \
		install -T -m "$${MASK}" "$${src}" "$${dst}"; \
	done


generate-files: \
		$(GENERATED) \
		$(NULL)


install-packaging-files:
	$(MAKE) copy-recursive SOURCEDIR=packaging/etc TARGETDIR="$(DESTDIR)$(SYSCONF_DIR)" EXCLUDE_GEN="$(GENERATED)"
	$(MAKE) copy-recursive SOURCEDIR="packaging/modules" TARGETDIR="$(DESTDIR)$(DATA_DIR)/modules" EXCLUDE_GEN="$(GENERATED)"
	install -d -m 755 "$(DESTDIR)$(DOC_DIR)"
	install -d -m 755 "$(DESTDIR)$(DOC_DIR)/$(PACKAGE_NAME)"
	install -m 644 README.http "$(DESTDIR)$(DOC_DIR)/$(PACKAGE_NAME)/README.http"
	install -m 644 README.mapping "$(DESTDIR)$(DOC_DIR)/$(PACKAGE_NAME)/README.mapping"


install-layout: \
		install-packaging-files \
		$(NULL)
	install -d -m 755 "$(DESTDIR)$(BIN_DIR)"
	ln -sf "$(JAVA_DIR)/$(PACKAGE_NAME)/$(PACKAGE_NAME).jar" "$(DESTDIR)$(DATA_DIR)/modules/org/ovirt/engine/extension/aaa/misc/main/$(PACKAGE_NAME).jar"

