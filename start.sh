#!/bin/bash

# Variables
APP_JAR="/home/ec2-user/app/testing-automation-0.0.1-SNAPSHOT.jar"
LOG_FILE="app.log"
JVM_OPTS="-Xms2G -Xmx4G"

# Find the PID of the running application
PID=$(pgrep -f "java.*-jar.*testing-automation-0.0.1-SNAPSHOT.jar")

if [ -n "$PID" ]; then
    echo "Application is already running with PID(s): $PID. Stopping it..."
    kill -15 $PID
    
    # Wait a few seconds for graceful shutdown
    sleep 3
    
    # Check if the process is still alive, and force kill if necessary
    if pgrep -f "java.*-jar.*testing-automation-0.0.1-SNAPSHOT.jar" > /dev/null; then
        echo "Process did not terminate gracefully. Force killing it..."
        kill -9 $PID
    fi
else
    echo "Application is not currently running."
fi

echo "Starting the application..."
nohup java $JVM_OPTS -jar $APP_JAR > $LOG_FILE 2>&1 &

echo "Application started in background. Logs are being written to $LOG_FILE"
echo "Process ID: $!"
