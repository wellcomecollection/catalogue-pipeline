echo "Scanning for warnings"

WARNING_COUNT=`cat - | grep -c '\[warn\]'`

echo "$WARNING_COUNT warning(s) found"

if [ $WARNING_COUNT != 0 ]
then
  buildkite-agent annotate "Completed with $WARNING_COUNT warning(s)" --style 'warning' --context 'ctx-warn'
fi
