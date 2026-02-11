#!/usr/bin/env bash

set -e

if [ "$(basename "$PWD")" == "scripts" ]; then
  cd ..
fi

function start_ollama() {
  # Ollama runs on a service, no need to launch
  until curl -s http://localhost:11434/v1/models > /dev/null; do
    echo "Waiting for Ollama server to start..."
    sleep 2
  done
}

start_ollama

# Switch to Java 21 on Linux AMD64 and MacOS
if [[ "$OSTYPE" == "linux-gnu"* ]]; then
  export JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"
else
  JAVA_HOME="$(/usr/libexec/java_home -v 21)"
  export JAVA_HOME
fi

docker compose up -d
tmux new-session -d -s PessoaFaladora "PREVIEW_ONLY=\"$PREVIEW_ONLY\" ./gradlew quarkusDev"
