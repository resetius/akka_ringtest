#!/bin/bash

N=${1:-10}  # Number of nodes
BASE_PORT=2001

sbt assembly

NODE_ARGS=""
for ((i=1; i<=N; ++i)); do
  NODE_ARGS+=" --node localhost:$((BASE_PORT + i - 1)):$i"
done
CMD="java -cp `echo target/scala-2.13/* | tr ' ' ':'` DistributedMain"

pkill -9 -f java

ulimit -c unlimited

# Start all nodes in background
echo $NODE_ARGS
for ((i=2; i<=N; ++i)); do
  echo "Starting node $i in background"
  $CMD --node-id $i $NODE_ARGS > ping_node_$i.log 2>&1 &
done

# Run first node in foreground
echo "Starting node 1 in foreground"
$CMD --node-id 1 $NODE_ARGS
