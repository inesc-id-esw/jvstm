#!/bin/bash

# Based on the script from:
# http://github.com/chkal/jsf-maven-util/blob/gh-pages/update-directory-index.sh

## This function sets this script's correct path even if it is run from
## another dir

SCRIPT_DIR=
function absname() {
  pushd `dirname "$1"` > /dev/null
  SCRIPT_DIR=`pwd`
  popd > /dev/null
  return
}

absname $0
TARGET_DIR="$SCRIPT_DIR"/../maven-repo

echo "This will overwrite all index.html files on "$TARGET_DIR" and subdirectories!"
echo
echo "Press <Return> to continue or ctrl-c to abort."
echo 
read
for DIR in $(find "$TARGET_DIR"/../maven-repo -type d); do
  (
    echo -e "<html>\n<body>\n<h1>Directory listing</h1>\n<hr/>\n<pre>"
    ls -1pa "${DIR}" | grep -v "^\./$" | grep -v "index.html" | awk '{ printf "<a href=\"%s\">%s</a>\n",$1,$1 }' 
    echo -e "</pre>\n</body>\n</html>"
  ) > "${DIR}/index.html"
done
