echo "Scanning for warnings"
WARNINGS=$(cat - | grep '\[warn\]')
WARNING_COUNT=$(echo "$WARNINGS" | sed '/^$/d' | wc -l | sed 's/^[ \t]*//')
UNIQUE_WARNINGS=$(echo "$WARNINGS" | uniq)
UNIQUE_WARNING_COUNT=$(echo "$UNIQUE_WARNINGS" | sed '/^$/d' | wc -l| sed 's/^[ \t]*//')
MESSAGE="Completed with $WARNING_COUNT ($UNIQUE_WARNING_COUNT distinct) warning(s)\n\`\`\`term\n$UNIQUE_WARNINGS\n\`\`\`"
echo "$MESSAGE"
if [ $WARNING_COUNT != 0 ]
then
  echo -e "$MESSAGE" | buildkite-agent annotate  --style 'warning' --context 'ctx-warn'
  buildkite-agent step update "outcome" "soft_failed"
fi
