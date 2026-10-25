#!/bin/sh
# Run on Debian only after reviewing network/configuration. No firewall or VM changes.
set -eu
[ "$(id -u)" -eq 0 ] || { echo "Ejecutar con sudo desde la cuenta administrativa" >&2; exit 1; }
[ "$#" -eq 1 ] || { echo "Uso: sudo sh install.sh /ruta/billeterax.jar" >&2; exit 1; }
jar=$(realpath "$1")
[ -f "$jar" ] || { echo "No existe el JAR" >&2; exit 1; }
base=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
java -version 2>&1 | head -1 | grep '"21\.' >/dev/null || { echo "Instalar OpenJDK 21 primero" >&2; exit 1; }
if ! id billeterax >/dev/null 2>&1; then
    useradd --system --home-dir /var/lib/billeterax --shell /usr/sbin/nologin billeterax
fi
install -d -o root -g billeterax -m 0750 /opt/billeterax /etc/billeterax
install -d -o billeterax -g billeterax -m 0750 /var/lib/billeterax
if [ ! -f /etc/billeterax/billeterax.env ]; then
    install -o root -g billeterax -m 0640 "$base/debian/billeterax.env.example" /etc/billeterax/billeterax.env
    echo "Configurar /etc/billeterax/billeterax.env y volver a ejecutar. Servicio no iniciado."
    exit 2
fi
if grep -q REPLACE /etc/billeterax/billeterax.env; then
    echo "Faltan valores de configuraciÃ³n en /etc/billeterax/billeterax.env" >&2; exit 2
fi
# Validate before stopping an existing deployment. Old JAR remains available for rollback.
if [ -f /opt/billeterax/billeterax.jar ]; then
    systemctl stop billeterax.service
    backup=$(mktemp -d /var/lib/billeterax/upgrade-backup.XXXXXXXX)
    chmod 0700 "$backup"
    cp -p /opt/billeterax/billeterax.jar "$backup/billeterax.jar"
    cp -p /etc/billeterax/billeterax.env "$backup/billeterax.env"
    if [ -f /var/lib/billeterax/wallet.mv.db ]; then
        cp -p /var/lib/billeterax/wallet.mv.db "$backup/wallet.mv.db"
    fi
    echo "Respaldo previo a la actualización: $backup"
    cp -p /opt/billeterax/billeterax.jar /opt/billeterax/billeterax.previous.jar
fi
install -o root -g billeterax -m 0640 "$jar" /opt/billeterax/billeterax.jar
install -o root -g root -m 0644 "$base/systemd/billeterax.service" /etc/systemd/system/billeterax.service
systemctl daemon-reload
systemctl enable --now billeterax.service
systemctl --no-pager status billeterax.service
