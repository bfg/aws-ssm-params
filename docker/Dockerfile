FROM ubuntu:26.04

ARG TARGETARCH
COPY --chmod=755 ${TARGETARCH}/aws-ssm-params /usr/local/bin/aws-ssm-params

ENTRYPOINT ["/usr/local/bin/aws-ssm-params"]
