#!/bin/bash

# Read the contents of the configuration file into a variable
configPath=$1
root=$2
config=$(cat "$configPath")

# Remove comment lines and extract node hostnames
nodes=$(echo "$config" | grep -E '^[0-9]+$' | head -n 1)
hostnames=$(echo "$config" | grep -E '^[0-9]+\s+\w+\.\w+\.\w+\s+[0-9]+$' | cut -d ' ' -f 2)
hostname_array=($hostnames)
netID="jxp220032"

# Get the hostname of the current machine
host=$(hostname)

# Compile the Java program in the parent directory
cd ..
javac Main.java

#connect to all nodes using ssh.
for remotehost in "${hostname_array[@]}"
do
  # Skip the host machine
  if [[ "$remotehost" == "$host" ]]; then
    continue
  fi
  echo "Connecting to $remotehost ..."
  ssh -f $remotehost "echo 'Connection test successful'; exec bash"
done

echo "completed connection test"

# Loop through the nodes and execute a command over SSH
for remotehost in "${hostname_array[@]}"
do
  # Skip the host machine
  if [[ "$remotehost" == "$host" ]]; then
    java Main Launcher/$configPath $root &
    continue
  fi
  echo "Starting main in $remotehost ..."
  ssh -f $netID@$remotehost "cd DSProject3/LayeredBFS && java Main Launcher/$configPath $root" &
  sleep 1
done
