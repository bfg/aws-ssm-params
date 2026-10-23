# aws-ssm-params

`aws-ssm-params` is a CLI for using values from [AWS Systems Manager Parameter Store][aws-ssm-param-store]
as environment variables. It also provides tools for listing, importing, exporting, copying, moving, and
removing AWS SSM parameters.

### Project goals

- Make it easy to load Parameter Store configuration into an application's environment before it starts.
- Support multiple paths and recursive lookups so shared and application-specific settings can be used together.
- Preserve values containing special characters when producing shell exports or dotenv content.

### Inspiration

- [aws-env] - the original tool for exporting AWS SSM parameters as environment variables
- [ssmtui] - amazing terminal GUI for browsing AWS SSM Parameter Store

# Features

- Query multiple Parameter Store paths and override values with those from later paths.
- Fetch parameters recursively.
- Handle values containing special characters and shell escapes.
- Support all AWS SSM parameter types, including `SecureString`.
- Include the tool in your application's container image.
- Perform batch operations on AWS SSM parameters:
  - listing
  - adding
  - copying
  - moving
  - removing

# Installation

### Prebuilt binary

Prebuilt binaries for various operating systems and CPU architectures can be downloaded from
[GitHub Releases][proj-releases].

Download the binary for your platform, then install it in a directory on your `PATH` with `0755` permissions:

```sh
# user-home-bin install (preferred)
install -m 755 /path/to/aws-ssm-params-linux-x86_64 ~/bin/aws-ssm-params

# system wide install
sudo install -m 755 /path/to/aws-ssm-params-linux-x86_64 /usr/local/bin/aws-ssm-params

# check whether it's working
aws-ssm-params --version
```

### Container image

The project publishes the `gracnar/aws-ssm-params` image on [Docker Hub][proj-dockerhub]. You can run it
directly or copy the binary into your own image:

```Dockerfile
FROM gracnar/aws-ssm-params:<version|latest> AS asp

FROM your-base-img

# copy aws-ssm-params from `asp`
COPY    --from=asp     /usr/local/bin/aws-ssm-params /usr/local/bin

# rest of your dockerfile goes here
```

To run the container directly:

```
docker run -it --rm  gracnar/aws-ssm-params --help
```

### Build from source

