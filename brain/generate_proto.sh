#!/bin/bash

# Script to generate Python protobuf and gRPC code from proto files
# Usage: ./generate_proto.sh

set -e

echo "Generating Python protobuf and gRPC code..."

# Define paths
PROTO_DIR="grpc_generated/proto"
OUT_DIR="grpc_generated"

# Create output directory if it doesn't exist
mkdir -p "$OUT_DIR"

# Generate Python code from proto files
python3 -m grpc_tools.protoc \
    -I"$PROTO_DIR" \
    --python_out="$OUT_DIR" \
    --grpc_python_out="$OUT_DIR" \
    "$PROTO_DIR/job_processing.proto"

echo "✓ Generated Python protobuf code in $OUT_DIR"

# Create __init__.py to make it a package
touch "$OUT_DIR/__init__.py"

echo "✓ Created package __init__.py"

# Fix imports in generated files (grpc_python_out generates incorrect imports)
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    sed -i '' 's/import job_processing_pb2/from . import job_processing_pb2/g' "$OUT_DIR/job_processing_pb2_grpc.py"
else
    # Linux
    sed -i 's/import job_processing_pb2/from . import job_processing_pb2/g' "$OUT_DIR/job_processing_pb2_grpc.py"
fi

echo "✓ Fixed imports in generated gRPC code"

echo ""
echo "Successfully generated:"
echo "  - $OUT_DIR/job_processing_pb2.py (protobuf messages)"
echo "  - $OUT_DIR/job_processing_pb2_grpc.py (gRPC service stubs)"
echo ""
echo "You can now import these in your Python code:"
echo "  from grpc import job_processing_pb2"
echo "  from grpc import job_processing_pb2_grpc"
