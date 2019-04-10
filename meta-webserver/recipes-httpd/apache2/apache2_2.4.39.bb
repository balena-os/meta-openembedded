DESCRIPTION = "The Apache HTTP Server is a powerful, efficient, and \
extensible web server."
SUMMARY = "Apache HTTP Server"
HOMEPAGE = "http://httpd.apache.org/"
SECTION = "net"
LICENSE = "Apache-2.0"

SRC_URI = "${APACHE_MIRROR}/httpd/httpd-${PV}.tar.bz2 \
           file://0001-configure-use-pkg-config-for-PCRE-detection.patch \
           file://0002-apache2-bump-up-the-core-size-limit-if-CoreDumpDirec.patch \
           file://0003-apache2-do-not-export-apr-apr-util-symbols-when-usin.patch \
           file://0004-apache2-log-the-SELinux-context-at-startup.patch \
           file://0005-replace-lynx-to-curl-in-apachectl-script.patch \
           file://0006-apache2-fix-the-race-issue-of-parallel-installation.patch \
           file://0007-apache2-allow-to-disable-selinux-support.patch \
          "

SRC_URI_append_class-target = " \
           file://0008-apache2-do-not-use-relative-path-for-gen_test_char.patch \
           file://init \
           file://apache2-volatile.conf \
           file://apache2.service \
           file://volatiles.04_apache2 \
           "

LIC_FILES_CHKSUM = "file://LICENSE;md5=d52d0fd0bc788f068e647116c01ddfcd"
SRC_URI[md5sum] = "930e217ba2d71e708a3f1521ecae7ec0"
SRC_URI[sha256sum] = "b4ca9d05773aa59b54d66cd8f4744b945289f084d3be17d7981d1783a5decfa2"

S = "${WORKDIR}/httpd-${PV}"

inherit autotools update-rc.d pkgconfig systemd update-alternatives

DEPENDS = "openssl expat pcre apr apr-util apache2-native "

CVE_PRODUCT = "http_server"

SSTATE_SCAN_FILES += "apxs config_vars.mk config.nice"

PACKAGECONFIG ?= "${@bb.utils.filter('DISTRO_FEATURES', 'selinux', d)}"
PACKAGECONFIG[selinux] = "--enable-selinux,--disable-selinux,libselinux,libselinux"
PACKAGECONFIG[openldap] = "--enable-ldap --enable-authnz-ldap,--disable-ldap --disable-authnz-ldap,openldap"
PACKAGECONFIG[zlib] = "--enable-deflate,,zlib,zlib"

CFLAGS_append = " -DPATH_MAX=4096"

EXTRA_OECONF_class-target = "\
    --enable-layout=Debian \
    --prefix=${base_prefix} \
    --exec_prefix=${exec_prefix} \
    --includedir=${includedir}/${BPN} \
    --sysconfdir=${sysconfdir}/${BPN} \
    --datadir=${datadir}/${BPN} \
    --libdir=${libdir} \
    --libexecdir=${libdir}/${BPN}/modules \
    --localstatedir=${localstatedir} \
    --enable-ssl \
    --with-dbm=sdbm \
    --with-gdbm=no \
    --with-ndbm=no \
    --with-berkeley-db=no \
    --enable-info \
    --enable-rewrite \
    --enable-mpms-shared \
    ap_cv_void_ptr_lt_long=no \
    ac_cv_have_threadsafe_pollset=no \
    "

EXTRA_OECONF_class-native = "\
    --prefix=${prefix} \
    --datadir=${datadir}/${BPN} \
    "

do_configure_prepend() {
    sed -i -e 's:$''{prefix}/usr/lib/cgi-bin:$''{libdir}/cgi-bin:g' ${S}/config.layout
}

