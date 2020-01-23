ACCOUNT_ID = 760097843905

include makefiles/functions.Makefile
include makefiles/formatting.Makefile

include api/Makefile
include common/Makefile
include pipeline/Makefile
include reindexer/Makefile
include sierra_adapter/Makefile
include snapshots/Makefile
include mets_adapter/Makefile
include calm_adapter/Makefile

lambda-test: snapshot_scheduler-test \
			 s3_demultiplexer-test \
			 sierra_progress_reporter-test \
			 sierra_window_generator-test

lambda-publish: snapshot_scheduler-publish \
                snapshot_slack_alarms-publish \
			    update_api_docs-publish \
			    s3_demultiplexer-publish \
			    sierra_progress_reporter-publish \
			    sierra_window_generator-publish
