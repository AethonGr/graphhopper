#!/bin/bash
# Build GraphHopper JAR with database connection fixes
# This script builds the GraphHopper web-bundle JAR that includes GTFS support

set -e

echo "========================================="
echo "Building GraphHopper with Database Fixes"
echo "========================================="

# Navigate to the graphhopper directory
cd "$(dirname "$0")"

echo ""
echo "1. Cleaning previous builds..."
mvn clean

echo ""
echo "2. Building GraphHopper (skipping tests)..."
mvn package -DskipTests

echo ""
echo "3. Locating the built JAR..."
JAR_FILE=$(find . -name "graphhopper-web-*.jar" -type f | grep -v "sources" | grep -v "javadoc" | head -n 1)

if [ -z "$JAR_FILE" ]; then
    echo "ERROR: Could not find built JAR file!"
    echo "Expected to find graphhopper-web-*.jar in web-bundle/target/"
    exit 1
fi

echo "Found JAR: $JAR_FILE"

echo ""
echo "4. Copying JAR to journey_planning_interface_dev..."
cp "$JAR_FILE" ../journey_planning_interface_dev/graphhopper.jar

echo ""
echo "========================================="
echo "Build Complete!"
echo "========================================="
echo ""
echo "The new graphhopper.jar has been copied to journey_planning_interface_dev/"
echo ""
echo "Next steps:"
echo "1. Rebuild the Docker image for journey_planning_interface_dev"
echo "2. Redeploy to DigitalOcean"
echo ""




