#!/bin/bash
set -e

# If no external DB_URL is provided, spin up internal MariaDB service
if [ -z "$DB_URL" ]; then
    echo "[Entrypoint] Starting local MariaDB service..."
    service mariadb start

    # Initialize fuel_delivery_db if it doesn't already exist
    if ! mariadb -u root -e "USE fuel_delivery_db;" 2>/dev/null; then
        echo "[Entrypoint] Initializing database from fuel_delivery.sql..."
        mariadb -u root < /app/fuel_delivery.sql
        mariadb -u root -e "ALTER USER 'root'@'localhost' IDENTIFIED VIA mysql_native_password USING PASSWORD('root@123'); FLUSH PRIVILEGES;" 2>/dev/null || true
    fi

    export DB_URL="jdbc:mysql://localhost:3306/fuel_delivery_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata"
    export DB_USER="root"
    export DB_PASSWORD="root@123"
    echo "[Entrypoint] Local database configured successfully."
else
    echo "[Entrypoint] Using external database: $DB_URL"
fi

# Configure Tomcat HTTP port from cloud environment (Render/Railway default to $PORT)
PORT="${PORT:-8080}"
echo "[Entrypoint] Configuring Tomcat port to $PORT..."
sed -i "s/port=\"8080\"/port=\"$PORT\"/" /usr/local/tomcat/conf/server.xml
sed -i "s/port=\"8005\"/port=\"-1\"/" /usr/local/tomcat/conf/server.xml

echo "[Entrypoint] Starting Apache Tomcat 10.1..."
exec catalina.sh run
