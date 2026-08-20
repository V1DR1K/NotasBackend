#!/bin/sh
set -eu

# Named Docker volumes are mounted after image permissions are applied.
chown cuaderno:cuaderno /var/lib/cuaderno/files
exec /usr/bin/su -s /bin/sh cuaderno -c 'exec /opt/java/openjdk/bin/java -jar /app/app.jar'
