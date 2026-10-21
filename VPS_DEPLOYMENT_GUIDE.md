# VPS Deployment Guide: Online Fuel Delivery Management System

This guide walks you through deploying the **Online Fuel Delivery Management System** onto an Ubuntu VPS (such as **AWS EC2**, **DigitalOcean Droplet**, **Hostinger VPS**, **Linode**, or **Hetzner**).

---

## 1. System Architecture

- **Operating System:** Ubuntu 22.04 LTS or 24.04 LTS (Recommended: 1–2 GB RAM minimum)
- **Runtime:** OpenJDK 17
- **Application Server:** Apache Tomcat 10.1 (Jakarta EE 10)
- **Database:** MySQL 8.x (`fuel_delivery_db`)
- **Reverse Proxy:** Nginx (Serves on standard port 80 / 443 SSL)

---

## 2. Firewall / Security Group Setup

Before starting, configure your cloud provider's firewall / AWS Security Group to allow inbound traffic on these ports:

| Port | Protocol | Purpose |
|------|----------|---------|
| **22** | TCP | SSH Access |
| **80** | TCP | HTTP Web Traffic (Nginx) |
| **443**| TCP | HTTPS Web Traffic (SSL) |
| **8080**| TCP | Direct Tomcat Access (Optional) |

---

## 3. Fast-Track: Automated 1-Script Deployment

We have created an automated deployment script [`deploy-ubuntu.sh`](deploy-ubuntu.sh).

### Step 1: Connect to your VPS via SSH
```bash
ssh ubuntu@YOUR_SERVER_IP
# Or for root login:
ssh root@YOUR_SERVER_IP
```

### Step 2: Clone this repository
```bash
git clone https://github.com/yashmasne/Online-Fuel-Delivery-Management-System.git
cd Online-Fuel-Delivery-Management-System
```

### Step 3: Run the installer script
```bash
chmod +x deploy-ubuntu.sh
sudo bash deploy-ubuntu.sh
```

The script will automatically:
1. Install Java 17, MySQL 8, Tomcat 10.1, and Nginx.
2. Initialize the database `fuel_delivery_db` and import [`fuel_delivery.sql`](fuel_delivery.sql).
3. Deploy `Online_Fuel_Delivery_Management_System.war` to Tomcat's `ROOT.war` (so it runs cleanly at `/`).
4. Configure database connection environment variables (`DB_URL`, `DB_USER`, `DB_PASSWORD`).
5. Configure systemd service for Tomcat with auto-restart on boot.
6. Configure Nginx to forward port 80 traffic to Tomcat on port 8080.

---

## 4. Manual Step-by-Step Deployment (Alternative)

If you prefer to configure each component manually:

### Step 1: Update & Install Core Dependencies
```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y openjdk-17-jdk git curl wget unzip mysql-server nginx
```

### Step 2: Configure MySQL & Import Database
```bash
sudo systemctl enable --now mysql

# Access MySQL shell
sudo mysql
```

Run inside MySQL:
```sql
CREATE DATABASE fuel_delivery_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'fuel_user'@'localhost' IDENTIFIED BY 'FuelPass@2026#';
GRANT ALL PRIVILEGES ON fuel_delivery_db.* TO 'fuel_user'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

Import the database schema:
```bash
mysql -u fuel_user -p'FuelPass@2026#' fuel_delivery_db < fuel_delivery.sql
```

### Step 3: Install Apache Tomcat 10.1
```bash
sudo useradd -m -U -d /opt/tomcat -s /bin/false tomcat
cd /tmp
wget https://archive.apache.org/dist/tomcat/tomcat-10/v10.1.34/bin/apache-tomcat-10.1.34.tar.gz
sudo mkdir -p /opt/tomcat
sudo tar -xf apache-tomcat-10.1.34.tar.gz -C /opt/tomcat --strip-components=1

# Remove sample webapps
sudo rm -rf /opt/tomcat/webapps/*
```

### Step 4: Deploy the WAR File
```bash
# Copy your WAR to ROOT.war so it serves from the root domain /
sudo cp Online_Fuel_Delivery_Management_System.war /opt/tomcat/webapps/ROOT.war
```

### Step 5: Configure Environment Variables
Create `/opt/tomcat/bin/setenv.sh`:
```bash
sudo tee /opt/tomcat/bin/setenv.sh << 'EOF'
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export DB_URL="jdbc:mysql://localhost:3306/fuel_delivery_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata"
export DB_USER="fuel_user"
export DB_PASSWORD="FuelPass@2026#"
export CATALINA_OPTS="-Xms512m -Xmx1024m"
EOF

sudo chmod +x /opt/tomcat/bin/setenv.sh
sudo chown -R tomcat:tomcat /opt/tomcat
```

### Step 6: Create Tomcat Systemd Service
Create `/etc/systemd/system/tomcat.service`:
```ini
[Unit]
Description=Apache Tomcat 10
After=network.target mysql.service

[Service]
Type=forking
User=tomcat
Group=tomcat
Environment=JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
Environment=CATALINA_PID=/opt/tomcat/temp/tomcat.pid
Environment=CATALINA_HOME=/opt/tomcat
Environment=CATALINA_BASE=/opt/tomcat
ExecStart=/opt/tomcat/bin/startup.sh
ExecStop=/opt/tomcat/bin/shutdown.sh
RestartSec=10
Restart=always

[Install]
WantedBy=multi-user.target
```

Reload and start Tomcat:
```bash
sudo systemctl daemon-reload
sudo systemctl enable --now tomcat
```

### Step 7: Setup Nginx Reverse Proxy
Edit `/etc/nginx/sites-available/default`:
```nginx
server {
    listen 80;
    server_name _;

    location / {
        proxy_pass http://127.0.0.1:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Restart Nginx:
```bash
sudo nginx -t && sudo systemctl restart nginx
```

---

## 5. Adding a Domain Name and Free SSL (HTTPS)

If you have a domain (e.g. `fuel.yourdomain.com`):

1. Add an **A Record** in your DNS pointing to your VPS public IP.
2. Install Certbot:
   ```bash
   sudo apt install -y certbot python3-certbot-nginx
   ```
3. Issue an SSL certificate:
   ```bash
   sudo certbot --nginx -d fuel.yourdomain.com
   ```
Certbot will automatically configure HTTPS encryption and auto-renewal.

---

## 6. Accessing the Application & Test Logins

Once deployed, visit `http://YOUR_SERVER_IP/`:

| Role | Portal URL | Email | Password |
|------|------------|-------|----------|
| **Admin** | `/Auth_Pages.html` | `admin@fuelexpress.in` | `admin123` |
| **Customer** | `/Auth_Pages.html` or `/Home_Page.html` | `rahul@example.com` | `pass123` |
| **Delivery Agent** | `/Delivery_Agent_Dashboard.html` | `suresh.agent@example.com` | `agent123` |

---

## 7. Useful Operational Commands

- **Check Tomcat Status:**
  ```bash
  sudo systemctl status tomcat
  ```
- **View Live Application Logs:**
  ```bash
  sudo tail -f /opt/tomcat/logs/catalina.out
  ```
- **Restart Application:**
  ```bash
  sudo systemctl restart tomcat
  ```
- **Check MySQL Status:**
  ```bash
  sudo systemctl status mysql
  ```
- **Check Nginx Status & Logs:**
  ```bash
  sudo systemctl status nginx
  sudo tail -f /var/log/nginx/error.log
  ```
