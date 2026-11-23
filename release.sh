#!/bin/bash
# Releases a new version: raises the version in pom.xml, tags the commit, builds the image and pushes it.
#
#   ./release.sh          1.2.3 -> 1.2.4, for fixes
#   ./release.sh minor    1.2.3 -> 1.3.0, for new endpoints and fields
#   ./release.sh major    1.2.3 -> 2.0.0, for changes that break clients
#   ./release.sh 3.0.0    exactly this version
#
# The image is built from the tagged commit, not from the working directory, so uncommitted changes
# are not part of it. It is pushed as wutzebaer/cardano-db-sync-api:1.2.4 and as :latest.
set -euo pipefail
cd "$(dirname "$0")"

IMAGE=wutzebaer/cardano-db-sync-api
PROJECT='<artifactId>cardano-db-sync-api<\/artifactId>'

if ! git diff --quiet HEAD -- pom.xml; then
  echo "Commit or revert your changes to pom.xml first" >&2
  exit 1
fi

# the project's own version is the line after its artifactId
current=$(sed -n "/$PROJECT/{n;s/.*<version>\(.*\)<\/version>.*/\1/p}" pom.xml)
IFS=. read -r major minor patch <<<"${current%%-*}"
case "${1:-patch}" in
  major) version="$((major + 1)).0.0" ;;
  minor) version="$major.$((minor + 1)).0" ;;
  patch) version="$major.$minor.$((patch + 1))" ;;
  *) version="$1" ;;
esac

sed -i "/$PROJECT/{n;s|<version>.*</version>|<version>$version</version>|}" pom.xml
git add pom.xml
git commit -q -m "release: v$version"
# annotated, because "git push --follow-tags" leaves lightweight tags behind
git tag -a "v$version" -m "v$version"

work=$(mktemp -d)
mkdir "$work/src"
git archive "v$version" | tar -x -C "$work/src"

build=(spring-boot:build-image -B -q "-Dspring-boot.build-image.imageName=$IMAGE:$version" -DskipTests=true)
if command -v java >/dev/null; then
  (cd "$work/src" && ./mvnw "${build[@]}")
  rm -rf "$work"
else
  # without Java on this machine Maven runs in a container; the volume keeps its downloads and has to
  # contain cardano-java-lib, which is on no public repository
  docker run --rm -v "$work/src":/src -v /var/run/docker.sock:/var/run/docker.sock -v api-release-m2:/root/.m2 \
    -w /src maven:3.9-eclipse-temurin-25 mvn "${build[@]}"
  # the container wrote its build output as root
  docker run --rm -v "$work":/work alpine rm -rf /work/src
  rmdir "$work"
fi

docker tag "$IMAGE:$version" "$IMAGE:latest"
docker push -q "$IMAGE:$version"
docker push -q "$IMAGE:latest"

echo "Released $IMAGE:$version"
echo "Publish the commit and its tag with: git push --follow-tags"
