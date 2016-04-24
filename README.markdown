# JavaDiameter #

_JavaDiameter_ is a library for supporting the diameter protocol. It contains raw protocol support, message and AVP encoders/decoders and various classes for implementing different diameter node types (proxies, servers, clients, ...). It is dictionary-less and schema-less.

It is designed to be lean and mean with as few dependencies as possible.

## Dependencies ##
One. My JavaSCTP library (available at http://i1.dk/JavaSCTP/). You can disable that dependency by editing the makefile.

## A few notes on the source code ##
I'm not a java programmer so some constructs and fewer abstraction layers are not what you normally see in java. I like to think that the result is smaller and faster than what a normaly java programmer would produce.

In order to keep the dependencies down it uses java.util.logging which is what was available in java 1.5. I have considered changing it but it seems to me that the java community can't make up their mind, and adding additional layers (eg. SLF4J) is neither going to make it faster nor have fewer dependencies. Quite the opposite.

There is a branch _tls_ that supports TLS as per RFC3868. It doesn't do the CN<->node-identity validation, but it works. It doesn't support the new-style TLS as per the newer RFC6733.

The source uses tabs (=8 spaces). Deal with it.

## History ##
I made it as a proof-of-concept in 2005 and also to have a realistic project for learning java. The previous license was closed-source and restrictive in order to avoid conflicts of interest with my then-current employer. This is no longer a problem, so it is using the permissive zlib/png-style license.

I no longer have the time to maintain it, but I did find time to put it up on github. Pull-requests are welcome but my reaction may not be timely.
