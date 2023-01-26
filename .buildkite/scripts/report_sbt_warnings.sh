echo "Scanning for warnings"
WARNINGS=$(cat - | grep '\[warn\]')
WARNING_COUNT=$(echo $WARNINGS | sed '/^$/d' | wc -l)

echo "$WARNING_COUNT warning(s) found"

if [ $WARNING_COUNT != 0 ]
then
  buildkite-agent annotate "Completed with $WARNING_COUNT warning(s)\n\`\`\`$WARNINGS\`\`\`" --style 'warning' --context 'ctx-warn'
  exit 2
fi
