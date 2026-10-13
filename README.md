# AWS SSM Params Env

A small CLI tool that securely exposes parameters and secrets stored in one or more
[AWS Systems Manager Parameter Store][aws-ssm-param-store] paths (recursion supported) and outputs them in
a format that can be used by shell to export environment variables before starting a real
application or to construct [docker compose .env][dc-env].

## Build

You need [GraalVM][graalvm] to compile this project.

```sh
./gradlew nativeCompile
```

The executable is written to `build/native/nativeCompile/`.

## Use

The AWS SDK uses its standard credential and region configuration, such as `AWS_PROFILE` and `AWS_REGION`.

```
Usage: aws-ssm-params-env [-hlrRstuV] [--endpoint=<endpoint>]
                          [-F=<outputFormat>] [-L=<logLevel>]
                          [--region=<region>] [PATH...]
Fetches AWS SSM parameter store parameters and formats the output
      [PATH...]           one or more SSM parameter store path(s) to fetch
                            parameters from  (default: [])
                            Default: []
  -r, --[no-]recursive    recursively fetch parameters from sub-paths
  -s, --[no-]sort         sort fetched parameters by name in output?
  -R, --reverse-sort      sort fetched parameters by name in reverse order?
  -l, --last-one-wins     use last parameter with distinct name will be emitted
                            instead of the first one; useful when same
                            parameter name is contained in multiple paths
  -u, --[no-]uppercase    uppercase parameter names?
  -t, --[no-]trim         trim fetched AWS SSM parameter values?
  -F, --format=<outputFormat>
                          output format, possible values: (exports, dotenv)
                            Default: exports
      --endpoint=<endpoint>
                          AWS endpoint (optional, useful for testing)
                            Default:
      --region=<region>   AWS region (optional, useful for testing)
                            Default:
  -L, --log-level=<logLevel>
                          logger level (off/warn/info/debug/trace)
                            Default:
  -h, --help              Show this help message and exit.
  -V, --version           Print version information and exit.


ENVIRONMENT

  SSM_PATHS        :: comma separated list of AWS SSM parameter paths to fetch
  SSM_RECURSIVE    :: recursively fetch parameters from sub-paths
  SSM_SORT         :: sort fetched parameters by name
  SSM_SORT_REVERSE :: sort fetched parameters by name in reverse order
  SSM_USE_LAST     :: use last fetched value
  SSM_UPPERCASE    :: uppercase
  SSM_TRIM         :: comma separated list of AWS SSM parameter paths to fetch
  SSM_FORMAT       :: comma separated list of AWS SSM parameter paths to fetch
  AWS_ENV_PATH     :: alias for SSM_PATHS

EXAMPLES:

# fetch parameters from AWS SSM paths /foo, /bar, /baz/kaz
  aws-ssm-params-env /foo /bar /baz/kaz

# fetch parameters from AWS SSM paths /foo, /bar, /baz/kaz
  SSM_PATHS="/foo, /bar, /baz/kaz"  aws-ssm-params-env
  AWS_ENV_PATH="/foo, /bar, /baz/kaz"  aws-ssm-params-env
```

[aws-ssm-param-store]: https://docs.aws.amazon.com/systems-manager/latest/userguide/systems-manager-parameter-store.html
[dc-env]: https://docs.docker.com/compose/how-tos/environment-variables/set-environment-variables/#use-the-env_file-attribute