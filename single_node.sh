#!/bin/bash

sbt assembly

CMD="java -cp `echo target/scala-2.13/* | tr ' ' ':'` Main"

pkill -9 -f java

ulimit -c unlimited

echo "Starting node 1 in foreground"
echo $CMD
$CMD --actors 100 --messages 1000000 --batch 1024
