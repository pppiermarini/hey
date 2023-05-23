#!/bin/bash

#Authors: SCM
#Description: Run a telnet for multiple hosts
#Date: 3/5/2020


test=$(echo exit | telnet $1 $2 2>/dev/null | grep "Connected")

if [ "$test" == "" ]
then
    echo "From $HOSTNAME to $1 the Port $2 is not open"
else
    echo "From $HOSTNAME to $1 the Port $2 is open"
fi