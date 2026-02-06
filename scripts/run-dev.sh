#!/usr/bin/env bash

set -e

if [ "$(basename "$PWD")" == "scripts" ]; then
  cd ..
fi

function start_ollama() {
    if ! pgrep -x "ollama" > /dev/null; then
    echo "Starting Ollama server..."
    tmux new-session -d -s Ollama "ollama serve"
    # wait for ollama to start
    sleep 5
    # curl ollama to check until it's running
    until curl -s http://localhost:11434/v1/models > /dev/null; do
      echo "Waiting for Ollama server to start..."
      sleep 2
    done
  else
    echo "Ollama server is already running."
  fi
}

start_ollama

# switch to java 21
JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export JAVA_HOME

docker compose up -d
./gradlew quarkusDev
