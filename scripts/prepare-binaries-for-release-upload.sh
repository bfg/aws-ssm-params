#!/usr/bin/env bash

artifacts_dir=${1:-"built-artifacts"}
release_dir=${2:-"release-binaries"}
tool="aws-ssm-params-env"

set -e

for dir in "${artifacts_dir}"/*; do
  [ -d "$dir" ] || continue
  platform="$(basename "$dir" | sed -e "s|${tool}-||g")"
  [ -z "$platform" ] && continue

  src_file="$dir/$tool"
  [ -s "$src_file" ] && [ -r "$src_file" ] || continue

  mkdir -p "$release_dir"
  dst_file="${release_dir}/${tool}-${platform}"

  echo "[$platform] => $src_file -> $dst_file"
  cp "${src_file}" "${dst_file}"
  chmod 0755 "${dst_file}"
done
