#!/bin/bash
set -ex

# Set ownership of the home directory
sudo chown -R vscode:vscode /home/vscode

# Add ll alias (ls -al)
echo 'alias ll="ls -al"' >> ~/.bashrc
echo 'alias mvn=mvnd' >> ~/.bashrc

install_from_github() {
  set -e
  local repo="$1"
  local tmp
  tmp=$(mktemp -d)
  git clone --depth 1 "https://github.com/${repo}.git" "$tmp"
  ./mvnw -f "$tmp/pom.xml" install -DskipTests
  rm -rf "$tmp"
}

# SNAPSHOT dependency is not on Maven Central; install it (and its parent) locally
install_from_github wutzebaer/peterspace-springboot-parent
install_from_github wutzebaer/cardano-java-lib

# Clean the application
./mvnw clean compile
