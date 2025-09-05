#!/bin/bash
set -euo pipefail

# Certificate generation script for mTLS development environment
# Creates self-signed certificates for gRPC communication

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
CERTS_DIR="$PROJECT_ROOT/infra/certs"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')] $*${NC}"
}

log_success() {
    echo -e "${GREEN}[$(date +'%Y-%m-%d %H:%M:%S')] ✓ $*${NC}"
}

log_warning() {
    echo -e "${YELLOW}[$(date +'%Y-%m-%d %H:%M:%S')] ⚠ $*${NC}"
}

# Create certificates directory
mkdir -p "$CERTS_DIR"
cd "$CERTS_DIR"

log "Generating development certificates for mTLS..."

# Generate CA private key
if [[ ! -f ca-key.pem ]]; then
    log "Generating CA private key..."
    openssl genrsa -out ca-key.pem 4096
    log_success "CA private key generated"
else
    log_warning "CA private key already exists"
fi

# Generate CA certificate
if [[ ! -f ca-cert.pem ]]; then
    log "Generating CA certificate..."
    openssl req -new -x509 -days 365 -key ca-key.pem -out ca-cert.pem -subj "/C=US/ST=WA/L=Seattle/O=HuskyApply/OU=Development/CN=HuskyApply-CA"
    log_success "CA certificate generated"
else
    log_warning "CA certificate already exists"
fi

# Generate server private key for Gateway
if [[ ! -f gateway-key.pem ]]; then
    log "Generating Gateway server private key..."
    openssl genrsa -out gateway-key.pem 4096
    log_success "Gateway server private key generated"
else
    log_warning "Gateway server private key already exists"
fi

# Generate server certificate request for Gateway
if [[ ! -f gateway-csr.pem ]]; then
    log "Generating Gateway certificate signing request..."
    openssl req -new -key gateway-key.pem -out gateway-csr.pem -subj "/C=US/ST=WA/L=Seattle/O=HuskyApply/OU=Gateway/CN=gateway"
    log_success "Gateway CSR generated"
fi

# Generate server certificate for Gateway
if [[ ! -f gateway-cert.pem ]]; then
    log "Generating Gateway server certificate..."
    cat > gateway-extensions.conf << EOF
[req]
distinguished_name = req_distinguished_name
req_extensions = v3_req

[req_distinguished_name]

[v3_req]
subjectAltName = @alt_names

[alt_names]
DNS.1 = gateway
DNS.2 = localhost
DNS.3 = huskyapply-gateway
DNS.4 = *.huskyapply.com
IP.1 = 127.0.0.1
IP.2 = ::1
EOF
    
    openssl x509 -req -days 365 -in gateway-csr.pem -CA ca-cert.pem -CAkey ca-key.pem -CAcreateserial -out gateway-cert.pem -extensions v3_req -extfile gateway-extensions.conf
    log_success "Gateway server certificate generated"
else
    log_warning "Gateway server certificate already exists"
fi

# Generate client private key for Brain
if [[ ! -f brain-key.pem ]]; then
    log "Generating Brain client private key..."
    openssl genrsa -out brain-key.pem 4096
    log_success "Brain client private key generated"
else
    log_warning "Brain client private key already exists"
fi

# Generate client certificate request for Brain
if [[ ! -f brain-csr.pem ]]; then
    log "Generating Brain certificate signing request..."
    openssl req -new -key brain-key.pem -out brain-csr.pem -subj "/C=US/ST=WA/L=Seattle/O=HuskyApply/OU=Brain/CN=brain"
    log_success "Brain CSR generated"
fi

# Generate client certificate for Brain
if [[ ! -f brain-cert.pem ]]; then
    log "Generating Brain client certificate..."
    cat > brain-extensions.conf << EOF
[req]
distinguished_name = req_distinguished_name
req_extensions = v3_req

[req_distinguished_name]

[v3_req]
subjectAltName = @alt_names

