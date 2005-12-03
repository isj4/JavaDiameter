#!/bin/sh
sed -r -e 's/(<A HREF="[^"]*src-html[^"]*">)([^ ]*)(<\/A>)/\2/g' <$1 >$1.tmp
touch -r $1.tmp $1
mv -f $1.tmp $1
