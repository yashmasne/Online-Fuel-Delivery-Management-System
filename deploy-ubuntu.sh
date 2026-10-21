#!/usr/bin/env bash
# ==============================================================================
# Automated Production Deployment Script for Ubuntu 22.04 / 24.04 LTS
# Project: Online Fuel Delivery Management System
# Target Stack: OpenJDK 17 + Apache Tomcat 10.1 + MySQL 8 + Nginx Reverse Proxy
# ==============================================================================

set -euo pipefail

# Configuration defaults
TOMCAT_VERSION="10.1.34"
DB_NAME="fuel_delivery_db"
DB_USER="fuel_user"
DB_PASS="FuelPass@2026#"
TOMCAT_DIR="/opt/tomcat"
APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_FILE="${APP_DIR}/fuel_delivery.sql"
WAR_FILE="${APP_DIR}/Online_Fuel_Delivery_Management_System.war"

echo "=========================================================="
echo " Starting Fuel Delivery Management System Deployment..."
echo "=========================================================="

# 1. Update system packages
echo ">>> [1/7] Updating package index..."
sudo apt update -y
sudo apt install -y openjdk-17-jdk git curl wget unzip ufw nginx mysql-server

# 2. Configure MySQL Database
echo ">>> [2/7] Configuring MySQL Database..."
sudo systemctl enable --now mysql

# Create database and user
sudo mysql <<EOF
CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASS}';
GRANT ALL PRIVILEGES ON \`${DB_NAME}\`.* TO '${DB_USER}'@'localhost';
FLUSH PRIVILEGES;
EOF

# Import schema and sample seed data if SQL file exists
if [ -f "${SQL_FILE}" ]; then
    echo ">>> Importing SQL schema from ${SQL_FILE}..."
    sudo mysql -u "${DB_USER}" -p"${DB_PASS}" "${DB_NAME}" < "${SQL_FILE}" || {
        echo "Direct import returned a warning; trying with root..."
        sudo mysql "${DB_NAME}" < "${SQL_FILE}"
    }
    echo ">>> Database schema and seed data imported successfully!"
else
    echo "WARNING: ${SQL_FILE} not found! Please import the database schema manually."
fi

# 3. Install Apache Tomcat 10.1 (Jakarta EE 10)
echo ">>> [3/7] Setting up Apache Tomcat ${TOMCAT_VERSION}..."

if [ ! -d "${TOMCAT_DIR}" ]; then
    # Create tomcat system user and group
    if ! id -u tomcat >/dev/null 2>&1; then
        sudo useradd -m -U -d ${TOMCAT_DIR} -s /bin/false tomcat
    fi

    # Download Tomcat 10.1 archive
    TOMCAT_URL="https://archive.apache.org/dist/tomcat/tomcat-10/v${TOMCAT_VERSION}/bin/apache-tomcat-${TOMCAT_VERSION}.tar.gz"
    echo "Downloading Tomcat from ${TOMCAT_URL}..."
    wget -q --show-progress "${TOMCAT_URL}" -O /tmp/tomcat.tar.gz

    sudo mkdir -p ${TOMCAT_DIR}
    sudo tar -xf /tmp/tomcat.tar.gz -C ${TOMCAT_DIR} --strip-components=1
    rm -f /tmp/tomcat.tar.gz
fi

# Clean default demo webapps
sudo rm -rf ${TOMCAT_DIR}/webapps/*

# 4. Deploy Application as ROOT (serves from base URL /)
echo ">>> [4/7] Deploying WAR application..."
if [ -f "${WAR_FILE}" ]; then
    sudo cp "${WAR_FILE}" "${TOMCAT_DIR}/webapps/ROOT.war"
    echo "Copied ${WAR_FILE} to ${TOMCAT_DIR}/webapps/ROOT.war"
else
    echo "ERROR: ${WAR_FILE} not found! Please place the .war file in ${APP_DIR}."
    exit 1
fi

# 5. Configure Tomcat Environment Variables (DB Connection)
echo ">>> [5/7] Configuring Tomcat environment variables..."
sudo tee ${TOMCAT_DIR}/bin/setenv.sh > /dev/null <<EOF
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export DB_URL="jdbc:mysql://localhost:3306/${DB_NAME}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata"
export DB_USER="${DB_USER}"
export DB_PASSWORD="${DB_PASS}"
export CATALINA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC"
EOF

sudo chmod +x ${TOMCAT_DIR}/bin/setenv.sh
sudo chown -R tomcat:tomcat ${TOMCAT_DIR}
sudo chmod -R u+x ${TOMCAT_DIR}/bin

# Create Systemd Service for Tomcat
echo "Creating systemd unit for Tomcat..."
sudo tee /etc/systemd/system/tomcat.service > /dev/null <<EOF
[Unit]
Description=Apache Tomcat 10 Web Application Server
After=network.target mysql.service

[Service]
Type=forking
User=tomcat
Group=tomcat
Environment=JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
Environment=CATALINA_PID=${TOMCAT_DIR}/temp/tomcat.pid
Environment=CATALINA_HOME=${TOMCAT_DIR}
Environment=CATALINA_BASE=${TOMCAT_DIR}
ExecStart=${TOMCAT_DIR}/bin/startup.sh
ExecStop=${TOMCAT_DIR}/bin/shutdown.sh
RestartSec=10
Restart=always

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now tomcat

# 6. Configure Nginx Reverse Proxy (Port 80 -> Tomcat Port 8080)
echo ">>> [6/7] Setting up Nginx Reverse Proxy on Port 80..."
sudo tee /etc/nginx/sites-available/fuel-delivery > /dev/null <<'EOF'
server {
    listen 80;
    server_name _;

    client_max_body_size 50M;

    location / {
        proxy_pass http://127.0.0.1:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
EOF

sudo rm -f /etc/nginx/sites-enabled/default
sudo ln -sf /etc/nginx/sites-available/fuel-delivery /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl restart nginx
sudo systemctl enable nginx

# 7. Configure Firewall
echo ">>> [7/7] Configuring UFW Firewall..."
sudo ufw allow OpenSSH || true
sudo ufw allow 'Nginx Full' || true
sudo ufw allow 80/tcp || true
sudo ufw allow 8080/tcp || true
sudo ufw --force enable || true

# Summary
PUBLIC_IP=$(curl -s https://api.ipify.org || hostname -I | awk '{print $1}')
echo ""
echo "=========================================================="
echo " DEPLOYMENT COMPLETED SUCCESSFULLY!"
echo "=========================================================="
echo " - Web App URL:      http://${PUBLIC_IP}/"
echo " - Direct Tomcat:    http://${PUBLIC_IP}:8080/"
echo " - Admin Portal:     http://${PUBLIC_IP}/Auth_Pages.html"
echo "                     Default Login: admin@fuelexpress.in / admin123"
echo " - Customer Portal:  http://${PUBLIC_IP}/Home_Page.html"
echo ""
echo " Database Details:"
echo " - Database Name:    ${DB_NAME}"
echo " - Database User:    ${DB_USER}"
echo " - Database Pass:    ${DB_PASS}"
echo ""
echo " Service Controls:"
echo " - Check Tomcat:     sudo systemctl status tomcat"
echo " - Tomcat Logs:      sudo tail -f ${TOMCAT_DIR}/logs/catalina.out"
echo " - Restart Tomcat:   sudo systemctl restart tomcat"
echo "=========================================================="
