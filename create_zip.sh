# Create a temporary directory to hold source code and packages
mkdir -p temp

cp -r src/* temp/
pip3 install -r src/requirements.txt --platform manylinux2014_x86_64 --target temp/ --only-binary=:all: --python-version 3.13

cd temp
zip -r ../build.zip .
cd ..

# Clean up the temporary build directory
rm -rf temp
