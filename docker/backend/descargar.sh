#!/bin/sh
set -eu
maven=https://repo.maven.apache.org/maven2
mkdir -p /opt/liberty /opt/libraries
fetch() {
    curl --fail --location --retry 3 --proto '=https' "$1" -o "$2"
    printf '%s  %s\n' "$3" "$2" | sha256sum --check --status
}
fetch "$maven/io/openliberty/openliberty-runtime/26.0.0.11/openliberty-runtime-26.0.0.11.zip" /tmp/liberty.zip dba6f751e7875646ea103484d84194b6ace2dc66d01feb15db1a48aa66887766
unzip -q /tmp/liberty.zip -d /opt/liberty
rm /tmp/liberty.zip
fetch "$maven/org/postgresql/postgresql/42.7.3/postgresql-42.7.3.jar" /opt/libraries/postgresql.jar a2644cbfba1baa145ff7e8c8ef582a6eed7a7ec4ca792f7f054122bdec756268
fetch "$maven/org/primefaces/primefaces/13.0.0/primefaces-13.0.0-jakarta.jar" /opt/libraries/primefaces.jar 2b5e6885b87e74677b78da2727830f81e81420a9805b197d904ee9da22c0979d
