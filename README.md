# aws-ssm-params-env - a secure way to expose AWS SSM Parameter Store parameters as environment variables

`aws-ssm-params-env` is a small CLI tool that securely exposes parameters and secrets stored in one or more
[AWS Systems Manager Parameter Store][aws-ssm-param-store] paths (recursion supported) and outputs them in
a format that can be used by shell to export environment variables before starting a real
application or to construct [docker compose .env][dc-env].

This project was inspired by [aws-env].

# Features

- ability to query multiple AWS SSM parameter store paths
- recursive parameter fetching
- ability to handle parameter store values containing special characters and shell escapes
- support for all AWS SSM parameter types (including `SecureString`)
- docker image for easy inclusion into your app's container image

# Usage

You can use this tool in many ways:

- as part of docker `ENTRYPOINT` script to populate environment variables before starting actual application
```
eval $(aws-ssm-params-env)
```
- to display AWS SSM parameters in a terse way
```
aws-ssm-params-env -r /dev/common /dev/my-app
```
- to import aws ssm parameter store parameters into current shell
```
eval $(aws-ssm-params-env -r /dev/common /dev/my-app)
```
- to generate [.env][dc-env] file for your [docker compose][dc] development setup.
```
aws-ssm-params-env -r /dev/common /dev/my-app -f dotenv > .env.local
```

## Docker

This project also publishes container image `gracnar/aws-ssm-params-env` on [Docker Hub][proj-dockerhub]

```Dockerfile
FROM gracnar/aws-ssm-params-env:<version> AS aspe

FROM your-base-img

# copy aws-ssm-params-env
COPY    --from=aspe     /usr/local/bin/aws-ssm-params-env /usr/local/bin
# aws-ssm-params-env now it can be used as part of a entrypoint script
```

## CLI

The AWS SDK uses its standard credential and region configuration, such as `AWS_PROFILE` and `AWS_REGION`.

```
Usage: aws-ssm-params-env [-hlrRstuV] [--full-version] [--endpoint=<endpoint>] [-f=<outputFormat>] [-L=<logLevel>]
                          [--region=<region>] [PATH...]

Exports parameters from AWS SSM parameter store to environment variables or envfile.

      [PATH...]           One or more AWS SSM parameter store paths to fetch parameters from
                            Default: []
  -r, --[no-]recursive    Recursively fetch parameters from sub-paths
                            Default: false
  -s, --[no-]sort         Sort fetched parameters by name in output?
                            Default: true
  -R, --reverse-sort      Sort fetched parameters by name in reverse order?
                            Default: false
  -l, --[no-]use-last     Use last parameter with distinct name instead of the first one? Useful when same parameter
                            name is contained in multiple paths
                            Default: false
  -u, --[no-]uppercase    Uppercase parameter names?
                            Default: true
  -t, --[no-]trim         Trim fetched AWS SSM parameter values?
                            Default: true
  -f, -F, --format=<outputFormat>
                          Output format (possible values: exports, dotenv)
                            Default: exports
      --endpoint=<endpoint>
                          AWS endpoint (optional, useful for testing)
                            Default:
      --region=<region>   AWS region (optional, useful for testing)
                            Default:
  -L, --log-level=<logLevel>
                          Logger level (off/warn/info/debug/trace)
      --full-version      Display detailed application version
  -h, --help              Show this help message and exit.
  -V, --version           Print version information and exit.


ENVIRONMENT VARIABLES

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
  aws-ssm-params-env /dev/common /dev/my-app /dev/my-app/special

# fetch parameters from AWS SSM paths /dev/common, /dev/my-app, recursively, taking first parameter if there
# is pa parameter with the same name  in subsequential parameter paths
  aws-ssm-params-env -r /dev/common /dev/my-app

# fetch parameters from AWS SSM paths /dev/common, /dev/my-app, recursively, taking last parameter if there
# is pa parameter with the same name  in subsequential parameter paths
  aws-ssm-params-env -l -r /dev/common /dev/my-app

# completely env variable driven invocation, useful for ENTRYPOINT integration
  SSM_PATHS="/dev/common,/dev/my-app" SSM_RECURSIVE=1 SSM_USE_LAST=1 aws-ssm-params-env
```

## Local development

### Building

You need [GraalVM][graalvm] to compile this project into a native binary.

```sh
./gradlew nativeCompile
```

The executable is written to `build/native/nativeCompile/`.

### Tests

The integration tests use a LocalStack container with only SSM enabled. Docker Compose
runs the initialization script to create plaintext and encrypted parameters in three paths.

```sh
docker compose up -d --wait
./gradlew test
```

[graalvm]: https://www.graalvm.org/downloads/
[aws-ssm-param-store]: https://docs.aws.amazon.com/systems-manager/latest/userguide/systems-manager-parameter-store.html
[dc]: https://docs.docker.com/compose/
[dc-env]: https://docs.docker.com/compose/how-tos/environment-variables/set-environment-variables/#use-the-env_file-attribute
[aws-env]: https://github.com/droplr/aws-env
[proj-dockerhub]: https://hub.docker.com/r/gracnar/aws-ssm-params-env