To build the project yourself, follow the [local development instructions](#building).

# Usage


You can use the tool in several ways:

- In a `ENTRYPOINT` script to set environment variables before starting your application:
```
eval $(aws-ssm-params /common,/my-app/common,/my-app/dev)
```
- To display all AWS SSM parameters with recursion:
```
aws-ssm-params -r /common,/my-app/common,/my-app/dev
```
- To load AWS SSM parameters into the current shell:
```
eval $(aws-ssm-params /common,/my-app/common,/my-app/dev)
```
- To generate a [.env file][dc-env] for your [Docker Compose][dc] development setup:
```
aws-ssm-params /common,/my-app/common,/my-app/dev -f dotenv > .env.local
aws-ssm-params /common /my-app/common /my-app/dev -f dotenv > .env.local
```

### Suggested AWS SSM parameter structure

<details>

```
# common parameters for all applications
/common
  |
  +--> PARAM_A
  +--> PARAM_B
  |
  | # settings root for application `my-app`
  +--> /my-app
  |       |
  |       | # common `my-app` settings for all environments
  |       +--> /my-app/common
  |       |       |
  |       |       +--> APP_FOO
  |       |
  |       | # `dev` environment settings for `my-app`
  |       +---> /my-app/dev
  |       |         |
  |       |         +--> APP_BAR
  |       |         +--> APP_BAZ
  |       |
  |       | # `staging` environment settings for `my-app`
  |       +---> /my-app/dev
  |       |         |
  |       |         +--> APP_BAR
  |       |         +--> APP_BAZ
  |       |
  |       | # `prod` environment settings for `my-app`
  |       +---> /my-app/dev
  |                |
  |                +--> APP_FOO
  |                +--> APP_BAR
  |                +--> APP_BAZ
  | # settings root for application `some-other-app`
  +--> /some-other-app
```

</details>

The structure above allows parameter inheritance and override in a scalable way; the correct command to utilize such
parameter hierarchy for `my-app` is:

```
aws-ssm-params /common,/my-app/common,/my-app/dev
```

## CLI

The tool uses standard AWS SDK credentials and region settings, such as `AWS_PROFILE` and `AWS_REGION`.
You can also set `AWS_ENDPOINT` to use a custom SSM endpoint, such as `http://localhost:4566/` for a
[LocalStack][localstack] SSM instance.

### env (default sub-command)

Fetches AWS SSM parameters from the specified paths and formats them for:

- a shell (`-f exports`)
- a [Docker Compose environment file][dc-envfile] (`-f dotenv`)

**Note:** `env` is the default subcommand, so you can omit it.

```
Usage: aws-ssm-params env [-hlrRstuvV] [--full-version] [--endpoint=<endpoint>] [-f=<outputFormat>] [-L=<logLevel>]
                          [--region=<region>] [PATH...]
Fetches and outputs AWS SSM parameters for shell evaluation [default command]
      [PATH...]           One or more AWS SSM parameter store paths to fetch parameters from; each path is also being
                            split by the comma (',') character, so multiple paths can be specified with a single
                            argument; changing order of paths affects the output.
                            Default: []
OPTIONS:
      --endpoint=<endpoint>
                          AWS endpoint (optional, useful for testing)
                            Default:
      --region=<region>   AWS region (optional, useful for testing)
                            Default:
  -v, --verbose           Verbose execution
  -L, --log-level=<logLevel>
                          Logger level (off/warn/info/debug/trace)
      --full-version      Display detailed application version
  -r, --[no-]recursive    Recursively fetch parameters from sub-paths
                            Default: false
  -s, --[no-]sort         Sort fetched parameters by name in output?
                            Default: true
  -R, --reverse-sort      Sort fetched parameters by name in reverse order?
                            Default: false
  -l, --[no-]use-last     Use last parameter with distinct name instead of the first one? Useful when same parameter
                            name is contained in multiple paths
                            Default: true
  -u, --[no-]uppercase    Uppercase parameter names?
                            Default: true
  -t, --[no-]trim         Trim fetched AWS SSM parameter values?
                            Default: true
  -f, -F, --format=<outputFormat>
                          Output format (possible values: exports, dotenv)
                            Default: exports
  -h, --help              Show this help message and exit.
  -V, --version           Print version information and exit.


ENVIRONMENT VARIABLES:

The behaviour of this tool can be altered by setting the following environment variables:

  SSM_PATHS        :: (csv string) comma separated list of AWS SSM parameter paths to fetch
  SSM_RECURSIVE    :: (bool) recursively fetch parameters from sub-paths?
  SSM_SORT         :: (bool) sort fetched parameters by name?
  SSM_SORT_REVERSE :: (bool) sort fetched parameters by name in reverse order
  SSM_USE_LAST     :: (bool) use last fetched value?
  SSM_UPPERCASE    :: (bool) uppercase parameter names?
  SSM_TRIM         :: (bool) trim fetched parameter values?
  SSM_FORMAT       :: (string) output format (example: exports, dotenv)
  AWS_ENV_PATH     :: alias for SSM_PATHS

EXAMPLES:

# fetch parameters from AWS SSM paths /dev/common, /dev/my-app, /dev/my-app/special
# without recursive querying
  aws-ssm-params /dev/common /dev/my-app /dev/my-app/special

# fetch parameters from AWS SSM paths /dev/common, /dev/my-app, recursively, taking first parameter if there
# is pa parameter with the same name  in subsequential parameter paths
  aws-ssm-params -r /dev/common /dev/my-app

# fetch parameters from AWS SSM paths /dev/common, /dev/my-app, recursively, taking last parameter if there
# is pa parameter with the same name  in subsequential parameter paths
  aws-ssm-params -l -r /dev/common /dev/my-app

# completely env variable driven invocation, useful for ENTRYPOINT integration
  SSM_PATHS="/dev/common,/dev/my-app" SSM_RECURSIVE=1 SSM_USE_LAST=1 aws-ssm-params
```

Sample output for
- **exports** format (`-f exports`)
```
export 'API_KEY=gamma-api_key'
export 'DISPLAY_NAME=Alpha Display Name'
export 'DUPLICATE=beta-duplicate'
```
- **dotenv** format (`-f dotenv`)
```
API_KEY="gamma-api_key"
DISPLAY_NAME="Alpha Display Name"
DUPLICATE="beta-duplicate"
```

### ls

Lists AWS SSM parameters at the specified paths; [glob(7)][glob] patterns are supported in the last path segment.

```
Usage: aws-ssm-params ls [-hrvV] [--full-version] [--local] [--endpoint=<endpoint>] [-L=<logLevel>] [--region=<region>]
                         PATH...
Lists AWS SSM parameters
      PATH...             One or more parameter paths or glob patterns

OPTIONS:
      --endpoint=<endpoint>
                          AWS endpoint (optional, useful for testing)
                            Default:
      --region=<region>   AWS region (optional, useful for testing)
                            Default:
      --local             Use locally running localstack AWS SSM instance instead of real AWS SSM endpoint?
  -v, --verbose           Verbose execution
  -L, --log-level=<logLevel>
                          Logger level (off/warn/info/debug/trace)
      --full-version      Display detailed application version
  -r, --recursive         List parameters in sub-paths
  -h, --help              Show this help message and exit.
  -V, --version           Print version information and exit.
```

### add

Creates AWS SSM parameters from one or more dotenv files using `key=value` or `key="value"` lines;
blank lines and lines starting with `#` are ignored. Each variable becomes a parameter under `PATH`.
If no files are given, `add` reads dotenv content from standard input. Parameters are created as `String`
unless `--list` selects `StringList` or `-s/--secure` selects `SecureString`. Existing parameters are not
overwritten unless `-f/--force` is given.

```
Usage: aws-ssm-params add [-fhsvV] [--full-version] [--list] [--endpoint=<endpoint>] [-L=<logLevel>]
                          [--region=<region>] PATH [FILE...]
Creates AWS SSM parameters from dotenv files
      PATH                AWS SSM Parameter Store path to add parameters into
      [FILE...]           Dotenv files (stdin if omitted or "-")

OPTIONS:
      --endpoint=<endpoint>
                          AWS endpoint (optional, useful for testing)
                            Default:
      --region=<region>   AWS region (optional, useful for testing)
                            Default:
  -v, --verbose           Verbose execution
  -L, --log-level=<logLevel>
                          Logger level (off/warn/info/debug/trace)
      --full-version      Display detailed application version
  -f, --force             Overwrite existing parameters
      --list              Create parameters of StringList type
  -s, --secure            Create parameters of SecureString type
  -h, --help              Show this help message and exit.
  -V, --version           Print version information and exit.
```

## cp

Copies one or more AWS SSM parameters.

```
Usage: aws-ssm-params cp [-fhrvVy] [--full-version] [--local] [--endpoint=<endpoint>] [-L=<logLevel>]
                         [--region=<region>] SOURCE DESTINATION
Copies one or more AWS SSM parameters
      SOURCE              Parameter name or basename glob pattern
      DESTINATION         Destination directory

OPTIONS:
      --endpoint=<endpoint>
                          AWS endpoint (optional, useful for testing)
                            Default:
      --region=<region>   AWS region (optional, useful for testing)
                            Default:
      --local             Use locally running localstack AWS SSM instance instead of real AWS SSM endpoint?
  -v, --verbose           Verbose execution
  -L, --log-level=<logLevel>
                          Logger level (off/warn/info/debug/trace)
      --full-version      Display detailed application version
  -r, --recursive         Include matching parameters in sub-paths
  -f, --force             Overwrite existing parameters
  -y, --do-it             Actually perform potentially destructive operations
  -h, --help              Show this help message and exit.
  -V, --version           Print version information and exit.
```

## mv

Moves one or more AWS SSM parameters to a destination directory. The command shows a dry run by default;
use `-y/--do-it` to copy each parameter and then remove its source. It accepts the same options as `cp`,
including `-r/--recursive` for matching parameters in sub-paths and `-f/--force` to overwrite existing destinations.

```
Usage: aws-ssm-params mv [-fhrvVy] [--full-version] [--local] [--endpoint=<endpoint>] [-L=<logLevel>]
                         [--region=<region>] SOURCE DESTINATION
Moves one or more AWS SSM parameters
      SOURCE              Parameter name or basename glob pattern
      DESTINATION         Destination directory

OPTIONS:
      --endpoint=<endpoint>
                          AWS endpoint (optional, useful for testing)
                            Default:
      --region=<region>   AWS region (optional, useful for testing)
                            Default:
      --local             Use locally running localstack AWS SSM instance instead of real AWS SSM endpoint?
  -v, --verbose           Verbose execution
  -L, --log-level=<logLevel>
                          Logger level (off/warn/info/debug/trace)
      --full-version      Display detailed application version
  -r, --recursive         Include matching parameters in sub-paths
  -f, --force             Overwrite existing parameters
  -y, --do-it             Actually perform potentially destructive operations
  -h, --help              Show this help message and exit.
  -V, --version           Print version information and exit.
```

### rm

Removes one or more named AWS SSM parameters. A [glob(7)][glob] pattern in the last path segment
selects multiple parameters; quote patterns in the shell. Use `-r/--recursive` with a glob to include
matching parameters in sub-paths. Recursive removal requires a glob pattern.

```
Usage: aws-ssm-params rm [-hrvVy] [--full-version] [--endpoint=<endpoint>] [-L=<logLevel>] [--region=<region>] PATH...
Removes AWS SSM parameters
      PATH...             One or more parameter paths (or glob patterns) to remove

OPTIONS:
      --endpoint=<endpoint>
                          AWS endpoint (optional, useful for testing)
                            Default:
      --region=<region>   AWS region (optional, useful for testing)
                            Default:
  -v, --verbose           Verbose execution
  -L, --log-level=<logLevel>
                          Logger level (off/warn/info/debug/trace)
      --full-version      Display detailed application version
  -r, --recursive         Include parameters in sub-paths when using a glob
  -y, --do-it             Actually remove the parameters
  -h, --help              Show this help message and exit.
  -V, --version           Print version information and exit.
```

## Local development

The project is written in [Java][java] and compiled into a **native binary** with
[GraalVM Native Image][graalvm-native-image]. A fully statically linked binary is currently available
only for Linux x86_64.

### Building

You need [GraalVM][graalvm] to compile this project into a native binary.

```sh
# just build the binary
./gradlew nativeCompile
```

The executable is written to `build/native/nativeCompile/`.

### Tests

The integration tests use a [LocalStack][localstack] container with only SSM enabled. Docker Compose
runs the initialization script to create plaintext and encrypted parameters in three paths.

```sh
docker compose up -d --wait
./gradlew test
```

[proj-dockerhub]: https://hub.docker.com/r/gracnar/aws-ssm-params
[proj-releases]: https://github.com/bfg/aws-ssm-params/releases

[java]: https://www.java.com/
[graalvm]: https://www.graalvm.org/downloads/
[graalvm-native-image]: https://www.graalvm.org/jdk25/reference-manual/native-image/
[aws-ssm-param-store]: https://docs.aws.amazon.com/systems-manager/latest/userguide/systems-manager-parameter-store.html
[dc]: https://docs.docker.com/compose/
[dc-env]: https://docs.docker.com/compose/how-tos/environment-variables/set-environment-variables/#use-the-env_file-attribute
[dc-envfile]: https://docs.docker.com/compose/how-tos/environment-variables/set-environment-variables/#use-the-env_file-attribute
[glob]: https://man7.org/linux/man-pages/man7/glob.7.html
[localstack]: https://github.com/localstack/localstack
[aws-env]: https://github.com/droplr/aws-env
[ssmtui]: https://github.com/suraj16thjan/ssmtui