do_install_append_class-target() {
    install -d ${D}/${sysconfdir}/init.d

    cat ${WORKDIR}/init | \
        sed -e 's,/usr/sbin/,${sbindir}/,g' \
            -e 's,/usr/bin/,${bindir}/,g' \
            -e 's,/usr/lib/,${libdir}/,g' \
            -e 's,/etc/,${sysconfdir}/,g' \
            -e 's,/usr/,${prefix}/,g' > ${D}/${sysconfdir}/init.d/${BPN}

    chmod 755 ${D}/${sysconfdir}/init.d/${BPN}

    # Remove the goofy original files...
    rm -rf ${D}/${sysconfdir}/${BPN}/original

    install -d ${D}${sysconfdir}/${BPN}/conf.d
    install -d ${D}${sysconfdir}/${BPN}/modules.d

    # Ensure configuration file pulls in conf.d and modules.d
    printf "\nIncludeOptional ${sysconfdir}/${BPN}/conf.d/*.conf" >> ${D}/${sysconfdir}/${BPN}/httpd.conf
    printf "\nIncludeOptional ${sysconfdir}/${BPN}/modules.d/*.load" >> ${D}/${sysconfdir}/${BPN}/httpd.conf
    printf "\nIncludeOptional ${sysconfdir}/${BPN}/modules.d/*.conf\n\n" >> ${D}/${sysconfdir}/${BPN}/httpd.conf

    # Match with that is in init script
    printf "\nPidFile /run/httpd.pid" >> ${D}/${sysconfdir}/${BPN}/httpd.conf

    # Set 'ServerName' to fix error messages when restart apache service
    sed -i 's/^#ServerName www.example.com/ServerName localhost/' ${D}/${sysconfdir}/${BPN}/httpd.conf

    sed -i 's/^ServerRoot/#ServerRoot/' ${D}/${sysconfdir}/${BPN}/httpd.conf

    if ${@bb.utils.contains('DISTRO_FEATURES', 'systemd', 'true', 'false', d)}; then
        install -d ${D}${sysconfdir}/tmpfiles.d/
        install -m 0644 ${WORKDIR}/apache2-volatile.conf ${D}${sysconfdir}/tmpfiles.d/

        install -d ${D}${systemd_unitdir}/system
        install -m 0644 ${WORKDIR}/apache2.service ${D}${systemd_unitdir}/system
        sed -i -e 's,@SBINDIR@,${sbindir},g' ${D}${systemd_unitdir}/system/apache2.service
        sed -i -e 's,@BASE_BINDIR@,${base_bindir},g' ${D}${systemd_unitdir}/system/apache2.service
    elif ${@bb.utils.contains('DISTRO_FEATURES', 'sysvinit', 'true', 'false', d)}; then
        install -d ${D}${sysconfdir}/default/volatiles
        install -m 0644 ${WORKDIR}/volatiles.04_apache2 ${D}${sysconfdir}/default/volatiles/04_apache2
    fi

    rm -rf ${D}${datadir}/${BPN}/build
    rm -rf ${D}${localstatedir}
    chown -R root:root ${D}
}

do_install_class-native() {
    install -d ${D}${bindir} ${D}${libdir}
    install -m 755 server/gen_test_char ${D}${bindir}
}

# Implications - used by update-rc.d scripts
INITSCRIPT_NAME = "apache2"
INITSCRIPT_PARAMS = "defaults 91 20"

SYSTEMD_SERVICE_${PN} = "apache2.service"
SYSTEMD_AUTO_ENABLE_${PN} = "enable"

ALTERNATIVE_${PN}-doc = "htpasswd.1"
ALTERNATIVE_LINK_NAME[htpasswd.1] = "${mandir}/man1/htpasswd.1"

PACKAGES = "${PN}-scripts ${PN}-doc ${PN}-dev ${PN}-dbg ${PN}"

CONFFILES_${PN} = "${sysconfdir}/${BPN}/httpd.conf \
                   ${sysconfdir}/${BPN}/magic \
                   ${sysconfdir}/${BPN}/mime.types"

# We override here rather than append so that .so links are
# included in the runtime package rather than here (-dev)
# and to get icons, error into the -dev package
FILES_${PN}-dev = "${datadir}/${BPN}/icons \
                   ${datadir}/${BPN}/error \
                   ${includedir}/${BPN} \
                  "

FILES_${PN}-scripts += "${bindir}/dbmmanage"

# Override this too - here is the default, less datadir
FILES_${PN} =  "${bindir} ${sbindir} ${libexecdir} ${libdir} \
                ${sysconfdir} ${libdir}/${BPN}"

# We want htdocs and cgi-bin to go with the binary
FILES_${PN} += "${datadir}/${BPN}/ ${libdir}/cgi-bin"

FILES_${PN}-dbg += "${libdir}/${BPN}/modules/.debug"

RDEPENDS_${PN} += "openssl libgcc"
RDEPENDS_${PN}-scripts += "perl ${PN}"
RDEPENDS_${PN}-dev = "perl"

BBCLASSEXTEND = "native"