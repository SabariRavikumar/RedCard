for i in $(seq 1 36); do curl -s localhost:8080/slow-mono > /dev/null & done
echo "slow-mono requests start..."
time curl -s localhost:8080/ping
