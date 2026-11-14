#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p tools
base=https://repo.maven.apache.org/maven2
for entry in \
 org/smali/baksmali/2.5.2/baksmali-2.5.2.jar \
 org/smali/smali/2.5.2/smali-2.5.2.jar \
 org/smali/dexlib2/2.5.2/dexlib2-2.5.2.jar \
 org/smali/util/2.5.2/util-2.5.2.jar \
 com/beust/jcommander/1.64/jcommander-1.64.jar \
 com/google/guava/guava/27.1-android/guava-27.1-android.jar \
 org/antlr/antlr/3.5.2/antlr-3.5.2.jar \
 org/antlr/antlr-runtime/3.5.2/antlr-runtime-3.5.2.jar \
 org/antlr/stringtemplate/3.2.1/stringtemplate-3.2.1.jar
do
 curl --fail --location --output "tools/${entry##*/}" "$base/$entry"
done
(cd tools && sha256sum -c SHA256SUMS)
