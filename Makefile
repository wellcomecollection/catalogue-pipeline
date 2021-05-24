ACCOUNT_ID = 760097843905

include makefiles/functions.Makefile
include makefiles/formatting.Makefile

include common/Makefile
include pipeline/Makefile
include reindexer/Makefile
include sierra_adapter/Makefile
include mets_adapter/Makefile
include calm_adapter/Makefile
include tei_adapter/Makefile

lambda-test: s3_demultiplexer-test \
			 sierra_progress_reporter-test \
			 window_generator-test \
			 calm_deletion_check_initiator-test \
			 tei_updater-test

lambda-publish: snapshot_scheduler-publish \
                snapshot_slack_alarms-publish \
			    update_api_docs-publish \
			    s3_demultiplexer-publish \
			    sierra_progress_reporter-publish \
			    window_generator-publish \
			 	calm_deletion_check_initiator-publish \
			 	calm_window_generator-publish \
			    tei_updater-publish
