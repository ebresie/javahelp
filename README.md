JavaHelp
========

This is a repository with sources for the **JavaHelp** library and 
[Ant](http://ant.apache.org) builds scripts to create a release `JAR` from
them.

To do a complete build of all JavaHelp JARs with Ant, use the `release` 
target of the [Ant](http://ant.apache.org) script found in the root
directory (which delegates to `javahelp_nbproject/build.xml`). The resulting
JARs are then located at
```bash
javahelp$ ls -1 javahelp_nbproject/dist/lib/
jhall.jar
jhbasic.jar
jh-client.jar
jh.jar
jsearch-client.jar
jsearch-indexer.jar
jsearch.jar
jsearch-misc.jar
```
the `jhall.jar` is the uber JAR that contains all the released bits.

## What's here:

  - `jhMaster/` - the main JavaHelp sources and Makefiles
  - `javahelp_nbproject/` - Ant build script and NetBeans project metadata for building JavaHelp
  - `JSearchClient_nbproject/` - Ant build script and NetBeans project metadata for building the JSearch client sources
  - `JSearchIndexer_nbproject/` - Ant build script and NetBeans project metadata for building the JSearch indexer sources

