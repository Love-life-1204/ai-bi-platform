#!/usr/bin/env bash
set -euo pipefail

# Idempotent remote bootstrap. Upload this file with the compose/config/.env files.

cd /opt/ai-bi/rabbitmq
set -a
. ./.env
set +a

install -d -m 700 rabbitmq/tls
if [ ! -s rabbitmq/tls/ca.pem ]; then
  openssl genrsa -out rabbitmq/tls/ca-key.pem 4096
  openssl req -x509 -new -nodes -key rabbitmq/tls/ca-key.pem -sha256 -days 1825 \
    -subj '/CN=AI BI RabbitMQ Local CA' -out rabbitmq/tls/ca.pem
  openssl genrsa -out rabbitmq/tls/server-key.pem 3072
  openssl req -new -key rabbitmq/tls/server-key.pem \
    -subj '/CN=localhost' -out rabbitmq/tls/server.csr
  printf '%s\n' \
    'subjectAltName=DNS:localhost,DNS:rabbitmq,DNS:ai-bi-rabbitmq,IP:127.0.0.1' \
    'extendedKeyUsage=serverAuth' > rabbitmq/tls/server.ext
  openssl x509 -req -in rabbitmq/tls/server.csr \
    -CA rabbitmq/tls/ca.pem -CAkey rabbitmq/tls/ca-key.pem -CAcreateserial \
    -out rabbitmq/tls/server.pem -days 825 -sha256 -extfile rabbitmq/tls/server.ext
  rm -f rabbitmq/tls/server.csr rabbitmq/tls/server.ext
fi
chmod 600 rabbitmq/tls/*-key.pem
chmod 644 rabbitmq/tls/ca.pem rabbitmq/tls/server.pem
chown root:999 rabbitmq/tls
chmod 750 rabbitmq/tls
chown 999:999 rabbitmq/tls/server-key.pem

systemctl enable docker >/dev/null
image='rabbitmq:4.2.9-management'
if ! docker image inspect "$image" >/dev/null 2>&1 && ! docker pull "$image"; then
  pulled=false
  for mirror in \
    docker.m.daocloud.io/library/rabbitmq:4.2.9-management \
    docker.1ms.run/library/rabbitmq:4.2.9-management; do
    if docker pull "$mirror"; then
      docker tag "$mirror" "$image"
      pulled=true
      break
    fi
  done
  if [ "$pulled" != true ]; then
    echo 'Unable to pull the pinned RabbitMQ image from Docker Hub or either fallback mirror.' >&2
    exit 1
  fi
fi
if [ "$(docker network inspect ai-bi-messaging \
    --format '{{.Internal}}' 2>/dev/null || true)" = true ]; then
  docker compose --env-file .env -f compose.yaml down
  docker network rm ai-bi-messaging >/dev/null 2>&1 || true
fi
docker compose --env-file .env -f compose.yaml up -d --pull never

for attempt in $(seq 1 30); do
  health=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' \
    ai-bi-rabbitmq 2>/dev/null || true)
  if [ "$health" = healthy ]; then
    break
  fi
  if [ "$attempt" = 30 ]; then
    docker logs --tail 200 ai-bi-rabbitmq
    exit 1
  fi
  sleep 3
done

if docker exec ai-bi-rabbitmq rabbitmqctl list_users -q \
    | awk '{print $1}' | grep -Fxq "$RABBITMQ_APP_USER"; then
  docker exec ai-bi-rabbitmq rabbitmqctl change_password \
    "$RABBITMQ_APP_USER" "$RABBITMQ_APP_PASSWORD" >/dev/null
else
  docker exec ai-bi-rabbitmq rabbitmqctl add_user \
    "$RABBITMQ_APP_USER" "$RABBITMQ_APP_PASSWORD" >/dev/null
fi
docker exec ai-bi-rabbitmq rabbitmqctl set_user_tags "$RABBITMQ_APP_USER" >/dev/null
docker exec ai-bi-rabbitmq rabbitmqctl set_permissions -p "$RABBITMQ_VHOST" \
  "$RABBITMQ_APP_USER" '^aibi\..*$' '^(aibi\.tasks|aibi\.dlx)$' \
  '^aibi\.(sync|import|export|ai)\.(q|dlq)$' >/dev/null
docker exec ai-bi-rabbitmq rabbitmqctl delete_user guest >/dev/null 2>&1 || true

admin=(docker exec ai-bi-rabbitmq rabbitmqadmin --vhost "$RABBITMQ_VHOST" \
  --username "$RABBITMQ_ADMIN_USER" --password "$RABBITMQ_ADMIN_PASSWORD")
"${admin[@]}" declare exchange --name aibi.tasks --type topic \
  --durable true --auto-delete false >/dev/null
"${admin[@]}" declare exchange --name aibi.dlx --type topic \
  --durable true --auto-delete false >/dev/null

for kind in sync import export ai; do
  main_args=$(printf '{"x-delivery-limit":5,"x-dead-letter-exchange":"aibi.dlx","x-dead-letter-routing-key":"dead.%s"}' "$kind")
  "${admin[@]}" declare queue --name "aibi.${kind}.q" --type quorum \
    --durable true --auto-delete false --arguments "$main_args" >/dev/null
  "${admin[@]}" declare queue --name "aibi.${kind}.dlq" --type quorum \
    --durable true --auto-delete false >/dev/null
  "${admin[@]}" declare binding --source aibi.tasks --destination-type queue \
    --destination "aibi.${kind}.q" --routing-key "task.${kind}" >/dev/null
  "${admin[@]}" declare binding --source aibi.dlx --destination-type queue \
    --destination "aibi.${kind}.dlq" --routing-key "dead.${kind}" >/dev/null
done

echo '--- RABBITMQ STATUS ---'
docker exec ai-bi-rabbitmq rabbitmq-diagnostics -q ping
docker exec ai-bi-rabbitmq rabbitmq-diagnostics -q server_version
docker exec ai-bi-rabbitmq rabbitmq-diagnostics -q listeners
echo '--- USERS/PERMISSIONS ---'
docker exec ai-bi-rabbitmq rabbitmqctl list_users -q
docker exec ai-bi-rabbitmq rabbitmqctl list_permissions -p "$RABBITMQ_VHOST" -q
echo '--- QUEUES ---'
docker exec ai-bi-rabbitmq rabbitmqctl list_queues -p "$RABBITMQ_VHOST" \
  name type durable messages consumers state -q
echo '--- CONTAINER ---'
docker inspect --format 'image={{.Config.Image}} status={{.State.Status}} health={{.State.Health.Status}} restart={{.HostConfig.RestartPolicy.Name}} ports={{json .HostConfig.PortBindings}}' ai-bi-rabbitmq
docker image inspect rabbitmq:4.2.9-management --format 'digest={{index .RepoDigests 0}} id={{.Id}}'