[alt_names]
DNS.1 = brain
DNS.2 = localhost
DNS.3 = huskyapply-brain
IP.1 = 127.0.0.1
IP.2 = ::1
EOF
    
    openssl x509 -req -days 365 -in brain-csr.pem -CA ca-cert.pem -CAkey ca-key.pem -CAcreateserial -out brain-cert.pem -extensions v3_req -extfile brain-extensions.conf
    log_success "Brain client certificate generated"
else
    log_warning "Brain client certificate already exists"
fi

# Generate Java KeyStore for Gateway
if [[ ! -f gateway-keystore.p12 ]]; then
    log "Generating Gateway KeyStore..."
    
    # Convert to PKCS12 format
    openssl pkcs12 -export -in gateway-cert.pem -inkey gateway-key.pem -certfile ca-cert.pem -out gateway-keystore.p12 -name "gateway" -passout pass:changeit
    
    log_success "Gateway KeyStore generated"
else
    log_warning "Gateway KeyStore already exists"
fi

# Generate Java TrustStore
if [[ ! -f truststore.p12 ]]; then
    log "Generating TrustStore..."
    
    # Import CA certificate into truststore
    keytool -import -trustcacerts -alias ca -file ca-cert.pem -keystore truststore.p12 -storetype PKCS12 -storepass changeit -noprompt
    
    log_success "TrustStore generated"
else
    log_warning "TrustStore already exists"
fi

# Set appropriate permissions
chmod 600 *-key.pem
chmod 644 *-cert.pem ca-cert.pem
chmod 644 *.p12

# Clean up temporary files
rm -f *.csr *.conf *.srl

log_success "All certificates generated successfully!"
log ""
log "Generated files:"
log "  - ca-cert.pem: Certificate Authority certificate"
log "  - gateway-cert.pem, gateway-key.pem: Gateway server certificate and key"
log "  - brain-cert.pem, brain-key.pem: Brain client certificate and key"
log "  - gateway-keystore.p12: Java KeyStore for Gateway (password: changeit)"
log "  - truststore.p12: Java TrustStore (password: changeit)"
log ""
log_warning "These are development certificates only. DO NOT use in production!"

# Create certificate validation script
cat > validate-certs.sh << 'EOF'
#!/bin/bash
# Certificate validation script

CERTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$CERTS_DIR"

echo "Validating certificates..."

# Check CA certificate
echo "CA Certificate:"
openssl x509 -in ca-cert.pem -text -noout | grep -A2 "Subject:"
echo

# Check Gateway certificate
echo "Gateway Certificate:"
openssl x509 -in gateway-cert.pem -text -noout | grep -A2 "Subject:"
openssl x509 -in gateway-cert.pem -text -noout | grep -A10 "Subject Alternative Name"
echo

# Check Brain certificate
echo "Brain Certificate:"
openssl x509 -in brain-cert.pem -text -noout | grep -A2 "Subject:"
openssl x509 -in brain-cert.pem -text -noout | grep -A10 "Subject Alternative Name"
echo

# Verify certificate chain
echo "Verifying certificate chain..."
if openssl verify -CAfile ca-cert.pem gateway-cert.pem; then
    echo "✓ Gateway certificate chain valid"
else
    echo "✗ Gateway certificate chain invalid"
fi

if openssl verify -CAfile ca-cert.pem brain-cert.pem; then
    echo "✓ Brain certificate chain valid"
else
    echo "✗ Brain certificate chain invalid"
fi

# Check expiration
echo
echo "Certificate expiration:"
echo -n "CA: "
openssl x509 -in ca-cert.pem -noout -enddate | cut -d= -f2
echo -n "Gateway: "
openssl x509 -in gateway-cert.pem -noout -enddate | cut -d= -f2
echo -n "Brain: "
openssl x509 -in brain-cert.pem -noout -enddate | cut -d= -f2
EOF

chmod +x validate-certs.sh

log_success "Certificate validation script created: validate-certs.sh"