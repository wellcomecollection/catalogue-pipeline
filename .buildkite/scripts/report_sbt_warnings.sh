WARNING_COUNT=`cat - | grep -c '\[warn\]'`

if [ $WARNING_COUNT != 0 ]
then
  buildkite-agent annotate "Completed with $WARNING_COUNT warning(s)" --style 'warning' --context 'ctx-warn'
fi
