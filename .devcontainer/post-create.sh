#!/bin/bash
set -ex

# Set ownership of the home directory
sudo chown -R vscode:vscode /home/vscode

# Add ll alias (ls -al)
echo 'alias ll="ls -al"' >> ~/.bashrc
echo 'alias mvn=mvnd' >> ~/.bashrc


# SNAPSHOT dependency is not on Maven Central; install it (and its parent) locally
rm -rf /tmp/cardano-java-lib
git clone --depth 1 "https://github.com/wutzebaer/cardano-java-lib.git" /tmp/cardano-java-lib
./mvnw -f /tmp/cardano-java-lib/pom.xml install -DskipTests

# Clean the application
./mvnw clean compile
