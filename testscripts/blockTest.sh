for i in $(seq 1 36); do curl -s localhost:8080/slow-block > /dev/null & done
echo "slow-block requests start..."
time curl -s localhost:8080/ping
