#!/bin/bash

ACTORS=${1:-100}
MESSAGES=${2:-1000000}
INFL=${3:-2}
BASE_PORT=2001

sbt assembly

CMD="java -cp `echo target/scala-2.13/* | tr ' ' ':'` Main"

pkill -9 -f java

# Run first node in foreground
echo "Starting node 1 in foreground"
$CMD --batch $INFL --actors $ACTORS --messages $MESSAGES
