FROM ubuntu:26.04

ARG TARGETARCH
COPY --chmod=755 ${TARGETARCH}/aws-ssm-params-env /usr/local/bin/aws-ssm-params-env

ENTRYPOINT ["/usr/local/bin/aws-ssm-params-env"]
