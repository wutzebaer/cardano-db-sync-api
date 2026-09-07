#!/bin/bash
set -e

# Set ownership of the home directory
sudo chown -R vscode:vscode /home/vscode

# Add ll alias (ls -al)
echo 'alias ll="ls -al"' >> ~/.bashrc
echo 'alias mvn=mvnd' >> ~/.bashrc

# Clean the application
./mvnw clean compile