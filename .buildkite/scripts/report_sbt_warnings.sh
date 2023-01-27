echo "Scanning for warnings"
WARNINGS=$(cat - | grep '\[warn\]'| sort | uniq)
WARNING_COUNT=$(echo $WARNINGS | sed '/^$/d' | wc -l)

if [ $WARNING_COUNT != 0 ]
then
  echo -e "Completed with $WARNING_COUNT unique warning(s)\n\`\`\`term\n$WARNINGS\n\`\`\`" | buildkite-agent annotate  --style 'warning' --context 'ctx-warn'
  exit 2
fi
