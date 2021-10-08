# Binscure

Java obfuscator created by [x4e](https://github.com/x4e).

## Usage

First, create a config file ([example config](./waterqueue-config-example.yml) used by [blazenarchy/waterqueue](https://github.com/blazenarchy/waterqueue)).

When you have a config file, run binscure with the config file as an argument (also make sure that the config file points to the correct relative paths of the input file and any needed libraries), e.g.:
```
java -jar binscure.jar config.yml
```

## Building

Install the JDK and setup `JAVA_HOME` by following the instructions [here](https://docs.oracle.com/cd/E19182-01/821-0917/inst_jdk_javahome_t/index.html).

Install [maven](https://maven.apache.org/install.html).

Run `mvn package` in the binscure repository, when the command completes the binscure jar file will be located at `./target/binscure.jar`.
